"""Builds the MeshAlive prototype video (1920x1080, 30 fps) with motion graphics, real app screens,
time-synced live dashboard footage, narration, captions and a soft music bed.

usage: python build.py <work_dir> <output.mp4> [scene,scene,...]
  <work_dir>/voice/*.mp3 + timings.json   (voice.py)
  <work_dir>/dashframes/f0001.jpg ...     (dashboard.webm from record_dashboard.py, as 30 fps frames, 1400 px wide)
"""
import json
import math
import random
import subprocess
import sys
import wave
from functools import lru_cache
from pathlib import Path

import numpy as np
import qrcode
from PIL import Image, ImageDraw, ImageFilter, ImageFont

sys.path.insert(0, str(Path(__file__).parent))
from script import CUTS, SCENES  # noqa: E402

WORK = Path(sys.argv[1])
OUTFILE = Path(sys.argv[2])
ONLY = sys.argv[3].split(",") if len(sys.argv) > 3 else None   # render only these scenes (preview)
ROOT = Path(__file__).resolve().parents[2]
MOCK = ROOT / "website" / "assets" / "mock"
TMP = WORK / "build"
TMP.mkdir(parents=True, exist_ok=True)

W, H, FPS = 1920, 1080, 30
LEAD, TAIL, MIN_LEN = 0.5, 0.8, 5.0
RED, WHITE, GREY, DIM, LINE = (255, 59, 59), (255, 255, 255), (150, 150, 150), (92, 92, 92), (40, 40, 40)
GREEN, AMBER = (34, 197, 94), (255, 190, 30)
F = "C:/Windows/Fonts/"
CHAPTERS = {"problem": "The problem", "setup": "Setup", "home": "Always ready", "sos": "One-tap SOS",
            "tamil": "Tamil", "mesh": "The mesh", "dashboard": "Control room", "evidence": "Evidence"}


# ---------------------------------------------------------------- helpers
@lru_cache(None)
def font(w, s):
    return ImageFont.truetype(F + {"r": "segoeui.ttf", "sb": "seguisb.ttf", "b": "segoeuib.ttf", "l": "segoeuil.ttf"}[w], s)


def ease(p):
    p = min(1.0, max(0.0, p))
    return 1 - (1 - p) ** 3


def text_layer(txt, w="b", s=64, fill=WHITE, fnt=None):
    f = fnt or font(w, s)
    l, t, r, b = f.getbbox(txt)
    im = Image.new("RGBA", (r - l + 8, b - t + 8), (0, 0, 0, 0))
    ImageDraw.Draw(im).text((4 - l, 4 - t), txt, font=f, fill=fill)
    return im


def phone(name, h):
    p = Image.open(MOCK / f"phone-{name}.png").convert("RGBA")
    return p.resize((int(p.width * h / p.height), h), Image.LANCZOS)


_alpha_cache = {}


def faded(layer, a):
    if a >= 0.999:
        return layer
    k = (id(layer), int(a * 24))
    hit = _alpha_cache.get(k)
    if hit is None or hit[0] is not layer:   # ids can be reused once a layer is freed: check identity
        im = layer.copy()
        al = np.asarray(im.getchannel("A"), dtype=np.float32) * (k[1] / 24)
        im.putalpha(Image.fromarray(al.astype(np.uint8)))
        hit = _alpha_cache[k] = (layer, im)
    return hit[1]


class El:
    """An animated layer: appears at t0 with a fade plus movement ('up', 'right', 'left', 'fade')."""

    def __init__(self, layer, x, y, t0=0.0, kind="up", dur=0.7, dist=40, t1=None):
        self.layer, self.x, self.y, self.t0, self.kind, self.dur, self.dist, self.t1 = layer, x, y, t0, kind, dur, dist, t1

    def draw(self, fr, t):
        if t < self.t0:
            return
        e = ease((t - self.t0) / self.dur)
        a = e
        if self.t1 is not None and t > self.t1:
            a *= 1 - ease((t - self.t1) / 0.4)
        if a <= 0.01:
            return
        dx = dy = 0
        if self.kind == "up":
            dy = self.dist * (1 - e)
        elif self.kind == "right":
            dx = self.dist * 2 * (1 - e)
        elif self.kind == "left":
            dx = -self.dist * 2 * (1 - e)
        lay = faded(self.layer, a)
        fr.paste(lay, (int(self.x + dx), int(self.y + dy)), lay)


def eyebrow(x, y, txt, t0=0.0):
    im = Image.new("RGBA", (900, 40), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse((0, 14, 12, 26), fill=RED)
    d.text((26, 20), txt.upper(), font=font("sb", 24), fill=GREY, anchor="lm")
    return El(im, x, y, t0, "fade")


def heading(x, y, lines, size=72, t0=0.15, colors=None, stagger=0.18):
    els = []
    for i, ln in enumerate(lines):
        els.append(El(text_layer(ln, "b", size, (colors or [WHITE] * len(lines))[i]), x, y + i * size * 1.16, t0 + i * stagger))
    return els


def bullet_rows(x, y, items, times, size=31, width=700):
    els = []
    for i, (it, t0) in enumerate(zip(items, times)):
        im = Image.new("RGBA", (width, size + 40), (0, 0, 0, 0))
        d = ImageDraw.Draw(im)
        d.line((0, 0, width, 0), fill=LINE, width=2)
        d.ellipse((2, 20 + size * 0.42, 12, 30 + size * 0.42), fill=RED)
        d.text((30, 18), it, font=font("r", size), fill=(228, 228, 228))
        els.append(El(im, x, y + i * (size + 40), t0, "left", dist=20))
    return els


def card(w, h, fill=(14, 14, 14), outline=LINE, r=20):
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(im).rounded_rectangle((0, 0, w - 1, h - 1), radius=r, fill=fill + (255,), outline=outline, width=2)
    return im


def logo_layer(s=1.0, text=True):
    im = Image.new("RGBA", (int(330 * s), int(50 * s)), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    x, y, r = 8 * s, 8 * s, 7 * s
    pts = [(x + 16 * s, y), (x, y + 28 * s), (x + 32 * s, y + 28 * s)]
    for a, b in [(0, 1), (0, 2), (1, 2)]:
        d.line([pts[a], pts[b]], fill=RED, width=max(2, int(4.5 * s)))
    d.ellipse((pts[0][0] - r, pts[0][1] - r, pts[0][0] + r, pts[0][1] + r), fill=RED)
    for p in pts[1:]:
        d.ellipse((p[0] - r * .85, p[1] - r * .85, p[0] + r * .85, p[1] + r * .85), fill=WHITE)
    if text:
        d.text((x + 50 * s, y + 14 * s), "MeshAlive", font=font("b", int(30 * s)), fill=WHITE, anchor="lm")
    return im


def check_icon(size=26, color=GREEN):
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse((0, 0, size - 1, size - 1), fill=color)
    d.line([(size * .27, size * .52), (size * .44, size * .68), (size * .74, size * .34)], fill=(0, 0, 0), width=max(2, size // 9))
    return im


# ---------------------------------------------------------------- background
class MeshBG:
    """Slowly drifting network of dots and faint links, like the website hero."""

    def __init__(self, seed=7, n=46):
        rnd = random.Random(seed)
        self.nodes = [[rnd.uniform(0, W), rnd.uniform(0, H), rnd.uniform(-12, 12), rnd.uniform(-12, 12)] for _ in range(n)]
        glow = Image.new("L", (W, H), 0)
        ImageDraw.Draw(glow).ellipse((W * 0.5, -H * 0.4, W * 1.4, H * 0.8), fill=24)
        glow = glow.filter(ImageFilter.GaussianBlur(170))
        self.base = Image.new("RGB", (W, H), (0, 0, 0))
        self.base.paste(Image.new("RGB", (W, H), (255, 255, 255)), (0, 0), glow)

    def frame(self, t, strength=1.0):
        fr = self.base.copy()
        if strength <= 0:
            return fr
        d = ImageDraw.Draw(fr)
        pts = []
        for x, y, vx, vy in self.nodes:
            pts.append(((x + vx * t) % (W + 200) - 100, (y + vy * t) % (H + 200) - 100))
        R = 230
        for i in range(len(pts)):
            for j in range(i + 1, len(pts)):
                dx, dy = pts[i][0] - pts[j][0], pts[i][1] - pts[j][1]
                dd = dx * dx + dy * dy
                if dd < R * R:
                    c = int((1 - math.sqrt(dd) / R) * 34 * strength)
                    if c > 2:
                        d.line([pts[i], pts[j]], fill=(c, c, c), width=1)
        c = int(70 * strength)
        for px, py in pts:
            d.ellipse((px - 2, py - 2, px + 2, py + 2), fill=(c, c, c))
        return fr


BG = MeshBG()

# ---------------------------------------------------------------- timings
voice = WORK / "voice"
timings = json.loads((voice / "timings.json").read_text(encoding="utf-8"))


def dur(path):
    return float(subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(path)]).decode().strip())


def st(scene, idx):
    return timings[scene][idx]["start"] + LEAD


def st_prefix(scene, prefix):
    for s in timings[scene]:
        if s["text"].strip().startswith(prefix):
            return s["start"] + LEAD
    raise SystemExit(f"no sentence {prefix!r} in {scene}")


# ---------------------------------------------------------------- scenes (each returns draw(t) -> RGB frame)
def sc_title(L):
    els = [El(logo_layer(1.3), 140, 150, 0.1, "fade")]
    els += heading(140, 330, ["Help reaches you,", "even when the", "network doesn’t."], 92, 0.35, [WHITE, GREY, GREY], 0.22)
    els.append(El(text_layer("An offline disaster-response network for India", "r", 34, (205, 205, 205)), 140, 690, 1.3))
    els.append(El(text_layer("SevaFirst Innovation Challenge 2026  ·  Working prototype", "r", 26, DIM), 140, 748, 1.6))
    trio = Image.open(MOCK / "phones-trio.png").convert("RGBA")
    trio = trio.resize((int(trio.width * 760 / trio.height), 760), Image.LANCZOS)
    els.append(El(trio, W - trio.width - 40, 150, 0.6, "right", 1.1, 60))

    def draw(t):
        fr = BG.frame(t, 1.0)
        for e in els:
            e.draw(fr, t)
        return fr
    return draw


def sc_problem(L):
    els = [eyebrow(140, 120, "The problem")]
    els += heading(140, 175, ["When the towers go down,", "rescue goes blind."], 84, 0.2, [WHITE, GREY])
    data = [("2015", "Chennai floods", ["Mobile networks down", "for days across the city"], RED),
            ("2023", "Cyclone Michaung", ["Neighbourhoods cut off", "while the water kept rising"], RED),
            ("Today", "Control rooms work blind", ["No view of who needs help,", "where, or how urgent"], WHITE)]
    times = [st("problem", 1) - 0.2, st("problem", 1) + 2.0, st("problem", 2) + 0.3]
    for i, (big, t1, sub, col) in enumerate(data):
        c = card(520, 300)
        d = ImageDraw.Draw(c)
        d.text((36, 26), big, font=font("b", 66), fill=col)
        d.text((36, 136), t1, font=font("sb", 34), fill=WHITE)
        for j, ln in enumerate(sub):
            d.text((36, 190 + j * 34), ln, font=font("r", 26), fill=GREY)
        els.append(El(c, 140 + i * 560, 520, times[i], "up", 0.8, 60))

    def draw(t):
        fr = BG.frame(t, 0.6)
        for e in els:
            e.draw(fr, t)
        return fr
    return draw


def sc_setup(L):
    els = [eyebrow(140, 150, "Setup · 30 seconds")]
    els += heading(140, 205, ["Ready before", "disaster strikes."], 72, 0.2, [WHITE, GREY])
    s1 = st("setup", 1)
    els += bullet_rows(140, 430, ["Free Android app", "English or Tamil", "Household details, entered once", "Citizen, rescue carrier or gateway"],
                       [0.9, s1 + 0.2, s1 + 1.6, s1 + 3.4], 29, 520)
    ps = [phone(n, 640) for n in ["welcome", "language", "role"]]
    x = W - 110 - (sum(p.width for p in ps) - 40)
    for i, p in enumerate(ps):
        els.append(El(p, x, 190, 0.5 if i == 0 else s1 + (i - 1) * 1.8, "up", 0.9, 80))
        x += p.width - 20

    def draw(t):
        fr = BG.frame(t, 0.5)
        for e in els:
            e.draw(fr, t)
        return fr
    return draw


def sc_home(L):
    els = [eyebrow(140, 150, "Always ready")]
    els += heading(140, 205, ["No towers.", "No internet.", "Still connected."], 78, 0.2, [WHITE, WHITE, GREY], 0.3)
    els += bullet_rows(140, 540, ["Finds nearby phones over Bluetooth and Wi-Fi", "Live mesh status on the home screen"], [2.4, 3.6], 30, 740)
    p = phone("home", 800)
    els.append(El(p, 1380 - p.width // 2, 110, 0.3, "up", 0.9, 80))
    cx, cy = 1380, 110 + 800 * 0.58

    def draw(t):
        fr = BG.frame(t, 0.4)
        if t > 1.2:   # radar rings: the phone looking for neighbours
            d = ImageDraw.Draw(fr)
            for k in range(3):
                ph = ((t - 1.2) / 2.4 + k / 3) % 1
                r = 120 + ph * 420
                c = int(110 * (1 - ph))
                d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=(c, int(c * .3), int(c * .3)), width=3)
        for e in els:
            e.draw(fr, t)
        return fr
    return draw


def sc_sos(L):
    cut = st_prefix("sos", CUTS["sos"])
    a = [eyebrow(140, 150, "One-tap SOS")]
    a += heading(140, 205, ["An SOS that says", "how urgent it is."], 72, 0.2, [WHITE, GREY])
    s1, s2 = st("sos", 1), st("sos", 2)
    a += bullet_rows(140, 430, ["GPS location", "People, trapped, injured, elderly", "Water level: knee to roof", "Urgency score 0–100"],
                     [s1 + 0.3, s1 + 2.6, s1 + 6.5, s2 + 0.2], 30, 640)
    p = phone("situation", 800)
    a.append(El(p, 1380 - p.width // 2, 110, 0.3, "up", 0.9, 80))
    t_score = st("sos", 3) - 0.3
    b = [eyebrow(140, 150, "No false alarms", cut)]
    b += heading(140, 205, ["3-second countdown,", "with Cancel."], 72, cut + 0.15, [WHITE, GREY])
    pc = phone("countdown", 800)
    pel = El(pc, 1380 - pc.width // 2, 110, cut + 0.1, "up", 0.8, 60)

    def draw(t):
        fr = BG.frame(t, 0.4)
        if t < cut:
            for e in a:
                e.draw(fr, t)
            if t > t_score:   # urgency badge counts up to 81
                val = int(round(81 * ease((t - t_score) / 1.4)))
                lay = Image.new("RGBA", (260, 130), (0, 0, 0, 0))
                d = ImageDraw.Draw(lay)
                d.rounded_rectangle((0, 0, 259, 129), radius=20, fill=RED + (255,))
                d.text((130, 52), str(val), font=font("b", 60), fill=WHITE, anchor="mm")
                d.text((130, 104), "CRITICAL" if val >= 75 else "URGENCY", font=font("b", 22), fill=WHITE, anchor="mm")
                lay = faded(lay, ease((t - t_score) / 0.3))
                fr.paste(lay, (1570, 670), lay)
        else:
            tb = t - cut
            d = ImageDraw.Draw(fr)
            ph = (tb % 1.1) / 1.1
            r = 430 + ph * 120
            c = int(120 * (1 - ph))
            d.ellipse((1380 - r, 510 - r, 1380 + r, 510 + r), outline=(c, int(c * .25), int(c * .25)), width=4)
            for e in b:
                e.draw(fr, t)
            if tb > 0.6:
                if tb < 0.6 + 3.3:
                    n = 3 - int(min(2.99, (tb - 0.6) / 1.1))
                    d.text((140, 470), str(n), font=font("b", 220), fill=RED)
                    d.text((140 + 160, 610), "seconds to cancel", font=font("r", 32), fill=GREY)
                else:
                    d.text((140, 500), "Sent.", font=font("b", 130), fill=WHITE)
                    d.text((140, 680), "With your location and situation report.", font=font("r", 32), fill=GREY)
            pel.draw(fr, t)
        return fr
    return draw


def sc_tamil(L):
    els = [eyebrow(140, 150, "In your language")]
    els += heading(140, 205, ["Works fully", "in Tamil."], 84, 0.2, [WHITE, GREY])
    els.append(El(text_layer("தமிழ்", fnt=ImageFont.truetype(F + "Nirmala.ttc", 140, index=1), fill=(80, 80, 80)), 140, 450, 0.6, "fade"))
    p = phone("tamil", 800)
    els.append(El(p, 1380 - p.width // 2, 110, 0.2, "up", 0.8, 70))

    def draw(t):
        fr = BG.frame(t, 0.4)
        for e in els:
            e.draw(fr, t)
        return fr
    return draw


def sc_mesh(L):
    cut = st_prefix("mesh", CUTS["mesh"])
    X0, Y0, MW, MH = 180, 430, 1080, 460
    N = {"sos": (0.06, 0.62), "a": (0.25, 0.40), "b": (0.27, 0.82), "c": (0.45, 0.58), "d": (0.47, 0.20),
         "e": (0.64, 0.76), "f": (0.66, 0.36), "gw": (0.92, 0.52), "iso": (0.10, 0.10)}
    P = {k: (X0 + v[0] * MW, Y0 + v[1] * MH) for k, v in N.items()}
    links = [("sos", "a"), ("sos", "b"), ("a", "c"), ("b", "c"), ("a", "d"), ("c", "e"), ("c", "f"), ("d", "f"), ("e", "gw"), ("f", "gw"), ("b", "e")]
    route = ["sos", "a", "c", "f", "gw"]
    hop_t = 0.85
    cycle = hop_t * 4 + 1.2
    s_hop = st("mesh", 1)
    head_a = heading(140, 175, ["Battery-aware routing.", "Most urgent first."], 64, 0.2, [WHITE, GREY])
    head_b = heading(140, 175, ["No route? A rescue boat", "carries it out."], 64, cut + 0.1, [WHITE, GREY])
    eb_a, eb_b = eyebrow(140, 120, "Phone to phone"), eyebrow(140, 120, "Rescue carriers", cut)
    p = phone("routing-log", 700)
    ph = El(p, 1600 - p.width // 2, 170, 0.4, "up", 0.9, 70)
    lbl = {"sos": text_layer("Person in danger", "sb", 22), "gw": text_layer("Phone with signal", "sb", 22),
           "d": text_layer("low battery: avoided", "r", 20, (130, 130, 130))}
    sub_gw = text_layer("→ control room", "r", 20, GREY)
    iso_l, boat_l = text_layer("No phone in range", "sb", 22), text_layer("Rescue carrier", "sb", 22)
    boat_path = [(P["iso"][0] + 30, P["iso"][1] + 10), (P["iso"][0] + 220, P["iso"][1] + 40), (P["d"][0] + 140, P["d"][1] - 30), (P["gw"][0] - 70, P["gw"][1] - 110)]

    def along(path, u):
        segs = list(zip(path, path[1:]))
        lens = [math.dist(a, b) for a, b in segs]
        dd = u * sum(lens)
        for (a, b), ln in zip(segs, lens):
            if dd <= ln:
                k = dd / ln
                return a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k
            dd -= ln
        return path[-1]

    def draw(t):
        fr = BG.frame(t, 0.3)
        d = ImageDraw.Draw(fr)
        carrier = t >= cut
        for i, (a, b) in enumerate(links):
            k = ease((t - 0.3 - i * 0.06) / 0.5)
            if k > 0:
                (ax, ay), (bx, by) = P[a], P[b]
                d.line([(ax, ay), (ax + (bx - ax) * k, ay + (by - ay) * k)], fill=(60, 60, 60), width=3)
        delivered = False
        if not carrier and t > s_hop:
            u = ((t - s_hop) % cycle) / hop_t
            for i in range(4):
                if u > i:
                    a, b = P[route[i]], P[route[i + 1]]
                    k = min(1, u - i)
                    d.line([a, (a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k)], fill=RED, width=6)
            if u < 4:
                i = int(u)
                a, b = P[route[i]], P[route[i + 1]]
                k = u - i
                px, py = a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k
                d.ellipse((px - 11, py - 11, px + 11, py + 11), fill=RED, outline=WHITE, width=2)
            else:
                delivered = True
        if t > 0.6:
            for k_, (x, y) in P.items():
                if k_ in ("sos", "gw", "iso"):
                    continue
                c = (100, 100, 100) if k_ == "d" else (215, 215, 215)
                d.ellipse((x - 13, y - 13, x + 13, y + 13), fill=(18, 18, 18), outline=c, width=3)
            sx, sy = P["sos"]
            pulse = 26 + 5 * math.sin(t * 5)
            d.ellipse((sx - pulse, sy - pulse, sx + pulse, sy + pulse), fill=RED)
            d.text((sx, sy), "SOS", font=font("b", 18), fill=WHITE, anchor="mm")
            gx, gy = P["gw"]
            d.rounded_rectangle((gx - 30, gy - 30, gx + 30, gy + 30), radius=10, fill=GREEN)
            if delivered or (carrier and t > cut + 4.9):
                d.rounded_rectangle((gx - 42, gy - 42, gx + 42, gy + 42), radius=14, outline=GREEN, width=3)
            for k_, lay in lbl.items():
                x, y = P[k_]
                yy = y - 50 if k_ == "d" else y + 40
                fr.paste(lay, (int(x - lay.width / 2), int(yy)), lay)
            fr.paste(sub_gw, (int(gx - sub_gw.width / 2), int(gy + 68)), sub_gw)
        if carrier:
            tc = t - cut
            ix, iy = P["iso"]
            d.ellipse((ix - 22, iy - 22, ix + 22, iy + 22), fill=RED)
            d.text((ix, iy), "SOS", font=font("b", 16), fill=WHITE, anchor="mm")
            fr.paste(iso_l, (int(ix + 34), int(iy - 16)), iso_l)
            u = ease((tc - 1.0) / 3.8)
            n = 70
            for i in range(int(n * u)):
                if i % 2 == 0:
                    d.line([along(boat_path, i / n), along(boat_path, (i + 1) / n)], fill=AMBER, width=4)
            if tc > 0.4:
                bx, by = along(boat_path, u * 0.999)
                d.rounded_rectangle((bx - 46, by - 22, bx + 46, by + 22), radius=11, fill=AMBER)
                d.text((bx, by), "BOAT", font=font("b", 20), fill=(0, 0, 0), anchor="mm")
                if tc > 1.0:
                    d.ellipse((bx + 36, by - 34, bx + 56, by - 14), fill=RED, outline=WHITE, width=2)
                fr.paste(boat_l, (int(bx - boat_l.width / 2), int(by - 62)), boat_l)
        for e in ([eb_a] + head_a if not carrier else [eb_b] + head_b):
            e.draw(fr, t)
        ph.draw(fr, t)
        return fr
    return draw


def sc_dashboard(L):
    frames = sorted((WORK / "dashframes").glob("f*.jpg"))
    nf = len(frames)
    S = [st("dashboard", i) for i in range(6)]
    # scene time -> footage time anchors, so each UI event lands on the sentence that describes it
    anchors = [(0.0, 0.3), (S[1], 6.4), (S[2], 11.6), (S[3], 14.3), (S[4], 18.3), (S[5], 22.6), (L, (nf - 1) / FPS)]

    def ftime(t):
        for (t0, f0), (t1, f1) in zip(anchors, anchors[1:]):
            if t <= t1:
                return f0 + (f1 - f0) * (t - t0) / max(1e-6, t1 - t0)
        return anchors[-1][1]

    bar = Image.new("RGB", (1400, 40), (26, 26, 26))
    bd = ImageDraw.Draw(bar)
    for i, c in enumerate([(255, 95, 87), (254, 188, 46), (40, 200, 64)]):
        bd.ellipse((18 + i * 22, 14, 30 + i * 22, 26), fill=c)
    bd.rounded_rectangle((420, 7, 980, 33), radius=13, fill=(44, 44, 44))
    bd.text((700, 20), "meshalive.benzo.co.in/dashboard", font=font("r", 17), fill=(205, 205, 205), anchor="mm")
    BX, BY = (W - 1400) // 2, 26
    callouts = [(S[1] + 0.8, "Sorted by urgency", "Most critical on top", RED),
                (S[2] + 1.6, "Rescue team dispatched", "One click, back through the mesh", WHITE),
                (S[3] + 0.3, "Family’s phone confirmed", "“Rescue team on the way”", GREEN),
                (S[4] + 0.6, "Evacuation alert sent", "To every phone in the mesh", AMBER),
                (S[5] + 0.5, "Digitally signed", "Fake orders are rejected", WHITE)]
    lays = []
    for t0, a, b, col in callouts:
        c = card(470, 104, (12, 12, 12), (70, 70, 70), 16)
        d = ImageDraw.Draw(c)
        if col == GREEN:
            ic = check_icon(30)
            c.paste(ic, (24, 26), ic)
        else:
            d.ellipse((28, 32, 46, 50), fill=col)
        d.text((68, 16), a, font=font("sb", 30), fill=WHITE)
        d.text((68, 60), b, font=font("r", 22), fill=GREY)
        lays.append((t0, c))
    cache = {}

    def foot(i):
        if i not in cache:
            cache.clear()
            cache[i] = Image.open(frames[i]).convert("RGB")
        return cache[i]

    def draw(t):
        fr = Image.new("RGB", (W, H), (0, 0, 0))
        fi = min(nf - 1, max(0, int(round(ftime(t) * FPS))))
        fr.paste(bar, (BX, BY))
        fr.paste(foot(fi), (BX, BY + 40))
        cur = [c for c in lays if c[0] <= t]
        if cur:
            t0, c = cur[-1]
            e = ease((t - t0) / 0.5)
            lay = faded(c, e)
            fr.paste(lay, (int(BX + 1400 - 490 + 40 * (1 - e)), BY + 120), lay)
        return fr
    return draw


def sc_evidence(L):
    els = [eyebrow(140, 120, "Evidence")]
    els += heading(140, 170, ["Built, tested, measured."], 72, 0.2)
    s1, s2 = st("evidence", 1), st("evidence", 2)
    stats = [(100, "%", "SOS delivered (200-phone simulation)", s1 + 0.2),
             (147, "×", "fewer radio transmissions than flooding", s1 + 2.4),
             (2, "×", "battery left on the weakest phone", s1 + 4.0),
             (49, "", "automated tests passing", s1 + 5.0)]
    pos = [(140, 320), (700, 320), (140, 550), (700, 550)]
    cards = []
    for v, suf, lab, t0 in stats:
        c = card(520, 190)
        ImageDraw.Draw(c).text((32, 128), lab, font=font("r", 25), fill=GREY)
        cards.append(c)
    p = phone("planner", 760)
    pe = El(p, 1580 - p.width // 2, 130, s2 - 0.3, "up", 0.9, 80)
    bar_t = s1 + 3.4

    def draw(t):
        fr = BG.frame(t, 0.35)
        for e in els:
            e.draw(fr, t)
        for c, (v, suf, lab, t0), (x, y) in zip(cards, stats, pos):
            if t < t0:
                continue
            e = ease((t - t0) / 0.6)
            lay = faded(c, e)
            yy = int(y + 30 * (1 - e))
            fr.paste(lay, (x, yy), lay)
            val = int(round(v * ease((t - t0) / 1.3)))
            ImageDraw.Draw(fr).text((x + 32, yy + 18), f"{val}{suf}", font=font("b", 78), fill=WHITE)
        if t > bar_t:   # radio transmissions: flooding vs MeshAlive
            d = ImageDraw.Draw(fr)
            k = ease((t - bar_t) / 1.6)
            d.text((140, 790), "Radio transmissions for 20 SOS, same network", font=font("sb", 24), fill=GREY)
            d.rectangle((140, 836, 140 + int(900 * k), 862), fill=(80, 80, 80))
            d.text((160 + int(900 * k), 849), "Flooding  14,533", font=font("sb", 24), fill=GREY, anchor="lm")
            d.rectangle((140, 882, 140 + max(4, int(900 * k * 99 / 14533)), 908), fill=RED)
            d.text((166 + int(900 * k * 99 / 14533), 895), "MeshAlive  99", font=font("sb", 24), fill=WHITE, anchor="lm")
        pe.draw(fr, t)
        return fr
    return draw


def sc_close(L):
    qr = qrcode.QRCode(border=2, box_size=8)
    qr.add_data("https://meshalive.benzo.co.in")
    qr.make()
    qim = qr.make_image(fill_color="black", back_color="white").convert("RGBA").resize((190, 190), Image.NEAREST)
    els = [El(logo_layer(1.6), W // 2 - 150, 150, 0.1, "fade")]
    chips = ["No towers", "No extra hardware", "₹0 for citizens"]
    f = font("sb", 32)
    widths = [int(f.getlength(c)) + 64 for c in chips]
    x = W // 2 - (sum(widths) + 40) // 2
    for i, (c, w) in enumerate(zip(chips, widths)):
        im = Image.new("RGBA", (w, 62), (0, 0, 0, 0))
        dd = ImageDraw.Draw(im)
        dd.rounded_rectangle((0, 0, w - 1, 61), radius=30, outline=(90, 90, 90), width=2)
        dd.text((w / 2, 31), c, font=f, fill=WHITE, anchor="mm")
        els.append(El(im, x, 300, st("close", i), "up", 0.6, 25))
        x += w + 20
    l1, l2 = text_layer("Because when the network fails,", "b", 80), text_layer("help shouldn’t.", "b", 80, GREY)
    els += [El(l1, W // 2 - l1.width // 2, 430, st("close", 3) - 0.1), El(l2, W // 2 - l2.width // 2, 528, st("close", 4) - 0.1)]
    url = text_layer("meshalive.benzo.co.in", "b", 46)
    sub = text_layer("Download the app  ·  Try the control room", "r", 26, GREY)
    tq = st("close", 3) + 0.4
    els.append(El(url, W // 2 - 380, 730, tq))
    els.append(El(sub, W // 2 - 380, 800, tq + 0.2))
    qbox = Image.new("RGBA", (210, 210), (255, 255, 255, 255))
    qbox.paste(qim, (10, 10))
    els.append(El(qbox, W // 2 + 200, 690, tq + 0.3, "fade"))

    def draw(t):
        fr = BG.frame(t, 0.9)
        for e in els:
            e.draw(fr, t)
        return fr
    return draw


BUILDERS = {"title": sc_title, "problem": sc_problem, "setup": sc_setup, "home": sc_home, "sos": sc_sos, "tamil": sc_tamil,
            "mesh": sc_mesh, "dashboard": sc_dashboard, "evidence": sc_evidence, "close": sc_close}


# ---------------------------------------------------------------- rendering
def chapter_tag(i, name):
    im = Image.new("RGBA", (520, 40), (0, 0, 0, 0))
    ImageDraw.Draw(im).text((519, 20), f"{i:02d}  ·  {name.upper()}", font=font("sb", 20), fill=(120, 120, 120), anchor="rm")
    return im


def run(cmd, **kw):
    r = subprocess.run(cmd, capture_output=True, text=True, **kw)
    if r.returncode:
        print(r.stderr[-3000:])
        raise SystemExit("ffmpeg failed")


def render_scene(si, scene, length, t_global, total):
    _alpha_cache.clear()
    draw = BUILDERS[scene](length)
    out = TMP / f"v_{si:02d}.mp4"
    n = int(round(length * FPS))
    chap = chapter_tag(list(CHAPTERS).index(scene) + 1, CHAPTERS[scene]) if scene in CHAPTERS and scene != "dashboard" else None
    p = subprocess.Popen(["ffmpeg", "-v", "error", "-y", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-",
                          "-c:v", "libx264", "-preset", "medium", "-crf", "16", "-pix_fmt", "yuv420p", str(out)], stdin=subprocess.PIPE)
    black = Image.new("RGB", (W, H))
    for i in range(n):
        t = i / FPS
        fr = draw(t)
        if chap is not None:
            fr.paste(chap, (W - 140 - 520, 52), chap)
        ImageDraw.Draw(fr).rectangle((0, H - 5, int(W * (t_global + t) / total), H), fill=RED)
        k = min(1.0, t / 0.3, (length - t) / 0.3)
        if k < 1:
            fr = Image.blend(black, fr, max(0.0, k))
        p.stdin.write(fr.tobytes())
    p.stdin.close()
    p.wait()
    return out


def music(total, path):
    """Soft ambient pad: slow sine chords (Am – F – C – G) with gentle swells, low in the mix."""
    sr = 48000
    tt = np.arange(int(total * sr)) / sr
    chords = [[220.0, 261.63, 329.63], [174.61, 220.0, 261.63], [261.63, 329.63, 392.0], [196.0, 246.94, 293.66]]
    seg = 6.0
    out = np.zeros_like(tt)
    for ci in range(int(total // seg) + 2):
        t0 = ci * seg
        m = (tt >= t0 - 1.5) & (tt < t0 + seg + 1.5)
        x = tt[m] - t0
        env = np.clip((x + 1.5) / 2.5, 0, 1) * np.clip((seg + 1.5 - x) / 2.5, 0, 1)
        sig = np.zeros_like(x)
        for f0 in chords[ci % 4]:
            for h, a in [(1, 1.0), (2, 0.15), (0.5, 0.4)]:
                sig += a * np.sin(2 * np.pi * f0 * h * x + h)
        out[m] += env * sig
    out *= 0.85 + 0.15 * np.sin(2 * np.pi * 0.11 * tt)
    out *= np.clip(tt / 3, 0, 1) * np.clip((total - tt) / 4, 0, 1)
    out = out / np.max(np.abs(out)) * 0.5
    stereo = np.stack([out, np.roll(out, 240)], axis=1)
    with wave.open(str(path), "wb") as w:
        w.setnchannels(2); w.setsampwidth(2); w.setframerate(sr)
        w.writeframes((stereo * 32767).astype(np.int16).tobytes())


def chunks(start, end, text, limit=60):
    words, out, cur = text.split(), [], ""
    for w_ in words:
        if cur and len(cur) + 1 + len(w_) > limit:
            out.append(cur); cur = w_
        else:
            cur = (cur + " " + w_).strip()
    out.append(cur)
    tot = sum(len(c) for c in out)
    res, s = [], start
    for c in out:
        e = s + (end - start) * len(c) / tot
        res.append((s, e, c)); s = e
    return res


def ts(x):
    return f"{int(x // 3600)}:{int(x % 3600 // 60):02d}:{x % 60:05.2f}"


lengths = [max(MIN_LEN, LEAD + dur(voice / f"{s}.mp3") + TAIL) + (2.5 if s == "close" else 0) for s, _ in SCENES]
total = sum(lengths)
segments, captions, tg = [], [], 0.0
for si, (scene, _) in enumerate(SCENES):
    length = lengths[si]
    a = voice / f"{scene}.mp3"
    if ONLY is None or scene in ONLY:
        v = render_scene(si, scene, length, tg, total)
        seg = TMP / f"seg_{si:02d}.mp4"
        run(["ffmpeg", "-v", "error", "-y", "-i", str(v), "-i", str(a), "-filter_complex",
             f"[1:a]adelay={int(LEAD * 1000)}:all=1,apad,atrim=0:{length:.3f},aformat=sample_rates=48000:channel_layouts=stereo[a]",
             "-map", "0:v", "-map", "[a]", "-t", f"{length:.3f}", "-c:v", "copy", "-c:a", "aac", "-b:a", "192k", str(seg)])
        print(f"{scene:10s} {length:6.2f}s rendered", flush=True)
    segments.append(TMP / f"seg_{si:02d}.mp4")
    for s in timings[scene]:
        captions.append((tg + LEAD + s["start"], tg + LEAD + s["end"], s["text"]))
    tg += length

if ONLY is not None:
    raise SystemExit(0)

ass = ["[Script Info]", "ScriptType: v4.00+", f"PlayResX: {W}", f"PlayResY: {H}", "", "[V4+ Styles]",
       "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding",
       "Style: Cap,Segoe UI Semibold,40,&H00FFFFFF,&H00FFFFFF,&H00000000,&H40000000,0,0,0,0,100,100,0,0,3,12,0,2,200,200,46,1",
       "", "[Events]", "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text"]
for s, e, txt in captions:
    for cs, ce, ct in chunks(s, e, txt):
        ass.append(f"Dialogue: 0,{ts(cs)},{ts(ce)},Cap,,0,0,0,,{ct}")
(TMP / "captions.ass").write_text("\n".join(ass) + "\n", encoding="utf-8")

(TMP / "segments.txt").write_text("".join(f"file '{p.as_posix()}'\n" for p in segments))
run(["ffmpeg", "-v", "error", "-y", "-f", "concat", "-safe", "0", "-i", str(TMP / "segments.txt"), "-c", "copy", str(TMP / "joined.mp4")])
music(total, TMP / "music.wav")
# voice + music (ducked under the voice), captions burned in, loudness normalised
run(["ffmpeg", "-v", "error", "-y", "-i", "joined.mp4", "-i", "music.wav", "-filter_complex",
     "[0:a]asplit=2[vo][sc];[1:a]volume=0.16[m];[m][sc]sidechaincompress=threshold=0.03:ratio=6:attack=40:release=600[md];"
     "[vo][md]amix=inputs=2:duration=first:normalize=0,loudnorm=I=-16:TP=-1.5:LRA=11[a];[0:v]subtitles=captions.ass[v]",
     "-map", "[v]", "-map", "[a]", "-c:v", "libx264", "-preset", "slow", "-crf", "20", "-pix_fmt", "yuv420p",
     "-ar", "48000", "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart", str(OUTFILE.resolve())], cwd=TMP)
print("total", round(total, 1), "s ->", OUTFILE)

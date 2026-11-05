"""Builds the MeshAlive prototype video (1920x1080, 30 fps) from real app screens, live dashboard
footage and the narration made by voice.py.

usage: python build.py <work_dir> <output.mp4>
  <work_dir>/voice/*.mp3 + timings.json   (voice.py)
  <work_dir>/dash/dashboard.webm          (record_dashboard.py)
"""
import json
import math
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

sys.path.insert(0, str(Path(__file__).parent))
from script import SCENES  # noqa: E402

WORK = Path(sys.argv[1])
OUTFILE = Path(sys.argv[2])
ROOT = Path(__file__).resolve().parents[2]
MOCK = ROOT / "website" / "assets" / "mock"
TMP = WORK / "build"
TMP.mkdir(parents=True, exist_ok=True)

W, H, FPS = 1920, 1080, 30
LEAD, TAIL = 0.45, 0.75          # silence before / after each scene's narration
RED = (255, 59, 59)
WHITE = (255, 255, 255)
GREY = (143, 143, 143)
DIM = (92, 92, 92)
LINE = (38, 38, 38)
FONTS = "C:/Windows/Fonts/"


def font(weight, size):
    return ImageFont.truetype(FONTS + {"r": "segoeui.ttf", "sb": "seguisb.ttf", "b": "segoeuib.ttf", "l": "segoeuil.ttf"}[weight], size)


def canvas():
    im = Image.new("RGB", (W, H), (0, 0, 0))
    # very soft vignette glow so pure black does not look flat
    glow = Image.new("L", (W, H), 0)
    ImageDraw.Draw(glow).ellipse((W * 0.45, -H * 0.35, W * 1.35, H * 0.85), fill=26)
    glow = glow.filter(ImageFilter.GaussianBlur(160))
    im.paste(Image.new("RGB", (W, H), (255, 255, 255)), (0, 0), glow)
    return im


def logo(d, x, y, s=1.0, text=True):
    r = 7 * s
    pts = [(x + 16 * s, y), (x, y + 28 * s), (x + 32 * s, y + 28 * s)]
    for a, b in [(0, 1), (0, 2), (1, 2)]:
        d.line([pts[a], pts[b]], fill=RED, width=max(2, int(4.5 * s)))
    d.ellipse((pts[0][0] - r, pts[0][1] - r, pts[0][0] + r, pts[0][1] + r), fill=RED)
    for p in pts[1:]:
        d.ellipse((p[0] - r * .85, p[1] - r * .85, p[0] + r * .85, p[1] + r * .85), fill=WHITE)
    if text:
        d.text((x + 50 * s, y + 14 * s), "MeshAlive", font=font("b", int(30 * s)), fill=WHITE, anchor="lm")


def eyebrow(d, x, y, txt):
    d.ellipse((x, y + 8, x + 10, y + 18), fill=RED)
    d.text((x + 22, y + 13), txt.upper(), font=font("sb", 22), fill=GREY, anchor="lm")


def heading(d, x, y, lines, size=64, colors=None):
    f = font("b", size)
    for i, ln in enumerate(lines):
        d.text((x, y + i * size * 1.12), ln, font=f, fill=(colors or [WHITE] * len(lines))[i])
    return y + len(lines) * size * 1.12


def bullets(d, x, y, items, size=30, gap=22, width=760):
    f = font("r", size)
    for it in items:
        d.line((x, y, x + width, y), fill=LINE, width=1)
        d.text((x, y + gap), it, font=f, fill=(225, 225, 225))
        y += size + gap * 2
    d.line((x, y, x + width, y), fill=LINE, width=1)
    return y


def phone(name, h):
    p = Image.open(MOCK / f"phone-{name}.png").convert("RGBA")
    return p.resize((int(p.width * h / p.height), h), Image.LANCZOS)


def place_phones(im, names, cx, top, h, gap=-40, captions=None):
    ps = [phone(n, h) for n in names]
    total = sum(p.width for p in ps) + gap * (len(ps) - 1)
    x = int(cx - total / 2)
    d = ImageDraw.Draw(im)
    for i, p in enumerate(ps):
        im.paste(p, (x, top), p)
        if captions:
            d.text((x + p.width / 2, top + h + 6), captions[i], font=font("sb", 24), fill=GREY, anchor="mt")
        x += p.width + gap


def stat(d, x, y, w, big, small):
    d.rounded_rectangle((x, y, x + w, y + 190), radius=18, fill=(12, 12, 12), outline=LINE, width=2)
    d.text((x + 32, y + 28), big, font=font("b", 76), fill=WHITE)
    d.text((x + 32, y + 128), small, font=font("r", 25), fill=GREY)


def mesh_diagram(im, x0, y0, w, h, carrier=False):
    """Phones (nodes) relaying an SOS to a gateway; optionally a rescue boat carrying it out."""
    d = ImageDraw.Draw(im)
    N = {
        "sos": (0.06, 0.62), "a": (0.25, 0.40), "b": (0.27, 0.82), "c": (0.45, 0.58), "d": (0.47, 0.22),
        "e": (0.64, 0.74), "f": (0.66, 0.36), "gw": (0.90, 0.50), "iso": (0.12, 0.15),
    }
    P = {k: (x0 + v[0] * w, y0 + v[1] * h) for k, v in N.items()}
    links = [("sos", "a"), ("sos", "b"), ("a", "c"), ("b", "c"), ("a", "d"), ("c", "e"), ("c", "f"), ("d", "f"), ("e", "gw"), ("f", "gw"), ("b", "e")]
    for a, b in links:
        d.line([P[a], P[b]], fill=(55, 55, 55), width=3)
    route = ["sos", "a", "c", "f", "gw"]
    if not carrier:
        for a, b in zip(route, route[1:]):
            d.line([P[a], P[b]], fill=RED, width=6)
    for k, (x, y) in P.items():
        if k in ("sos", "gw"):
            continue
        r = 13
        low = k == "d"
        d.ellipse((x - r, y - r, x + r, y + r), fill=(20, 20, 20), outline=(200, 200, 200) if not low else (110, 110, 110), width=3)
        if low:
            d.text((x, y - 30), "low battery", font=font("r", 20), fill=(130, 130, 130), anchor="mb")
    # SOS phone
    sx, sy = P["sos"]
    d.ellipse((sx - 26, sy - 26, sx + 26, sy + 26), fill=RED)
    d.text((sx, sy), "SOS", font=font("b", 18), fill=WHITE, anchor="mm")
    # gateway / control room
    gx, gy = P["gw"]
    d.rounded_rectangle((gx - 30, gy - 30, gx + 30, gy + 30), radius=10, fill=(22, 163, 74))
    d.text((gx, gy + 48), "Phone with signal", font=font("sb", 22), fill=WHITE, anchor="mt")
    d.text((gx, gy + 76), "→ control room", font=font("r", 20), fill=GREY, anchor="mt")
    d.text((sx, sy + 44), "Person in danger", font=font("sb", 22), fill=WHITE, anchor="mt")
    if carrier:
        # isolated SOS phone, rescue boat picks it up and carries it to the gateway
        ix, iy = P["iso"]
        d.ellipse((ix - 22, iy - 22, ix + 22, iy + 22), fill=RED)
        d.text((ix, iy), "SOS", font=font("b", 16), fill=WHITE, anchor="mm")
        d.text((ix, iy - 34), "No phone in range", font=font("sb", 22), fill=WHITE, anchor="mb")
        bx, by = ix + 150, iy + 30
        # dashed custody path: isolated phone -> boat -> gateway
        pts = [(ix + 22, iy + 4), (bx - 40, by), (bx + 40, by), (gx - 34, gy - 20)]
        for (ax, ay), (cx, cy) in zip(pts, pts[1:]):
            L = math.hypot(cx - ax, cy - ay)
            n = int(L // 22)
            for i in range(n):
                if i % 2 == 0:
                    t0, t1 = i / n, (i + 1) / n
                    d.line([(ax + (cx - ax) * t0, ay + (cy - ay) * t0), (ax + (cx - ax) * t1, ay + (cy - ay) * t1)], fill=(255, 196, 0), width=5)
        d.rounded_rectangle((bx - 44, by - 22, bx + 44, by + 22), radius=11, fill=(255, 196, 0))
        d.text((bx, by), "BOAT", font=font("b", 20), fill=(0, 0, 0), anchor="mm")
        d.text((bx, by + 36), "Rescue carrier", font=font("sb", 22), fill=WHITE, anchor="mt")


# ---------------------------------------------------------------- scene images
def img_title():
    im = canvas(); d = ImageDraw.Draw(im)
    logo(d, 140, 150, 1.3)
    y = heading(d, 140, 330, ["Help reaches you,", "even when the", "network doesn’t."], 92, [WHITE, GREY, GREY])
    d.text((140, y + 40), "An offline disaster-response network for India", font=font("r", 34), fill=(200, 200, 200))
    d.text((140, y + 96), "SevaFirst Innovation Challenge 2026  ·  Working prototype", font=font("r", 26), fill=DIM)
    trio = Image.open(MOCK / "phones-trio.png").convert("RGBA")
    trio = trio.resize((int(trio.width * 760 / trio.height), 760), Image.LANCZOS)
    im.paste(trio, (W - trio.width - 40, 150), trio)
    return im


def img_problem():
    im = canvas(); d = ImageDraw.Draw(im)
    eyebrow(d, 140, 130, "The problem")
    heading(d, 140, 180, ["When the towers go down,", "rescue goes blind."], 84, [WHITE, GREY])
    cards = [("2015", "Chennai floods", ["Mobile networks down", "for days across the city"]),
             ("2023", "Cyclone Michaung", ["Neighbourhoods cut off", "while the water kept rising"]),
             ("Today", "Control rooms work blind", ["No view of who needs help,", "where, or how urgent"])]
    x = 140
    for big, t, s in cards:
        d.rounded_rectangle((x, 520, x + 520, 820), radius=20, fill=(12, 12, 12), outline=LINE, width=2)
        d.text((x + 36, 552), big, font=font("b", 64), fill=RED if big != "Today" else WHITE)
        d.text((x + 36, 662), t, font=font("sb", 34), fill=WHITE)
        for j, ln in enumerate(s):
            d.text((x + 36, 716 + j * 34), ln, font=font("r", 26), fill=GREY)
        x += 560
    return im


def img_setup():
    im = canvas(); d = ImageDraw.Draw(im)
    eyebrow(d, 140, 150, "Setup · 30 seconds")
    heading(d, 140, 200, ["Ready before", "disaster strikes."], 72, [WHITE, GREY])
    bullets(d, 140, 420, ["Free Android app", "English or Tamil", "Household details, entered once", "Citizen, rescue carrier or gateway"], 30, 18, 600)
    place_phones(im, ["welcome", "language", "role"], 1270, 150, 700, gap=-20)
    return im


def img_home():
    im = canvas(); d = ImageDraw.Draw(im)
    eyebrow(d, 140, 150, "Always listening")
    heading(d, 140, 200, ["No towers.", "No internet.", "Still connected."], 78, [WHITE, WHITE, GREY])
    bullets(d, 140, 520, ["Finds nearby phones over Bluetooth and Wi-Fi", "Shows live mesh status on the home screen"], 30, 18, 720)
    place_phones(im, ["home"], 1380, 110, 800)
    return im


def img_sos_report():
    im = canvas(); d = ImageDraw.Draw(im)
    eyebrow(d, 140, 150, "One-tap SOS")
    heading(d, 140, 200, ["An SOS that says", "how urgent it is."], 72, [WHITE, GREY])
    bullets(d, 140, 420, ["GPS location", "People, trapped, injured, elderly, children", "Water level: knee, waist, chest, roof", "Urgency score 0–100"], 30, 18, 660)
    place_phones(im, ["situation"], 1380, 110, 800)
    d.rounded_rectangle((1580, 690, 1830, 790), radius=16, fill=RED)
    d.text((1705, 712), "81", font=font("b", 44), fill=WHITE, anchor="mt")
    d.text((1705, 762), "CRITICAL", font=font("b", 18), fill=WHITE, anchor="mm")
    return im


def img_sos_countdown():
    im = canvas(); d = ImageDraw.Draw(im)
    eyebrow(d, 140, 150, "No false alarms")
    heading(d, 140, 200, ["3-second countdown,", "with Cancel."], 72, [WHITE, GREY])
    place_phones(im, ["countdown"], 1380, 110, 800)
    return im


def img_tamil():
    im = canvas(); d = ImageDraw.Draw(im)
    eyebrow(d, 140, 150, "In your language")
    heading(d, 140, 200, ["Works fully", "in Tamil."], 84, [WHITE, GREY])
    d.text((140, 440), "தமிழ்", font=ImageFont.truetype("C:/Windows/Fonts/Nirmala.ttc", 120, index=1), fill=(60, 60, 60))
    place_phones(im, ["tamil"], 1380, 110, 800)
    return im


def img_mesh(carrier):
    im = canvas(); d = ImageDraw.Draw(im)
    eyebrow(d, 140, 120, "Rescue carriers" if carrier else "Phone to phone")
    heading(d, 140, 170, ["No route? A rescue boat", "carries it out."] if carrier else ["Battery-aware routing.", "Most urgent first."], 64, [WHITE, GREY])
    mesh_diagram(im, 190, 420, 1080, 470, carrier)
    place_phones(im, ["routing-log"], 1600, 170, 700)
    return im


def img_evidence():
    im = canvas(); d = ImageDraw.Draw(im)
    eyebrow(d, 140, 130, "Evidence")
    heading(d, 140, 180, ["Built, tested, measured."], 72)
    stat(d, 140, 330, 520, "100%", "SOS delivered (200-phone simulation)")
    stat(d, 700, 330, 520, "147×", "fewer radio transmissions than flooding")
    stat(d, 140, 560, 520, "2×", "battery left on the weakest phone")
    stat(d, 700, 560, 520, "49", "automated tests passing")
    place_phones(im, ["planner"], 1580, 130, 760)
    return im


def img_close():
    im = canvas(); d = ImageDraw.Draw(im)
    logo(d, W / 2 - 150, 170, 1.6)
    d.text((W / 2, 400), "Because when the network fails,", font=font("b", 80), fill=WHITE, anchor="mm")
    d.text((W / 2, 495), "help shouldn’t.", font=font("b", 80), fill=GREY, anchor="mm")
    items = ["No towers", "No new hardware", "₹0 for citizens"]
    f = font("sb", 32)
    widths = [d.textlength(t, font=f) + 64 for t in items]
    x = W / 2 - (sum(widths) + 20 * 2) / 2
    for t, w in zip(items, widths):
        d.rounded_rectangle((x, 590, x + w, 650), radius=30, outline=(70, 70, 70), width=2)
        d.text((x + w / 2, 620), t, font=f, fill=WHITE, anchor="mm")
        x += w + 20
    d.text((W / 2, 745), "meshalive.benzo.co.in", font=font("b", 46), fill=WHITE, anchor="mm")
    d.text((W / 2, 800), "Download the app  ·  Try the control room", font=font("r", 28), fill=GREY, anchor="mm")
    return im


# ---------------------------------------------------------------- timings, captions
voice = WORK / "voice"
timings = json.loads((voice / "timings.json").read_text(encoding="utf-8"))


def dur(path):
    return float(subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(path)]).decode().strip())


def sent_start(scene, idx):
    return timings[scene][idx]["start"] + LEAD


# shots: (scene, image or 'dash', start offset within the scene)
def shots_for(scene, length):
    if scene == "sos":
        cut = sent_start("sos", 5)
        return [(img_sos_report(), 0, cut), (img_sos_countdown(), cut, length)]
    if scene == "mesh":
        cut = sent_start("mesh", 2)
        return [(img_mesh(False), 0, cut), (img_mesh(True), cut, length)]
    return [({
        "title": img_title, "problem": img_problem, "setup": img_setup, "home": img_home,
        "tamil": img_tamil, "evidence": img_evidence, "close": img_close,
    }[scene]() if scene != "dashboard" else "dash", 0, length)]


def run(cmd):
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode:
        print(r.stderr[-3000:])
        raise SystemExit("ffmpeg failed")


def still_clip(img, seconds, out, zoom_in=True):
    src = TMP / (out.stem + ".png")
    img.save(src)
    n = max(1, int(round(seconds * FPS)))
    z = f"1+0.035*on/{n}" if zoom_in else f"1.035-0.035*on/{n}"
    run(["ffmpeg", "-v", "error", "-y", "-loop", "1", "-i", str(src), "-vf",
         f"scale=3840:2160,zoompan=z='{z}':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d={n}:s={W}x{H}:fps={FPS},format=yuv420p",
         "-frames:v", str(n), "-c:v", "libx264", "-preset", "medium", "-crf", "17", str(out)])


def dash_clip(seconds, out):
    src = WORK / "dash" / "dashboard.webm"
    have = dur(src)
    pad = max(0.0, seconds - have)
    # browser-style frame: dark title bar with the site URL
    bar = Image.new("RGB", (1600, 44), (24, 24, 24))
    bd = ImageDraw.Draw(bar)
    for i, c in enumerate([(255, 95, 87), (254, 188, 46), (40, 200, 64)]):
        bd.ellipse((20 + i * 24, 16, 32 + i * 24, 28), fill=c)
    bd.rounded_rectangle((480, 8, 1120, 36), radius=14, fill=(40, 40, 40))
    bd.text((800, 22), "meshalive.benzo.co.in/dashboard", font=font("r", 18), fill=(200, 200, 200), anchor="mm")
    bar.save(TMP / "bar.png")
    # 1600x1044 window scaled to 1520 wide, centred near the top on black
    run(["ffmpeg", "-v", "error", "-y", "-i", str(src), "-i", str(TMP / "bar.png"), "-filter_complex",
         f"[0:v]fps={FPS},tpad=stop_mode=clone:stop_duration={pad + 0.1:.2f}[v];"
         f"[1:v][v]vstack[w];[w]scale=1400:-2[s];"
         f"color=c=black:s={W}x{H}:r={FPS}[bg];[bg][s]overlay=(W-w)/2:22:shortest=1,format=yuv420p",
         "-t", f"{seconds:.3f}", "-c:v", "libx264", "-preset", "medium", "-crf", "17", str(out)])


segments, captions, t = [], [], 0.0
for si, (scene, _) in enumerate(SCENES):
    a = voice / f"{scene}.mp3"
    length = LEAD + dur(a) + TAIL
    parts = []
    for k, (img, s0, s1) in enumerate(shots_for(scene, length)):
        out = TMP / f"{si:02d}_{scene}_{k}.mp4"
        if img == "dash":
            dash_clip(s1 - s0, out)
        else:
            still_clip(img, s1 - s0, out, zoom_in=(si + k) % 2 == 0)
        parts.append(out)
    # scene = shots + narration, with short fades in/out
    seg = TMP / f"seg_{si:02d}.mp4"
    lst = TMP / f"parts_{si:02d}.txt"
    lst.write_text("".join(f"file '{p.as_posix()}'\n" for p in parts))
    fade_out = max(0, length - 0.35)
    run(["ffmpeg", "-v", "error", "-y", "-f", "concat", "-safe", "0", "-i", str(lst), "-i", str(a), "-filter_complex",
         f"[0:v]fade=t=in:st=0:d=0.35,fade=t=out:st={fade_out:.3f}:d=0.35[v];"
         f"[1:a]adelay={int(LEAD * 1000)}:all=1,apad,atrim=0:{length:.3f},aformat=sample_rates=48000:channel_layouts=stereo[a]",
         "-map", "[v]", "-map", "[a]", "-t", f"{length:.3f}", "-c:v", "libx264", "-preset", "medium", "-crf", "17",
         "-c:a", "aac", "-b:a", "192k", str(seg)])
    segments.append(seg)
    for s in timings[scene]:
        captions.append((t + LEAD + s["start"], t + LEAD + s["end"], s["text"]))
    t += length
    print(f"{scene:10s} {length:6.2f}s")


def chunks(start, end, text, limit=64):
    words, out, cur = text.split(), [], ""
    for w_ in words:
        if cur and len(cur) + 1 + len(w_) > limit:
            out.append(cur); cur = w_
        else:
            cur = (cur + " " + w_).strip()
    out.append(cur)
    total = sum(len(c) for c in out)
    res, s = [], start
    for c in out:
        e = s + (end - start) * len(c) / total
        res.append((s, e, c)); s = e
    return res


def ts(x):
    h, m = int(x // 3600), int(x % 3600 // 60)
    return f"{h}:{m:02d}:{x % 60:05.2f}"


ass = ["[Script Info]", "ScriptType: v4.00+", f"PlayResX: {W}", f"PlayResY: {H}", "",
       "[V4+ Styles]",
       "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding",
       "Style: Cap,Segoe UI Semibold,44,&H00FFFFFF,&H00FFFFFF,&H00000000,&H50000000,0,0,0,0,100,100,0,0,3,14,0,2,200,200,56,1",
       "", "[Events]", "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text"]
for s, e, txt in captions:
    for cs, ce, ct in chunks(s, e, txt):
        ass.append(f"Dialogue: 0,{ts(cs)},{ts(ce)},Cap,,0,0,0,,{ct}")
(TMP / "captions.ass").write_text("\n".join(ass) + "\n", encoding="utf-8")

lst = TMP / "segments.txt"
lst.write_text("".join(f"file '{p.as_posix()}'\n" for p in segments))
joined = TMP / "joined.mp4"
run(["ffmpeg", "-v", "error", "-y", "-f", "concat", "-safe", "0", "-i", str(lst), "-c", "copy", str(joined)])
# burn captions (run from TMP so the filter path has no drive-letter colon)
r = subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", "joined.mp4", "-vf", "subtitles=captions.ass",
                    "-c:v", "libx264", "-preset", "slow", "-crf", "20", "-pix_fmt", "yuv420p",
                    "-af", "loudnorm=I=-16:TP=-1.5:LRA=11", "-ar", "48000", "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart", str(OUTFILE.resolve())], cwd=TMP, capture_output=True, text=True)
if r.returncode:
    print(r.stderr[-3000:]); raise SystemExit("caption burn failed")
print("total", round(t, 1), "s ->", OUTFILE)

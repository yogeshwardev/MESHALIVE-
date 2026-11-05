"""Turns raw app screenshots into realistic phone / browser mockups (transparent PNG + WebP)."""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

SHOTS = Path(sys.argv[1])
OUT = Path(sys.argv[2])
OUT.mkdir(parents=True, exist_ok=True)
SS = 2  # supersampling for smooth edges

PAPER = (246, 247, 251)


def is_banner_row(px, w, y):
    hits = 0
    for x in range(40, w - 40, 8):
        r, g, b = px[x, y][:3]
        if r > 250 and 240 <= g <= 250 and 222 <= b <= 236:  # warning banner #FFF7E6
            hits += 1
    return hits > (w - 80) / 8 * 0.6


def remove_emulator_banner(im: Image.Image) -> Image.Image:
    """The emulator has no Bluetooth radio, so it shows a 'Discovery failed' banner a real phone never shows."""
    im = im.convert("RGB")
    w, h = im.size
    px = im.load()
    rows = [y for y in range(0, int(h * 0.8)) if is_banner_row(px, w, y)]
    if not rows:
        return im
    top, bottom = rows[0] - 12, rows[-1] + 30
    # bottom navigation bar: first row from the bottom that is not pure white
    nav_top = h - 1
    while nav_top > h * 0.75 and px[w // 2, nav_top][:3] == (255, 255, 255):
        nav_top -= 1
    nav_top = max(nav_top - 4, bottom + 1)
    cut = bottom - top
    out = Image.new("RGB", (w, h), PAPER)
    out.paste(im.crop((0, 0, w, top)), (0, 0))
    out.paste(im.crop((0, bottom, w, nav_top)), (0, top))
    out.paste(im.crop((0, nav_top, w, h)), (0, nav_top))
    return out


def rounded_mask(size, radius):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius=radius, fill=255)
    return m


def phone(screen: Image.Image, height=1600) -> Image.Image:
    """Modern Android phone: graphite body, thin bezel, punch-hole camera, side buttons."""
    sw, sh = screen.size
    scale = height * SS / (sh * 1.045)
    screen = screen.resize((int(sw * scale), int(sh * scale)), Image.LANCZOS)
    sw, sh = screen.size
    bezel = int(sw * 0.035)
    body_w, body_h = sw + 2 * bezel, sh + 2 * bezel
    pad = int(body_w * 0.03)  # room for side buttons
    W, H = body_w + 2 * pad, body_h + 2 * pad
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    body_r = int(body_w * 0.13)
    bx, by = pad, pad
    # side buttons (right edge: volume + power)
    btn = (36, 39, 45, 255)
    bw = int(pad * 0.55)
    d.rounded_rectangle((bx + body_w - 4, by + int(body_h * 0.20), bx + body_w + bw, by + int(body_h * 0.31)), radius=bw, fill=btn)
    d.rounded_rectangle((bx + body_w - 4, by + int(body_h * 0.35), bx + body_w + bw, by + int(body_h * 0.41)), radius=bw, fill=btn)
    # frame: metallic rim + black glass
    d.rounded_rectangle((bx, by, bx + body_w, by + body_h), radius=body_r, fill=(58, 62, 70, 255))
    rim = int(bezel * 0.18)
    d.rounded_rectangle((bx + rim, by + rim, bx + body_w - rim, by + body_h - rim), radius=body_r - rim, fill=(10, 11, 14, 255))
    # screen
    scr_r = int(body_r * 0.55)
    mask = rounded_mask((sw, sh), scr_r)
    img.paste(screen.convert("RGBA"), (bx + bezel, by + bezel), mask)
    # punch-hole camera over the status bar
    cam_r = int(sw * 0.018)
    cx, cy = bx + bezel + sw // 2, by + bezel + int(sh * 0.018) + cam_r
    d.ellipse((cx - cam_r, cy - cam_r, cx + cam_r, cy + cam_r), fill=(5, 5, 7, 255))
    d.ellipse((cx - cam_r * 0.45, cy - cam_r * 0.45, cx - cam_r * 0.05, cy - cam_r * 0.05), fill=(40, 48, 70, 255))
    # subtle highlight on the rim
    hl = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(hl).rounded_rectangle((bx + 1, by + 1, bx + body_w - 1, by + body_h - 1), radius=body_r,
                                         outline=(150, 156, 168, 110), width=max(2, rim // 3))
    img = Image.alpha_composite(img, hl)
    return img.resize((W // SS, H // SS), Image.LANCZOS)


def with_shadow(img: Image.Image, blur=40, offset=(0, 28), opacity=0.35) -> Image.Image:
    w, h = img.size
    m = blur * 2
    canvas = Image.new("RGBA", (w + 2 * m, h + 2 * m), (0, 0, 0, 0))
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    alpha = img.split()[3].point(lambda a: int(a * opacity))
    shadow.paste((8, 16, 32, 255), (m + offset[0], m + offset[1]), alpha)
    shadow = shadow.filter(ImageFilter.GaussianBlur(blur))
    canvas = Image.alpha_composite(canvas, shadow)
    canvas.alpha_composite(img, (m, m))
    return canvas


def browser(shot: Image.Image, url="meshalive.benzo.co.in/dashboard", width=1600) -> Image.Image:
    shot = shot.convert("RGB")
    s = width * SS / shot.width
    shot = shot.resize((int(shot.width * s), int(shot.height * s)), Image.LANCZOS)
    w, h = shot.size
    bar = int(46 * SS * width / 1600)
    r = int(14 * SS)
    img = Image.new("RGBA", (w, h + bar), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((0, 0, w - 1, h + bar - 1), radius=r, fill=(32, 36, 44, 255))
    for i, c in enumerate([(255, 95, 87), (254, 188, 46), (40, 200, 64)]):
        cx = int(bar * 0.6 + i * bar * 0.5)
        d.ellipse((cx - bar * 0.13, bar / 2 - bar * 0.13, cx + bar * 0.13, bar / 2 + bar * 0.13), fill=c)
    ux0, ux1 = int(w * 0.28), int(w * 0.72)
    d.rounded_rectangle((ux0, int(bar * 0.2), ux1, int(bar * 0.8)), radius=int(bar * 0.3), fill=(52, 58, 68, 255))
    try:
        from PIL import ImageFont
        f = ImageFont.truetype("C:/Windows/Fonts/segoeui.ttf", int(bar * 0.36))
    except Exception:
        f = None
    d.text(((ux0 + ux1) / 2, bar / 2), "🔒 " + url if False else url, fill=(200, 208, 218), anchor="mm", font=f)
    mask = Image.new("L", (w, h), 255)
    md = ImageDraw.Draw(mask)
    md.rectangle((0, 0, w, h), fill=255)
    bottom = rounded_mask((w, h + r), r).crop((0, r, w, h + r))
    img.paste(shot, (0, bar), bottom)
    return img.resize((w // SS, (h + bar) // SS), Image.LANCZOS)


def save(img: Image.Image, name: str):
    img.save(OUT / f"{name}.png", optimize=True)
    img.save(OUT / f"{name}.webp", quality=88, method=6)


SCREENS = {
    "welcome": "m_welcome",
    "role": "m_role",
    "home": "m_home",
    "situation": "m_situation",
    "countdown": "m_after_cancel",
    "tamil": "m_tamil",
    "planner": "m_planner",
    "routing-log": "m_log",
    "language": "m_lang",
}

phones = {}
for name, src in SCREENS.items():
    shot = Image.open(SHOTS / f"{src}.png")
    shot = remove_emulator_banner(shot)
    p = phone(shot)
    phones[name] = p
    save(with_shadow(p), f"phone-{name}")
    print("phone", name, p.size)

# hero trio: countdown | situation | planner
left, mid, right = phones["countdown"], phones["situation"], phones["planner"]
k = 0.86
left = left.resize((int(left.width * k), int(left.height * k)), Image.LANCZOS)
right = right.resize((int(right.width * k), int(right.height * k)), Image.LANCZOS)
W = int(mid.width * 2.25)
H = mid.height + 40
trio = Image.new("RGBA", (W, H), (0, 0, 0, 0))
trio.alpha_composite(with_shadow(left, 30, (0, 20), 0.3).crop((0, 0, left.width + 120, left.height + 120)), (0, int(H * 0.09)))
trio.alpha_composite(with_shadow(right, 30, (0, 20), 0.3).crop((0, 0, right.width + 120, right.height + 120)), (W - right.width - 120, int(H * 0.09)))
trio.alpha_composite(with_shadow(mid, 40, (0, 28), 0.4), ((W - mid.width) // 2 - 80, -40))
save(trio, "phones-trio")

dash = browser(Image.open(SHOTS / "dashboard.png"))
save(with_shadow(dash, 50, (0, 30), 0.45), "browser-dashboard")
print("done")

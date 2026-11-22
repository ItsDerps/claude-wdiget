"""
Dot-matrix wallpapers for the Nothing Phone (2), 1080 x 2412.
Everything is drawn at 2x and scaled down so dots and lines stay crisp.
Run: python3 wallpapers/make.py
"""
import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = os.path.dirname(os.path.abspath(__file__)) + '/'
FONTS = os.path.join(OUT, '..', 'launcher', 'src', 'main', 'res', 'font')
W, H = 1080, 2412
S = 2                       # supersampling
W2, H2 = W * S, H * S
RED = (215, 25, 33)
PITCH = 24                  # LED grid pitch at 1x


def mono(size):
    return ImageFont.truetype(os.path.join(FONTS, 'space_mono.ttf'), size * S)


def dots_font(size):
    return ImageFont.truetype(os.path.join(FONTS, 'doto_black.ttf'), size * S)


def canvas(bg=(0, 0, 0)):
    return Image.new('RGB', (W2, H2), bg)


def grid_points(pitch=PITCH):
    p = pitch * S
    for gy in range(p // 2, H2, p):
        for gx in range(p // 2, W2, p):
            yield gx, gy


def dot(d, x, y, r, fill):
    d.ellipse([x - r, y - r, x + r, y + r], fill=fill)


def bloom(img, lit, radius=10, strength=0.9):
    """Add a soft glow from the lit layer only."""
    glow = lit.filter(ImageFilter.GaussianBlur(radius * S))
    a = np.asarray(img).astype(np.float32) + strength * np.asarray(glow).astype(np.float32) \
        + np.asarray(lit).astype(np.float32)
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))


def finish(img, name, grain=2.5):
    small = img.resize((W, H), Image.LANCZOS)
    a = np.asarray(small).astype(np.float32)
    a += np.random.default_rng(3).normal(0, grain, a.shape[:2])[..., None]  # a whisper of grain, against banding
    Image.fromarray(np.clip(a, 0, 255).astype(np.uint8)).save(OUT + name + '.png', optimize=True)
    print('saved', name)


def label(d, x, y, text, size=13, fill=(90, 90, 90), anchor='la'):
    d.text((x * S, y * S), text, font=mono(size), fill=fill, anchor=anchor)


def crosshair(d, x, y, size=14, fill=(70, 70, 70), w=1):
    x, y, s = x * S, y * S, size * S
    d.line([x - s, y, x + s, y], fill=fill, width=w * S)
    d.line([x, y - s, x, y + s], fill=fill, width=w * S)
    d.ellipse([x - s * 0.45, y - s * 0.45, x + s * 0.45, y + s * 0.45], outline=fill, width=w * S)


# --------------------------------------------------------------------------------------------
# 2. TRANSPARENT — line art of the parts under a see-through back
def transparent(light=False):
    bg = (232, 232, 228) if light else (0, 0, 0)
    ink = (150, 150, 146) if light else (88, 88, 88)
    ink2 = (120, 120, 116) if light else (140, 140, 140)
    gridc = (214, 214, 209) if light else (30, 30, 30)
    img = canvas(bg)
    d = ImageDraw.Draw(img)
    for gx, gy in grid_points():
        dot(d, gx, gy, 1.2 * S, gridc)
    lw = S

    # wireless charging coil: a tight spiral of circles
    cx, cy = W2 * 0.52, H2 * 0.56
    for i in range(30):
        r = (70 + i * 7) * S
        d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=ink, width=lw)
    d.ellipse([cx - 62 * S, cy - 62 * S, cx + 62 * S, cy + 62 * S], outline=ink2, width=2 * S)
    # NFC antenna: rounded loops around it
    for i in range(4):
        m = (300 + i * 12) * S
        d.rounded_rectangle([cx - m, cy - m * 1.15, cx + m, cy + m * 1.15], radius=60 * S, outline=ink, width=lw)
    # camera module, top left
    mx, my = 120 * S, 150 * S
    d.rounded_rectangle([mx, my, mx + 250 * S, my + 420 * S], radius=110 * S, outline=ink2, width=2 * S)
    for k, ly in enumerate([my + 110 * S, my + 300 * S]):
        for rr in (78, 64, 40, 18):
            d.ellipse([mx + 125 * S - rr * S, ly - rr * S, mx + 125 * S + rr * S, ly + rr * S], outline=ink if rr != 78 else ink2, width=lw)
    # ribbon cables with rounded bends
    for j in range(6):
        off = j * 9 * S
        x0, y0 = mx + 250 * S, my + 210 * S + off
        x1 = W2 - 140 * S - off
        d.line([x0, y0, x1 - 40 * S, y0], fill=ink, width=lw)
        d.arc([x1 - 80 * S, y0, x1, y0 + 80 * S], 270, 360, fill=ink, width=lw)
        d.line([x1, y0 + 40 * S, x1, H2 * 0.30], fill=ink, width=lw)
    # mid-frame with screws
    fx0, fy0, fx1, fy1 = 70 * S, 90 * S, W2 - 70 * S, H2 - 110 * S
    d.rounded_rectangle([fx0, fy0, fx1, fy1], radius=110 * S, outline=gridc if not light else (205, 205, 200), width=2 * S)
    for sx, sy in [(fx0 + 60 * S, fy0 + 640 * S), (fx1 - 60 * S, fy0 + 640 * S), (fx0 + 60 * S, fy1 - 300 * S), (fx1 - 60 * S, fy1 - 300 * S),
                   (W2 * 0.5, fy1 - 120 * S)]:
        d.ellipse([sx - 16 * S, sy - 16 * S, sx + 16 * S, sy + 16 * S], outline=ink2, width=2 * S)
        d.line([sx - 9 * S, sy, sx + 9 * S, sy], fill=ink2, width=2 * S)
        d.line([sx, sy - 9 * S, sx, sy + 9 * S], fill=ink2, width=2 * S)
    # battery connector block and test pads
    bx, by = W2 * 0.18, H2 * 0.83
    d.rectangle([bx, by, bx + 180 * S, by + 60 * S], outline=ink2, width=2 * S)
    for k in range(8):
        dot(d, bx + (16 + k * 21) * S, by + 30 * S, 4 * S, ink2)
    # labels with leader lines
    def callout(px, py, tx, ty, text):
        dot(d, px * S, py * S, 4 * S, ink2)
        d.line([px * S, py * S, tx * S, ty * S], fill=ink, width=lw)
        label(d, tx + 8, ty - 10, text, 12, ink2)
    callout(int(cx / S) + 230, int(cy / S) - 120, 860, 1080, 'WLC / 15W')
    callout(int(cx / S) - 320, int(cy / S) + 200, 60, 1650, 'NFC')
    callout(245, 260, 420, 120, 'CAM 50MP')
    callout(int(bx / S) + 180, int(by / S) + 30, 560, 2050, 'J1101 BTB')
    # one red LED, like the recording light
    lit = canvas()
    dot(ImageDraw.Draw(lit), (mx + 125 * S), my + 205 * S, 7 * S, RED)
    img = bloom(img, lit, 8, 1.0) if not light else Image.composite(lit, img, lit.convert('L').point(lambda v: 255 if v > 10 else 0))
    finish(img, '5-transparent-paper' if light else '4-transparent')


# --------------------------------------------------------------------------------------------
# Lit LED matrices: the whole screen is a dot matrix whose brightness follows a light field,
# in six real LED steps. The gaps between widgets show crisp, glowing dots.
LEVELS = [44, 66, 98, 142, 196, 246]


def lit_matrix(field, name, pitch=22, red=None, extra=None):
    img = canvas()
    d = ImageDraw.Draw(img)
    lit = canvas()
    dl = ImageDraw.Draw(lit)
    p = pitch * S
    for gy in range(p // 2, H2, p):
        for gx in range(p // 2, W2, p):
            f = max(0.0, min(1.0, field(gx / W2, gy / H2)))
            level = min(5, int(f * 6))
            c = LEVELS[level]
            r = (1.45 + 0.42 * level) * S
            if level >= 3:
                dot(dl, gx, gy, r, (c, c, c))      # bright dots glow
            else:
                dot(d, gx, gy, r, (c, c, c))
    if red:
        dot(dl, red[0] * W2, red[1] * H2, 4.6 * S, RED)
    img = bloom(img, lit, 7, 0.55)
    if extra:
        extra(ImageDraw.Draw(img))
    finish(img, name, 1.5)


def sweep(x, y):
    # A diagonal band of light from top right down to the lower left, like stage light through haze
    band = math.exp(-((x + 0.75 * y - 1.05) / 0.20) ** 2)          # top right to lower left
    halo = math.exp(-((x + 0.75 * y - 1.05) / 0.55) ** 2) * 0.35   # soft light either side
    return 0.06 + 0.94 * max(band, halo)


def glow(x, y):
    # Light pooling behind the middle of the screen, falling off to the edges
    d = math.hypot((x - 0.55) / 0.9, (y - 0.47) / 1.05)
    return 0.06 + 0.94 * math.exp(-(d / 0.42) ** 2)


def ring_xl(x, y):
    # A huge Glyph ring that runs through the gaps, over a faint ambient light
    px, py = x * W, y * H
    cx, cy, R = W * 0.54, H * 0.47, W * 0.60
    dist = math.hypot(px - cx, py - cy)
    ang = math.degrees(math.atan2(py - cy, px - cx)) % 360
    ring = math.exp(-((dist - R) / 26) ** 2)
    if 120 < ang < 152:
        ring *= 0.05
    inner = math.exp(-((dist - R * 0.82) / 12) ** 2) * (1 if 190 < ang < 330 else 0) * 0.55
    ambient = 0.10 + 0.22 * math.exp(-(math.hypot(x - 0.5, y - 0.5) / 0.6) ** 2)
    return max(ambient, ring, inner)


def labels(d):
    label(d, 34, 2346, 'DOT / MATRIX', 12, (120, 120, 120))
    label(d, W - 34, 2346, '22 PX', 12, (80, 80, 80), 'ra')


if __name__ == '__main__':
    lit_matrix(sweep, '1-dot-sweep', red=(0.88, 0.05), extra=labels)
    lit_matrix(glow, '2-dot-glow', red=(0.5, 0.255), extra=labels)
    a = math.radians(152)
    lit_matrix(ring_xl, '3-glyph-ring', red=(0.54 + math.cos(a) * 0.60 * W / W, (0.47 * H + math.sin(a) * 0.60 * W) / H), extra=labels)
    transparent()
    transparent(light=True)

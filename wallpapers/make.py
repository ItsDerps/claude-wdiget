import numpy as np
from PIL import Image, ImageDraw, ImageFilter
W, H = 1080, 2412
import os
OUT = os.path.dirname(os.path.abspath(__file__)) + '/'
rng = np.random.default_rng(7)
yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)

def grain(img, amount=14, fine=True):
    a = np.asarray(img).astype(np.float32)
    n = rng.normal(0, amount, (H, W)).astype(np.float32)
    if not fine:
        n = np.asarray(Image.fromarray(np.clip(n+128,0,255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.7))).astype(np.float32)-128
    if a.ndim == 3: a += n[..., None]
    else: a += n
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))

def save(img, name):
    img.convert('RGB').save(OUT + name + '.png', optimize=True)
    print('saved', name)

def glow(cx, cy, r):
    d = np.sqrt((xx-cx)**2 + (yy-cy)**2) / r
    return np.exp(-d*d)

# 1. Fog: a soft light bloom drifting through black, heavy film grain
base = 10 + 150*glow(W*0.95, H*0.28, W*0.75) + 110*glow(W*0.05, H*0.72, W*0.65) + 40*glow(W*0.6, H*1.02, W*0.5)
save(grain(Image.fromarray(np.clip(base,0,255).astype(np.uint8)), 22), '1-fog')

# 2. Halftone horizon: dots swell up from the bottom, like light rising
img = Image.new('L', (W, H), 6); d = ImageDraw.Draw(img)
step = 20
for y in range(step//2, H, step):
    for x in range(step//2, W, step):
        t = max(0.0, (y/H - 0.42) / 0.58)
        wave = 0.08*np.sin(x/W*3.1 + 1.3)
        f = min(1.0, max(0.0, t**1.6 + wave*t))
        r = 1.2 + 7.5*f
        c = int(40 + 190*f)
        d.ellipse([x-r, y-r, x+r, y+r], fill=c)
save(grain(img, 10), '2-halftone-horizon')

# 3. Glyph ring: a big dotted ring with a gap, like the Phone (2)'s back
img = Image.fromarray(np.clip(8 + 30*glow(W*0.7, H*0.45, W*0.9), 0, 255).astype(np.uint8))
d = ImageDraw.Draw(img)
cx, cy, R = W*0.62, H*0.47, W*0.58
for ring, (rad, size, col) in enumerate([(R, 9, 235), (R-34, 4, 120)]):
    n = int(2*np.pi*rad/26)
    for i in range(n):
        a = -np.pi/2 + i*2*np.pi/n
        deg = (np.degrees(a) + 360) % 360
        if 115 < deg < 150: continue          # the gap
        x, y = cx + np.cos(a)*rad, cy + np.sin(a)*rad
        d.ellipse([x-size, y-size, x+size, y+size], fill=col)
soft = img.filter(ImageFilter.GaussianBlur(10))
img = Image.blend(img, soft, 0.35)
img = Image.fromarray(np.clip(np.asarray(img).astype(np.float32) + 0.6*np.asarray(soft).astype(np.float32), 0, 255).astype(np.uint8))
save(grain(img, 14), '3-glyph-ring')

# 4. Dot moon: a huge sphere shaded only with dots
img = Image.new('L', (W, H), 5); d = ImageDraw.Draw(img)
mx, my, MR = W*0.78, H*0.30, W*0.62
lx, ly, lz = -0.55, -0.45, 0.70
step = 16
for y in range(step//2, H, step):
    for x in range(step//2, W, step):
        dx, dy = (x-mx)/MR, (y-my)/MR
        rr = dx*dx + dy*dy
        if rr > 1: continue
        dz = np.sqrt(1-rr)
        lum = max(0.0, dx*lx + dy*ly + dz*lz)
        lum = lum**1.3
        r = 0.6 + 6.6*lum
        d.ellipse([x-r, y-r, x+r, y+r], fill=int(60 + 190*lum))
save(grain(img, 12), '4-dot-moon')

# 5. Signal: near black, one soft beam of light down the gap between the widgets, and a red dot
base = 6 + 22*glow(W*0.5, H*0.5, W*1.2)
gap = W*0.5
beam = 135*np.exp(-((xx - (gap + (yy-H*0.55)*0.03))/26)**2) * (0.25 + 0.75*glow(gap, H*0.62, H*0.42))
rgb = np.stack([base+beam]*3, -1)
red = glow(gap + (H*0.665-H*0.55)*0.03, H*0.665, 15)
rgb[..., 0] += 255*red; rgb[..., 1] += 25*red; rgb[..., 2] += 35*red
img = Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8))
save(grain(img, 16), '5-signal')

# 6. Concrete: mid-grey, rough, industrial; dark widgets pop off it
n1 = rng.normal(0, 1, (H//8, W//8)).astype(np.float32)
low = np.asarray(Image.fromarray(np.clip(n1*40+128,0,255).astype(np.uint8)).resize((W, H), Image.BICUBIC).filter(ImageFilter.GaussianBlur(30))).astype(np.float32)
base = 118 + (low-128)*0.5 - 40*(1-glow(W*0.5, H*0.45, H*0.75))
save(grain(Image.fromarray(np.clip(base,0,255).astype(np.uint8)), 20, fine=False), '6-concrete')

# 7. Paper: Nothing's off-white with a light dot grid
img = Image.new('RGB', (W, H), (236, 236, 232)); d = ImageDraw.Draw(img)
step = 28
for y in range(step//2, H, step):
    for x in range(step//2, W, step):
        d.ellipse([x-2.2, y-2.2, x+2.2, y+2.2], fill=(206, 206, 200))
d.ellipse([W*0.84-9, H*0.10-9, W*0.84+9, H*0.10+9], fill=(215, 25, 33))
save(grain(img, 6), '7-paper')

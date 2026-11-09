#!/usr/bin/env python3
"""Génère l'icône adaptative ProxGuard (foreground, legacy, monochrome)."""
from PIL import Image, ImageDraw, ImageFilter
import math
import os

OUT = "/home/dev/projects/proxguard/android/app/src/main/res"
DENSITIES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}

CYAN = (34, 211, 238)
CYAN_DARK = (14, 165, 183)
TEAL = (45, 212, 191)
NAVY = (11, 18, 32)
NAVY_LIGHT = (13, 27, 42)


def smooth_closed(points, steps=14):
    """Ferme et lisse le contour par interpolation Catmull-Rom."""
    out = []
    n = len(points)
    for i in range(n):
        p0 = points[(i - 1) % n]
        p1 = points[i]
        p2 = points[(i + 1) % n]
        p3 = points[(i + 2) % n]
        for s in range(steps):
            t = s / steps
            t2 = t * t
            t3 = t2 * t
            out.append((
                0.5 * ((2 * p1[0]) + (-p0[0] + p2[0]) * t + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t2 + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t3),
                0.5 * ((2 * p1[1]) + (-p0[1] + p2[1]) * t + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t2 + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t3),
            ))
    return out


def shield_points(cx=512, cy=512, scale=1.0):
    s = scale
    pts = [
        (cx, cy - 300 * s),
        (cx + 160 * s, cy - 260 * s),
        (cx + 268 * s, cy - 225 * s),
        (cx + 268 * s, cy - 120 * s),
        (cx + 264 * s, cy + 45 * s),
        (cx + 232 * s, cy + 175 * s),
        (cx + 155 * s, cy + 270 * s),
        (cx + 40 * s, cy + 335 * s),
        (cx, cy + 350 * s),
        (cx - 40 * s, cy + 335 * s),
        (cx - 155 * s, cy + 270 * s),
        (cx - 232 * s, cy + 175 * s),
        (cx - 264 * s, cy + 45 * s),
        (cx - 268 * s, cy - 120 * s),
        (cx - 268 * s, cy - 225 * s),
        (cx - 160 * s, cy - 260 * s),
    ]
    return smooth_closed(pts)


def gradient_fill(size, top_color, bottom_color):
    img = Image.new("RGBA", size)
    px = img.load()
    for y in range(size[1]):
        t = y / max(1, size[1] - 1)
        c = tuple(int(top_color[i] + (bottom_color[i] - top_color[i]) * t) for i in range(3))
        for x in range(size[0]):
            px[x, y] = c + (255,)
    return img


def draw_shield(img, cx, cy, scale, fill=True, outline=None, color_mode=False):
    d = ImageDraw.Draw(img)
    pts = shield_points(cx, cy, scale)
    if color_mode:
        mask = Image.new("L", img.size, 0)
        ImageDraw.Draw(mask).polygon(pts, fill=255)
        grad = gradient_fill(img.size, (103, 232, 249), (13, 148, 166))
        img.paste(grad, (0, 0), mask)
    elif fill:
        d.polygon(pts, fill=(255, 255, 255, 255))
    if outline:
        d.line(pts + [pts[0]], fill=outline, width=int(10 * scale), joint="curve")
    return pts


def draw_icon(canvas=1024, background=True, with_glow=True):
    img = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    if background:
        bg = gradient_fill((canvas, canvas), (18, 32, 51), (7, 12, 22))
        mask = Image.new("L", (canvas, canvas), 0)
        ImageDraw.Draw(mask).rounded_rectangle([0, 0, canvas - 1, canvas - 1], radius=int(canvas * 0.22), fill=255)
        img.paste(bg, (0, 0), mask)
        if with_glow:
            glow = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
            gd = ImageDraw.Draw(glow)
            gd.ellipse([canvas * 0.18, canvas * 0.12, canvas * 0.82, canvas * 0.76], fill=(34, 211, 238, 90))
            glow = glow.filter(ImageFilter.GaussianBlur(int(canvas * 0.11)))
            img = Image.alpha_composite(img, glow)

    # Halo derrière le bouclier
    halo = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    hd = ImageDraw.Draw(halo)
    hs = canvas * 0.30
    hd.ellipse([canvas / 2 - hs, canvas / 2 - hs, canvas / 2 + hs, canvas / 2 + hs], fill=(34, 211, 238, 70))
    halo = halo.filter(ImageFilter.GaussianBlur(int(canvas * 0.05)))
    img = Image.alpha_composite(img, halo)

    shadow = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    draw_shield(shadow, canvas / 2, canvas / 2 + canvas * 0.015, canvas / 1024, fill=False, outline=(0, 0, 0, 120))
    shadow = shadow.filter(ImageFilter.GaussianBlur(int(canvas * 0.022)))
    img = Image.alpha_composite(img, shadow)

    draw_shield(img, canvas / 2, canvas / 2, canvas / 1024, color_mode=True)

    d = ImageDraw.Draw(img)
    c = canvas / 2
    r = canvas * 0.145
    gauge_cy = c - canvas * 0.012
    d.ellipse([c - r, gauge_cy - r, c + r, gauge_cy + r], fill=(8, 14, 26, 255))
    d.arc([c - r, gauge_cy - r, c + r, gauge_cy + r], start=150, end=390, fill=(236, 254, 255, 255), width=int(canvas * 0.030))
    d.arc([c - r, gauge_cy - r, c + r, gauge_cy + r], start=205, end=338, fill=CYAN, width=int(canvas * 0.030))
    # aiguille
    nx = c + r * 0.58
    ny = gauge_cy - r * 0.62
    d.line([(c, gauge_cy), (nx, ny)], fill=(255, 255, 255, 255), width=int(canvas * 0.023))
    d.ellipse([c - canvas * 0.020, gauge_cy - canvas * 0.020, c + canvas * 0.020, gauge_cy + canvas * 0.020], fill=(255, 255, 255, 255))
    # éclair de statut
    d.ellipse([c + canvas * 0.145, gauge_cy + canvas * 0.095, c + canvas * 0.205, gauge_cy + canvas * 0.155], fill=(52, 211, 153, 255))
    return img


def save_foreground(target, size):
    icon = draw_icon(1024, background=False, with_glow=False)
    icon = icon.resize((size, size), Image.LANCZOS)
    icon.save(target)


def main():
    for density, size in DENSITIES.items():
        folder = os.path.join(OUT, f"mipmap-{density}")
        save_foreground(os.path.join(folder, "ic_launcher_foreground.png"), size)

    full = draw_icon(1024, background=True)
    full.resize((512, 512), Image.LANCZOS).save("/home/dev/projects/proxguard/assets/icon-preview.png")
    for density, size in DENSITIES.items():
        folder = os.path.join(OUT, f"mipmap-{density}")
        full.resize((size, size), Image.LANCZOS).save(os.path.join(folder, "ic_launcher.png"))

    round_img = Image.new("RGBA", (1024, 1024), (0, 0, 0, 0))
    mask = Image.new("L", (1024, 1024), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, 1023, 1023], fill=255)
    round_img.paste(full, (0, 0), mask)
    for density, size in DENSITIES.items():
        folder = os.path.join(OUT, f"mipmap-{density}")
        round_img.resize((size, size), Image.LANCZOS).save(os.path.join(folder, "ic_launcher_round.png"))
    print("icônes générées")


if __name__ == "__main__":
    os.makedirs("/home/dev/projects/proxguard/assets", exist_ok=True)
    main()

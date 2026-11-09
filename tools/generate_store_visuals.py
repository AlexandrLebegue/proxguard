#!/usr/bin/env python3
"""Génère trois visuels de fiche store 1080x1920 pour ProxGuard."""
from functools import lru_cache
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

sys.path.insert(0, os.path.dirname(__file__))
import generate_icon

W, H = 1080, 1920
OUT = "/home/dev/projects/proxguard/store"

BG_TOP = (7, 12, 22)
BG_BOT = (10, 24, 38)
SURF = (17, 27, 44)
SURF2 = (13, 22, 36)
SURF3 = (21, 33, 54)
BORDER = (35, 50, 74)
TEXT = (231, 238, 249)
MUTED = (148, 166, 189)
CYAN = (34, 211, 238)
GREEN = (52, 211, 153)
AMBER = (251, 191, 36)
RED = (248, 113, 113)
PURPLE = (167, 139, 250)

FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
FONT_MONO = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"


@lru_cache(maxsize=128)
def font(size, bold=False, mono=False):
    path = FONT_MONO if mono else (FONT_BOLD if bold else FONT_REG)
    return ImageFont.truetype(path, size)


def blend(a, b, t):
    return tuple(int(a[i] * t + b[i] * (1 - t)) for i in range(3))


def text(d, xy, value, size, fill=TEXT, bold=False, anchor=None, max_width=None, mono=False):
    fnt = font(size, bold, mono)
    if max_width:
        while size > 7 and d.textlength(value, font=fnt) > max_width:
            size -= 1
            fnt = font(size, bold, mono)
    d.text(xy, value, font=fnt, fill=fill, anchor=anchor)


def wrap(d, value, size, max_width, bold=False):
    fnt = font(size, bold)
    words = value.split()
    lines = []
    current = ""
    for word in words:
        candidate = f"{current} {word}".strip()
        if d.textlength(candidate, font=fnt) <= max_width or not current:
            current = candidate
        else:
            lines.append(current)
            current = word
    if current:
        lines.append(current)
    return lines


def card(d, box, radius=20, fill=SURF, outline=BORDER, width=1):
    d.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def pill(d, right, y, label, color, size=12, height=26):
    fnt = font(size, True)
    tw = d.textlength(label, font=fnt)
    width = tw + 24
    x = right - width
    d.rounded_rectangle([x, y, right, y + height], radius=height / 2, fill=blend(color, SURF, 0.16))
    d.text((x + width / 2, y + height / 2), label, font=fnt, fill=color, anchor="mm")
    return width


def switch(d, x, y, on=True):
    width, height = 46, 26
    d.rounded_rectangle([x, y, x + width, y + height], radius=height / 2, fill=CYAN if on else (55, 68, 88))
    knob_x = x + width - 13 if on else x + 13
    d.ellipse([knob_x - 10, y + 3, knob_x + 10, y + 23], fill=(255, 255, 255))


def slider(d, x, y, width, fraction, color=CYAN):
    d.rounded_rectangle([x, y - 2, x + width, y + 2], radius=2, fill=BORDER)
    d.rounded_rectangle([x, y - 2, x + width * fraction, y + 2], radius=2, fill=color)
    knob = x + width * fraction
    d.ellipse([knob - 8, y - 8, knob + 8, y + 8], fill=(255, 255, 255))


def sparkline(d, box, values, color, surface=SURF, width=2):
    x0, y0, x1, y1 = box
    if len(values) < 2:
        return
    lo, hi = min(values), max(values)
    span = max(hi - lo, 0.001)
    points = []
    for i, value in enumerate(values):
        x = x0 + (x1 - x0) * i / (len(values) - 1)
        y = y1 - (value - lo) / span * (y1 - y0)
        points.append((x, y))
    area = [(x0, y1)] + points + [(x1, y1)]
    d.polygon(area, fill=blend(color, surface, 0.18))
    d.line(points, fill=color, width=width, joint="curve")
    d.ellipse([points[-1][0] - 2.5, points[-1][1] - 2.5, points[-1][0] + 2.5, points[-1][1] + 2.5], fill=color)


def ring(d, cx, cy, radius, percent, color, value_label, label):
    box = [cx - radius, cy - radius, cx + radius, cy + radius]
    d.arc(box, 135, 405, fill=blend(color, SURF, 0.13), width=9)
    d.arc(box, 135, 135 + 270 * percent, fill=color, width=9)
    d.text((cx, cy - 2), value_label, font=font(17, True), fill=TEXT, anchor="mm")
    d.text((cx, cy + radius + 16), label, font=font(11), fill=MUTED, anchor="mm")


def series(base, amp, n=26, phase=0.4):
    values = []
    for i in range(n):
        value = base + amp * math.sin(i * 0.48 + phase) + amp * 0.35 * math.sin(i * 0.19 + 1.4)
        values.append(max(0.0, min(100.0, value)))
    return values


def draw_status_bar(d, w):
    d.text((28, 24), "09:41", font=font(17, True), fill=TEXT, anchor="lm")
    d.ellipse([w / 2 - 5, 12, w / 2 + 5, 22], fill=(3, 6, 12))
    x = w - 118
    for i, height in enumerate([5, 8, 11, 14]):
        d.rounded_rectangle([x + i * 5, 31 - height, x + i * 5 + 3, 31], radius=1, fill=TEXT)
    bx, by = w - 66, 32
    for radius in (13, 9, 5):
        d.arc([bx - radius, by - radius, bx + radius, by + radius], 225, 315, fill=TEXT, width=2)
    d.ellipse([bx - 2, by - 2, bx + 2, by + 2], fill=TEXT)
    bx = w - 38
    d.rounded_rectangle([bx, 20, bx + 23, 33], radius=3, outline=TEXT, width=1)
    d.rounded_rectangle([bx + 3, 23, bx + 17, 30], radius=1, fill=GREEN)
    d.rectangle([bx + 23, 24, bx + 26, 29], fill=TEXT)


def draw_refresh(d, x, y):
    d.ellipse([x - 17, y - 17, x + 17, y + 17], fill=SURF2, outline=BORDER, width=1)
    d.arc([x - 9, y - 9, x + 9, y + 9], 35, 320, fill=MUTED, width=2)
    d.polygon([(x + 7, y - 10), (x + 12, y - 3), (x + 4, y - 3)], fill=MUTED)


def draw_header(d, w, title, subtitle):
    d.text((22, 58), "P R O X G U A R D", font=font(10, True), fill=CYAN)
    d.text((22, 74), title, font=font(26, True), fill=TEXT)
    d.text((22, 108), subtitle, font=font(12), fill=MUTED)
    draw_refresh(d, w - 42, 86)


def draw_health_banner(d, w, y):
    x = 18
    banner_w = w - 36
    d.rounded_rectangle(
        [x, y, x + banner_w, y + 74],
        radius=20,
        fill=blend(GREEN, BG_TOP, 0.12),
        outline=blend(GREEN, BG_TOP, 0.38),
        width=1,
    )
    d.ellipse([x + 14, y + 19, x + 50, y + 55], fill=blend(GREEN, BG_TOP, 0.18))
    d.line([(x + 24, y + 37), (x + 31, y + 44), (x + 42, y + 29)], fill=GREEN, width=2, joint="curve")
    d.text((x + 62, y + 22), "Infrastructure opérationnelle", font=font(16, True), fill=TEXT)
    d.text((x + 62, y + 44), "12 invités actifs · synchro 09:41", font=font(11), fill=MUTED)
    d.line([(x + banner_w - 28, y + 30), (x + banner_w - 22, y + 37), (x + banner_w - 28, y + 44)], fill=MUTED, width=2)


def draw_bottom_nav(d, w, h, active):
    nav_h = 88
    top = h - nav_h
    d.rectangle([0, top, w, h], fill=(9, 15, 26))
    d.line([0, top, w, top], fill=BORDER, width=1)
    labels = ["Tableau", "Invités", "Alertes", "Réglages"]
    item_w = w / 4
    for index, label in enumerate(labels):
        cx = item_w * index + item_w / 2
        color = CYAN if index == active else MUTED
        if index == active:
            d.rounded_rectangle([cx - 34, top + 8, cx + 34, top + 54], radius=16, fill=blend(CYAN, BG_TOP, 0.14))
        if index == 0:
            for gx in (-8, 1):
                for gy in (-8, 1):
                    d.rounded_rectangle([cx + gx, top + 22 + gy, cx + gx + 6, top + 28 + gy], radius=1, fill=color)
        elif index == 1:
            for offset in (-7, 1):
                d.rounded_rectangle([cx - 9, top + 29 + offset, cx + 9, top + 34 + offset], radius=2, fill=color)
                d.ellipse([cx - 7, top + 31 + offset, cx - 5, top + 33 + offset], fill=BG_TOP)
        elif index == 2:
            d.arc([cx - 9, top + 20, cx + 9, top + 38], 180, 360, fill=color, width=2)
            d.rectangle([cx - 9, top + 29, cx + 9, top + 32], fill=color)
            d.ellipse([cx - 2, top + 34, cx + 2, top + 38], fill=color)
        else:
            for offset, knob in ((0, -5), (6, 6), (12, -2)):
                d.line([(cx - 9, top + 27 + offset), (cx + 9, top + 27 + offset)], fill=color, width=2)
                d.ellipse([cx + knob - 2, top + 25 + offset, cx + knob + 2, top + 29 + offset], fill=color)
        d.text((cx, top + 66), label, font=font(10, index == active), fill=color, anchor="ma")


def draw_phone(canvas, x, y, w, h, content):
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle([x + 6, y + 20, x + w + 6, y + h + 20], radius=58, fill=(0, 0, 0, 170))
    shadow = shadow.filter(ImageFilter.GaussianBlur(28))
    canvas.alpha_composite(shadow)

    d = ImageDraw.Draw(canvas)
    d.rounded_rectangle([x, y, x + w, y + h], radius=56, fill=(4, 8, 15), outline=(45, 61, 83), width=2)
    d.rounded_rectangle([x - 3, y + 210, x + 1, y + 290], radius=2, fill=(35, 46, 63))
    d.rounded_rectangle([x + w - 1, y + 250, x + w + 3, y + 330], radius=2, fill=(35, 46, 63))

    screen_w, screen_h = w - 24, h - 24
    screen = Image.new("RGBA", (screen_w, screen_h), BG_TOP + (255,))
    sd = ImageDraw.Draw(screen)
    content(screen, sd, screen_w, screen_h)
    mask = Image.new("L", (screen_w, screen_h), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, screen_w, screen_h], radius=44, fill=255)
    canvas.paste(screen, (x + 12, y + 12), mask)


def dashboard_content(img, d, w, h):
    draw_status_bar(d, w)
    draw_header(d, w, "Hôte pve", "Supervision en temps réel")
    draw_health_banner(d, w, 128)

    card(d, [18, 216, w - 18, 590], radius=22)
    d.text((36, 232), "HÔTE PROXMOX", font=font(9, True), fill=MUTED)
    d.text((36, 248), "pve", font=font(25, True), fill=TEXT)
    pill(d, w - 36, 244, "En ligne", GREEN, size=11)
    d.text((36, 282), "PVE 9.2.2 · AMD Ryzen 5 3600 · 12 vCPU", font=font(10), fill=MUTED)
    ring(d, 36 + 110, 356, 40, 0.131, CYAN, "13%", "CPU")
    ring(d, w / 2, 356, 40, 0.59, CYAN, "59%", "RAM")
    ring(d, w - 36 - 110, 356, 40, 0.254, CYAN, "25%", "Disque")
    d.line([36, 426, w - 36, 426], fill=BORDER, width=1)
    for x, label, value in [
        (36, "CHARGE 1/5/15", "2,59 · 2,01 · 2,17"),
        (270, "UPTIME", "6 j 4 h"),
        (486, "INVITÉS", "12 / 12"),
    ]:
        d.text((x, 442), label, font=font(8, True), fill=MUTED)
        d.text((x, 458), value, font=font(13, True), fill=TEXT)
    d.text((36, 558), "RAM 18,3 Go / 31,3 Go · swap 14,5 Go · PVE 9.2.2", font=font(9), fill=MUTED)

    card(d, [18, 602, w - 18, 872], radius=22)
    d.text((36, 618), "Activité sur 1 h", font=font(16, True), fill=TEXT)
    pill(d, w - 36, 617, "1 h", CYAN, size=10)
    rows = [
        ("CPU", "13,1 %", series(13, 4.5), CYAN),
        ("Mémoire", "59,0 %", series(59, 7.0, phase=1.2), GREEN),
        ("Disque racine", "25,4 %", series(25, 2.0, phase=2.1), AMBER),
    ]
    for index, (label, value, values, color) in enumerate(rows):
        top = 660 + index * 64
        d.text((36, top), label, font=font(10), fill=MUTED)
        d.text((w - 36, top), value, font=font(10, True), fill=TEXT, anchor="ra")
        sparkline(d, (36, top + 19, w - 36, top + 49), values, color, surface=SURF, width=2)

    card_w = (w - 48) / 2
    card(d, [18, 884, 18 + card_w, 1032], radius=20)
    d.text((36, 900), "12/12", font=font(24, True), fill=TEXT)
    d.text((36, 934), "invités actifs", font=font(11), fill=MUTED)
    d.text((36, 954), "0 VM · 12 conteneurs", font=font(9), fill=MUTED)
    d.text((36, 982), "Tout est en ligne", font=font(9, True), fill=GREEN)
    card(d, [w - 18 - card_w, 884, w - 18, 1032], radius=20)
    d.text((w - 18 - card_w + 18, 900), "201", font=font(24, True), fill=TEXT)
    d.text((w - 18 - card_w + 18, 934), "mises à jour APT", font=font(11), fill=MUTED)
    d.text((w - 18 - card_w + 18, 954), "201 paquets disponibles", font=font(9), fill=MUTED)
    d.text((w - 18 - card_w + 18, 982), "À planifier", font=font(9, True), fill=AMBER)

    card(d, [18, 1044, w - 18, 1148], radius=20, fill=SURF)
    d.text((36, 1060), "Historique local", font=font(14, True), fill=TEXT)
    d.text((36, 1080), "24 h · 2 880 mesures", font=font(9), fill=MUTED)
    d.line([(w - 52, 1066), (w - 46, 1072), (w - 52, 1078)], fill=MUTED, width=2)
    sparkline(d, (36, 1098, w - 36, 1136), series(13, 6, n=40), CYAN, surface=SURF, width=2)

    draw_bottom_nav(d, w, h, 0)


def guest_row(d, w, y, name, vmid, status, cpu, ram, disk, uptime, type_label="CT", stopped=False):
    height = 118
    card(d, [18, y, w - 18, y + height], radius=18, fill=SURF)
    type_color = CYAN if type_label == "CT" else AMBER
    d.ellipse([34, y + 17, 72, y + 55], fill=blend(type_color, SURF, 0.16))
    d.text((53, y + 36), type_label, font=font(10, True), fill=type_color, anchor="mm")
    d.text((84, y + 16), name, font=font(16, True), fill=TEXT)
    d.text((84, y + 40), f"{type_label} {vmid} · pve", font=font(9), fill=MUTED)
    if stopped:
        pill(d, w - 32, y + 16, "Arrêté", RED, size=9, height=22)
    else:
        d.ellipse([w - 92, y + 24, w - 84, y + 32], fill=GREEN)
        d.text((w - 32, y + 28), "Actif", font=font(9, True), fill=GREEN, anchor="rm")
    d.line([34, y + 66, w - 34, y + 66], fill=BORDER, width=1)
    for x, label, value in [
        (34, "CPU", cpu),
        (196, "RAM", ram),
        (358, "DISQUE", disk),
        (556, "UPTIME", uptime),
    ]:
        d.text((x, y + 78), label, font=font(8, True), fill=MUTED)
        d.text((x, y + 92), value, font=font(12, True), fill=TEXT)


def guests_content(img, d, w, h):
    draw_status_bar(d, w)
    draw_header(d, w, "Invités", "11 actifs · 1 arrêté")

    card(d, [18, 132, w - 18, 180], radius=24, fill=SURF2)
    d.ellipse([36, 147, 51, 162], outline=MUTED, width=2)
    d.line([(49, 160), (57, 168)], fill=MUTED, width=2)
    d.text((66, 148), "Rechercher un nom, un VMID, un tag…", font=font(11), fill=MUTED)

    chips = [("Tous", True), ("VM", False), ("Conteneurs", False), ("Actifs", False), ("Arrêtés", False)]
    x = 18
    for label, active in chips:
        fnt = font(10, active)
        width = d.textlength(label, font=fnt) + 28
        d.rounded_rectangle(
            [x, 194, x + width, 226],
            radius=16,
            fill=blend(CYAN, BG_TOP, 0.16) if active else SURF,
            outline=blend(CYAN, BG_TOP, 0.55) if active else BORDER,
            width=1,
        )
        d.text((x + width / 2, 210), label, font=fnt, fill=CYAN if active else MUTED, anchor="mm")
        x += width + 8

    d.text((18, 246), "7 invités affichés", font=font(9), fill=MUTED)
    d.text((w - 18, 246), "Trier · Statut", font=font(9, True), fill=CYAN, anchor="ra")

    rows = [
        ("openclaw", 100, "0,6 %", "78 %", "36 %", "6 j 4 h", False),
        ("searxng", 101, "0,1 %", "21 %", "21 %", "6 j 4 h", False),
        ("llm", 102, "2,4 %", "64 %", "45 %", "6 j 4 h", False),
        ("code", 103, "—", "—", "42 %", "—", True),
        ("trading", 104, "0,8 %", "12 %", "8 %", "6 j 3 h", False),
        ("documind", 105, "1,2 %", "31 %", "52 %", "6 j 2 h", False),
        ("beszel", 106, "0,2 %", "7 %", "4 %", "6 j 2 h", False),
    ]
    for index, (name, vmid, cpu, ram, disk, uptime, stopped) in enumerate(rows):
        guest_row(d, w, 264 + index * 126, name, vmid, "", cpu, ram, disk, uptime, stopped=stopped)

    draw_bottom_nav(d, w, h, 1)


def toggle_row(d, w, y, title, subtitle, checked=True, icon="warning"):
    height = 104
    card(d, [18, y, w - 18, y + height], radius=18)
    d.ellipse([34, y + 20, 68, y + 54], fill=blend(CYAN, SURF, 0.15))
    if icon == "warning":
        d.polygon([(51, y + 29), (61, y + 47), (41, y + 47)], outline=CYAN, width=2)
        d.line([(51, y + 36), (51, y + 41)], fill=CYAN, width=2)
        d.ellipse([50, y + 43, 52, y + 45], fill=CYAN)
    else:
        for radius in (11, 7):
            d.arc([51 - radius, y + 37 - radius, 51 + radius, y + 37 + radius], 225, 315, fill=CYAN, width=2)
        d.ellipse([49, y + 33, 53, y + 37], fill=CYAN)
        d.line([(40, y + 26), (62, y + 48)], fill=RED, width=2)
    d.text((80, y + 22), title, font=font(15, True), fill=TEXT)
    d.text((80, y + 44), subtitle, font=font(10), fill=MUTED)
    switch(d, w - 64, y + 39, checked)


def alerts_content(img, d, w, h):
    draw_status_bar(d, w)
    draw_header(d, w, "Alertes", "Prévenu avant la panne")

    card(d, [18, 132, w - 18, 220], radius=20)
    d.ellipse([34, 148, 70, 184], fill=blend(CYAN, SURF, 0.15))
    d.arc([44, 157, 60, 175], 180, 360, fill=CYAN, width=2)
    d.rectangle([44, 165, 60, 169], fill=CYAN)
    d.ellipse([50, 172, 54, 176], fill=CYAN)
    d.text((82, 150), "Alertes actives", font=font(16, True), fill=TEXT)
    d.text((82, 172), "Vérification toutes les 15 min", font=font(10), fill=MUTED)
    switch(d, w - 64, 163, True)

    card(d, [18, 232, w - 18, 356], radius=18, fill=blend(RED, SURF, 0.10), outline=blend(RED, SURF, 0.34))
    logo = generate_icon.draw_icon(128, background=True).resize((24, 24), Image.LANCZOS)
    img.paste(logo, (34, 246), logo)
    d.text((66, 248), "ProxGuard", font=font(10, True), fill=TEXT)
    d.text((w - 34, 248), "maintenant", font=font(9), fill=MUTED, anchor="ra")
    d.text((34, 276), "CPU de l'hôte élevé", font=font(15, True), fill=TEXT)
    d.text((34, 298), "Le CPU de « pve » est à 92 % (seuil 85 %).", font=font(10), fill=MUTED)
    d.text((w - 34, 322), "OUVRIR", font=font(9, True), fill=CYAN, anchor="ra")

    d.text((18, 372), "SEUILS DE L'HÔTE", font=font(8, True), fill=MUTED)
    card(d, [18, 388, w - 18, 566], radius=18)
    d.ellipse([34, 404, 70, 440], fill=blend(CYAN, SURF, 0.15))
    d.arc([42, 412, 62, 432], 150, 390, fill=CYAN, width=2)
    d.line([(52, 422), (58, 416)], fill=CYAN, width=2)
    d.text((82, 406), "CPU de l'hôte", font=font(15, True), fill=TEXT)
    d.text((82, 428), "Alerte au-delà du seuil", font=font(9), fill=MUTED)
    switch(d, w - 64, 415, True)
    d.text((34, 466), "Seuil d'alerte", font=font(10), fill=MUTED)
    pill(d, w - 34, 458, "85 %", CYAN, size=10, height=24)
    slider(d, 38, 506, w - 76, (85 - 50) / 49, CYAN)
    d.text((34, 522), "50 %", font=font(8), fill=MUTED)
    d.text((w - 34, 522), "99 %", font=font(8), fill=MUTED, anchor="ra")

    card(d, [18, 578, w - 18, 742], radius=18)
    d.ellipse([34, 594, 70, 630], fill=blend(GREEN, SURF, 0.15))
    d.rounded_rectangle([44, 606, 60, 619], radius=3, outline=GREEN, width=2)
    d.rectangle([44, 611, 60, 613], fill=GREEN)
    d.text((82, 596), "Mémoire de l'hôte", font=font(15, True), fill=TEXT)
    d.text((82, 618), "RAM utilisée", font=font(9), fill=MUTED)
    switch(d, w - 64, 605, True)
    d.text((34, 652), "Seuil d'alerte", font=font(10), fill=MUTED)
    pill(d, w - 34, 644, "90 %", GREEN, size=10, height=24)
    slider(d, 38, 692, w - 76, (90 - 50) / 49, GREEN)
    d.text((34, 708), "50 %", font=font(8), fill=MUTED)
    d.text((w - 34, 708), "99 %", font=font(8), fill=MUTED, anchor="ra")

    card(d, [18, 754, w - 18, 918], radius=18)
    d.ellipse([34, 770, 70, 806], fill=blend(AMBER, SURF, 0.15))
    for offset in (-8, 0, 8):
        d.rounded_rectangle([44, 785 + offset, 60, 787 + offset], radius=1, fill=AMBER)
    d.text((82, 772), "Disque racine", font=font(15, True), fill=TEXT)
    d.text((82, 794), "Approche de la saturation", font=font(9), fill=MUTED)
    switch(d, w - 64, 781, True)
    d.text((34, 828), "Seuil d'alerte", font=font(10), fill=MUTED)
    pill(d, w - 34, 820, "85 %", AMBER, size=10, height=24)
    slider(d, 38, 868, w - 76, (85 - 50) / 49, AMBER)
    d.text((34, 884), "50 %", font=font(8), fill=MUTED)
    d.text((w - 34, 884), "99 %", font=font(8), fill=MUTED, anchor="ra")

    toggle_row(d, w, 930, "Invité arrêté", "Une VM ou un conteneur s'arrête", True, icon="warning")
    toggle_row(d, w, 1046, "Hôte injoignable", "L'API Proxmox ne répond plus", True, icon="wifi")

    draw_bottom_nav(d, w, h, 2)


def background():
    img = Image.new("RGBA", (W, H), BG_TOP + (255,))
    d = ImageDraw.Draw(img)
    for y in range(H):
        t = y / (H - 1)
        color = tuple(int(BG_TOP[i] + (BG_BOT[i] - BG_TOP[i]) * t) for i in range(3))
        d.line([(0, y), (W, y)], fill=color + (255,))

    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse([W * 0.45, -W * 0.25, W * 1.35, W * 0.70], fill=CYAN + (55,))
    gd.ellipse([-W * 0.35, H * 0.55, W * 0.45, H * 1.10], fill=PURPLE + (30,))
    glow = glow.filter(ImageFilter.GaussianBlur(130))
    img = Image.alpha_composite(img, glow)

    dots = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    dd = ImageDraw.Draw(dots)
    for y in range(40, 520, 44):
        for x in range(40, W, 44):
            dd.ellipse([x, y, x + 2, y + 2], fill=(255, 255, 255, 13))
    img = Image.alpha_composite(img, dots)
    return img


def base_visual(headline_white, headline_accent, subline, chips):
    img = background()
    d = ImageDraw.Draw(img)

    icon = generate_icon.draw_icon(256, background=True).resize((84, 84), Image.LANCZOS)
    img.paste(icon, (66, 62), icon)
    d.text((168, 72), "ProxGuard", font=font(38, True), fill=TEXT)
    d.text((170, 118), "Supervision Proxmox VE et alertes sur ton téléphone", font=font(17), fill=MUTED)

    category = "OUTILS"
    fnt = font(12, True)
    cw = d.textlength(category, font=fnt) + 34
    d.rounded_rectangle([W - 72 - cw, 84, W - 72, 118], radius=17, fill=(13, 26, 41), outline=(44, 84, 100))
    d.text((W - 72 - cw / 2, 101), category, font=fnt, fill=CYAN, anchor="mm")

    d.text((72, 198), headline_white, font=font(54, True), fill=TEXT)
    d.text((72, 262), headline_accent, font=font(54, True), fill=CYAN)

    sub_y = 348
    for line in wrap(d, subline, 23, W - 144):
        d.text((72, sub_y), line, font=font(23), fill=MUTED)
        sub_y += 31

    x = 72
    for chip in chips:
        fnt = font(14, True)
        cw = d.textlength(chip, font=fnt) + 46
        d.rounded_rectangle([x, 430, x + cw, 474], radius=22, fill=(13, 26, 41), outline=BORDER)
        d.ellipse([x + 16, 447, x + 24, 455], fill=CYAN)
        d.text((x + 32, 452), chip, font=fnt, fill=MUTED, anchor="lm")
        x += cw + 12

    return img


def footer(img):
    d = ImageDraw.Draw(img)
    d.text((W / 2, 1880), "Supervision Proxmox VE · Alertes locales · Proxboss",
           font=font(17), fill=(110, 128, 150), anchor="mm")
    return img


def build_dashboard():
    img = base_visual(
        "Toute ton infra,",
        "en un coup d'œil.",
        "CPU, RAM, stockage et invités. L'essentiel de Proxmox VE, toujours à portée de main.",
        ["Temps réel", "24 h d'historique", "PVE 9"],
    )
    draw_phone(img, 170, 520, 740, 1320, dashboard_content)
    return footer(img)


def build_guests():
    img = base_visual(
        "Tes invités,",
        "triés et filtrés.",
        "VMs et conteneurs, recherche instantanée, filtres et détail de chaque ressource.",
        ["VMs & conteneurs", "Recherche", "Filtres"],
    )
    draw_phone(img, 170, 520, 740, 1320, guests_content)
    return footer(img)


def build_alerts():
    img = base_visual(
        "Alerté avant",
        "la panne.",
        "Des seuils clairs, une vérification toutes les 15 min et aucun spam.",
        ["Seuils sur mesure", "WorkManager", "Anti-spam"],
    )
    draw_phone(img, 170, 520, 740, 1320, alerts_content)
    return footer(img)


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, builder in [
        ("screenshot-dashboard.png", build_dashboard),
        ("screenshot-guests.png", build_guests),
        ("screenshot-alerts.png", build_alerts),
    ]:
        image = builder().convert("RGB")
        image.save(os.path.join(OUT, name), "PNG")
        print("écrit", name, image.size)


if __name__ == "__main__":
    main()

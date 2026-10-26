#!/usr/bin/env python3
"""BCP LATAM Pass: Visa (Clásica, Oro, Platinum, Signature, Infinite Iridium/Sapphire) y American Express
(Clásica, Oro, Platinum, Black). Reutiliza las primitivas de `gen_bcp_visa_qore.py`.

Ejecutar:  python scripts/cards/gen_bcp_latam.py
Salida: docs/design/cards/bcp-<red>-latam-<nivel>/*.svg y app/src/main/res/drawable-nodpi/bcp_<red>_latam_<nivel>_{front,back}.xml
Todo es vectorial; la geometría se midió del arte oficial publicado por BCP solo como referencia.
"""
from __future__ import annotations

import math
import os
import random
from dataclasses import dataclass

from gen_bcp_visa_qore import (  # type: ignore
    BCP_ORANGE, CORNER, DESIGN_DIR, H, RES_DIR, W, WHITE, Gradient, Node, Path, Radial, Scene,
    _font_glyph_path, _mpl_font, bcp_logo, poly, rrect, visa_logo, write_svg, write_vector_drawable,
)

BLUE_PLATE = "#1A3B8E"
AMEX_BLUE = "#2E77BC"


# --------------------------------------------------------------------------- piezas


def text_path(text: str, x: float, y: float, width: float, cap: float, bold=True, tracking=0.0, align="left") -> Path:
    glyphs, adv, upm = _font_glyph_path(_mpl_font("DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf"), text, tracking)
    sx = width / adv
    sy = cap / (0.729 * upm)
    ox = x if align == "left" else x - width
    return glyphs.transform(sx, 0, 0, -sy, ox, y + cap)


def latam_logo(color=WHITE, mark="#E5233B", x=878.0, y=108.0, s=1.0) -> list:
    bars = Path()
    for i, (dx, w) in enumerate(((0, 44), (6, 34), (12, 24))):
        top = y + i * 21 * s
        bars.extend(poly([(x + dx * s + 10 * s, top), (x + (dx + w) * s + 10 * s, top), (x + (dx + w) * s, top + 15 * s), (x + dx * s, top + 15 * s)]))
    return [
        Node("LATAM-marca", bars, fill=mark),
        Node("LATAM-texto", text_path("LATAM", x + 64 * s, y, 232 * s, 46 * s, tracking=40), fill=color),
        Node("PASS-texto", text_path("PASS", x + 74 * s, y + 62 * s, 118 * s, 30 * s, bold=False, tracking=120), fill=color, fill_alpha=0.92),
    ]


def sample_cubic(p0, p1, p2, p3, n=48):
    pts = []
    for i in range(n + 1):
        t = i / n
        u = 1 - t
        x = u ** 3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t ** 3 * p3[0]
        y = u ** 3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t ** 3 * p3[1]
        dx = 3 * u * u * (p1[0] - p0[0]) + 6 * u * t * (p2[0] - p1[0]) + 3 * t * t * (p3[0] - p2[0])
        dy = 3 * u * u * (p1[1] - p0[1]) + 6 * u * t * (p2[1] - p1[1]) + 3 * t * t * (p3[1] - p2[1])
        pts.append((t, x, y, dx, dy))
    return pts


def ribbon(p0, p1, p2, p3, wmax: float) -> Path:
    left, right = [], []
    for t, x, y, dx, dy in sample_cubic(p0, p1, p2, p3):
        ln = math.hypot(dx, dy) or 1
        nx, ny = -dy / ln, dx / ln
        w = wmax * (math.sin(math.pi * t) ** 0.9) / 2
        left.append((x + nx * w, y + ny * w))
        right.append((x - nx * w, y - ny * w))
    return poly(left + right[::-1])


def ribbons(alpha=0.22, color=WHITE) -> list:
    a = ribbon((-40, 300), (330, 260), (470, 470), (1260, 330), 62)
    b = ribbon((-40, 470), (330, 500), (520, 280), (1260, 380), 52)
    return [Node("Cinta-A", a, fill=color, fill_alpha=alpha), Node("Cinta-B", b, fill=color, fill_alpha=alpha * 0.8)]


def dot_field(color=WHITE, seed=7, x0=-10.0, y0=420.0, avoid=((860.0, 560.0, 1213.0, 766.0),)) -> list:
    rnd = random.Random(seed)
    layers = {0.16: Path(), 0.28: Path(), 0.42: Path()}
    row = 0
    y = y0
    while y < H - 14:
        x = x0 + (24 if row % 2 else 0)
        while x < W + 10:
            if not any(a <= x <= c and b <= y <= d for a, b, c, d in avoid):
                depth = (y - y0) / (H - y0)
                level = 0.16 if depth < 0.33 else 0.28 if depth < 0.66 else 0.42
                if rnd.random() < 0.9:
                    r = 4.2 + 3.4 * depth + rnd.random() * 1.2
                    layers[level].extend(poly([(x + r * math.cos(a), y + r * math.sin(a)) for a in [i * math.pi / 3 for i in range(6)]]))
            x += 48
        y += 36
        row += 1
    return [Node(f"Puntos-{int(a * 100)}", p, fill=color, fill_alpha=a) for a, p in layers.items()]


def visa_plate(h_plate=139.0, x=883.0, y=583.0, w=300.0) -> list:
    logo = visa_logo(x=x + 30, y=y + 36, h=66, color=BLUE_PLATE)
    return [Node("Placa-VISA", rrect(x, y, w, h_plate, 10), fill=WHITE)] + [Node(n.name + "-azul", n.path, fill=BLUE_PLATE) for n in logo]


def amex_square(x=983.0, y=538.0, s=190.0) -> list:
    pad = s * 0.08
    return [
        Node("AMEX-cuadro", rrect(x, y, s, s, 6), fill=AMEX_BLUE),
        Node("AMEX-AMERICAN", text_path("AMERICAN", x + pad, y + s * 0.34, s - 2 * pad, s * 0.13, tracking=-10), fill=WHITE),
        Node("AMEX-EXPRESS", text_path("EXPRESS", x + pad, y + s * 0.54, s - 2 * pad, s * 0.13, tracking=-10), fill=WHITE),
        Node("AMEX-lineas", Path().M(x + pad, y + s * 0.30).L(x + s - pad, y + s * 0.30).M(x + pad, y + s * 0.71).L(x + s - pad, y + s * 0.71), stroke=WHITE, stroke_width=3, cap="butt"),
    ]


def plain_visa(label: str, color=WHITE, label_color=WHITE) -> list:
    from gen_bcp_visa_qore import tier_label
    return visa_logo(color=color) + tier_label(label, color=label_color)


# --------------------------------------------------------------------------- especiales


def black_planet() -> tuple[list, list]:
    grads = [Radial("planeta", 1330, 470, 560, [(0.0, "#0A2038", 1.0), (0.8, "#04101D", 1.0), (1.0, "#000000", 1.0)])]
    c = Path().extend(__import__("gen_bcp_visa_qore").circle(1330, 470, 540))
    nodes = [
        Node("Planeta", c, fill="url(#planeta)"),
        Node("Planeta-halo", c, stroke="#2E7CB0", stroke_width=22, stroke_alpha=0.18),
        Node("Planeta-borde", c, stroke="#4AA5D8", stroke_width=5, stroke_alpha=0.85),
    ]
    rnd = random.Random(3)
    stars = Path()
    for _ in range(14):
        sx, sy = rnd.uniform(40, 760), rnd.uniform(40, 700)
        stars.extend(poly([(sx - 2.5, sy), (sx, sy - 2.5), (sx + 2.5, sy), (sx, sy + 2.5)]))
    nodes.append(Node("Estrellas", stars, fill=WHITE, fill_alpha=0.7))
    lines = Path().M(70, 470).L(170, 450).M(430, 355).L(560, 330).M(500, 640).L(590, 622).M(760, 190).L(830, 175)
    nodes.append(Node("Estelas", lines, stroke=WHITE, stroke_width=3, stroke_alpha=0.45))
    return nodes, grads


def iridium_diamond() -> list:
    facets = [
        ([(560, 250), (620, 205), (730, 300)], "#1E1F24"), ([(620, 205), (880, 205), (730, 300)], "#34363C"),
        ([(880, 205), (960, 270), (800, 330)], "#26282D"), ([(560, 250), (730, 300), (640, 360)], "#2B2D33"),
        ([(730, 300), (800, 330), (640, 360)], "#17181B"), ([(960, 270), (800, 330), (1000, 360)], "#202226"),
        ([(560, 250), (640, 360), (730, 560)], "#0F1013"), ([(640, 360), (800, 330), (730, 560)], "#3B3E45"),
        ([(800, 330), (1000, 360), (730, 560)], "#15161A"),
    ]
    def grow(pts, k=1.1, cx=780.0, cy=440.0, dx=-90.0):
        # Escala y desplaza el diamante para que no invada la marca LATAM Pass (arriba a la derecha).
        return [(cx + (x - cx) * k + dx, cy + (y - cy) * k) for x, y in pts]

    def lift(c):
        r, g, b = (int(c[i:i + 2], 16) for i in (1, 3, 5))
        return "#%02X%02X%02X" % tuple(min(255, int(v * 1.35 + 14)) for v in (r, g, b))

    nodes = [Node(f"Facetas-{i}", poly(grow(pts)), fill=lift(c)) for i, (pts, c) in enumerate(facets)]
    rainbow = [("#E0452B", [(640, 360), (730, 560)]), ("#3CC46B", [(730, 300), (640, 360)]), ("#3B74E6", [(800, 330), (730, 560)]), ("#C649D8", [(560, 250), (640, 360)])]
    for i, (col, pts) in enumerate(rainbow):
        g = grow(pts)
        nodes.append(Node(f"Arista-{i}", Path().M(*g[0]).L(*g[1]), stroke=col, stroke_width=5, stroke_alpha=0.85))
    return nodes


def sapphire_mesh() -> list:
    rnd = random.Random(11)
    cols, rows = 9, 5
    pts = [[(c * W / (cols - 1) + rnd.uniform(-40, 40), r * H / (rows - 1) + rnd.uniform(-40, 40)) for c in range(cols)] for r in range(rows)]
    nodes = []
    for r in range(rows - 1):
        for c in range(cols - 1):
            a, b, d, e = pts[r][c], pts[r][c + 1], pts[r + 1][c], pts[r + 1][c + 1]
            for k, tri in enumerate(([a, b, d], [b, e, d])):
                t = (c / (cols - 1)) * 0.7 + (1 - r / (rows - 1)) * 0.3 + rnd.uniform(-0.12, 0.12)
                t = max(0.0, min(1.0, t))
                col = f"#{int(8 + 40 * t):02X}{int(18 + 90 * t):02X}{int(40 + 140 * t):02X}"
                nodes.append(Node(f"Malla-{r}-{c}-{k}", poly(tri), fill=col))
    return nodes


# --------------------------------------------------------------------------- variantes


@dataclass
class Latam:
    key: str                 # classic, gold, platinum, signature, iridium, sapphire, black
    network: str             # visa | amex
    name: str                # nombre mostrado
    preset_id: str
    bg: list                 # stops (offset, hex) con degradado diagonal
    bcp: str
    chevron: str
    latam: str
    ribbon_alpha: float
    dot_color: str
    plate: bool = False      # VISA sobre placa blanca
    tier: str | None = None  # etiqueta bajo el VISA suelto
    special: str | None = None
    back_bg: tuple = ("#17469A", "#123A82", "#0C2A66")

    @property
    def pascal(self): return f"Bcp{'Visa' if self.network == 'visa' else 'Amex'}Latam{self.key.capitalize()}"
    @property
    def folder(self): return f"bcp-{self.network}-latam-{self.key}"
    @property
    def res(self): return f"bcp_{self.network}_latam_{self.key}"


GOLD = [(0, "#B48B45"), (1, "#D9BF7C")]
PLAT = [(0, "#7C8088"), (1, "#A8ACB3")]
LATAM = [
    Latam("classic", "visa", "BCP Visa Clásica LATAM Pass", "bcp-visa-latam-classic", [(0, "#16449A"), (1, "#2E62B2")], WHITE, BCP_ORANGE, WHITE, 0.20, WHITE, plate=True),
    Latam("gold", "visa", "BCP Visa Oro LATAM Pass", "bcp-visa-latam-gold", GOLD, WHITE, WHITE, WHITE, 0.22, WHITE, plate=True, back_bg=("#B98429", "#A8741F", "#8E5F17")),
    Latam("platinum", "visa", "BCP Visa Platinum LATAM Pass", "bcp-visa-latam-platinum", PLAT, WHITE, BCP_ORANGE, WHITE, 0.24, WHITE, tier="Platinum", back_bg=("#8E8D8E", "#7A797A", "#626162")),
    Latam("signature", "visa", "BCP Visa Signature LATAM Pass", "bcp-visa-latam-signature", [(0, "#2B2723"), (1, "#3A3532")], "#9A9692", "#9A9692", "#E9E6E1", 0.18, "#9A9692", tier="Signature", back_bg=("#2E2A26", "#24211E", "#171513")),
    Latam("iridium", "visa", "BCP Visa Infinite Iridium LATAM Pass", "bcp-visa-latam-iridium", [(0, "#101113"), (1, "#26272B")], WHITE, WHITE, "#E8E8E8", 0.0, WHITE, tier="Infinite", special="iridium", back_bg=("#1A1B1E", "#111214", "#08090A")),
    Latam("sapphire", "visa", "BCP Visa Infinite Sapphire LATAM Pass", "bcp-visa-latam-sapphire", [(0, "#06142C"), (1, "#0C2A58")], WHITE, WHITE, "#E8EEF8", 0.0, WHITE, tier="Infinite", special="sapphire", back_bg=("#0B2347", "#081A36", "#040D1D")),
    Latam("classic", "amex", "BCP American Express Clásica LATAM Pass", "bcp-amex-latam-classic", [(0, "#0A479A"), (1, "#2C78CC")], WHITE, BCP_ORANGE, WHITE, 0.22, WHITE),
    Latam("gold", "amex", "BCP American Express Oro LATAM Pass", "bcp-amex-latam-gold", GOLD, WHITE, WHITE, WHITE, 0.22, WHITE, back_bg=("#B98429", "#A8741F", "#8E5F17")),
    Latam("platinum", "amex", "BCP American Express Platinum LATAM Pass", "bcp-amex-latam-platinum", PLAT, WHITE, BCP_ORANGE, WHITE, 0.24, WHITE, back_bg=("#8E8D8E", "#7A797A", "#626162")),
    Latam("black", "amex", "BCP American Express Black LATAM Pass", "bcp-amex-latam-black", [(0, "#000000"), (1, "#050607")], WHITE, WHITE, WHITE, 0.0, WHITE, special="black", back_bg=("#14161A", "#0B0C0F", "#030304")),
]


def front(v: Latam) -> Scene:
    grads = [Gradient("fondo", 0, 0, W, H * 0.75, [(o, c, 1.0) for o, c in v.bg])]
    nodes = [Node("Fondo", rrect(0, 0, W, H, CORNER), fill="url(#fondo)")]
    if v.special == "black":
        n, g = black_planet()
        nodes += n
        grads += g
    elif v.special == "iridium":
        nodes += iridium_diamond()
    elif v.special == "sapphire":
        nodes += sapphire_mesh()
    else:
        nodes += ribbons(v.ribbon_alpha)
        nodes += dot_field(v.dot_color)
    nodes += bcp_logo(1.25, 50, 100, color=v.bcp, chevron=v.chevron)
    nodes += latam_logo(color=v.latam)
    if v.network == "amex":
        nodes += amex_square()
    elif v.plate:
        nodes += visa_plate()
    else:
        nodes += plain_visa(v.tier or "", color=WHITE if v.key != "signature" else "#E9E6E1", label_color="#D6D6D6")
    return Scene(f"{v.name} · anverso", f"Anverso vectorial de {v.name}. Aproximación de diseño: no es un recurso oficial del banco.", grads, nodes)


def back(v: Latam) -> Scene:
    top, mid, bot = v.back_bg
    grads = [
        Gradient("fondo", 0, 0, 0, H, [(0.0, top, 1.0), (0.45, mid, 1.0), (1.0, bot, 1.0)]),
        Gradient("banda", 0, 78, 0, 192, [(0.0, "#1A1D22", 1.0), (0.5, "#0B0D10", 1.0), (1.0, "#1A1D22", 1.0)]),
        Gradient("firma", 0, 236, 0, 316, [(0.0, "#F4F6F8", 1.0), (1.0, "#E3E7EC", 1.0)]),
    ]
    nodes = [Node("Fondo", rrect(0, 0, W, H, CORNER), fill="url(#fondo)")]
    if v.special is None:
        nodes += [Node(n.name + "-tenue", n.path.transform(1, 0, 0, 1, 0, 120), fill=n.fill, fill_alpha=0.06) for n in ribbons(1.0)]
    nodes.append(Node("Banda-magnetica", Path().M(0, 78).L(W, 78).L(W, 192).L(0, 192).Z(), fill="url(#banda)"))
    nodes.append(Node("Panel-firma", rrect(72, 238, 760, 76, 6), fill="url(#firma)"))
    hatch = Path()
    x = 84
    while x + 38 < 826:
        hatch.M(x, 316).L(x + 38, 238)
        x += 22
    nodes.append(Node("Panel-firma-trama", hatch, stroke="#9BA6B4", stroke_width=1.6, cap="butt", stroke_alpha=0.55))
    nodes += bcp_logo(0.62, 72, 618, color=WHITE if v.key != "signature" else "#9A9A9A", chevron=v.chevron)
    if v.network == "amex":
        nodes += amex_square(x=1020, y=588, s=120)
    else:
        nodes += visa_logo(x=938, y=630, h=52, color=WHITE)
    return Scene(f"{v.name} · reverso", "Reverso vectorial ilustrativo (aproximación). No contiene PAN, CVV ni fecha de vencimiento.", grads, nodes)


def main():
    for v in LATAM:
        folder = os.path.join(DESIGN_DIR, v.folder)
        f, b = front(v), back(v)
        write_svg(f, os.path.join(folder, f"{v.pascal}Front.svg"))
        write_svg(b, os.path.join(folder, f"{v.pascal}Back.svg"))
        write_vector_drawable(f, os.path.join(RES_DIR, f"{v.res}_front.xml"))
        write_vector_drawable(b, os.path.join(RES_DIR, f"{v.res}_back.xml"))
        print("OK", v.folder)


if __name__ == "__main__":
    main()

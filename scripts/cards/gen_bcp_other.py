#!/usr/bin/env python3
"""BCP: productos sin familia propia — Débito, Visa Clásica convencional, Visa Light y Visa iO.

Ejecutar:  python scripts/cards/gen_bcp_other.py
Reutiliza las primitivas de `gen_bcp_visa_qore.py` y las piezas de `gen_bcp_latam.py`.
La Visa iO real es una tarjeta VERTICAL; aquí se adapta a horizontal (como el resto de tarjetas de la app).
"""
from __future__ import annotations

import math
import os
import random
from dataclasses import dataclass

from gen_bcp_visa_qore import (  # type: ignore
    BCP_ORANGE, CORNER, DESIGN_DIR, H, RES_DIR, W, WHITE, Gradient, Node, Path, Radial, Scene,
    arc, bcp_logo, circle, contactless, poly, rrect, smooth, tier_label, visa_logo, write_svg, write_vector_drawable,
)
from gen_bcp_latam import BLUE_PLATE, dot_field, text_path, visa_plate  # type: ignore


@dataclass
class Other:
    key: str
    name: str
    preset_id: str
    folder: str
    pascal: str
    res: str
    back_bg: tuple
    bcp: str = WHITE
    chevron: str = BCP_ORANGE
    visa: str = WHITE


# --------------------------------------------------------------------------- fondos


def swoosh(points, alpha, color=WHITE, name="Curva") -> Node:
    return Node(name, smooth(points, closed=True, tension=0.6), fill=color, fill_alpha=alpha)


def debit_scene() -> Scene:
    grads = [Gradient("fondo", 0, 0, W, H, [(0.0, "#0E62BE", 1.0), (0.55, "#0A4EA6", 1.0), (1.0, "#073A86", 1.0)])]
    nodes = [Node("Fondo", rrect(0, 0, W, H, CORNER), fill="url(#fondo)")]
    # panel naranja curvo a la derecha (parte del diseño de la débito BCP)
    panel = Path().M(930, -10).C(1010, 120, 1000, 330, 880, 520).C(840, 590, 800, 700, 770, 790).L(W + 20, 790).L(W + 20, -10).Z()
    nodes.append(Node("Panel-naranja", panel, fill="#EF7814"))
    nodes.append(Node("Curva-sombra", smooth([(-40, 640), (300, 520), (640, 560), (880, 700), (880, 800), (-40, 800)], closed=True, tension=0.6), fill="#06306F", fill_alpha=0.25))
    nodes += dot_field(WHITE, seed=5, y0=380.0, avoid=((830.0, 540.0, 1213.0, 766.0), (760.0, 0.0, 1213.0, 380.0)))
    nodes += bcp_logo(1.25, 50, 100, color=WHITE, chevron=BCP_ORANGE)
    nodes += visa_plate(h_plate=118, y=575.0)
    nodes += tier_label("Débito", x=1183.0, y=702.0, width=96, color=WHITE)
    return Scene("BCP Débito · anverso", "Anverso vectorial de la tarjeta de débito BCP. Aproximación de diseño: no es un recurso oficial.", grads, nodes)


def classic_scene() -> Scene:
    grads = [Gradient("fondo", 0, 0, W, H, [(0.0, "#1B62B0", 1.0), (0.5, "#164D99", 1.0), (1.0, "#0F2F6E", 1.0)])]
    nodes = [Node("Fondo", rrect(0, 0, W, H, CORNER), fill="url(#fondo)")]
    nodes.append(swoosh([(-50, 300), (260, 80), (700, -40), (1260, -60), (1260, 40), (800, 120), (420, 330), (120, 700), (-50, 800)], 0.10, name="Curva-superior"))
    nodes.append(swoosh([(-50, 820), (350, 470), (800, 230), (1260, 120), (1260, 820)], 0.12, color="#0B2A66", name="Curva-inferior"))
    nodes.append(swoosh([(520, 830), (760, 560), (1000, 360), (1260, 280), (1260, 830)], 0.16, color="#0B2A66", name="Curva-derecha"))
    nodes += dot_field(WHITE, seed=9, y0=400.0, avoid=((860.0, 560.0, 1213.0, 766.0),))
    nodes += bcp_logo(1.25, 50, 100, color=WHITE, chevron=BCP_ORANGE)
    nodes += visa_plate()
    return Scene("BCP Visa Clásica · anverso", "Anverso vectorial de la BCP Visa Clásica. Aproximación de diseño: no es un recurso oficial.", grads, nodes)


def light_scene() -> Scene:
    grads = [
        Gradient("fondo", 0, 0, 0, H, [(0.0, "#FFFFFF", 1.0), (1.0, "#F1F5F9", 1.0)]),
        Radial("pluma-base", 1100, 560, 560, [(0.0, "#3FB6C9", 1.0), (0.5, "#3C94C6", 0.75), (1.0, "#3C86C0", 0.0)]),
    ]
    nodes = [Node("Fondo", rrect(0, 0, W, H, CORNER), fill="url(#fondo)")]
    nodes.append(Node("Pluma-fondo", circle(1100, 560, 560), fill="url(#pluma-base)"))
    rnd = random.Random(21)
    base = (1160.0, 640.0)
    strokes = 78
    for i in range(strokes):
        t = i / (strokes - 1)
        ang = math.radians(-150 + 95 * t + rnd.uniform(-4, 4))        # abanico hacia arriba-izquierda
        length = 520 + 220 * math.sin(math.pi * t) + rnd.uniform(-40, 40)
        tip = (base[0] + length * math.cos(ang), base[1] + length * math.sin(ang))
        bend = rnd.uniform(70, 150) * (1 if t < 0.5 else 0.6)
        c1 = (base[0] + (tip[0] - base[0]) * 0.35 - bend * 0.3, base[1] + (tip[1] - base[1]) * 0.35 + bend)
        c2 = (base[0] + (tip[0] - base[0]) * 0.75 - bend * 0.5, base[1] + (tip[1] - base[1]) * 0.75 + bend * 0.2)
        r = int(0x2A + (0x3F - 0x2A) * t); g = int(0x2C + (0xB6 - 0x2C) * t); b = int(0x7C + (0xC9 - 0x7C) * t)
        col = "#%02X%02X%02X" % (r, g, b)
        nodes.append(Node(f"Pluma-{i}", Path().M(*base).C(*c1, *c2, *tip), stroke=col, stroke_width=2.5 + 5 * rnd.random(), stroke_alpha=0.35 + 0.5 * rnd.random()))
    nodes += bcp_logo(1.25, 50, 100, color="#0B3A8F", chevron=BCP_ORANGE)
    nodes += tier_label("Light", x=620.0, y=112.0, width=96, color="#5B6674")
    nodes += visa_plate()   # placa blanca: el logo VISA blanco no se lee sobre la pluma
    return Scene("BCP Visa Light · anverso", "Anverso vectorial de la BCP Visa Light. Aproximación de diseño: no es un recurso oficial.", grads, nodes)


def io_scene() -> Scene:
    grads = [
        Gradient("fondo", 0, 0, W, H, [(0.0, "#1A1A1B", 1.0), (0.5, "#0C0C0D", 1.0), (1.0, "#050506", 1.0)]),
        Gradient("media-luna", 0, 0, 0, H, [(0.0, "#55C2DC", 1.0), (0.55, "#5EA4DA", 1.0), (1.0, "#8C93D4", 1.0)]),
        Gradient("aro-io", 0, 620, 130, 700, [(0.0, "#FFFFFF", 1.0), (1.0, "#4FC0E0", 1.0)]),
    ]
    nodes = [Node("Fondo", rrect(0, 0, W, H, CORNER), fill="url(#fondo)")]
    outer = circle(230, 383, 450)
    inner = circle(150, 383, 370)
    moon = Path().extend(outer).extend(inner)
    nodes.append(Node("Media-luna", moon, fill="url(#media-luna)", even_odd=True))
    # contactless (gris), VISA (gris) y marca iO
    nodes += contactless(color="#8A8A8A", cx=1090, cy=94)
    nodes += visa_logo(color="#9A9A9A")
    io_i = Path().extend(arc(98, 616, 13, 0, 360)).Z()
    stem = Path().M(98, 654).L(98, 708)
    ring = arc(188, 664, 46, 0, 360)
    nodes += [Node("iO-punto", io_i, fill=WHITE), Node("iO-i", stem, stroke=WHITE, stroke_width=17),
              Node("iO-o", ring, stroke="#9BD8EA", stroke_width=17)]
    return Scene("BCP Visa iO · anverso", "Anverso vectorial de la tarjeta BCP Visa iO, adaptada a horizontal (la tarjeta real es vertical). Aproximación: no es un recurso oficial.", grads, nodes)


def back_for(v: Other, title: str) -> Scene:
    top, mid, bot = v.back_bg
    grads = [
        Gradient("fondo", 0, 0, 0, H, [(0.0, top, 1.0), (0.45, mid, 1.0), (1.0, bot, 1.0)]),
        Gradient("banda", 0, 78, 0, 192, [(0.0, "#1A1D22", 1.0), (0.5, "#0B0D10", 1.0), (1.0, "#1A1D22", 1.0)]),
        Gradient("firma", 0, 236, 0, 316, [(0.0, "#F4F6F8", 1.0), (1.0, "#E3E7EC", 1.0)]),
    ]
    nodes = [Node("Fondo", rrect(0, 0, W, H, CORNER), fill="url(#fondo)")]
    nodes.append(Node("Banda-magnetica", Path().M(0, 78).L(W, 78).L(W, 192).L(0, 192).Z(), fill="url(#banda)"))
    nodes.append(Node("Panel-firma", rrect(72, 238, 760, 76, 6), fill="url(#firma)"))
    hatch = Path()
    x = 84
    while x + 38 < 826:
        hatch.M(x, 316).L(x + 38, 238)
        x += 22
    nodes.append(Node("Panel-firma-trama", hatch, stroke="#9BA6B4", stroke_width=1.6, cap="butt", stroke_alpha=0.55))
    nodes += bcp_logo(0.62, 72, 618, color=v.bcp, chevron=v.chevron)
    nodes += visa_logo(x=938, y=630, h=52, color=v.visa)
    return Scene(title, "Reverso vectorial ilustrativo (aproximación). No contiene PAN, CVV ni fecha de vencimiento.", grads, nodes)


PRODUCTS = [
    (Other("debit", "BCP Débito", "bcp-debit-classic", "bcp-debit", "BcpDebit", "bcp_debit", ("#0E4F9E", "#0B438A", "#072C63")), debit_scene),
    (Other("visa-classic", "BCP Visa Clásica", "bcp-visa-classic", "bcp-visa-classic", "BcpVisaClassic", "bcp_visa_classic", ("#1B5CA8", "#164A92", "#0E2D6A")), classic_scene),
    (Other("visa-light", "BCP Visa Light", "bcp-visa-light", "bcp-visa-light", "BcpVisaLight", "bcp_visa_light", ("#F4F7FA", "#E6EDF3", "#D3DEE8"), bcp="#0B3A8F", visa="#0B3A8F"), light_scene),
    (Other("visa-io", "BCP Visa iO", "bcp-visa-io", "bcp-visa-io", "BcpVisaIo", "bcp_visa_io", ("#1C1C1D", "#0E0E0F", "#050506"), chevron=WHITE, visa="#9A9A9A"), io_scene),
]


def main():
    for v, front_fn in PRODUCTS:
        folder = os.path.join(DESIGN_DIR, v.folder)
        f = front_fn()
        b = back_for(v, f"{v.name} · reverso")
        write_svg(f, os.path.join(folder, f"{v.pascal}Front.svg"))
        write_svg(b, os.path.join(folder, f"{v.pascal}Back.svg"))
        write_vector_drawable(f, os.path.join(RES_DIR, f"{v.res}_front.xml"))
        write_vector_drawable(b, os.path.join(RES_DIR, f"{v.res}_back.xml"))
        print("OK", v.folder)


if __name__ == "__main__":
    main()

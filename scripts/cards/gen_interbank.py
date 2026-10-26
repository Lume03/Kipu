#!/usr/bin/env python3
"""Interbank: 17 estilos (débito, American Express, Visa, Mastercard y garantizada).

Ejecutar:  python scripts/cards/gen_interbank.py
Reutiliza las primitivas de `gen_bcp_visa_qore.py` y las piezas de `gen_bcp_latam.py`.
Medido (colores, composición) del arte publicado en interbank.pe para: Amex (Green, Gold, Platinum, Black, The Platinum Card) y
Visa (Clásica, Oro, Platinum, Signature, Infinite, Access, Premia). Débito, Mastercard y Garantía Líquida NO tienen arte
verificable: usan la misma composición «Benefit» con la paleta del catálogo y se marcan como aproximación.
"""
from __future__ import annotations

import math
import os
from dataclasses import dataclass

from gen_bcp_visa_qore import (  # type: ignore
    CORNER, DESIGN_DIR, H, RES_DIR, W, WHITE, Gradient, Node, Path, Radial, Scene, arc, circle, poly, rrect, smooth,
    tier_label, visa_logo, write_svg, write_vector_drawable,
)
from gen_bcp_latam import amex_square, text_path  # type: ignore

IBK_GREEN = "#00B34D"
IBK_BLUE = "#0036AA"
RED, ORANGE = "#EB001B", "#F79E1B"


# --------------------------------------------------------------------------- piezas


def ibk_mark(x, y, s, color, window=None, cut=True) -> list:
    """Logotipo (aproximado): cuadrado de esquina superior izquierda más redondeada con una ventana en paralelogramo."""
    outer = Path().M(x + 0.34 * s, y).L(x + s, y).L(x + s, y + s).L(x, y + s).L(x, y + 0.34 * s)
    outer.C(x, y + 0.14 * s, x + 0.14 * s, y, x + 0.34 * s, y).Z()
    hole = poly([(x + 0.27 * s, y + 0.27 * s), (x + 0.74 * s, y + 0.19 * s), (x + 0.74 * s, y + 0.73 * s), (x + 0.27 * s, y + 0.81 * s)])
    if cut:
        return [Node("Interbank-marca", Path().extend(outer).extend(hole), fill=color, even_odd=True)]
    return [Node("Interbank-marca", outer, fill=color), Node("Interbank-ventana", hole, fill=window or WHITE)]


def wordmark(x, y, color, h=40.0) -> list:
    return [Node("Interbank-texto", text_path("Interbank", x, y, h * 4.6, h * 0.8, tracking=-20), fill=color)]


def benefit(color=WHITE) -> list:
    return [Node("Benefit-texto", text_path("Benefit", 75, 78, 215, 38, bold=False, tracking=10), fill=color)]


def mastercard(cx=1060.0, cy=640.0, r=62.0) -> list:
    d = r * 0.58
    ax, bx = cx - d, cx + d
    half = (bx - ax) / 2
    a = math.acos(half / r)
    pts = [(ax + r * math.cos(t), cy + r * math.sin(t)) for t in [(-a + 2 * a * i / 24) for i in range(25)]]
    pts += [(bx + r * math.cos(math.pi - t), cy + r * math.sin(math.pi - t)) for t in [(a - 2 * a * i / 24) for i in range(25)]]
    return [Node("MC-rojo", circle(ax, cy, r), fill=RED), Node("MC-naranja", circle(bx, cy, r), fill=ORANGE), Node("MC-lente", poly(pts), fill="#FF5F00")]


def guilloche(x0, y0, w, h, color, alpha, step=14) -> Node:
    p = Path()
    cx, cy = x0 + w / 2, y0 + h / 2
    r = step
    while r < max(w, h) * 0.6:
        p.extend(arc(cx, cy, r, 0, 360))
        r += step
    return Node("Guilloche", p, stroke=color, stroke_width=1.4, stroke_alpha=alpha)


def centurion(cx, cy, r, color, alpha) -> list:
    return [Node("Medallon", circle(cx, cy, r), fill=color, fill_alpha=alpha * 0.55),
            Node("Medallon-aro", circle(cx, cy, r), stroke=color, stroke_width=4, stroke_alpha=alpha),
            Node("Medallon-casco", smooth([(cx - r * 0.45, cy + r * 0.5), (cx - r * 0.5, cy - r * 0.1), (cx - r * 0.1, cy - r * 0.62), (cx + r * 0.35, cy - r * 0.45), (cx + r * 0.2, cy + r * 0.05), (cx + r * 0.1, cy + r * 0.5)], closed=True), fill=color, fill_alpha=alpha)]


# --------------------------------------------------------------------------- variantes


@dataclass
class Ibk:
    key: str
    name: str
    preset_id: str
    layout: str                  # benefit | access | premia | debit | amex-classic | amex-card
    bg: list                     # stops (offset, hex)
    horizontal: bool = False     # degradado horizontal (acero cepillado)
    mark: str = WHITE
    text: str = WHITE
    network: str = "visa"        # visa | amex | mc | none
    tier: str | None = None
    footer: str | None = None    # texto adicional (p. ej. «Garantía Líquida»)
    digits: str = WHITE
    back_bg: tuple = ("#0B3A8F", "#082C70", "#051C47")
    brushed: bool = False

    @property
    def folder(self): return f"interbank-{self.key}"
    @property
    def pascal(self): return "Interbank" + "".join(p.capitalize() for p in self.key.split("-"))
    @property
    def res(self): return f"interbank_{self.key.replace('-', '_')}"


def stops(*pairs): return [(o, c) for o, c in pairs]


VARIANTS = [
    Ibk("visa-classic", "Interbank Visa Clásica", "interbank-visa-classic", "benefit", stops((0, IBK_BLUE), (1, IBK_BLUE)), mark="#17C25A", tier=None, digits=WHITE, back_bg=("#0A3AA6", "#082E86", "#05205C")),
    Ibk("visa-gold", "Interbank Visa Oro", "interbank-visa-gold", "benefit", stops((0, "#C99300"), (1, "#C99300")), mark=WHITE, digits="#2A1F00", back_bg=("#C99300", "#B58300", "#8F6700")),
    Ibk("visa-platinum", "Interbank Visa Platinum", "interbank-visa-platinum", "benefit", stops((0, "#8B8F90"), (1, "#8B8F90")), mark=WHITE, tier="Platinum", digits="#101418", back_bg=("#8B8F90", "#777B7D", "#5D6062")),
    Ibk("visa-signature", "Interbank Visa Signature", "interbank-visa-signature", "benefit", stops((0, "#111820"), (1, "#111820")), mark="#2A2E34", tier="Signature", digits=WHITE, back_bg=("#1B2129", "#111820", "#080B0F")),
    Ibk("visa-infinite", "Interbank Visa Infinite", "interbank-visa-infinite", "benefit", stops((0, "#6C6E70"), (0.3, "#9A9C9E"), (0.55, "#7A7C7E"), (0.8, "#A4A6A8"), (1, "#707274")), horizontal=True, mark=WHITE, tier="Infinite", digits="#101418", brushed=True, back_bg=("#85878A", "#6E7072", "#555759")),
    Ibk("visa-access", "Interbank Visa Access", "interbank-visa-access", "access", stops((0, "#2FC96D"), (0.35, "#05BE50"), (1, "#05BE50")), mark=IBK_BLUE, text=WHITE, digits="#06381B", back_bg=("#19C45F", "#08A94A", "#058038")),
    Ibk("visa-premia", "Interbank Visa Premia", "interbank-visa-premia", "premia", stops((0, "#FFFFFF"), (1, "#F4F6FA")), mark=IBK_BLUE, text=IBK_BLUE, digits="#12306E", back_bg=("#F5F7FA", "#E7ECF3", "#D3DBE6")),
    Ibk("mastercard-classic", "Interbank Mastercard Clásica", "interbank-mastercard-classic", "access", stops((0, "#2FC96D"), (0.35, "#00B34D"), (1, "#009940")), mark=IBK_BLUE, network="mc", digits="#06381B", back_bg=("#19C45F", "#08A94A", "#058038")),
    Ibk("mastercard-gold", "Interbank Mastercard Oro", "interbank-mastercard-gold", "benefit", stops((0, "#D8AB3E"), (1, "#B58832")), mark="#052654", network="mc", digits="#2A1F00", back_bg=("#C99C35", "#B58832", "#8F6A22")),
    Ibk("mastercard-platinum", "Interbank Mastercard Platinum", "interbank-mastercard-platinum", "benefit", stops((0, "#4A5568"), (1, "#2D3748")), mark=WHITE, network="mc", digits=WHITE, back_bg=("#4A5568", "#394253", "#232A38")),
    Ibk("guaranteed", "Interbank Tarjeta Garantía Líquida", "interbank-guaranteed", "benefit", stops((0, "#052654"), (1, "#021430")), mark="#00B34D", network="none", footer="Garantía Líquida", digits=WHITE, back_bg=("#0A2F66", "#052654", "#021430")),
    Ibk("debit", "Interbank Débito", "interbank-debit-classic", "debit", stops((0, "#2FC96D"), (0.35, "#00B34D"), (1, "#009940")), mark="#052654", tier="Débito", digits="#06381B", back_bg=("#19C45F", "#08A94A", "#058038")),
    Ibk("amex-green", "Interbank American Express Green", "interbank-amex-green", "amex-classic", stops((0, "#A9D8BC"), (0.5, "#CFE3D6"), (1, "#8FBFA2")), mark="#1D4F37", text="#1D4F37", network="none", digits="#12301F", back_bg=("#A9D8BC", "#8FBFA2", "#6FA486")),
    Ibk("amex-gold", "Interbank American Express Gold", "interbank-amex-gold", "amex-classic", stops((0, "#E1BD71"), (0.5, "#A88848"), (1, "#E1BD71")), mark="#452E24", text="#3B2A18", network="none", digits="#2A1F00", back_bg=("#D2AD62", "#A88848", "#7E6230")),
    Ibk("amex-platinum", "Interbank American Express Platinum", "interbank-amex-platinum", "benefit", stops((0, "#A2A2A2"), (0.5, "#B0B0B0"), (1, "#8E8E8E")), mark=WHITE, network="amex", digits="#101418", back_bg=("#A2A2A2", "#8E8E8E", "#707070")),
    Ibk("amex-black", "Interbank American Express Black", "interbank-amex-black", "benefit", stops((0, "#111820"), (1, "#111820")), mark="#2A2E34", network="amex", digits=WHITE, back_bg=("#1B2129", "#111820", "#080B0F")),
    Ibk("amex-the-platinum-card", "Interbank The Platinum Card", "interbank-amex-the-platinum-card", "amex-card", stops((0, "#E2E6E9"), (0.5, "#C4CACF"), (1, "#BAC2C7")), mark="#5A5553", text="#3E3B3A", network="none", digits="#191C1E", back_bg=("#D4D9DD", "#BAC2C7", "#9AA3A9")),
]


def front(v: Ibk) -> Scene:
    if v.horizontal:
        grads = [Gradient("fondo", 0, 0, W, 0, [(o, c, 1.0) for o, c in v.bg])]
    else:
        grads = [Gradient("fondo", 0, 0, W, H, [(o, c, 1.0) for o, c in v.bg])]
    nodes = [Node("Fondo", rrect(0, 0, W, H, CORNER), fill="url(#fondo)")]
    if v.brushed:
        lines = Path()
        y = 6
        while y < H:
            lines.M(0, y).L(W, y)
            y += 7
        nodes.append(Node("Acero-cepillado", lines, stroke=WHITE, stroke_width=1.2, cap="butt", stroke_alpha=0.07))
    lay = v.layout
    if lay == "benefit":
        nodes += benefit(v.text)
        nodes += ibk_mark(1010, 52, 150, v.mark)
    elif lay in ("access", "debit"):
        nodes += ibk_mark(75, 56, 66, v.mark)
        nodes += wordmark(158, 62, v.text, h=44)
        label = "Access" if lay == "access" and v.network == "visa" else None
        if lay == "debit":
            label = None
        if label:
            nodes += [Node("Access-texto", text_path(label, 1160, 66, 150, 34, bold=False, align="right"), fill=WHITE)]
    elif lay == "premia":
        nodes += ibk_mark(75, 56, 66, IBK_GREEN)
        nodes += wordmark(158, 62, "#00A94F", h=44)
        nodes += [Node("Premia-texto", text_path("Premia", 1160, 66, 150, 34, bold=False, align="right"), fill=IBK_BLUE)]
        nodes += ibk_mark(560, 120, 330, IBK_BLUE, window=WHITE, cut=False)
    elif lay == "amex-classic":
        base = "#FFFFFF"
        nodes.append(Node("Marco", rrect(34, 34, W - 68, H - 68, 28), stroke=v.mark, stroke_width=3, stroke_alpha=0.45))
        nodes.append(guilloche(0, 0, W, H, v.mark, 0.10))
        nodes += centurion(600, 360, 170, v.mark, 0.5)
        nodes += [Node("AMEX-nombre", text_path("AMERICAN EXPRESS", W / 2, 96, 640, 52, tracking=30, align="left").transform(1, 0, 0, 1, -320, 0), fill=v.text)]
    elif lay == "amex-card":
        nodes.append(Node("Marco", rrect(34, 34, W - 68, H - 68, 28), stroke=v.mark, stroke_width=3, stroke_alpha=0.5))
        nodes.append(guilloche(0, 0, W, H, v.mark, 0.10))
        nodes += centurion(600, 360, 170, v.mark, 0.5)
        nodes += [Node("AMEX-nombre", text_path("AMERICAN EXPRESS", W / 2, 96, 640, 52, tracking=30, align="left").transform(1, 0, 0, 1, -320, 0), fill=v.text)]
    # red de pago
    if v.network == "visa":
        if lay == "premia":
            nodes += [Node(n.name, n.path, fill=IBK_BLUE) for n in visa_logo()]
        elif lay in ("access", "debit"):
            nodes += [Node(n.name, n.path, fill=IBK_BLUE if lay == "access" else WHITE) for n in visa_logo()]
        else:
            nodes += visa_logo(color=WHITE)
        if v.tier:
            nodes += tier_label(v.tier, color=v.text if lay == "premia" else WHITE)
    elif v.network == "amex":
        nodes += amex_square()
    elif v.network == "mc":
        nodes += mastercard()
    if v.footer:
        nodes += [Node("Pie", text_path(v.footer, 75, 520, 380, 34, bold=False, tracking=15), fill=WHITE, fill_alpha=0.9)]
    return Scene(f"{v.name} · anverso", f"Anverso vectorial de {v.name}. Aproximación de diseño: no es un recurso oficial del banco.", grads, nodes)


def back(v: Ibk) -> Scene:
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
    dark_bg = v.digits == WHITE
    c = WHITE if dark_bg or v.layout in ("access", "debit") else v.mark
    if v.layout in ("amex-classic", "amex-card", "premia"):
        c = v.mark
    nodes += ibk_mark(72, 600, 70, c if c != WHITE else WHITE)
    nodes += wordmark(158, 610, c, h=40)
    if v.network == "visa":
        nodes += visa_logo(x=938, y=630, h=52, color=WHITE if dark_bg or v.layout in ("access", "debit") else IBK_BLUE)
    elif v.network == "amex":
        nodes += amex_square(x=1020, y=588, s=120)
    elif v.network == "mc":
        nodes += mastercard(cx=1080, cy=650, r=42)
    return Scene(f"{v.name} · reverso", "Reverso vectorial ilustrativo (aproximación). No contiene PAN, CVV ni fecha de vencimiento.", grads, nodes)


def main():
    for v in VARIANTS:
        folder = os.path.join(DESIGN_DIR, v.folder)
        f, b = front(v), back(v)
        write_svg(f, os.path.join(folder, f"{v.pascal}Front.svg"))
        write_svg(b, os.path.join(folder, f"{v.pascal}Back.svg"))
        write_vector_drawable(f, os.path.join(RES_DIR, f"{v.res}_front.xml"))
        write_vector_drawable(b, os.path.join(RES_DIR, f"{v.res}_back.xml"))
        print("OK", v.folder)


if __name__ == "__main__":
    main()

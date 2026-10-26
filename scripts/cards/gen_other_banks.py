#!/usr/bin/env python3
"""Banco Falabella (CMR), Banco Ripley y BanBif: 15 estilos.

Ejecutar:  python scripts/cards/gen_other_banks.py
- Falabella: colores y composición medidos del arte oficial de bancofalabella.pe (CMR Visa, Platinum y Signature son tarjetas
  verticales lisas: se adaptan a horizontal). Básica, Garantía y Débito no tienen arte propio verificable.
- Ripley y BanBif: no se encontró arte oficial verificable; usan la paleta del catálogo (`CardStylePresets`, marcados «inspirado»).
"""
from __future__ import annotations

import os
from dataclasses import dataclass

from gen_bcp_visa_qore import (  # type: ignore
    CORNER, DESIGN_DIR, H, RES_DIR, W, WHITE, Gradient, Node, Path, Scene, arc, circle, contactless, rrect, smooth,
    tier_label, visa_logo, write_svg, write_vector_drawable,
)
from gen_bcp_latam import text_path  # type: ignore
from gen_interbank import mastercard  # type: ignore


@dataclass
class Bank:
    bank: str                  # falabella | ripley | banbif
    key: str
    name: str
    preset_id: str
    bg: list
    network: str               # visa | mc
    tier: str | None = None
    text: str = WHITE
    accent: str = WHITE
    digits: str = WHITE
    back_bg: tuple = ("#333", "#222", "#111")
    light: bool = False        # fondo claro (logos oscuros)

    @property
    def folder(self): return f"{self.bank}-{self.key}"
    @property
    def pascal(self): return self.bank.capitalize() + "".join(p.capitalize() for p in self.key.split("-"))
    @property
    def res(self): return f"{self.bank}_{self.key.replace('-', '_')}"


def S(*p): return [(o, c) for o, c in p]


FAL = "#40AC35"
VARIANTS = [
    Bank("falabella", "cmr-visa-classic", "Banco Falabella CMR Visa", "falabella-cmr-visa-classic", S((0, FAL), (1, "#3A9F30")), "visa", digits="#06260A", back_bg=("#47B33C", "#3FA934", "#2F8728")),
    Bank("falabella", "cmr-visa-basic", "Banco Falabella CMR Visa Básica", "falabella-cmr-visa-basic", S((0, "#4DB848"), (1, "#40AC35")), "visa", tier="Básica", digits="#06260A", back_bg=("#4DB848", "#40AC35", "#2F8728")),
    Bank("falabella", "cmr-visa-platinum", "Banco Falabella CMR Visa Platinum", "falabella-cmr-visa-platinum", S((0, "#A6B3B2"), (1, "#8E9C9C")), "visa", tier="Platinum", digits="#0F1A1A", back_bg=("#A6B3B2", "#8E9C9C", "#6F7C7C")),
    Bank("falabella", "cmr-visa-signature", "Banco Falabella CMR Visa Signature", "falabella-cmr-visa-signature", S((0, "#141618"), (1, "#030303")), "visa", tier="Signature", digits=WHITE, back_bg=("#1B1D20", "#0E0F10", "#030303")),
    Bank("falabella", "cmr-guaranteed", "Banco Falabella CMR con Garantía", "falabella-cmr-guaranteed", S((0, "#00753A"), (1, "#00381A")), "visa", tier="Garantía", digits=WHITE, back_bg=("#046C36", "#00532B", "#00381A")),
    Bank("falabella", "debit-classic", "Banco Falabella Débito", "falabella-debit-classic", S((0, "#12934A"), (1, "#00843D")), "visa", tier="Débito", digits=WHITE, back_bg=("#0F8F47", "#00843D", "#005C2B")),
    Bank("ripley", "mastercard-classic", "Banco Ripley Mastercard", "ripley-mastercard-classic", S((0, "#7A1E93"), (1, "#3A0847")), "mc", digits=WHITE, back_bg=("#6B1880", "#4A0D5C", "#2E0539")),
    Bank("ripley", "debit-mastercard", "Banco Ripley Débito", "ripley-debit-mastercard", S((0, "#8A2A9E"), (1, "#3A0847")), "mc", tier="Débito", digits=WHITE, back_bg=("#702082", "#4A0D5C", "#2E0539")),
    Bank("banbif", "debit-classic", "BanBif Débito", "banbif-debit-classic", S((0, "#00669E"), (1, "#002E54")), "visa", tier="Débito", accent="#009FE3", digits=WHITE, back_bg=("#005A8C", "#003F6E", "#002342")),
    Bank("banbif", "visa-cero", "BanBif Cero Membresía Visa", "banbif-visa-cero", S((0, "#F1FAFE"), (1, "#C9E8F7")), "visa", tier="Cero Membresía", text="#00386B", accent="#008CD2", digits="#00386B", light=True, back_bg=("#EAF6FC", "#D5ECF8", "#B8DDF0")),
    Bank("banbif", "visa-classic", "BanBif Visa Clásica", "banbif-visa-classic", S((0, "#00669E"), (1, "#002E54")), "visa", accent="#009FE3", digits=WHITE, back_bg=("#005A8C", "#003F6E", "#002342")),
    Bank("banbif", "visa-gold", "BanBif Visa Oro", "banbif-visa-gold", S((0, "#E0B54E"), (1, "#B58832")), "visa", tier="Oro", text="#001E3D", accent="#001E3D", digits="#001E3D", back_bg=("#D2A745", "#B58832", "#8F6A22")),
    Bank("banbif", "visa-platinum", "BanBif Visa Platinum", "banbif-visa-platinum", S((0, "#5C6B7A"), (1, "#2F3A44")), "visa", tier="Platinum", accent="#009FE3", digits=WHITE, back_bg=("#586675", "#3D4955", "#262F37")),
    Bank("banbif", "visa-signature", "BanBif Visa Signature", "banbif-visa-signature", S((0, "#0E2F57"), (1, "#040F1D")), "visa", tier="Signature", accent="#D4AF37", digits=WHITE, back_bg=("#13335E", "#0A2240", "#040F1D")),
    Bank("banbif", "visa-infinite", "BanBif Visa Infinite", "banbif-visa-infinite", S((0, "#1A2128"), (1, "#050709")), "visa", tier="Infinite", accent="#009FE3", digits=WHITE, back_bg=("#1E252C", "#101418", "#050709")),
]


# --------------------------------------------------------------------------- marcas


def falabella_logo(color=WHITE) -> list:
    return [Node("CMR-texto", text_path("CMR", 75, 70, 230, 76, tracking=-10), fill=color),
            Node("BancoFalabella-texto", text_path("Banco Falabella", 75, 160, 232, 26, bold=False, tracking=-6), fill=color)]


def ripley_logo(color=WHITE) -> list:
    return [Node("ripley-texto", text_path("ripley", 75, 62, 300, 72, tracking=-8), fill=color),
            Node("BancoRipley-texto", text_path("Banco Ripley", 78, 158, 300, 24, bold=False, tracking=12), fill=color, fill_alpha=0.9),
            Node("Ripley-barra-roja", rrect(78, 196, 92, 12, 6), fill="#E5383B"),
            Node("Ripley-barra-amarilla", rrect(176, 196, 40, 12, 6), fill="#F2B632"),
            Node("Ripley-barra-morada", rrect(222, 196, 92, 12, 6), fill="#B089C4")]


def banbif_logo(color=WHITE, accent="#009FE3") -> list:
    return [Node("BanBif-texto", text_path("BanBif", 75, 62, 280, 76, tracking=-10), fill=color),
            Node("BanBif-punto", circle(309, 56, 12), stroke="#C6D82B", stroke_width=6)]


def brand_nodes(v: Bank, small=False) -> list:
    if v.bank == "falabella":
        return falabella_logo(v.text)
    if v.bank == "ripley":
        return ripley_logo(v.text)
    return banbif_logo(v.text, v.accent)


def front(v: Bank) -> Scene:
    grads = [Gradient("fondo", 0, 0, W, H, [(o, c, 1.0) for o, c in v.bg])]
    nodes = [Node("Fondo", rrect(0, 0, W, H, CORNER), fill="url(#fondo)")]
    if v.bank == "banbif":
        for i, r in enumerate((520, 640)):
            nodes.append(Node(f"Arco-{i}", arc(W + 40, H + 60, r, 180, 270), stroke=v.accent, stroke_width=6 if i == 0 else 3, stroke_alpha=0.55 if i == 0 else 0.3))
    if v.bank == "ripley":
        nodes.append(Node("Resplandor", circle(1120, 120, 360), fill=WHITE, fill_alpha=0.05))
    nodes += brand_nodes(v)
    nodes += contactless(color=v.text, cx=1090, cy=94) if v.bank == "falabella" else []
    if v.network == "visa":
        nodes += visa_logo(color=v.text if v.light else WHITE)
        if v.tier:
            nodes += tier_label(v.tier, color=v.text if v.light else WHITE)
    else:
        nodes += mastercard()
        if v.tier:
            nodes += [Node("Nivel-texto", text_path(v.tier, 940, 690, 110, 26, bold=False, align="right"), fill=WHITE)]
    return Scene(f"{v.name} · anverso", f"Anverso vectorial de {v.name}. Aproximación de diseño: no es un recurso oficial del banco.", grads, nodes)


def back(v: Bank) -> Scene:
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
    word = {"falabella": "CMR", "ripley": "ripley", "banbif": "BanBif"}[v.bank]
    nodes.append(Node("Marca-reverso", text_path(word, 72, 620, 150 if word != "CMR" else 110, 46, tracking=-6), fill=v.text))
    if v.network == "visa":
        nodes += visa_logo(x=938, y=630, h=52, color=v.text if v.light else WHITE)
    else:
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

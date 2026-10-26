#!/usr/bin/env python3
"""Nine visually verified BBVA presets. See docs/design/cards/README.md for sources/exclusions.

Run: python scripts/cards/gen_bbva.py
Portrait reference artwork is recomposed into Kipu's landscape 1213 x 766 viewport.
No official artwork is embedded, automatically traced, or needed to regenerate the assets.
"""
from __future__ import annotations

import math
import os
from dataclasses import dataclass

from card_art_common import bbva_logo, illustrated_back, mastercard, text_path
from gen_bcp_visa_qore import (CORNER, DESIGN_DIR, H, RES_DIR, W, Gradient, Node, Path,
                              Scene, arc, circle, contactless, poly, rrect, visa_logo,
                              write_svg, write_vector_drawable)


@dataclass(frozen=True)
class Bbva:
    key: str
    pascal: str
    name: str
    network: str
    finish: str
    top: str
    bottom: str
    ink: str
    mark: str
    label: str = ""
    tier: str = ""

    @property
    def folder(self): return "bbva-" + self.key
    @property
    def res(self): return "bbva_" + self.key.replace("-", "_")


# Palettes sampled from official images in scratch/cardart-bbva-scotiabank/references/.
# Existing style data stays intact; this is only the vector artwork resolved by preset ID.
BBVA = [
    Bbva("debit-classic", "BbvaDebitClassic", "Débito BBVA", "VISA", "turquoise", "#32C7CF", "#229FA7", "#FFFFFF", "#A1E9E9", tier="Débito"),
    Bbva("visa-bfree", "BbvaVisaBfree", "BBVA Bfree Visa", "VISA", "pearl", "#F1F2F1", "#E6E8E6", "#104984", "#85C9DE", "Bfree"),
    Bbva("mastercard-bfree", "BbvaMastercardBfree", "BBVA Bfree Mastercard", "MASTERCARD", "pearl", "#F1F2F1", "#E6E8E6", "#104984", "#85C9DE", "Bfree"),
    Bbva("visa-cero", "BbvaVisaCero", "BBVA Cero Visa", "VISA", "ice", "#C6E8F6", "#8ACCE4", "#FFFFFF", "#4EBADA", "CERO"),
    Bbva("visa-platinum", "BbvaVisaPlatinum", "BBVA Platinum Visa", "VISA", "satin", "#B5B6BA", "#5B5D66", "#FFFFFF", "#AEB0B6", tier="Platinum"),
    Bbva("mastercard-platinum", "BbvaMastercardPlatinum", "BBVA Platinum Mastercard", "MASTERCARD", "satin", "#B5B6BA", "#5B5D66", "#FFFFFF", "#AEB0B6", "platinum"),
    Bbva("visa-signature", "BbvaVisaSignature", "BBVA Visa Signature", "VISA", "graphite", "#595A5C", "#141612", "#FFFFFF", "#686965", tier="Signature"),
    Bbva("visa-infinite", "BbvaVisaInfinite", "BBVA Visa Infinite", "VISA", "ornament", "#373B42", "#030405", "#FFFFFF", "#8A8D93", tier="Infinite"),
    Bbva("mastercard-black", "BbvaMastercardBlack", "BBVA Mastercard Black", "MASTERCARD", "graphite", "#515254", "#10120F", "#FFFFFF", "#62645F", "black"),
]


def elevation_mark(color: str, finish: str) -> list[Node]:
    """BBVA's open ascending double chevron, drawn manually with rounded cubic vertices."""
    # One connected line: lower open A, outer peak, small upper return and horizon.
    # This avoids drawing two complete overlapping As, which changes the bank's silhouette.
    a = Path().M(0, 385).L(518, 385).L(413, 550).L(454, 550).L(576, 350)
    a.L(699, 550).L(740, 550).L(587, 193).C(581, 179, 570, 179, 563, 193)
    a.L(444, 385).L(485, 385).L(576, 240).L(637, 385).L(W, 385)
    shine = "#FFFFFF" if finish in {"ice", "pearl", "turquoise"} else "#B7B9BA"
    return [Node("Chevron-y-horizonte", a, stroke=color, stroke_width=5.5),
            Node("Reflejo-chevron", a.transform(e=-2, f=-3), stroke=shine, stroke_width=1.7, stroke_alpha=.65)]


def pearl_grain() -> list[Node]:
    """A restrained grouped stipple, not raster noise or a traced texture."""
    layers = [Path(), Path(), Path()]
    for row in range(17):
        for col in range(29):
            x = 18 + col * 42 + (row % 2) * 18
            y = 19 + row * 45
            layers[(row + col) % 3].extend(circle(x, y, 1.2 + ((row * 5 + col * 7) % 4) * .35))
    # AAPT UTF-8 string-pool entries must stay below 32KiB; split into grouped draws.
    return [Node(f"Grano-perlado-{i}", p, fill="#9BA6AD", fill_alpha=.12 + i * .04) for i, p in enumerate(layers)]


def satin_lines(dark=False) -> list[Node]:
    lines = Path()
    for x in range(-800, 1500, 72):
        lines.M(x, H).L(x + H, 0)
    return [Node("Satin-diagonal", lines, stroke="#DDE1E5", stroke_width=16 if dark else 21, stroke_alpha=.025 if dark else .035)]


def ice_lines() -> list[Node]:
    p = Path()
    # Original flowing panels reflect the blue Cero geometry, without reproducing a bank path.
    for x in range(-160, 1450, 150):
        p.M(x, 0).L(x, 164).C(x, 211, x + 108, 222, x + 108, 278)
        p.L(x + 108, 510).C(x + 108, 552, x + 28, 553, x + 28, 610).L(x + 28, H)
    return [Node("Cero-paneles-lineales", p, stroke="#D9F3FD", stroke_width=2.3, stroke_alpha=.37)]


def ornament() -> list[Node]:
    """Own symmetric floral / paisley interpretation of Infinite's ornamental field.

    24 repeated rosettes are grouped by role into three nodes, rather than hundreds of nodes.
    """
    leaves, stems, seeds = Path(), Path(), Path()
    for row in range(4):
        for col in range(6):
            cx, cy = col * 218 + (100 if row % 2 else 0), row * 231 + 32
            for k in range(8):
                angle = k * math.pi / 4
                leaf = Path().M(0, 10).C(-25, 30, -27, 59, 0, 89).C(27, 59, 25, 30, 0, 10).Z()
                a, b = math.cos(angle), math.sin(angle)
                leaves.extend(leaf.transform(a, b, -b, a, cx, cy))
                curl = Path().M(13, 32).C(55, 10, 74, 62, 48, 71).C(30, 78, 21, 56, 36, 48)
                stems.extend(curl.transform(a, b, -b, a, cx, cy))
            seeds.extend(circle(cx, cy, 7))
    return [Node("Infinite-petalos", leaves, stroke="#080A0C", stroke_width=5.5, stroke_alpha=.7),
            Node("Infinite-volutas", stems, stroke="#080A0C", stroke_width=4.5, stroke_alpha=.8),
            Node("Infinite-semillas", seeds, fill="#080A0C", fill_alpha=.85)]


def front(v: Bbva) -> Scene:
    gradients = [Gradient("fondo", 0, 0, 0 if v.finish == "satin" else W * .58, H, [(0, v.top, 1), (1, v.bottom, 1)])]
    nodes = [Node("Fondo", rrect(0, 0, W, H, CORNER), fill="url(#fondo)")]
    if v.finish == "pearl": nodes += pearl_grain()
    elif v.finish == "ice": nodes += ice_lines()
    elif v.finish == "ornament": nodes += ornament()
    elif v.finish in {"satin", "graphite"}: nodes += satin_lines(v.finish == "graphite")
    nodes += elevation_mark(v.mark, v.finish)
    nodes += bbva_logo(color=v.ink)
    if v.label:
        width = {"Bfree": 141, "CERO": 144, "platinum": 175, "black": 109}[v.label]
        nodes.append(Node("Producto", text_path(v.label, W - 66 - width, 75, width, 34, bold=False), fill=v.mark if v.finish == "pearl" else v.ink))
    nodes += contactless(cx=84, cy=527, color=v.ink, scale=.62, ox=100, oy=527)
    if v.network == "VISA":
        nodes += visa_logo(930, 605, 66, v.ink)
        if v.tier:
            nodes.append(Node("Nivel", text_path(v.tier, 1134, 684, 126 if v.tier != "Débito" else 96, 23, bold=False, align="right"), fill=v.ink))
    else:
        nodes += mastercard(970, 602, 49, silver=v.key == "mastercard-black")
    return Scene(v.name + " · anverso", "Interpretación vectorial propia del arte público BBVA; composición adaptada de vertical a horizontal. Marcas aproximadas. Sin chip ni datos personales.", gradients, nodes)


def back(v: Bbva) -> Scene:
    ink = "#104984" if v.finish in {"ice", "pearl"} else "#FFFFFF"
    nodes = [Node("Chevron-reverso", poly([(570, 475), (710, 255), (1044, 766), (999, 766), (710, 320), (609, 475)]), stroke=ink, stroke_width=5, stroke_alpha=.09)]
    return illustrated_back(v.name, v.top, v.bottom, "BBVA", v.network, silver=v.key == "mastercard-black", ink=ink, motif=nodes)


def main():
    for v in BBVA:
        for face, scene in [("Front", front(v)), ("Back", back(v))]:
            write_svg(scene, os.path.join(DESIGN_DIR, v.folder, f"{v.pascal}{face}.svg"))
            write_vector_drawable(scene, os.path.join(RES_DIR, f"{v.res}_{face.lower()}.xml"))
        print("OK", v.folder)


if __name__ == "__main__": main()

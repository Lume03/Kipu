#!/usr/bin/env python3
"""Four verified Scotiabank presets, using the Qore geometry/export engine.

Run: python scripts/cards/gen_scotiabank.py
Sources, portrait-to-landscape adaptation and approximation limits: docs/design/cards/README.md.
"""
from __future__ import annotations

import os
from dataclasses import dataclass

from card_art_common import illustrated_back, mastercard, scotia_wordmark, text_path
from gen_bcp_visa_qore import (CORNER, DESIGN_DIR, H, RES_DIR, W, Gradient, Node, Path,
                              Scene, arc, circle, contactless, poly, rrect, visa_logo,
                              write_svg, write_vector_drawable)


@dataclass(frozen=True)
class Scotia:
    key: str
    pascal: str
    name: str
    network: str
    top: str
    bottom: str
    ink: str
    tier: str = ""

    @property
    def folder(self): return "scotiabank-" + self.key
    @property
    def res(self): return "scotiabank_" + self.key.replace("-", "_")


SCOTIA = [
    Scotia("debit-classic", "ScotiabankDebitClassic", "Débito Scotiabank", "VISA", "#EA120E", "#B2080E", "#FFFFFF", "Débito"),
    Scotia("singular-signature", "ScotiabankSingularSignature", "Scotiabank Visa Singular Signature", "VISA", "#13110C", "#100F0A", "#BEBDBC", "Signature"),
    Scotia("singular-infinite", "ScotiabankSingularInfinite", "Scotiabank Visa Singular Infinite", "VISA", "#C1BEBA", "#8A8783", "#FFFFFF", "Infinite"),
    Scotia("singular-black", "ScotiabankSingularBlack", "Scotiabank Mastercard Singular Black", "MASTERCARD", "#13110C", "#100F0A", "#BEBDBC"),
]


def singular_script(x=260.0, y=248.0, width=694.0, color="#FFFFFF") -> list[Node]:
    """Own hand-lettered monoline approximation; no extracted trademark/font outlines.

    Curves are composed as handwriting in a 530 x 175 coordinate space; uniform scaling
    preserves the drawing's proportions when placing it on the landscape plastic.
    """
    p = Path().M(93, 12).C(112, -15, 68, -6, 32, 24).C(-18, 66, 74, 58, 70, 81)
    p.C(65, 109, -11, 136, 4, 105).C(11, 92, 28, 85, 46, 78)
    p.M(105, 58).C(91, 92, 92, 106, 108, 89).L(129, 62)
    p.M(109, 41).L(110, 39)
    p.M(126, 67).C(107, 115, 127, 107, 141, 76).C(160, 45, 155, 72, 148, 92).C(145, 106, 163, 94, 177, 71)
    p.M(207, 65).C(185, 40, 155, 108, 180, 100).C(198, 94, 214, 60, 212, 64)
    p.C(194, 117, 183, 145, 155, 166).C(131, 184, 116, 165, 140, 145).C(168, 123, 218, 113, 244, 82)
    p.M(241, 62).C(224, 103, 233, 112, 252, 82).L(264, 63).C(252, 97, 260, 109, 280, 80)
    p.M(278, 81).C(302, 51, 333, -27, 313, 6).C(287, 48, 270, 119, 294, 96).L(319, 71)
    p.M(351, 68).C(329, 46, 304, 107, 326, 99).C(339, 93, 355, 65, 353, 67)
    p.C(341, 100, 348, 106, 370, 79)
    p.M(368, 80).L(382, 59).C(373, 86, 381, 68, 393, 65).C(408, 59, 394, 87, 413, 80)
    s = width / 418
    handwriting = p.transform(s, 0, 0, s, x, y)
    return [Node("Singular-caligrafia-aproximada", handwriting, stroke=color, stroke_width=4.5 * s),
            Node("by-Scotiabank-aproximado", text_path("by Scotiabank.", x + width * .55, y + 207, width * .44, 29, bold=False), fill=color)]


def front(v: Scotia) -> Scene:
    gradients = [Gradient("fondo", 0, 0, W, H, [(0, v.top, 1), (1, v.bottom, 1)])]
    nodes = [Node("Fondo", rrect(0, 0, W, H, CORNER), fill="url(#fondo)")]
    if v.key == "debit-classic":
        # Official Visa debit: broad diagonal X / diamond facets with a red halftone field.
        nodes += [Node("Debito-faceta-izquierda", poly([(0, 145), (569, 565), (310, H), (0, H)]), fill="#84010A", fill_alpha=.24),
                  Node("Debito-faceta-superior", poly([(230, 0), (657, 338), (W, 0)]), fill="#FF4435", fill_alpha=.20),
                  Node("Debito-faceta-inferior", poly([(0, H), (573, 390), (927, H)]), fill="#7B0007", fill_alpha=.19),
                  Node("Debito-faceta-derecha", poly([(W, 110), (661, 487), (W, H)]), fill="#7C0008", fill_alpha=.14)]
        dots = [Path(), Path()]
        for row in range(15):
            y = 194 + row * 28
            for col in range(11):
                x = 23 + col * 28
                if x < 360 - abs(y - 390) * .9:
                    dots[0].extend(circle(x, y, 2.8))
                    dots[1].extend(circle(W - x, y - 57, 2.8))
        nodes += [Node(f"Debito-semitono-{i}", p, fill="#81000B", fill_alpha=.25) for i, p in enumerate(dots)]
        nodes += scotia_wordmark(width=366)
        nodes.append(Node("Debito-producto", text_path("COMPRAS", 986, 84, 162, 28, bold=False), fill=v.ink))
        nodes += contactless(cx=100, cy=470, color=v.ink)
    else:
        if v.tier == "Infinite":
            # Silver Infinite is explicitly visible in the official Peru reference, not gold.
            sheen = Path()
            for x in range(-500, 1300, 150): sheen.M(x, H).L(x + H, 0)
            nodes.append(Node("Infinite-satin", sheen, stroke="#FFFFFF", stroke_width=42, stroke_alpha=.025))
        nodes += singular_script(color=v.ink)
    if v.network == "VISA":
        nodes += visa_logo(944, 608, 61, v.ink)
        nodes.append(Node("Nivel", text_path(v.tier, 1134, 682, 127, 25, bold=False, align="right"), fill=v.ink))
    else:
        nodes += mastercard(970, 603, 49, silver=True)
        nodes.append(Node("Mastercard-nombre", text_path("mastercard", 970, 715, 159, 19, bold=False), fill=v.ink))
    return Scene(v.name + " · anverso", "Interpretación vectorial propia de referencia pública Scotiabank Perú; Singular adaptada de vertical a horizontal. Marcas aproximadas. Sin chip ni datos personales.", gradients, nodes)


def back(v: Scotia) -> Scene:
    ink = "#252321" if v.tier == "Infinite" else "#FFFFFF" if v.key == "debit-classic" else "#BEBDBC"
    return illustrated_back(v.name, v.top, v.bottom, "SCOTIABANK", v.network, silver=True, ink=ink)


def main():
    for v in SCOTIA:
        for face, scene in [("Front", front(v)), ("Back", back(v))]:
            write_svg(scene, os.path.join(DESIGN_DIR, v.folder, f"{v.pascal}{face}.svg"))
            write_vector_drawable(scene, os.path.join(RES_DIR, f"{v.res}_{face.lower()}.xml"))
        print("OK", v.folder)


if __name__ == "__main__": main()

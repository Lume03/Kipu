"""Shared drawn marks / illustrative reverse. Geometry and exporters stay in the Qore engine.

Brand marks below are original approximations, not official/licensed brand resources.
"""
from __future__ import annotations

import math

from gen_bcp_latam import text_path
from gen_bcp_visa_qore import CORNER, H, W, Gradient, Node, Path, Scene, arc, circle, poly, rrect, visa_logo


def bbva_logo(x=66.0, y=65.0, width=259.0, color="#FFFFFF") -> list[Node]:
    """Own geometric interpretation at a fixed 3.8:1 aspect ratio."""
    b = Path().M(0, 0).L(29, 0).C(54, 0, 56, 30, 39, 34).C(62, 38, 57, 68, 30, 68).L(0, 68).Z()
    b.M(12, 11).L(12, 28).L(28, 28).C(45, 28, 44, 11, 28, 11).Z()
    b.M(12, 39).L(12, 57).L(30, 57).C(48, 57, 49, 39, 30, 39).Z()
    letters = Path().extend(b).extend(b.transform(e=65))
    letters.extend(poly([(126, 0), (140, 0), (164, 46), (188, 0), (202, 0), (164, 71)]))
    letters.extend(poly([(190, 68), (228, -3), (266, 68), (252, 68), (228, 22), (204, 68)]))
    return [Node("BBVA-aproximacion", letters.transform(width / 266, 0, 0, width / 266, x, y), fill=color)]


def mastercard(x=970.0, y=600.0, radius=49.0, silver=False) -> list[Node]:
    """Two drawn circles with a geometrically bounded overlap; no clip-path."""
    shift = radius * 0.59
    left, right = x + radius, x + radius + 2 * shift
    cy = y + radius
    angle = math.degrees(math.acos(shift / radius))
    lens = arc(left, cy, radius, -angle, angle)
    other = arc(right, cy, radius, 180 - angle, 180 + angle)
    lens.L(*other.cmds[0][1:])
    lens.cmds.extend(other.cmds[1:])
    lens.Z()
    nodes = [
        Node("Mastercard-circulo-izquierdo", circle(left, cy, radius), fill="#777A7C" if silver else "#EB001B"),
        Node("Mastercard-circulo-derecho", circle(right, cy, radius), fill="#DADDDC" if silver else "#F79E1B"),
        Node("Mastercard-interseccion", lens, fill="#A5A7A6" if silver else "#FF5F00"),
    ]
    if silver:
        edges = Path().extend(circle(left, cy, radius)).extend(circle(right, cy, radius))
        nodes.append(Node("Mastercard-contorno", edges, stroke="#DFD4AA", stroke_width=1.3))
    return nodes


def scotia_wordmark(x=70.0, y=63.0, width=330.0, color="#FFFFFF") -> list[Node]:
    """Free-font lettering and a drawn S/globe, expressly an approximation."""
    emblem = Path().M(35, 0).C(6, 0, -7, 21, 14, 35).C(26, 42, 44, 40, 44, 50).C(44, 64, 15, 66, 3, 52)
    globe = circle(26, 32, 26)
    nodes = [Node("Scotia-emblema-S", emblem, stroke=color, stroke_width=9),
             Node("Scotia-globo", globe, stroke=color, stroke_width=2.8),
             Node("Scotiabank-aproximacion", text_path("Scotiabank", 65, 15, 285, 40, tracking=-12), fill=color)]
    s = width / 350
    return [Node(n.name, n.path.transform(s, 0, 0, s, x, y), fill=n.fill, stroke=n.stroke, stroke_width=n.stroke_width * s) for n in nodes]


def illustrated_back(name: str, top: str, bottom: str, bank: str, network: str,
                      silver=False, ink="#FFFFFF", motif: list[Node] | None = None) -> Scene:
    """No claimed bank reverse, card data, phone numbers or fabricated legal copy."""
    gradients = [
        Gradient("fondo", 0, 0, W, H, [(0, top, 1), (1, bottom, 1)]),
        Gradient("banda", 0, 82, 0, 198, [(0, "#1B1C1F", 1), (.55, "#090B0D", 1), (1, "#191B1E", 1)]),
        Gradient("firma", 0, 224, 0, 368, [(0, "#F5F6F5", 1), (1, "#DFE4E6", 1)]),
    ]
    nodes = [Node("Fondo", rrect(0, 0, W, H, CORNER), fill="url(#fondo)")]
    nodes += motif or []
    nodes += [Node("Banda-magnetica", poly([(0, 82), (W, 82), (W, 198), (0, 198)]), fill="url(#banda)"),
              Node("Panel-firma", rrect(72, 224, 760, 144, 6), fill="url(#firma)")]
    hatch = Path()
    for x in range(82, 787, 23):
        hatch.M(x, 367).L(x + 36, 225)
    nodes.append(Node("Panel-trama", hatch, stroke="#8397A6", stroke_width=1.4, stroke_alpha=.36, cap="butt"))
    nodes += bbva_logo(72, 633, 178, ink) if bank == "BBVA" else scotia_wordmark(72, 628, 245, ink)
    if network == "VISA":
        nodes += visa_logo(962, 635, 48, ink)
    elif network == "MASTERCARD":
        nodes += mastercard(1014, 611, 34, silver)
    return Scene(name + " · reverso", "Composición propia ilustrativa; reverso no verificado. Marcas aproximadas con trazados propios. Sin datos personales.", gradients, nodes)

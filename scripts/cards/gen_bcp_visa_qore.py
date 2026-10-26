#!/usr/bin/env python3
"""Genera los gráficos vectoriales de las BCP Visa Qore (anverso y reverso): Classic, Gold, Platinum, Signature e Infinite.

Una sola definición geométrica produce:
  - docs/design/cards/bcp-visa-classic-qore/BcpVisaClassicQoreFront.svg
  - docs/design/cards/bcp-visa-classic-qore/BcpVisaClassicQoreBack.svg
  - app/src/main/res/drawable-nodpi/bcp_visa_classic_qore_front.xml (VectorDrawable)
  - app/src/main/res/drawable-nodpi/bcp_visa_classic_qore_back.xml  (VectorDrawable)

Todo son trazados vectoriales (Bézier cúbicos): sin imágenes rasterizadas ni texto
dependiente de fuentes. La geometría se midió a partir del arte oficial publicado por BCP
en https://www.viabcp.com/tarjetas/visa-clasica-qore (solo como referencia de proporciones y
colores); no se incrusta ni se calca ningún archivo del banco.

Ejecutar:  python scripts/cards/gen_bcp_visa_classic_qore.py
"""
from __future__ import annotations

import math
import os
from dataclasses import dataclass, field

W, H = 1213.0, 766.0          # viewBox (relación 1.5836, ~ tarjeta ID-1 de 1.586)
CORNER = 46.0                 # radio de las esquinas del plástico

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
DESIGN_DIR = os.path.join(ROOT, "docs", "design", "cards")
RES_DIR = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")

# --------------------------------------------------------------------------- geometría


@dataclass
class Path:
    cmds: list = field(default_factory=list)

    def M(self, x, y): self.cmds.append(("M", x, y)); return self
    def L(self, x, y): self.cmds.append(("L", x, y)); return self
    def C(self, x1, y1, x2, y2, x, y): self.cmds.append(("C", x1, y1, x2, y2, x, y)); return self
    def Z(self): self.cmds.append(("Z",)); return self

    def extend(self, other: "Path"): self.cmds.extend(other.cmds); return self

    def transform(self, a=1.0, b=0.0, c=0.0, d=1.0, e=0.0, f=0.0) -> "Path":
        """x' = a·x + c·y + e ;  y' = b·x + d·y + f"""
        out = Path()
        for cmd in self.cmds:
            if cmd[0] == "Z":
                out.cmds.append(cmd)
                continue
            vals = list(cmd[1:])
            pts = []
            for i in range(0, len(vals), 2):
                x, y = vals[i], vals[i + 1]
                pts += [a * x + c * y + e, b * x + d * y + f]
            out.cmds.append((cmd[0], *pts))
        return out

    def d(self) -> str:
        def n(v):
            s = f"{v:.1f}"
            return s[:-2] if s.endswith(".0") else s
        parts = []
        for cmd in self.cmds:
            parts.append(cmd[0] + " ".join(n(v) for v in cmd[1:]))
        return " ".join(parts)


def rrect(x, y, w, h, r) -> Path:
    k = 0.5522847498 * r
    p = Path().M(x + r, y).L(x + w - r, y).C(x + w - r + k, y, x + w, y + r - k, x + w, y + r)
    p.L(x + w, y + h - r).C(x + w, y + h - r + k, x + w - r + k, y + h, x + w - r, y + h)
    p.L(x + r, y + h).C(x + r - k, y + h, x, y + h - r + k, x, y + h - r)
    p.L(x, y + r).C(x, y + r - k, x + r - k, y, x + r, y)
    return p.Z()


def arc(cx, cy, r, a0, a1, move=True) -> Path:
    """Arco de circunferencia (grados, sentido horario en pantalla) como Béziers cúbicos."""
    p = Path()
    segs = max(1, int(math.ceil(abs(a1 - a0) / 90.0)))
    step = (a1 - a0) / segs
    for i in range(segs):
        s, e = math.radians(a0 + i * step), math.radians(a0 + (i + 1) * step)
        t = 4.0 / 3.0 * math.tan((e - s) / 4.0)
        x0, y0 = cx + r * math.cos(s), cy + r * math.sin(s)
        x3, y3 = cx + r * math.cos(e), cy + r * math.sin(e)
        c1 = (x0 - t * r * math.sin(s), y0 + t * r * math.cos(s))
        c2 = (x3 + t * r * math.sin(e), y3 - t * r * math.cos(e))
        if i == 0:
            (p.M if move else p.L)(x0, y0)
        p.C(c1[0], c1[1], c2[0], c2[1], x3, y3)
    return p


def circle(cx, cy, r) -> Path:
    return arc(cx, cy, r, 0, 360).Z()


def smooth(points, closed=False, tension=0.5) -> Path:
    """Spline Catmull-Rom -> Béziers. Cada punto es (x, y) o (x, y, 'c') si es esquina."""
    pts = [(p[0], p[1], len(p) > 2 and p[2] == "c") for p in points]
    n = len(pts)
    path = Path().M(pts[0][0], pts[0][1])

    def tangent(i):
        x, y, corner = pts[i]
        if corner:
            return None
        pr = pts[i - 1] if i > 0 else (pts[-1] if closed else pts[i])
        nx = pts[i + 1] if i < n - 1 else (pts[0] if closed else pts[i])
        return ((nx[0] - pr[0]) * tension / 1.5, (nx[1] - pr[1]) * tension / 1.5)

    last = n if closed else n - 1
    for i in range(last):
        a, b = pts[i], pts[(i + 1) % n]
        ta, tb = tangent(i), tangent((i + 1) % n)
        if ta is None:
            ta = ((b[0] - a[0]) / 3.0, (b[1] - a[1]) / 3.0)
        if tb is None:
            tb = ((b[0] - a[0]) / 3.0, (b[1] - a[1]) / 3.0)
        path.C(a[0] + ta[0], a[1] + ta[1], b[0] - tb[0], b[1] - tb[1], b[0], b[1])
    return path.Z() if closed else path


def poly(points, close=True) -> Path:
    p = Path().M(*points[0])
    for pt in points[1:]:
        p.L(*pt)
    return p.Z() if close else p


def skew_x(path: Path, angle_deg: float, pivot_y: float) -> Path:
    t = math.tan(math.radians(angle_deg))
    return path.transform(1, 0, t, 1, -t * pivot_y, 0)


# --------------------------------------------------------------------------- escena


@dataclass
class Node:
    name: str
    path: Path
    fill: str | None = None            # "#RRGGBB" o "url(#id)"
    fill_alpha: float = 1.0
    stroke: str | None = None
    stroke_width: float = 0.0
    cap: str = "round"
    join: str = "round"
    stroke_alpha: float = 1.0
    even_odd: bool = False


@dataclass
class Gradient:
    id: str
    x1: float
    y1: float
    x2: float
    y2: float
    stops: list  # (offset, hex, alpha)


@dataclass
class Scene:
    title: str
    desc: str
    gradients: list
    nodes: list


# Colores medidos del arte oficial
BG_STOPS = [(0.0, "#0087C6"), (0.13, "#026FB0"), (0.26, "#0A64A8"), (0.39, "#145EA3"),
            (0.52, "#14539A"), (0.65, "#0D4891"), (0.80, "#113E89"), (1.0, "#123B87")]
Q_BLUE = "#1F80C3"
BCP_ORANGE = "#EF7814"
WHITE = "#FFFFFF"


def _append_smooth(path: Path, pts, tension=0.52):
    """Continúa `path` con una spline que parte del último punto (pts[0] debe coincidir)."""
    sp = smooth(pts, closed=False, tension=tension)
    for cmd in sp.cmds[1:]:
        path.cmds.append(cmd)
    return path


def q_motif(fill=None, alpha=1.0) -> list:
    """Motivo gráfico de Qore: aro abierto de la «Q» + destello de cuatro puntas.

    Medidas tomadas del arte oficial (solo como referencia): aro de ~66 u de grosor, destello con
    brazos de punta plana que se ensanchan hacia el centro y brazo derecho largo y delgado.
    """
    body = smooth([
        (-60, -60, "c"), (64, -60, "c"),
        (64, -20), (64, 80), (66, 120), (68, 160), (74, 200), (84, 240), (98, 280), (117, 320),
        (145, 372), (190, 418), (240, 442), (270, 452), (320, 468), (370, 477), (420, 480), (470, 478),
        (520, 472, "c"),
    ], closed=False, tension=0.52)
    # destello (curvas cóncavas entre brazos, puntas planas con esquinas redondeadas)
    body.C(548, 466, 566, 446, 572, 418)
    body.C(576, 392, 579, 358, 585, 332)
    body.C(585.8, 326, 587, 323, 592, 322.5)
    body.L(620, 322.5)
    body.C(625, 323, 626, 326, 626.5, 331)
    body.C(629, 358, 632, 392, 639, 420)
    body.C(647, 452, 672, 470, 706, 474)
    body.C(780, 479, 880, 484, 946, 490)
    body.C(954, 491, 957, 493, 957, 499)
    body.L(957, 511)
    body.C(957, 517, 954, 519, 946, 520)
    body.C(880, 525, 780, 531, 706, 534)
    body.C(672, 538, 650, 552, 641, 580)
    body.C(633, 610, 630, 648, 626, 680)
    body.C(625.5, 685, 623, 688, 619, 688)
    body.L(592, 688)
    body.C(587, 688, 585, 685, 584.5, 680)
    body.C(581, 648, 577, 610, 572.5, 586)
    body.C(566, 556, 548, 541, 520, 538)
    _append_smooth(body, [
        (520, 538, "c"), (470, 534), (420, 532), (370, 529), (320, 520), (270, 506), (220, 485),
        (160, 452), (102, 400), (68, 360), (40, 320), (19, 280), (2, 240), (-20, 200), (-60, 160),
        (-60, -60, "c"),
    ])
    body.Z()
    right_band = smooth([
        (868, -60, "c"), (875, 0), (883, 80), (877, 160), (857, 240), (840, 280), (819, 320),
        (792, 360), (770, 386), (752, 404, "c"), (708, 372, "c"), (716, 360), (742, 320),
        (762, 280), (776, 240), (785, 200), (791, 160), (794, 120), (794, 80), (790, 30),
        (785, -60, "c"),
    ], closed=True, tension=0.52)
    return [
        Node("Q-aro-y-destello", body, fill=fill or Q_BLUE, fill_alpha=alpha),
        Node("Q-cola-derecha", right_band, fill=fill or Q_BLUE, fill_alpha=alpha),
    ]


def chip() -> list:
    x, y, w, h = 112, 323, 153, 117
    body = rrect(x, y, w, h, 24)
    lines = Path()
    # contactos del chip EMV: pistas horizontales y verticales
    for yy in (368, 396):
        lines.M(x, yy).L(160, yy)
        lines.M(x + w, yy).L(218, yy)
    lines.M(188, y).L(188, 340)
    lines.M(188, y + h).L(188, 426)
    lines.M(219, y).L(219, 340)
    lines.M(219, y + h).L(219, 426)
    center = rrect(158, 338, 62, 92, 24)
    return [
        Node("Chip-EMV-cuerpo", body, fill="#C1C0C0", stroke="#484947", stroke_width=2.4, join="round"),
        Node("Chip-EMV-pistas", lines, stroke="#2F302F", stroke_width=2.4, cap="butt"),
        Node("Chip-EMV-contacto-central", center, stroke="#2F302F", stroke_width=2.4),
    ]


def ellipse_arc(cx, cy, rx, ry, a0, a1) -> Path:
    return arc(0, 0, 1, a0, a1).transform(rx, 0, 0, ry, cx, cy)


def bcp_logo(scale=1.0, ox=0.0, oy=0.0, color=WHITE, chevron=None) -> list:
    """Aproximación identificable del logotipo BCP (no es el recurso oficial)."""
    lw = 17.5
    B = Path()
    B.M(139, 66).L(139, 124)
    B.M(139, 66).L(158, 66).C(176, 66, 176, 95, 158, 95).L(139, 95)
    B.M(139, 95).L(162, 95).C(181, 95, 181, 124, 162, 124).L(139, 124)
    C = ellipse_arc(223, 95, 21, 29, -52, -308)
    P = Path()
    P.M(268, 66).L(268, 124)
    P.M(268, 66).L(288, 66).C(306, 66, 306, 98, 288, 98).L(268, 98)
    letters = Path()
    for g in (B, C, P):
        letters.extend(skew_x(g, -9, 95).transform(0.93, 0, 0, 1, 130 * 0.07 - 2, 0))
    # chevrón (naranja) con medialuna blanca; el derecho es el mismo trazo desplazado
    chev = Path().M(88, 72).C(101, 78, 112, 87, 115, 97).C(111, 108, 101, 118, 87, 126)
    cres = arc(94, 85, 12, 200, 330)
    chev_r = chev.transform(1, 0, 0, 1, 243, 0)
    cres_r = cres.transform(1, 0, 0, 1, 243, 0)

    def tf(p):
        return p.transform(scale, 0, 0, scale, ox - 75 * scale, oy - 55 * scale)

    return [
        Node("BCP-letras", tf(letters), stroke=color, stroke_width=lw * scale, cap="round", join="round"),
        Node("BCP-chevron-izq", tf(chev), stroke=chevron or BCP_ORANGE, stroke_width=23 * scale),
        Node("BCP-chevron-der", tf(chev_r), stroke=chevron or BCP_ORANGE, stroke_width=23 * scale),
        Node("BCP-medialuna-izq", tf(cres), stroke=color, stroke_width=7 * scale),
        Node("BCP-medialuna-der", tf(cres_r), stroke=color, stroke_width=7 * scale),
    ]


def qore_wordmark(scale=1.0, ox=75.0, oy=568.0, color=WHITE) -> list:
    sw = 6.8 * scale
    base_x, base_y = 75.0, 568.0

    def tf(p):
        return p.transform(scale, 0, 0, scale, ox - base_x * scale, oy - base_y * scale)

    q = arc(117, 611, 41, 0, 360).Z()
    # destello (cuatro puntas) como cola de la Q
    star = smooth([(136, 640), (138, 650), (136, 662), (134, 650), (122, 646), (134, 642), (136, 632),
                   (138, 642), (150, 646), (138, 650)], closed=False, tension=0.0)
    star = poly([(136, 636), (139, 645), (150, 648), (139, 651), (136, 662), (133, 651), (122, 648), (133, 645)])
    o = arc(205, 626, 31, 0, 360).Z()
    r = Path().M(256, 596).L(256, 656)
    r.M(256, 626).C(256, 607, 268, 598, 288, 600)
    e = Path().M(293, 626).L(353, 626)
    e.extend(arc(323, 626, 30, 0, -320))
    nodes = [
        Node("Qore-Q", tf(q), stroke=color, stroke_width=sw),
        Node("Qore-destello", tf(star), fill=color),
        Node("Qore-o", tf(o), stroke=color, stroke_width=sw),
        Node("Qore-r", tf(r), stroke=color, stroke_width=sw),
        Node("Qore-e", tf(e), stroke=color, stroke_width=sw),
    ]
    return nodes


def contactless(cx=1090, cy=94, color=WHITE, scale=1.0, ox=0.0, oy=0.0) -> list:
    p = Path()
    for r, half in ((20, 44), (34, 43), (48, 40), (62, 38)):
        p.extend(arc(cx, cy, r, -half, half))
    if scale != 1.0 or ox or oy:
        p = p.transform(scale, 0, 0, scale, ox - cx * scale, oy - cy * scale)
    return [Node("Pago-sin-contacto", p, stroke=color, stroke_width=6.8 * scale, cap="round")]


# --------------------------------------------------------------------------- texto como trazados


def _font_glyph_path(font_file: str, text: str, tracking: float = 0.0):
    from fontTools.ttLib import TTFont
    from fontTools.pens.basePen import BasePen
    font = TTFont(font_file)
    gs, cmap = font.getGlyphSet(), font.getBestCmap()

    class Pen(BasePen):
        def __init__(self, gset):
            super().__init__(gset)
            self.path, self.cur = Path(), (0, 0)

        def _moveTo(self, p): self.path.M(*p); self.cur = p
        def _lineTo(self, p): self.path.L(*p); self.cur = p
        def _curveToOne(self, a, b, c): self.path.C(*a, *b, *c); self.cur = c
        def _qCurveToOne(self, a, b):
            x0, y0 = self.cur
            c1 = (x0 + 2 / 3 * (a[0] - x0), y0 + 2 / 3 * (a[1] - y0))
            c2 = (b[0] + 2 / 3 * (a[0] - b[0]), b[1] + 2 / 3 * (a[1] - b[1]))
            self.path.C(*c1, *c2, *b); self.cur = b
        def _closePath(self): self.path.Z()
        def _endPath(self): pass

    total, cursor = Path(), 0.0
    for ch in text:
        name = cmap[ord(ch)]
        pen = Pen(gs)
        gs[name].draw(pen)
        total.extend(pen.path.transform(1, 0, 0, 1, cursor, 0))
        cursor += font["hmtx"][name][0] + tracking
    return total, cursor - tracking, font["head"].unitsPerEm


def _mpl_font(name: str) -> str:
    import matplotlib
    return os.path.join(os.path.dirname(matplotlib.__file__), "mpl-data", "fonts", "ttf", name)


def visa_logo(x=893.0, y=573.0, h=87.0, color=WHITE) -> list:
    """Aproximación del logotipo VISA: contornos de DejaVu Sans Bold Oblique (licencia libre),
    comprimidos para parecerse al logotipo y con la «bandera» característica de la V."""
    glyphs, adv, upm = _font_glyph_path(_mpl_font("DejaVuSans-BoldOblique.ttf"), "VISA", tracking=-70)
    cap = 0.729 * upm
    sy = h / cap
    width = 267.0 * (h / 87.0)
    sx = width / adv
    path = glyphs.transform(sx, 0, 0, -sy, x, y + h)
    s = h / 87.0
    flag = poly([(x - 14 * s, y + 4 * s), (x + 34 * s, y), (x + 34 * s, y + 20 * s), (x + 8 * s, y + 20 * s)])
    return [Node("VISA-letras", path, fill=color), Node("VISA-bandera-V", flag, fill=color)]


def tier_label(text: str, x=1160.0, y=690.0, width=None, color=WHITE) -> list:
    """Texto del nivel («Classic», «Gold», …) convertido a trazados, alineado a la derecha."""
    glyphs, adv, upm = _font_glyph_path(_mpl_font("DejaVuSans.ttf"), text)
    scale = (width or 14.6 * len(text)) / adv
    cap = 0.76 * upm * scale
    path = glyphs.transform(scale, 0, 0, -scale, x - adv * scale, y + cap)
    return [Node("Nivel-texto", path, fill=color)]


# --------------------------------------------------------------------------- variantes Qore


@dataclass
class Radial:
    id: str
    cx: float
    cy: float
    r: float
    stops: list


@dataclass
class Variant:
    key: str                      # classic, gold, platinum, signature, infinite
    tier: str                     # texto bajo VISA
    preset_id: str
    bg: list                      # [(offset, hex)]
    q_fill: str | None            # None = azul sólido del arte Clásica; hex + q_alpha = translúcido
    q_alpha: float
    bcp: str
    chevron: str
    qore: str
    visa: str
    contactless: str
    label: str
    infinite: bool = False
    back_bg: tuple = ("#0E4F96", "#0D3F85", "#0A2A66")
    back_q: str = WHITE
    back_q_alpha: float = 0.07

    @property
    def pascal(self): return f"BcpVisa{self.tier}Qore"
    @property
    def folder(self): return f"bcp-visa-{self.key}-qore"
    @property
    def res(self): return f"bcp_visa_{self.key}_qore"


VARIANTS = [
    Variant("classic", "Classic", "bcp-visa-qore-classic", BG_STOPS, None, 1.0, WHITE, BCP_ORANGE, WHITE, WHITE, WHITE, WHITE),
    Variant("gold", "Gold", "bcp-visa-qore-gold", [(0, "#C6912F"), (1, "#C6912F")], WHITE, 0.30, WHITE, WHITE, WHITE, WHITE, WHITE, WHITE,
            back_bg=("#B98429", "#A8741F", "#8E5F17"), back_q_alpha=0.10),
    Variant("platinum", "Platinum", "bcp-visa-qore-platinum", [(0, "#9D9D9C"), (0.5, "#8E8D8E"), (1, "#777676")], WHITE, 0.17, WHITE, BCP_ORANGE, WHITE, WHITE, WHITE, WHITE,
            back_bg=("#8E8D8E", "#7A797A", "#626162"), back_q_alpha=0.10),
    Variant("signature", "Signature", "bcp-visa-qore-signature", [(0, "#0A0A0A"), (1, "#000000")], WHITE, 0.20, "#8A8A8A", "#8A8A8A", "#E6E6E6", "#D2D2D2", "#D2D2D2", "#BDBDBD",
            back_bg=("#1B1B1C", "#101011", "#050505"), back_q_alpha=0.07),
    Variant("infinite", "Infinite", "bcp-visa-qore-infinite", [(0, "#000000"), (1, "#000000")], None, 1.0, WHITE, WHITE, WHITE, "#E3E3E3", "#E3E3E3", "#C9C9C9", infinite=True,
            back_bg=("#07111F", "#050B14", "#020509"), back_q_alpha=0.0),
]


def infinite_star():
    """Destello grande de la Qore Infinite: estrella de cuatro puntas de curvas suaves con brillo
    de varios colores (aproximación: el arte oficial usa una malla de color que no es reproducible 1:1)."""
    cx, cy = 600.0, 375.0
    wv, wh = 92.0, 66.0       # semiancho de los brazos vertical y horizontal
    rx, ry = 430.0, 330.0     # radio de las curvas cóncavas entre brazos
    top, bottom, left, right = -120.0, 900.0, -130.0, 1340.0
    f = 0.55
    path = Path().M(cx + wv, top).L(cx + wv, cy - wh - ry)
    path.C(cx + wv, cy - wh - ry + f * ry, cx + wv + rx - f * rx, cy - wh, cx + wv + rx, cy - wh)
    path.L(right, cy - wh).L(right, cy + wh).L(cx + wv + rx, cy + wh)
    path.C(cx + wv + rx - f * rx, cy + wh, cx + wv, cy + wh + ry - f * ry, cx + wv, cy + wh + ry)
    path.L(cx + wv, bottom).L(cx - wv, bottom).L(cx - wv, cy + wh + ry)
    path.C(cx - wv, cy + wh + ry - f * ry, cx - wv - rx + f * rx, cy + wh, cx - wv - rx, cy + wh)
    path.L(left, cy + wh).L(left, cy - wh).L(cx - wv - rx, cy - wh)
    path.C(cx - wv - rx + f * rx, cy - wh, cx - wv, cy - wh - ry + f * ry, cx - wv, cy - wh - ry)
    path.L(cx - wv, top).Z()
    radials = [
        Radial("estrella-base", cx, cy, 520, [(0.0, "#0B1F8A", 1.0), (0.45, "#0E2A96", 1.0), (1.0, "#06103A", 1.0)]),
        Radial("brillo-arriba", 690, 0, 300, [(0.0, "#F28A5E", 0.95), (0.6, "#C0689A", 0.45), (1.0, "#C0689A", 0.0)]),
        Radial("brillo-violeta", 500, 150, 260, [(0.0, "#8D6BD0", 0.85), (1.0, "#8D6BD0", 0.0)]),
        Radial("brillo-derecha", 1120, 470, 470, [(0.0, "#35BDEB", 0.95), (0.55, "#2B86D2", 0.5), (1.0, "#2B86D2", 0.0)]),
        Radial("brillo-abajo", 600, 800, 300, [(0.0, "#8D64CC", 0.9), (0.7, "#6A4AC0", 0.3), (1.0, "#6A4AC0", 0.0)]),
    ]
    fade = Gradient("fade-izquierda", 0, 0, 430, 0, [(0.0, "#000000", 1.0), (0.55, "#000000", 0.55), (1.0, "#000000", 0.0)])
    nodes = [Node("Destello-base", path, fill="url(#estrella-base)")]
    for r in radials[1:]:
        nodes.append(Node("Destello-" + r.id, path, fill=f"url(#{r.id})"))
    nodes.append(Node("Destello-fade-izquierda", path, fill="url(#fade-izquierda)"))
    return nodes, radials + [fade]


def front_scene(v: Variant, with_chip: bool = False) -> Scene:
    grads = [Gradient("fondo", 0, 0, 0, H, [(o, c, 1.0) for o, c in v.bg])]
    nodes = [Node("Fondo", rrect(0, 0, W, H, CORNER), fill="url(#fondo)")]
    if v.infinite:
        star_nodes, radials = infinite_star()
        nodes += star_nodes
        grads += radials
    else:
        nodes += q_motif(v.q_fill, v.q_alpha)
    if with_chip:
        nodes += chip()
    nodes += bcp_logo(1.0, 75, 55, color=v.bcp, chevron=v.chevron)
    nodes += contactless(color=v.contactless)
    nodes += qore_wordmark(color=v.qore)
    nodes += visa_logo(color=v.visa)
    nodes += tier_label(v.tier, color=v.label)
    return Scene(f"BCP Visa {v.tier} Qore · anverso",
                 f"Anverso vectorial de la tarjeta BCP Visa {v.tier} Qore. Aproximación de diseño: no es un recurso oficial del banco.",
                 grads, nodes)


def back_scene(v: Variant) -> Scene:
    top, mid, bot = v.back_bg
    grads = [
        Gradient("fondo", 0, 0, 0, H, [(0.0, top, 1.0), (0.45, mid, 1.0), (1.0, bot, 1.0)]),
        Gradient("banda", 0, 78, 0, 192, [(0.0, "#1A1D22", 1.0), (0.5, "#0B0D10", 1.0), (1.0, "#1A1D22", 1.0)]),
        Gradient("firma", 0, 236, 0, 316, [(0.0, "#F4F6F8", 1.0), (1.0, "#E3E7EC", 1.0)]),
    ]
    nodes = [Node("Fondo", rrect(0, 0, W, H, CORNER), fill="url(#fondo)")]
    if v.back_q_alpha > 0:
        for n in q_motif():
            nodes.append(Node(n.name + "-tenue", n.path.transform(-0.9, 0, 0, 0.9, W - 40, 150), fill=v.back_q, fill_alpha=v.back_q_alpha))
    nodes.append(Node("Banda-magnetica", Path().M(0, 78).L(W, 78).L(W, 192).L(0, 192).Z(), fill="url(#banda)"))
    nodes.append(Node("Panel-firma", rrect(72, 238, 760, 76, 6), fill="url(#firma)"))
    hatch = Path()
    x = 84
    while x + 38 < 826:
        hatch.M(x, 316).L(x + 38, 238)
        x += 22
    nodes.append(Node("Panel-firma-trama", hatch, stroke="#9BA6B4", stroke_width=1.6, cap="butt", stroke_alpha=0.55))
    bcp_color = "#9A9A9A" if v.key == "signature" else v.bcp
    nodes += bcp_logo(0.62, 72, 618, color=bcp_color, chevron=v.chevron)
    nodes += visa_logo(x=938, y=630, h=52, color=v.visa)
    return Scene(f"BCP Visa {v.tier} Qore · reverso",
                 "Reverso vectorial ilustrativo (aproximación de diseño). No contiene PAN, CVV ni fecha de vencimiento.",
                 grads, nodes)


# --------------------------------------------------------------------------- escritura


def rounded_card_clip() -> Path:
    return rrect(0, 0, W, H, CORNER)


def write_svg(scene: Scene, filename: str):
    out = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {int(W)} {int(H)}" width="{int(W)}" height="{int(H)}" role="img" aria-labelledby="t d">',
           f'  <title id="t">{scene.title}</title>', f'  <desc id="d">{scene.desc}</desc>', "  <defs>"]
    for g in scene.gradients:
        if isinstance(g, Radial):
            out.append(f'    <radialGradient id="{g.id}" gradientUnits="userSpaceOnUse" cx="{g.cx:g}" cy="{g.cy:g}" r="{g.r:g}">')
            tag = "radialGradient"
        else:
            out.append(f'    <linearGradient id="{g.id}" gradientUnits="userSpaceOnUse" x1="{g.x1:g}" y1="{g.y1:g}" x2="{g.x2:g}" y2="{g.y2:g}">')
            tag = "linearGradient"
        for off, col, al in g.stops:
            out.append(f'      <stop offset="{off:g}" stop-color="{col}"' + (f' stop-opacity="{al:g}"' if al != 1 else "") + "/>")
        out.append(f"    </{tag}>")
    out.append(f'    <clipPath id="plastico"><path d="{rounded_card_clip().d()}"/></clipPath>')
    out.append("  </defs>")
    out.append('  <g id="tarjeta" clip-path="url(#plastico)">')
    for n in scene.nodes:
        attrs = [f'id="{n.name}"', f'd="{n.path.d()}"']
        attrs.append(f'fill="{n.fill}"' if n.fill else 'fill="none"')
        if n.fill_alpha != 1:
            attrs.append(f'fill-opacity="{n.fill_alpha:g}"')
        if n.even_odd:
            attrs.append('fill-rule="evenodd"')
        if n.stroke:
            attrs += [f'stroke="{n.stroke}"', f'stroke-width="{n.stroke_width:g}"', f'stroke-linecap="{n.cap}"', f'stroke-linejoin="{n.join}"']
            if n.stroke_alpha != 1:
                attrs.append(f'stroke-opacity="{n.stroke_alpha:g}"')
        out.append("    <path " + " ".join(attrs) + "/>")
    out.append("  </g>")
    out.append("</svg>")
    os.makedirs(os.path.dirname(filename), exist_ok=True)
    with open(filename, "w", encoding="utf-8") as fh:
        fh.write("\n".join(out) + "\n")


def argb(hexc, alpha=1.0):
    h = hexc.lstrip("#")
    a = round(alpha * 255)
    return f"#{a:02X}{h.upper()}" if a != 255 else f"#{h.upper()}"


def write_vector_drawable(scene: Scene, filename: str):
    grads = {g.id: g for g in scene.gradients}
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           '    xmlns:aapt="http://schemas.android.com/aapt"',
           f'    android:width="{int(W)}dp" android:height="{int(H)}dp"',
           f'    android:viewportWidth="{int(W)}" android:viewportHeight="{int(H)}">']
    # Sin <clip-path>: el viewport recorta lo que sobresale y las esquinas las recorta Compose
    # con la forma de la tarjeta (el parser de Compose aplica clip-path solo a su grupo).

    def gradient_block(attr, g):
        lines = [f'    <aapt:attr name="{attr}">']
        if isinstance(g, Radial):
            lines.append(f'      <gradient android:type="radial" android:centerX="{g.cx:g}" android:centerY="{g.cy:g}" android:gradientRadius="{g.r:g}">')
        else:
            lines.append(f'      <gradient android:type="linear" android:startX="{g.x1:g}" android:startY="{g.y1:g}" android:endX="{g.x2:g}" android:endY="{g.y2:g}">')
        for off, col, al in g.stops:
            lines.append(f'        <item android:offset="{off:g}" android:color="{argb(col, al)}"/>')
        lines += ["      </gradient>", "    </aapt:attr>"]
        return lines

    for n in scene.nodes:
        head = f'  <path android:name="{n.name}" android:pathData="{n.path.d()}"'
        if n.even_odd:
            head += ' android:fillType="evenOdd"'
        body = []
        if n.fill and n.fill.startswith("url(#"):
            body += gradient_block("android:fillColor", grads[n.fill[5:-1]])
        elif n.fill:
            head += f' android:fillColor="{argb(n.fill, 1.0)}"'
            if n.fill_alpha != 1:
                head += f' android:fillAlpha="{n.fill_alpha:g}"'
        if n.stroke:
            head += f' android:strokeColor="{argb(n.stroke, 1.0)}" android:strokeWidth="{n.stroke_width:g}" android:strokeLineCap="{n.cap}" android:strokeLineJoin="{n.join}"'
            if n.stroke_alpha != 1:
                head += f' android:strokeAlpha="{n.stroke_alpha:g}"'
        if body:
            out.append(head + ">")
            out += body
            out.append("  </path>")
        else:
            out.append(head + "/>")
    out.append("</vector>")
    os.makedirs(os.path.dirname(filename), exist_ok=True)
    with open(filename, "w", encoding="utf-8") as fh:
        fh.write("\n".join(out) + "\n")


def main():
    for v in VARIANTS:
        front, back = front_scene(v), back_scene(v)
        folder = os.path.join(DESIGN_DIR, v.folder)
        write_svg(front, os.path.join(folder, f"{v.pascal}Front.svg"))
        write_svg(back, os.path.join(folder, f"{v.pascal}Back.svg"))
        write_vector_drawable(front, os.path.join(RES_DIR, f"{v.res}_front.xml"))
        write_vector_drawable(back, os.path.join(RES_DIR, f"{v.res}_back.xml"))
        print("OK", v.key)


if __name__ == "__main__":
    main()

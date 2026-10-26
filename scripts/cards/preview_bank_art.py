#!/usr/bin/env python3
"""Development-only SVG preview/contact sheets and dynamic-digit contrast measurements.

Requires Pillow and resvg-py; writes only scratch/cardart-bbva-scotiabank/.
Optional official references must already be fetched into scratch/ (see README).
"""
from __future__ import annotations

import io
import hashlib
import json
import statistics
from pathlib import Path

import resvg_py
from PIL import Image, ImageDraw, ImageFont

from gen_bbva import BBVA
from gen_scotiabank import SCOTIA
from gen_bcp_visa_qore import DESIGN_DIR, ROOT

OUT = Path(ROOT) / "scratch/cardart-bbva-scotiabank"
STYLES = [v.folder for v in BBVA] + ["bbva-basica", "bbva-start"] + [v.folder for v in SCOTIA]
DIGIT_INK = {v.folder: "#123D65" if v.finish == "ice" else "#104984" if v.finish == "pearl" else "#082228" if v.finish == "turquoise" else "#FFFFFF" for v in BBVA}
DIGIT_INK.update({v.folder: "#252321" if v.tier == "Infinite" else "#FFFFFF" if v.key == "debit-classic" else "#D4D2D0" for v in SCOTIA})


def luminance(rgb):
    channels = [v / 255 / 12.92 if v / 255 <= .04045 else ((v / 255 + .055) / 1.055) ** 2.4 for v in rgb[:3]]
    return sum(a * b for a, b in zip(channels, [.2126, .7152, .0722]))


def ratio(a, b):
    a, b = luminance(a), luminance(b)
    return (max(a, b) + .05) / (min(a, b) + .05)


def compare_references(font):
    """Reference crops/sampling only: no geometry is derived from raster pixels."""
    directory = OUT / "references"
    if not directory.is_dir(): return
    names = {
        "bbva-debit-classic": "Tarjetas-smart-BBVA",
        "bbva-visa-bfree": "BFREE_VISA_",
        "bbva-mastercard-bfree": "BFREE_MC_",
        "bbva-visa-cero": "CERO_VISA_",
        "bbva-visa-platinum": "PLATINUM_VISA_",
        "bbva-mastercard-platinum": "mastercard-platinum-sinfondo",
        "bbva-visa-signature": "SIGNATURE_VISA_",
        "bbva-visa-infinite": "TC_INFINITE_",
        "bbva-mastercard-black": "BLACK_MC_",
        "scotiabank-debit-classic": "visa-debito.jpg",
        "scotiabank-singular-signature": "credit-card-2.png",
        "scotiabank-singular-infinite": "credit-card-1-v2.png",
        "scotiabank-singular-black": "credit-card-3.png",
    }
    measured = []
    for bank in ["bbva", "scotiabank"]:
        styles = [s for s in names if s.startswith(bank + "-")]
        sheet = Image.new("RGB", (1030, len(styles) * 350), "#E9EDF1")
        d = ImageDraw.Draw(sheet)
        for row, style in enumerate(styles):
            candidates = [p for p in directory.iterdir() if p.name.startswith(names[style]) and p.suffix.lower() != ".svg"]
            if not candidates: continue
            file = candidates[0]
            image = Image.open(file).convert("RGBA")
            if bank == "bbva" and image.width == 1600:
                bounds = (510, 70, 1092, 991)
            elif style == "scotiabank-debit-classic": bounds = (667, 314, 912, 469)
            elif style == "bbva-debit-classic": bounds = (495, 252, 932, 860)
            else: bounds = image.getbbox()
            crop = image.crop(bounds)
            samples = []
            for nx, ny in [(.18, .23), (.83, .48), (.22, .67)]:
                x, y = int(nx * crop.width), int(ny * crop.height)
                patch = crop.crop((x - 4, y - 4, x + 5, y + 5)).convert("RGB")
                rgb = tuple(int(statistics.median(patch.getpixel((px, py))[c] for px in range(9) for py in range(9))) for c in range(3))
                samples.append(dict(normalized_xy=[nx, ny], rgb="#%02X%02X%02X" % rgb))
            measured.append(dict(preset=style, reference=file.name, reference_size=image.size, crop=bounds, samples=samples,
                                 layout="Original portrait recomposed horizontally" if style != "scotiabank-debit-classic" else "Original landscape"))
            crop.thumbnail((380, 300), Image.Resampling.LANCZOS)
            x, y = (390 - crop.width) // 2, row * 350 + 35
            sheet.paste(crop, (x, y), crop)
            preview = Image.open(OUT / "previews" / (style + "_front.png"))
            preview.thumbnail((580, 300), Image.Resampling.LANCZOS)
            sheet.paste(preview, (422, row * 350 + 35), preview)
            d.text((12, row * 350 + 8), style, font=font, fill="#142332")
        sheet.save(OUT / ("comparison-" + bank + ".png"))
    (OUT / "measured-references.json").write_text(json.dumps(measured, indent=2, ensure_ascii=False), encoding="utf-8")


def device_sheets(font, small):
    directory = OUT / "device/cardart"
    if not directory.is_dir(): return
    manifest = []
    missing = []
    for style in DIGIT_INK:
        for width in [200, 320, 420]:
            for side in ["front", "back"]:
                file = directory / f"{style}_{width}_{side}.png"
                if not file.is_file():
                    missing.append(file.name)
                    continue
                manifest.append(dict(file=str(file.relative_to(OUT)), pixels=Image.open(file).size,
                                     sha256=hashlib.sha256(file.read_bytes()).hexdigest()))
    for side in ["front", "back"]:
        sheet = Image.new("RGB", (1530, 5 * 365), "#E9EDF1")
        d = ImageDraw.Draw(sheet)
        for idx, style in enumerate(STYLES):
            file = directory / (f"{style}_320_{side}.png" if style in DIGIT_INK else f"{style}_generic_{side}.png")
            x, y = idx % 3 * 510 + 15, idx // 3 * 365 + 15
            if file.is_file():
                im = Image.open(file).convert("RGBA")
                im.thumbnail((480, 303), Image.Resampling.LANCZOS)
                sheet.paste(im, (x, y), im)
            d.text((x, y + 312), style + " / " + side, font=font, fill="#142332")
            d.text((x, y + 337), "Samsung SM-A165M · 320 dp" if style in DIGIT_INK else "Preset genérico · sin arte nuevo", font=small, fill="#3A495A")
        sheet.save(OUT / ("device-contact-15-" + side + ".png"))
    (OUT / "device-review.json").write_text(json.dumps(dict(serial="RFGL70J9E2T", expected=78,
        screenshots=manifest, missing=missing), indent=2), encoding="utf-8")
    print(f"Device scale captures: {len(manifest)}/78; missing: {len(missing)}")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    previews = OUT / "previews"
    previews.mkdir(exist_ok=True)
    font = ImageFont.truetype("C:/Windows/Fonts/arial.ttf", 19)
    small = ImageFont.truetype("C:/Windows/Fonts/arial.ttf", 14)
    reports = []
    for face in ["Front", "Back"]:
        sheet = Image.new("RGB", (1530, 5 * 365), "#E9EDF1")
        draw = ImageDraw.Draw(sheet)
        for idx, style in enumerate(STYLES):
            files = list((Path(DESIGN_DIR) / style).glob("*" + face + ".svg"))
            x, y = idx % 3 * 510 + 15, idx // 3 * 365 + 15
            if files:
                png = resvg_py.svg_to_bytes(svg_path=str(files[0]), width=1213)
                full = Image.open(io.BytesIO(png)).convert("RGBA")
                full.save(previews / (style + "_" + face.lower() + ".png"))
                image = full.resize((480, 303), Image.Resampling.LANCZOS)
                sheet.paste(image, (x, y), image)
                # Pixel samples cover all possible glyph pixels at 200–420dp, including font floor.
                area = (66, 596, 530, 742) if face == "Front" else (350, 240, 808, 360)
                color = DIGIT_INK[style] if face == "Front" else "#1C2430"
                ink = tuple(bytes.fromhex(color[1:]))
                cr = min(ratio(ink, full.getpixel((xx, yy))) for xx in range(area[0], area[2], 4) for yy in range(area[1], area[3], 4))
                samples = {str((xx, yy)): '#%02X%02X%02X' % full.getpixel((xx, yy))[:3] for xx, yy in [(66, 624), (96, 656), (360, 660)]} if face == "Front" else {}
                reports.append(dict(preset=style, face=face.lower(), digits=color, minimum_contrast=round(cr, 3), samples=samples))
                if face == "Front":
                    draw.text((x + 480 * 66 / 1213, y + 303 * 596 / 766), "•••• 4242", font=font, fill=color)
                else:
                    draw.text((x + 480 * 806 / 1213, y + 303 * 228 / 766), "•••• 4242", font=font, fill=color, anchor="ra")
                draw.text((x, y + 335), f"dígitos: {cr:.2f}:1", fill="#3A495A", font=small)
            else:
                draw.rounded_rectangle((x, y, x + 480, y + 303), 18, fill="#D5DBE2")
                draw.text((x + 35, y + 125), "SIN ARTE VERIFICABLE", font=font, fill="#3D4C5C")
                draw.text((x + 35, y + 157), "Conserva el preset genérico", font=small, fill="#3D4C5C")
            draw.text((x, y + 311), style + " / " + face.lower(), fill="#142332", font=font)
        sheet.save(OUT / ("contact-15-" + face.lower() + ".png"))
    (OUT / "digit-contrast.json").write_text(json.dumps(reports, indent=2), encoding="utf-8")
    print(f"Rendered {len(reports)} faces; min digit contrast: {min(r['minimum_contrast'] for r in reports):.2f}:1")
    print(OUT / "contact-15-front.png")
    print(OUT / "contact-15-back.png")
    for r in reports:
        if r['minimum_contrast'] < 4.5: print('LOW CONTRAST', r)
    compare_references(font)
    device_sheets(font, small)


if __name__ == "__main__": main()

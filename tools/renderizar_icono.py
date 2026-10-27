"""Rasteriza los vectores M/L/Q/Z reales para respaldo y revisión a tamaño de launcher.

Solo herramienta de desarrollo (Pillow); no genera ni modifica imágenes con IA.
"""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
A = "{http://schemas.android.com/apk/res/android}"


def contours(data):
    tokens = re.findall(r"[MLQZ]|-?\d+(?:\.\d+)?", data)
    index, points = 0, []
    while index < len(tokens):
        command = tokens[index]
        index += 1
        if command == "Z":
            yield points
            points = []
        elif command in ("M", "L"):
            points.append((float(tokens[index]), float(tokens[index + 1])))
            index += 2
        elif command == "Q":
            x, y = points[-1]
            cx, cy, ex, ey = map(float, tokens[index:index + 4])
            index += 4
            for step in range(1, 33):
                t = step / 32
                points.append(((1-t)**2*x + 2*(1-t)*t*cx + t*t*ex,
                               (1-t)**2*y + 2*(1-t)*t*cy + t*t*ey))
        else:
            raise ValueError("Comando vectorial no compatible")


def render(size, mono=False, circular=False):
    supersample = size * 8
    vector = ET.parse(RES / "drawable" / ("ic_launcher_monochrome.xml" if mono else "ic_launcher_foreground.xml")).getroot()
    group = vector.find("group")
    sx, sy = (float(group.attrib[A + key]) for key in ("scaleX", "scaleY"))
    result = Image.new("RGBA", (supersample, supersample), "#000000")
    for path in group.findall("path"):
        mask = Image.new("L", result.size)
        for polygon in contours(path.attrib[A + "pathData"]):
            part = Image.new("L", result.size)
            ImageDraw.Draw(part).polygon([(((x-54)*sx+54)*supersample/108,
                                         ((y-54)*sy+54)*supersample/108) for x, y in polygon], fill=255)
            mask = ImageChops.difference(mask, part)
        result.paste(path.attrib[A + "fillColor"], (0, 0, supersample, supersample), mask)
    if circular:
        mask = Image.new("L", result.size)
        ImageDraw.Draw(mask).ellipse((0, 0, supersample-1, supersample-1), fill=255)
        result.putalpha(mask)
    return result.resize((size, size), Image.Resampling.LANCZOS)


def main():
    for density, size in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
        target = RES / f"mipmap-{density}"
        target.mkdir(exist_ok=True)
        render(size).save(target / "ic_launcher.png")
        render(size, circular=True).save(target / "ic_launcher_round.png")
    preview = Image.new("RGB", (620, 260), "#17212e")
    draw = ImageDraw.Draw(preview)
    for row, mono in enumerate((False, True)):
        draw.text((16, 16 + row * 120), "Monocromo" if mono else "Adaptativo / respaldo", fill="white")
        for col, size in enumerate((48, 64, 96)):
            icon = render(size, mono, circular=True)
            x, y = 205 + col * 135, 12 + row * 120
            preview.paste(icon, (x, y), icon)
            draw.text((x, y + 99), f"{size} px", fill="white")
    target = ROOT / "docs/assets/icono-launcher.png"
    preview.save(target)
    print("Iconos de respaldo y previsualización generados desde los vectores.")


if __name__ == "__main__":
    main()

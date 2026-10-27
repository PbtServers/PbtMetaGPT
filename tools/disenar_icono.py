"""Tres símbolos vectoriales originales y hoja de revisión reproducible (Pillow)."""
from PIL import Image, ImageDraw
from renderizar_icono import ROOT, RES, render

DEST = ROOT / "docs/assets/iconos"
BLUE = "#388BFF"  # Acento ya presente en el foreground del proyecto.
A_WHITE = "M25,40 Q25,37 29,37 L44,37 Q49,37 50,42 L51,46 Q54,43 57,46 L58,42 Q59,37 64,37 L79,37 Q83,37 83,40 L82,55 Q81,67 70,67 Q58,67 57,55 L56,52 Q54,49 52,52 L51,55 Q50,67 38,67 Q27,67 26,55 Z M32,44 L44,44 L45,54 Q45,61 38,61 Q32,61 32,54 Z M64,44 L76,44 L76,54 Q76,61 70,61 Q63,61 63,54 Z"
A_BLUE = "M50,46 Q54,42 58,46 L57,52 Q54,48 51,52 Z"
B_WHITE = "M23,39 L45,39 Q49,39 50,43 L52,54 Q53,66 40,67 L37,67 Q25,67 24,56 L21,43 Q20,39 23,39 Z M28,45 L44,45 L46,55 Q47,61 39,61 Q31,61 30,55 Z M85,39 L63,39 Q59,39 58,43 L56,54 Q55,66 68,67 L71,67 Q83,67 84,56 L87,43 Q88,39 85,39 Z M80,45 L64,45 L62,55 Q61,61 69,61 Q77,61 78,55 Z"
B_BLUE = "M48,47 L54,41 L60,47 L54,53 Z M52,47 L54,45 L56,47 L54,49 Z"
C_WHITE = "M23,39 L45,39 L53,47 L49,51 L42,45 L29,45 L31,58 L39,63 L47,58 L49,51 L54,54 L52,62 L39,70 L26,62 Z M85,39 L63,39 L55,47 L59,51 L66,45 L79,45 L77,58 L69,63 L61,58 L59,51 L54,54 L56,62 L69,70 L82,62 Z"
C_BLUE = "M48,48 L54,42 L60,48 L56,52 L54,50 L52,52 Z"
CONCEPTS = {"a": (A_WHITE, A_BLUE), "b": (B_WHITE, B_BLUE), "c": (C_WHITE, C_BLUE)}


def vector(white, blue, mono=False):
    return f'''<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <!-- Geometría original: gafas y nodo de diálogo integrados; sin letras. -->
    <group android:scaleX="0.85" android:scaleY="0.85" android:pivotX="54" android:pivotY="54">
        <path android:fillColor="#FFFFFF" android:fillType="evenOdd" android:pathData="{white}"/>
        <path android:fillColor="{'#FFFFFF' if mono else BLUE}" android:fillType="evenOdd" android:pathData="{blue}"/>
    </group>
</vector>
'''


def main():
    DEST.mkdir(parents=True, exist_ok=True)
    previous = DEST / "anterior.png"
    if not previous.exists():
        render(108).save(previous)
    for name, (white, blue) in CONCEPTS.items():
        (DEST / f"concepto-{name}.xml").write_text(vector(white, blue), encoding="utf-8")
    white, blue = CONCEPTS["b"]
    (RES / "drawable/ic_launcher_foreground.xml").write_text(vector(white, blue), encoding="utf-8")
    (RES / "drawable/ic_launcher_monochrome.xml").write_text(vector(white, blue, True), encoding="utf-8")
    board = Image.new("RGB", (960, 790), "#FFFFFF")
    draw = ImageDraw.Draw(board)
    draw.text((24, 18), "PbtMetaGPT / Tres propuestas / #000000 #FFFFFF #388BFF", fill="black")
    for row, (name, title) in enumerate((("a", "A - Fusion geometrica"), ("b", "B - Inteligencia integrada / elegido"), ("c", "C - Dialogo abstracto"))):
        y = 55 + row * 195
        draw.text((24, y), title, fill="black")
        path = DEST / f"concepto-{name}.xml"
        for col, size in enumerate((108, 72, 48, 36, 24)):
            icon = render(size, vector_path=path, rounded=col % 2 == 0, circular=col % 2 == 1)
            x = 28 + col * 165
            board.paste(icon, (x, y + 24), icon)
            draw.text((x, y + 142), f"{size} dp / 1x", fill="black")
    y = 665
    draw.text((24, y), "Anterior / elegido / monocromo / fondo negro", fill="black")
    for col, icon in enumerate((Image.open(previous), render(72, circular=True), render(72, mono=True, rounded=True))):
        x = 28 + col * 165
        if col == 0:
            icon = icon.resize((72, 72), Image.Resampling.LANCZOS)
        board.paste(icon, (x, y + 20), icon)
    board.paste("#000000", (520, y + 20, 680, y + 104))
    icon = render(72, circular=True)
    board.paste(icon, (562, y + 26), icon)
    board.save(DEST / "comparativa.png")
    masks = Image.new("RGB", (720, 400), "#FFFFFF")
    review = ImageDraw.Draw(masks)
    review.text((16, 12), "B / mascaras circular y redondeada / color y monocromo", fill="black")
    for row, (circular, mono) in enumerate(((True, False), (False, False), (True, True), (False, True))):
        for col, size in enumerate((24, 36, 48, 72)):
            icon = render(size, mono=mono, circular=circular, rounded=not circular)
            x, y = 24 + col * 165, 40 + row * 88
            masks.paste(icon, (x, y), icon)
            review.text((x + 78, y + 20), f"{size} dp", fill="black")
    masks.save(DEST / "mascaras.png")
    print("Tres propuestas y comparación con el icono anterior renderizadas; seleccionado B.")


if __name__ == "__main__":
    main()

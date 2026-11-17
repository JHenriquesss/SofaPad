"""Gera o ícone do SofaPad (sofá) para o agente do Windows e para o app Android.

As formas são as mesmas de assets/sofapad-icon.svg, na grade de 1024.

Uso: python scripts/make-icon.py
Requer Pillow. Escreve:
- windows-agent/assets/sofapad.ico e assets/sofapad-icon.png;
- os vetores do Android em android-app/app/src/main/res/drawable/.
"""
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
SIZE = 1024
BACKGROUND = (0, 0, 1024, 1024, 224, "#16293B")
# (x0, y0, x1, y1, raio, cor, opacidade no ícone monocromático do Android)
SOFA = [
    (252, 760, 322, 836, 20, "#3C7FA8", 0.55),   # pé esquerdo
    (702, 760, 772, 836, 20, "#3C7FA8", 0.55),   # pé direito
    (222, 268, 802, 640, 110, "#4FA9DB", 0.7),   # encosto
    (282, 520, 742, 780, 60, "#8FD0F5", 1.0),    # assento
    (132, 420, 322, 780, 90, "#78C6F0", 0.85),   # braço esquerdo
    (702, 420, 892, 780, 90, "#78C6F0", 0.85),   # braço direito
]
ANDROID_HEADER = '<?xml version="1.0" encoding="utf-8"?>\n<!-- Gerado por scripts/make-icon.py; não edite à mão. -->\n'


def render(size: int) -> Image.Image:
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    for x0, y0, x1, y1, radius, color in [BACKGROUND] + [shape[:6] for shape in SOFA]:
        draw.rounded_rectangle((x0, y0, x1 - 1, y1 - 1), radius=radius, fill=color)
    return image.resize((size, size), Image.LANCZOS)


def rounded(x0: int, y0: int, x1: int, y1: int, r: int) -> str:
    w, h = x1 - x0 - 2 * r, y1 - y0 - 2 * r
    return (f"M{x0 + r},{y0}h{w}a{r},{r} 0 0 1 {r},{r}v{h}a{r},{r} 0 0 1 -{r},{r}"
            f"h-{w}a{r},{r} 0 0 1 -{r},-{r}v-{h}a{r},{r} 0 0 1 {r},-{r}z")


def paths(indent: str, monochrome: bool) -> str:
    lines = []
    for x0, y0, x1, y1, radius, color, alpha in SOFA:
        fill = f'android:fillColor="#FFFFFF" android:fillAlpha="{alpha}"' if monochrome else f'android:fillColor="{color}"'
        lines.append(f'{indent}<path {fill} android:pathData="{rounded(x0, y0, x1, y1, radius)}" />')
    return "\n".join(lines)


def adaptive_layer(monochrome: bool) -> str:
    # Camada de ícone adaptativo: tela de 108 dp, com o sofá dentro da zona segura central de 66 dp.
    scale = 56 / 760
    tx, ty = 54 - 512 * scale, 54 - 552 * scale
    return (ANDROID_HEADER
            + '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            + '    android:width="108dp" android:height="108dp"\n'
            + '    android:viewportWidth="108" android:viewportHeight="108">\n'
            + f'    <group android:scaleX="{scale:.5f}" android:scaleY="{scale:.5f}" android:translateX="{tx:.3f}" android:translateY="{ty:.3f}">\n'
            + paths("        ", monochrome) + "\n    </group>\n</vector>\n")


def full_icon() -> str:
    # Ícone completo (fundo + sofá) para usar dentro do app.
    x0, y0, x1, y1, radius, color = BACKGROUND
    return (ANDROID_HEADER
            + '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            + '    android:width="48dp" android:height="48dp"\n'
            + '    android:viewportWidth="1024" android:viewportHeight="1024">\n'
            + f'    <path android:fillColor="{color}" android:pathData="{rounded(x0, y0, x1, y1, radius)}" />\n'
            + paths("    ", False) + "\n</vector>\n")


def main() -> None:
    sizes = [16, 20, 24, 32, 40, 48, 64, 256]
    images = [render(size) for size in sizes]
    ico = ROOT / "windows-agent" / "assets" / "sofapad.ico"
    ico.parent.mkdir(parents=True, exist_ok=True)
    images[-1].save(ico, format="ICO", sizes=[(s, s) for s in sizes], append_images=images[:-1])
    png = ROOT / "assets" / "sofapad-icon.png"
    png.parent.mkdir(parents=True, exist_ok=True)
    render(512).save(png)
    drawable = ROOT / "android-app" / "app" / "src" / "main" / "res" / "drawable"
    for name, content in [
        ("ic_launcher_foreground.xml", adaptive_layer(False)),
        ("ic_launcher_monochrome.xml", adaptive_layer(True)),
        ("ic_sofapad.xml", full_icon()),
    ]:
        (drawable / name).write_text(content, encoding="utf-8", newline="\n")
    print(f"{ico} ({ico.stat().st_size} bytes), {png}, vetores em {drawable}")


if __name__ == "__main__":
    main()

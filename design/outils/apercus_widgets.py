"""Aperçus des widgets dans le sélecteur du lanceur (J8) : découpés dans une capture réelle.

Le sélecteur affichait la mise en page vide (fond blanc, texte blanc) : il montre maintenant une
image du vrai widget. Captures du Pixel_10_Pro (1280 px de large, densité 3), en Nocturne.

Usage (Pillow, outil local) :  python design/outils/apercus_widgets.py <capture-aujourdhui.png> <capture-prieres.png>
Aujourd'hui vient du Pixel_10_Pro, Prières du téléphone (rendu MIUI réel).
Les rectangles sont ceux des captures du 04/11/2026 (widget entier, coins repeints).
"""
import pathlib
import sys

from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "feature/widget/src/main/res/drawable-nodpi"
RADIUS = 66  # 22 dp à densité 3


def crop(src: str, box: tuple[int, int, int, int], name: str, aspect: float) -> None:
    shot = Image.open(src).convert("RGBA").crop(box)
    # Les coins arrondis de la capture laissent voir le fond d'écran : repeints du fond du widget.
    bg = shot.getpixel((shot.width // 2, 12))
    corners = Image.new("L", shot.size, 0)
    ImageDraw.Draw(corners).rounded_rectangle((3, 3, shot.width - 4, shot.height - 4), RADIUS, fill=255)  # 3 px : le liseré lissé du bord part aussi
    widget = Image.new("RGBA", shot.size, bg)
    widget.paste(shot, (0, 0), corners)
    # Le lanceur pose l'aperçu dans un cadre (blanc sous MIUI) aux proportions des cases : l'image
    # est prolongée par le fond du widget jusqu'à ces proportions, pour que le cadre ne se voie pas.
    width = max(widget.width, round(widget.height * aspect))
    height = max(widget.height, round(widget.width / aspect))
    image = Image.new("RGBA", (width, height), bg)
    image.paste(widget, ((width - widget.width) // 2, (height - widget.height) // 2))
    mask = Image.new("L", image.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, image.width - 1, image.height - 1), RADIUS, fill=255)
    image.putalpha(mask)
    image = image.resize((image.width // 2, image.height // 2), Image.LANCZOS)
    OUT.mkdir(parents=True, exist_ok=True)
    image.save(OUT / f"{name}.png", optimize=True)
    print(name, image.size)


crop(sys.argv[1], (60, 527, 1220, 1163), "widget_today_preview", 2.16)  # 4 × 2 sous MIUI
# Prières : capture du téléphone (Xiaomi 15T Pro, 1280 px, densité 3,25) ; HyperOS le montre dans un cadre 4 × 2.
crop(sys.argv[2], (86, 1722, 956, 1916), "widget_prayers_preview", 2.16)

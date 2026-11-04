"""Aperçus des widgets dans le sélecteur du lanceur (J8) : découpés dans une capture réelle.

Le sélecteur affichait la mise en page vide (fond blanc, texte blanc) : il montre maintenant une
image du vrai widget. Captures du Pixel_10_Pro (1280 px de large, densité 3), en Nocturne.

Usage (Pillow, outil local) :  python design/outils/apercus_widgets.py <capture-aujourdhui.png> <capture-prieres.png>
Les rectangles sont ceux des captures du 04/11/2026 ; à reprendre si le widget change de taille.
"""
import pathlib
import sys

from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "feature/widget/src/main/res/drawable-nodpi"
RADIUS = 66  # 22 dp à densité 3


def crop(src: str, box: tuple[int, int, int, int], name: str) -> None:
    image = Image.open(src).convert("RGBA").crop(box)
    mask = Image.new("L", image.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, image.width - 1, image.height - 1), RADIUS, fill=255)
    image.putalpha(mask)
    image = image.resize((image.width // 2, image.height // 2), Image.LANCZOS)
    OUT.mkdir(parents=True, exist_ok=True)
    image.save(OUT / f"{name}.png", optimize=True)
    print(name, image.size)


crop(sys.argv[1], (60, 527, 1220, 1163), "widget_today_preview")
crop(sys.argv[2], (60, 1872, 1220, 2171), "widget_prayers_preview")

"""Génère les 31 icônes « du jour » (J8, lot 5) à partir de la vraie Fraunces de l'app.

Grammaire du logo « Ombre » (design/icon/hora-icon.svg) : fond lapis et longue ombre du style
(ic_launcher_background_ombre), le chiffre du jour à la place du H, centré dans la zone sûre
(x 36,5..71,5, y 35..64), le filet bleu ciel dessous. Chiffres en Fraunces 600, opsz 72, comme
sur la planche J8.

Usage (fontTools, outil local, rien dans l'app) :  python design/outils/icone_du_jour.py
Écrit core/ui/src/main/res/drawable/ic_launcher_{foreground,monochrome}_day_NN.xml,
app/src/main/res/mipmap-anydpi/ic_launcher_day_NN.xml, core/ui/.../DayIcons.kt et affiche le
bloc d'activity-alias à coller dans app/src/main/AndroidManifest.xml.
"""
import pathlib

from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.boundsPen import BoundsPen
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

ROOT = pathlib.Path(__file__).resolve().parents[2]
FONT = ROOT / "core/ui/src/main/res/font/fraunces.ttf"
DRAWABLE = ROOT / "core/ui/src/main/res/drawable"
MIPMAP = ROOT / "app/src/main/res/mipmap-anydpi"
KOTLIN = ROOT / "core/ui/src/main/java/com/gabrielifrim/hora/core/ui/theme/DayIcons.kt"

BASELINE, HEIGHT, CENTER, MAX_WIDTH = 64.0, 29.0, 54.0, 33.0
INK, ACC = "#FFF1ECDF", "#FF8FB8FF"
FILET = "M39.5,70h29a1,1 0 0 1 0,2h-29a1,1 0 0 1 0,-2z"
SHADOW = "M61,46L96,64L96,73L61,52z"

font = instantiateVariableFont(TTFont(FONT), {"wght": 600, "opsz": 72})
glyphs = font.getGlyphSet()
cmap = font.getBestCmap()
# Hauteur des chiffres : celle du « 0 », qui donne l'échelle commune aux 31 icônes.
bp = BoundsPen(glyphs)
glyphs[cmap[ord("0")]].draw(bp)
scale = HEIGHT / bp.bounds[3]


def digits_path(n: str) -> str:
    names = [cmap[ord(c)] for c in n]
    advance = sum(glyphs[g].width for g in names) * scale
    k = min(1.0, MAX_WIDTH / advance)  # « 28 » plus large que la zone sûre : il rapetisse
    s = scale * k
    x = CENTER - advance * k / 2
    # Rapetissé, le chiffre reste posé sur la même ligne et centré dans la hauteur du H.
    y = BASELINE - (HEIGHT - HEIGHT * k) / 2
    pen = SVGPathPen(glyphs, ntos=lambda v: f"{v:.2f}".rstrip("0").rstrip("."))
    for g in names:
        glyphs[g].draw(TransformPen(pen, (s, 0, 0, -s, x, y)))
        x += glyphs[g].width * s
    return pen.getCommands()


def vector(comment: str, body: str) -> str:
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- {comment} Généré par design/outils/icone_du_jour.py, ne pas modifier à la main. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
{body}</vector>
"""


aliases = []
for day in range(1, 32):
    nn = f"{day:02d}"
    d = digits_path(str(day))
    (DRAWABLE / f"ic_launcher_foreground_day_{nn}.xml").write_text(vector(
        f"Icône du jour, le {day} : le chiffre en Fraunces et le filet.",
        f'    <path android:pathData="{d}" android:fillColor="{INK}" />\n'
        f'    <path android:pathData="{FILET}" android:fillColor="{ACC}" />\n'), encoding="utf-8", newline="\n")
    (DRAWABLE / f"ic_launcher_monochrome_day_{nn}.xml").write_text(vector(
        f"Icône du jour, le {day}, monochrome (icônes à thème) : l'ombre à mi-opacité.",
        f'    <path android:pathData="{SHADOW}" android:fillColor="#FF000000" android:fillAlpha="0.5" />\n'
        f'    <path android:pathData="{d}" android:fillColor="#FF000000" />\n'
        f'    <path android:pathData="{FILET}" android:fillColor="#FF000000" />\n'), encoding="utf-8", newline="\n")
    (MIPMAP / f"ic_launcher_day_{nn}.xml").write_text(f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Généré par design/outils/icone_du_jour.py. -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background_ombre" />
    <foreground android:drawable="@drawable/ic_launcher_foreground_day_{nn}" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome_day_{nn}" />
</adaptive-icon>
""", encoding="utf-8", newline="\n")
    aliases.append(f"""        <activity-alias
            android:name=".IconDay{nn}"
            android:enabled="false"
            android:exported="true"
            android:icon="@mipmap/ic_launcher_day_{nn}"
            android:roundIcon="@mipmap/ic_launcher_day_{nn}"
            android:targetActivity=".MainActivity">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity-alias>""")

ids = ",\n".join(f"    R.drawable.ic_launcher_foreground_day_{d:02d}" for d in range(1, 32))
KOTLIN.write_text(f"""package com.gabrielifrim.hora.core.ui.theme

import com.gabrielifrim.hora.core.ui.R

// Généré par design/outils/icone_du_jour.py, ne pas modifier à la main.
/** Avant-plans des icônes du jour, du 1er au 31 (index 0 = le 1er). */
val DayIconForegrounds: IntArray = intArrayOf(
{ids},
)
""", encoding="utf-8", newline="\n")

print("        <!-- Icône du jour (J8) : 31 alias générés par design/outils/icone_du_jour.py. -->")
print("\n".join(aliases))

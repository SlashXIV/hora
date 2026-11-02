"""Génère les couches de l'icône Hora (core/ui, et les adaptive-icon dans app : un mipmap de bibliothèque n'est pas fusionné) et la source SVG commentée (à lancer depuis la racine du dépôt)."""
import math
import os

R = 'core/ui/src/main/res/'
LAPIS = '#FF1D2B5C'; CREME = '#FFF1ECDF'; CIEL = '#FF8FB8FF'; OMBRE = '#FF111A3F'
MIDI_BG = '#FFE8ECF2'; MIDI_INK = '#FF1D2B5C'; MIDI_ACC = '#FF2448A8'; MIDI_SH = '#FFC3CCDA'
H = 'M40,35h7v29h-7z M61,35h7v29h-7z'
GNOMON = 'M47,51.5L61,46L61,52L47,57.5z'
FILET = 'M39.5,70h29a1,1 0 0 1 0,2h-29a1,1 0 0 1 0,-2z'
SHADOW_LONG = 'M61,46L96,64L96,73L61,52z'
SHADOW_SHORT = 'M61,46L86,58.5L86,64.5L61,52z'


def lines():
    out = []
    for i in range(-6, 7):
        if i == 0:
            continue
        a = math.radians(i * 13); r0, r1 = 40, 64
        x0, y0 = 54 + r0 * math.sin(a), 71 - r0 * math.cos(a)
        x1, y1 = 54 + r1 * math.sin(a), 71 - r1 * math.cos(a)
        out.append((f'M{x0:.1f},{y0:.1f}L{x1:.1f},{y1:.1f}', i == 3))
    return out


def vec(body, comment):
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- {comment} Source : design/icon/hora-icon.svg. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
{body}</vector>
'''


def p(d, fill=None, alpha=None, stroke=None, sw=None, salpha=None, indent='    '):
    a = [f'android:pathData="{d}"']
    if fill: a.append(f'android:fillColor="{fill}"')
    if alpha is not None: a.append(f'android:fillAlpha="{alpha}"')
    if stroke: a += [f'android:strokeColor="{stroke}"', f'android:strokeWidth="{sw}"', 'android:strokeLineCap="round"']
    if salpha is not None: a.append(f'android:strokeAlpha="{salpha}"')
    return indent + '<path ' + ' '.join(a) + ' />\n'


def bg(color, detail):
    return p('M0,0h108v108h-108z', color) + detail


def hourlines(ink):
    return ''.join(p(d, stroke=ink, sw=1.6 if hot else 1.1, salpha=0.9 if hot else 0.22) for d, hot in lines())


week = ''.join(
    p(f'M{33 + i * 7 - 1.6:.1f},79a1.6,1.6 0 1 0 3.2,0a1.6,1.6 0 1 0 -3.2,0z', CIEL if i == 0 else CREME, alpha=None if i == 0 else 0.35)
    for i in range(7)
)

files = {
    'drawable/ic_launcher_background_cadran.xml': vec(bg(LAPIS, hourlines(CREME) + p(SHADOW_LONG, OMBRE)),
        'Icône A « Cadran » : fond lapis, lignes horaires (celle de l’heure présente marquée), ombre du style.'),
    'drawable/ic_launcher_background_ombre.xml': vec(bg(LAPIS, p(SHADOW_LONG, OMBRE)), 'Icône B « Ombre » : fond lapis et longue ombre du style.'),
    'drawable/ic_launcher_background_semaine.xml': vec(bg(LAPIS, week + p(SHADOW_SHORT, OMBRE)),
        'Icône C « Semaine » : sept points de semaine, aujourd’hui en ciel, ombre courte.'),
    'drawable/ic_launcher_background_midi.xml': vec(bg(MIDI_BG, hourlines(MIDI_INK) + p(SHADOW_LONG, MIDI_SH)), 'Icône « Midi » : la variante A en clair.'),
    'drawable/ic_launcher_foreground.xml': vec(p(H, CREME) + p(GNOMON, CREME) + p(FILET, CIEL), 'Avant-plan : le H, sa barre en style de cadran, la ligne de midi.'),
    'drawable/ic_launcher_foreground_midi.xml': vec(p(H, MIDI_INK) + p(GNOMON, MIDI_INK) + p(FILET, MIDI_ACC), 'Avant-plan clair de la variante « Midi ».'),
    'drawable/ic_launcher_monochrome.xml': vec(
        p(SHADOW_LONG, '#FF000000', alpha=0.5) + p(H, '#FF000000') + p(GNOMON, '#FF000000') + p(FILET, '#FF000000'),
        'Couche monochrome (icônes à thème, Android 13+) : lettre, style et filet ; l’ombre à mi-opacité.'),
    'drawable/splash_icon.xml': vec(
        '    <group android:name="shadow" android:pivotX="61" android:pivotY="49">\n'
        + p(SHADOW_LONG, OMBRE, indent='        ') + '    </group>\n'
        + p(H, CREME) + p(GNOMON, CREME) + p(FILET, CIEL),
        'Icône de démarrage : l’avant-plan et l’ombre du style, qui pivote dans avd_splash.'),
    'drawable/avd_splash.xml': '''<?xml version="1.0" encoding="utf-8"?>
<!-- Démarrage Android 12+ : l'ombre du style tourne d'un cran puis se pose (800 ms, courbe Enter). -->
<animated-vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:drawable="@drawable/splash_icon">
    <target android:name="shadow">
        <aapt:attr name="android:animation">
            <set>
                <objectAnimator android:propertyName="rotation" android:valueFrom="-28" android:valueTo="0"
                    android:duration="800" android:interpolator="@interpolator/hora_enter" />
                <objectAnimator android:propertyName="scaleX" android:valueFrom="0.6" android:valueTo="1"
                    android:duration="800" android:interpolator="@interpolator/hora_enter" />
            </set>
        </aapt:attr>
    </target>
</animated-vector>
''',
    'interpolator/hora_enter.xml': '''<?xml version="1.0" encoding="utf-8"?>
<!-- Courbe Enter de la famille : cubic(0.2, 0.7, 0.2, 1). -->
<pathInterpolator xmlns:android="http://schemas.android.com/apk/res/android"
    android:controlX1="0.2" android:controlY1="0.7" android:controlX2="0.2" android:controlY2="1" />
''',
}
for v, fg in [('cadran', 'ic_launcher_foreground'), ('ombre', 'ic_launcher_foreground'),
              ('semaine', 'ic_launcher_foreground'), ('midi', 'ic_launcher_foreground_midi')]:
    files[f'../../../../app/src/main/res/mipmap-anydpi/ic_launcher_{v}.xml'] = f'''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background_{v}" />
    <foreground android:drawable="@drawable/{fg}" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
'''

for k, v in files.items():
    os.makedirs(os.path.dirname(R + k), exist_ok=True)
    with open(R + k, 'w', encoding='utf-8') as f:
        f.write(v)

svg_lines = ''.join(
    f'    <path d="{d}" stroke="#F1ECDF" stroke-opacity="{0.9 if hot else 0.22}" stroke-width="{1.6 if hot else 1.1}" stroke-linecap="round"/>\n'
    for d, hot in lines()
)
os.makedirs('design/icon', exist_ok=True)
with open('design/icon/hora-icon.svg', 'w', encoding='utf-8') as f:
    f.write(f'''<?xml version="1.0" encoding="UTF-8"?>
<!--
    Hora — icône d'application, source de référence (variante A « Cadran », validée le 02/11/2026).

    Même grammaire que les autres applications de l'auteur (Dromos, Topo, Brut, Kalos,
    Popote) : une lettre tracée à la main, un trait d'accent fin dessous, les couleurs
    de la DA, et un détail qui raconte l'application.

    DA « le cadran solaire » : la barre du H est le STYLE d'un cadran, posé en biais
    à environ 20° (de 47,54.5 à 61,49 en son milieu ; à 43°, la lettre se lisait « И »), et il projette son OMBRE vers le bas à droite. Le trait
    d'accent sous la lettre est la LIGNE DE MIDI. Le détail : douze lignes horaires
    gravées, rayonnant du pied du cadran (54,71) tous les 13°, rayons 40 à 64 ; celle
    de l'heure présente (i = 3) est plus marquée.

    Canevas 108x108 (icône adaptative) ; seul le cercle central de diamètre 66 est
    garanti visible. Les lignes horaires et l'ombre débordent volontairement : leur
    milieu reste dans le cercle, leurs extrémités sont faites pour être rognées.

    ZONE D'EXCLUSION (lettre + style + filet, marge comprise) : x 36,5..71,5, y 33..74.
    Les lignes horaires partent à 40 du pied et restent hors de la lettre ; l'ombre
    naît sur le fût droit (x = 61) et sort de la zone vers la droite.

    Couches dérivées (core/ui/src/main/res/), générées par le même script :
      - drawable/ic_launcher_background_cadran : fond, lignes horaires, ombre ;
      - drawable/ic_launcher_foreground : H, style, ligne de midi ;
      - drawable/ic_launcher_monochrome : lettre, style, filet ; ombre à mi-opacité ;
      - drawable/splash_icon + avd_splash : démarrage, l'ombre qui tourne d'un cran.
    Variantes au choix dans les réglages : B « Ombre » (sans lignes), C « Semaine »
    (sept points, ombre courte), « Midi » (A en clair). Fond lapis, distinct des
    autres icônes de la famille.

    Palette : fond lapis #1D2B5C, lettre crème #F1ECDF, ligne de midi ciel #8FB8FF
    (accent de Nocturne), ombre #111A3F.
-->
<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">
  <defs>
    <clipPath id="squircle">
      <path d="M54,0 C86,0 108,22 108,54 C108,86 86,108 54,108 C22,108 0,86 0,54 C0,22 22,0 54,0 Z"/>
    </clipPath>
  </defs>
  <g clip-path="url(#squircle)">
    <rect width="108" height="108" fill="#1D2B5C"/>
{svg_lines}    <path fill="#111A3F" d="M61,46L96,64L96,73L61,52z"/>
    <path fill="#F1ECDF" d="M40,35h7v29h-7z M61,35h7v29h-7z"/>
    <path fill="#F1ECDF" d="M47,51.5L61,46L61,52L47,57.5z"/>
    <rect x="38.5" y="70" width="31" height="2" rx="1" fill="#8FB8FF"/>
  </g>
</svg>
''')
print('ok', len(files))

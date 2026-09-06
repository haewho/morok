#!/usr/bin/env python3
"""Derive platform vectors and SVG previews from the editable MOROK master.

Uses Python's standard library. Raster export is a separate optional step:
node assets/morok/rasterize.cjs (requires sharp with SVG support).
"""

import json
import math
from pathlib import Path
import re
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parent
SVG_NS = "http://www.w3.org/2000/svg"
ANDROID_NS = "http://schemas.android.com/apk/res/android"
XML_HEADER = '<?xml version="1.0" encoding="utf-8"?>\n'
brand = json.loads((ROOT / "brand.json").read_text())
colors = brand["colors"]
master = ET.parse(ROOT / "morok-master.svg").getroot()
path = master.find(f"{{{SVG_NS}}}path")
path_data = " ".join(path.attrib["d"].split())


def write(relative_path, content):
    target = ROOT / relative_path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")


def svg(content, viewbox="0 0 1254 1254", width=1254, height=1254):
    return (
        XML_HEADER
        + f'<svg xmlns="{SVG_NS}" viewBox="{viewbox}" width="{width}" height="{height}">\n'
        + content + "\n</svg>\n"
    )


def mark(color, transform=""):
    attr = f' transform="{transform}"' if transform else ""
    return f'<path fill="{color}" fill-rule="evenodd"{attr} d="{path_data}"/>'


for variant, bg_key, fg_key in (
    ("dark", "backgroundDark", "accent"),
    ("light", "backgroundLight", "accentOnLight"),
):
    write(f"morok-{variant}.svg", svg(
        f'<rect width="1254" height="1254" fill="{colors[bg_key]}"/>\n'
        + mark(colors[fg_key])
    ))

# Convert the original, unscaled geometry into the Android 108 dp layer.
# Height is 64 dp (y=22..86), inside the central 66 dp safe region.
scale = brand["adaptive"]["markHeight"] / (1127 - 132)
tx = 54 - 627 * scale
ty = 54 - ((1127 + 132) / 2) * scale
transform = f"translate({tx:.9f} {ty:.9f}) scale({scale:.9f})"


def vector(fill):
    return XML_HEADER + f'''<vector xmlns:android="{ANDROID_NS}"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <group android:scaleX="{scale:.9f}" android:scaleY="{scale:.9f}"
        android:translateX="{tx:.9f}" android:translateY="{ty:.9f}">
        <path android:fillColor="{fill}" android:fillType="evenOdd"
            android:pathData="{path_data}" />
    </group>
</vector>
'''


write("android/drawable/morok_launcher_foreground.xml", vector("@color/morok_brand_accent"))
write("android/drawable/morok_launcher_monochrome.xml", vector("#FFFFFFFF"))
write("android/drawable/morok_launcher_background.xml", XML_HEADER + f'''<shape xmlns:android="{ANDROID_NS}" android:shape="rectangle">
    <solid android:color="@color/morok_brand_background" />
</shape>
''')
write("android/values/morok_brand.xml", XML_HEADER + f'''<resources>
    <string name="morok_brand_name">{brand["name"]["default"]}</string>
    <color name="morok_brand_accent">{colors["accent"]}</color>
    <color name="morok_brand_background">{colors["backgroundDark"]}</color>
    <color name="morok_brand_accent_on_light">{colors["accentOnLight"]}</color>
    <color name="morok_brand_background_light">{colors["backgroundLight"]}</color>
</resources>
''')
write("android/values-ru/morok_brand.xml", XML_HEADER + f'''<resources>
    <string name="morok_brand_name">{brand["name"]["ru"]}</string>
</resources>
''')

for version in (26, 33):
    monochrome = '\n    <monochrome android:drawable="@drawable/morok_launcher_monochrome" />' if version == 33 else ""
    for resource in ("morok_launcher", "morok_launcher_round"):
        write(f"android/mipmap-anydpi-v{version}/{resource}.xml", XML_HEADER + f'''<adaptive-icon xmlns:android="{ANDROID_NS}">
    <background android:drawable="@drawable/morok_launcher_background" />
    <foreground android:drawable="@drawable/morok_launcher_foreground" />{monochrome}
</adaptive-icon>
''')

write("previews/adaptive-layer.svg", svg(
    f'<rect width="108" height="108" fill="{colors["backgroundDark"]}"/>\n'
    + mark(colors["accent"], transform), "0 0 108 108", 108, 108
))
write("previews/legacy-round.svg", svg(
    '<defs><clipPath id="round"><circle cx="627" cy="627" r="627"/></clipPath></defs>\n'
    f'<g clip-path="url(#round)"><rect width="1254" height="1254" fill="{colors["backgroundDark"]}"/>\n'
    + mark(colors["accent"]) + '</g>'
))

# Render launcher mask previews as SVG, without modifying the input bitmap.
# The static viewport is the central 72 dp of a 108 dp adaptive layer.
cells = []
for index, (name, clip, fg, bg) in enumerate((
    ("Circle / dark", '<circle cx="54" cy="54" r="36"/>', colors["accent"], colors["backgroundDark"]),
    ("Rounded / dark", '<rect x="18" y="18" width="72" height="72" rx="17"/>', colors["accent"], colors["backgroundDark"]),
    ("Circle / light", '<circle cx="54" cy="54" r="36"/>', colors["accentOnLight"], colors["backgroundLight"]),
    ("Monochrome", '<rect x="18" y="18" width="72" height="72" rx="17"/>', "#332744", "#DDD0EC"),
)):
    x = 24 + index * 234
    cells.append(
        f'<svg x="{x}" y="20" width="210" height="210" viewBox="18 18 72 72">'
        f'<defs><clipPath id="mask{index}">{clip}</clipPath></defs>'
        f'<g clip-path="url(#mask{index})"><rect width="108" height="108" fill="{bg}"/>'
        + mark(fg, transform) + '</g></svg>'
        f'<text x="{x+105}" y="256" text-anchor="middle" font-size="16" fill="#332744">{name}</text>'
    )

for i, size in enumerate((16, 24, 32, 48, 64)):
    x = 100 + i * 175
    cells.append(
        f'<svg x="{x}" y="300" width="{size}" height="{size}" viewBox="18 18 72 72">'
        '<defs><clipPath id="small%d"><circle cx="54" cy="54" r="36"/></clipPath></defs>' % i
        + f'<g clip-path="url(#small{i})"><rect width="108" height="108" fill="{colors["backgroundDark"]}"/>'
        + mark(colors["accent"], transform) + '</g></svg>'
        f'<text x="{x}" y="395" font-size="14" fill="#332744">{size} px</text>'
    )
write("previews/launcher-masks.svg", svg(
    '<rect width="960" height="420" fill="#ECE8F0"/>' + "\n".join(cells),
    "0 0 960 420", 960, 420
))

# Sample the Bézier contour for a geometric clipping check. The command set is
# deliberately small and must be updated explicitly if the master changes.
tokens = re.findall(r"[A-Za-z]|-?\d+(?:\.\d+)?", path_data)
points = []
i = 0
cursor = (0, 0)
start = cursor
while i < len(tokens):
    command = tokens[i]
    i += 1
    count = {"M": 2, "L": 2, "C": 6, "Z": 0}[command]
    values = [float(v) for v in tokens[i:i+count]]
    i += count
    if command in ("M", "L"):
        cursor = tuple(values)
        if command == "M":
            start = cursor
        points.append(cursor)
    elif command == "C":
        p0 = cursor
        p1, p2, p3 = tuple(values[0:2]), tuple(values[2:4]), tuple(values[4:6])
        for step in range(101):
            t = step / 100
            points.append(tuple(
                (1-t)**3*p0[k] + 3*(1-t)**2*t*p1[k] + 3*(1-t)*t*t*p2[k] + t**3*p3[k]
                for k in (0, 1)
            ))
        cursor = p3
    else:
        cursor = start

transformed = [(x*scale + tx, y*scale + ty) for x, y in points]
max_radius = max(math.hypot(x-54, y-54) for x, y in transformed)
if max_radius > brand["adaptive"]["safeCircleRadius"]:
    raise SystemExit(f"Mark exceeds the adaptive safe circle: {max_radius:.3f} dp")

for file in list(ROOT.rglob("*.svg")) + list((ROOT / "android").rglob("*.xml")):
    ET.parse(file)

report = {
    "source": "morok-master.svg",
    "androidViewportDp": 108,
    "markBoundsDp": [round(min(p[j] for p in transformed), 4) for j in (0, 1)]
        + [round(max(p[j] for p in transformed), 4) for j in (0, 1)],
    "sampledMaximumRadiusDp": round(max_radius, 4),
    "safeCircleRadiusDp": brand["adaptive"]["safeCircleRadius"],
    "xmlParsed": True,
    "androidResourceCompilation": "not run by asset generator",
    "deviceLauncherTest": "not run by asset generator"
}
write("previews/verification.json", json.dumps(report, ensure_ascii=False, indent=2) + "\n")
print(json.dumps(report, ensure_ascii=False, indent=2))

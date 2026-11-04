"""Rasterize the native polygon emblem at the same 62 px size as the other factions."""
from pathlib import Path
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parents[1]
source = ET.parse(root / "tools/team-pioneer.svg").getroot()
image = Image.new("RGBA", (int(source.attrib["width"]), int(source.attrib["height"])))
draw = ImageDraw.Draw(image)
for group in source.findall("{*}g"):
    for polygon in group.findall("{*}polygon"):
        points = [tuple(map(int, point.split(","))) for point in polygon.attrib["points"].split()]
        draw.polygon(points, fill=group.attrib["fill"])
target = root / "sprites/team-pioneer.png"
image.save(target)
print(f"Saved {target.relative_to(root)} ({image.width}x{image.height}, transparent RGBA)")

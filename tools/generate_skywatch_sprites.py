"""Create only the missing, geometric pixel sprites for the orbital support content."""
from pathlib import Path
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parents[1] / "sprites"
paths = [root / "units/skywatch.png", root / "blocks/units/orbital-deployment-pad.png",
         root / "blocks/units/orbital-deployment-pad-top.png",
         root / "blocks/units/orbital-deployment-pad-out.png",
         root / "blocks/units/orbital-deployment-pad-in.png"]

outline, dark, mid, light, blue = "#44413c", "#4d5967", "#8493a3", "#c1cdd2", "#8ad7f5"
ship = Image.new("RGBA", (56, 64)); d = ImageDraw.Draw(ship)
for x0, x1 in [(3, 17), (38, 52)]:
    d.polygon([(x0, 18), (x1, 14), (x1, 46), (x0, 42)], fill=outline)
    d.rectangle((x0 + 2, 21, x1 - 2, 39), fill=dark)
    for y in [23, 29, 35]: d.line((x0 + 3, y, x1 - 3, y), fill=blue)
    d.line((x0 + 7, 21, x0 + 7, 39), fill=mid)
d.rectangle((15, 27, 40, 34), fill=outline)
d.rectangle((16, 29, 39, 31), fill=mid)
d.polygon([(28, 3), (35, 15), (37, 40), (32, 54), (23, 54), (18, 40), (20, 15)], fill=outline)
d.polygon([(28, 8), (32, 18), (32, 40), (28, 48), (23, 39), (23, 18)], fill=mid)
d.polygon([(28, 8), (28, 45), (23, 39), (23, 18)], fill=light)
d.ellipse((22, 21, 34, 33), fill=outline)
d.ellipse((24, 23, 32, 31), fill=blue)
d.rectangle((27, 25, 29, 28), fill="#eaffff")
d.rectangle((24, 47, 31, 53), fill=dark)

pad = Image.new("RGBA", (128, 128)); d = ImageDraw.Draw(pad)
d.polygon([(8, 0), (119, 0), (127, 8), (127, 119), (119, 127), (8, 127), (0, 119), (0, 8)], fill=outline)
d.rectangle((6, 6, 121, 121), fill=dark)
d.rectangle((12, 12, 115, 115), fill="#66717d")
d.ellipse((22, 22, 105, 105), fill=outline, outline=mid, width=4)
d.ellipse((30, 30, 97, 97), fill="#364652", outline=blue, width=2)
d.ellipse((44, 44, 83, 83), outline="#536b7b", width=3)
d.line((48, 64, 80, 64), fill=blue, width=2)
d.line((64, 48, 64, 80), fill=blue, width=2)
top = Image.new("RGBA", (128, 128)); d = ImageDraw.Draw(top)
for x, y in [(10, 10), (98, 10), (10, 98), (98, 98)]:
    d.rectangle((x, y, x + 19, y + 19), fill=outline)
    d.rectangle((x + 3, y + 3, x + 16, y + 16), fill=mid)
    d.rectangle((x + 5, y + 5, x + 14, y + 7), fill=light)
    d.rectangle((x + 6, y + 11, x + 13, y + 14), fill=blue)
out = Image.new("RGBA", (128, 128)); d = ImageDraw.Draw(out)
d.rectangle((102, 48, 123, 50), fill=outline)
d.rectangle((102, 77, 123, 79), fill=outline)
d.line([(107, 56), (118, 64), (107, 72)], fill=outline, width=5)
d.line([(107, 56), (118, 64), (107, 72)], fill=blue, width=2)
incoming = Image.new("RGBA", (128, 128)); d = ImageDraw.Draw(incoming)
d.line([(10, 57), (20, 64), (10, 71)], fill=outline, width=5)
d.line([(10, 57), (20, 64), (10, 71)], fill=mid, width=2)
for image, path in zip([ship, pad, top, out, incoming], paths):
    if path.exists():
        continue  # Player edits and previously generated sprites are always preserved.
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)

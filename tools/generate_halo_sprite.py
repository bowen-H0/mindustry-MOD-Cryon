"""Create only the missing Halo hull, matching the existing procedural orbital pixel-art assets."""
from pathlib import Path
from PIL import Image, ImageDraw
import math

path = Path(__file__).resolve().parents[1] / "sprites/units/halo.png"
if path.exists():
    raise SystemExit(f"Refusing to overwrite existing sprite: {path.name}")
image = Image.new("RGBA", (96, 88))
draw = ImageDraw.Draw(image)
outline, dark, mid, light = "#44413c", "#505d6b", "#8796a5", "#c1d0d6"
draw.ellipse((3, 11, 92, 79), fill=outline)
draw.ellipse((6, 17, 89, 76), fill=dark)
draw.ellipse((5, 11, 90, 69), fill=mid)
draw.ellipse((8, 13, 87, 65), fill=light)
draw.ellipse((12, 17, 83, 61), fill=dark)
draw.ellipse((15, 19, 80, 58), fill=mid)
for angle in range(0, 360, 30):
    a = math.radians(angle)
    x1, y1 = 47 + math.cos(a) * 23, 39 + math.sin(a) * 14
    x2, y2 = 47 + math.cos(a) * 39, 39 + math.sin(a) * 23
    draw.line((int(x1), int(y1), int(x2), int(y2)), fill=dark, width=2)
    x, y = int(47 + math.cos(a) * 38), int(39 + math.sin(a) * 23)
    draw.rectangle((x - 1, y - 1, x + 1, y), fill="#a8f2ed")
draw.ellipse((26, 19, 69, 55), fill=outline)
draw.ellipse((28, 20, 67, 51), fill=light)
draw.ellipse((33, 24, 62, 47), fill=dark)
draw.ellipse((37, 27, 58, 43), fill="#82cbd1")
draw.ellipse((40, 27, 53, 36), fill="#c8f6f3")
# Dark underside and a bright central downward emitter, visible at the front lip.
draw.arc((9, 21, 86, 74), 8, 172, fill=outline, width=3)
draw.rectangle((42, 60, 53, 68), fill=outline)
draw.ellipse((43, 59, 52, 66), fill="#a8f2ed")
draw.line((45, 62, 50, 62), fill="#efffff", width=2)
path.parent.mkdir(parents=True, exist_ok=True)
image.save(path)

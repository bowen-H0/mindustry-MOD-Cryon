"""Generate only the missing T2 orbital hull in the existing procedural pixel-art style."""
from pathlib import Path
from PIL import Image, ImageDraw

path = Path(__file__).resolve().parents[1] / "sprites/units/transit.png"
if path.exists():
    raise SystemExit(f"Refusing to overwrite existing sprite: {path.name}")

image = Image.new("RGBA", (56, 64))
draw = ImageDraw.Draw(image)
outline, dark, mid, light = "#44413c", "#505d6b", "#8796a5", "#c1d0d6"
# Twin forward-forked wing booms around a central beam aperture, viewed from above.
for side in (0, 1):
    def mirror(points):
        return [(55 - x if side else x, y) for x, y in points]
    draw.polygon(mirror([(16, 4), (20, 8), (20, 28), (25, 38), (23, 53),
                         (15, 59), (11, 50), (2, 47), (3, 36), (10, 26), (12, 9)]), fill=outline)
    draw.polygon(mirror([(16, 9), (17, 27), (21, 39), (19, 51), (15, 53),
                         (14, 42), (6, 44), (7, 36), (13, 28), (15, 13)]), fill=mid)
    draw.polygon(mirror([(16, 10), (16, 27), (12, 33), (7, 39), (12, 27), (14, 12)]), fill=light)
    draw.polygon(mirror([(11, 36), (14, 33), (17, 39), (17, 46), (12, 45)]), fill=dark)
    draw.line(mirror([(9, 39), (13, 36)]), fill="#9bdcf7", width=2)
    draw.polygon(mirror([(15, 52), (19, 52), (18, 56), (16, 57)]), fill=dark)
draw.polygon([(27, 17), (31, 25), (35, 37), (33, 50), (22, 50), (20, 37), (24, 25)], fill=outline)
draw.polygon([(27, 21), (31, 33), (31, 47), (24, 47), (24, 33)], fill=mid)
draw.polygon([(27, 22), (27, 44), (24, 44), (24, 33)], fill=light)
draw.ellipse((23, 29, 32, 38), fill=dark)
draw.ellipse((25, 31, 30, 36), fill="#9bdcf7")
draw.rectangle((27, 32, 28, 34), fill="#effaff")
draw.rectangle((24, 49, 31, 53), fill=dark)
path.parent.mkdir(parents=True, exist_ok=True)
image.save(path)

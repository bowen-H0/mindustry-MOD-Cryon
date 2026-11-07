"""Small code-drawn inventory sprite; no external assets required."""
from pathlib import Path
import math
from PIL import Image, ImageDraw

image = Image.new("RGBA", (64, 64))
draw = ImageDraw.Draw(image)
draw.polygon([(16, 4), (47, 4), (59, 16), (59, 47), (47, 59), (16, 59), (4, 47), (4, 16)], fill="#35333e", outline="#201d28", width=3)
draw.polygon([(18, 9), (45, 9), (54, 18), (54, 45), (45, 54), (18, 54), (9, 45), (9, 18)], fill="#be4746", outline="#fa8974", width=2)
draw.ellipse((14, 14, 49, 49), fill="#29272f", outline="#ffb17a", width=2)
for angle in (0, 120, 240):
    points = []
    for radius, angles in ((14, range(angle - 28, angle + 29, 4)), (6, range(angle + 28, angle - 29, -4))):
        points.extend((32 + math.cos(math.radians(a)) * radius, 32 + math.sin(math.radians(a)) * radius) for a in angles)
    draw.polygon(points, fill="#ffc879")
draw.ellipse((29, 29, 35, 35), fill="#fff1c4")
for x, y in ((14, 14), (49, 14), (14, 49), (49, 49)):
    draw.rectangle((x - 1, y - 1, x + 1, y + 1), fill="#dddded")
output = Path(__file__).resolve().parents[1] / "sprites/items/nuclear-explosive.png"
output.parent.mkdir(parents=True, exist_ok=True)
image.save(output)

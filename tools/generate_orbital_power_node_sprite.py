"""Code-drawn receiver platform, in the mod's native block sprite format."""
from pathlib import Path
from PIL import Image, ImageDraw
import math

image = Image.new("RGBA", (96, 96))
draw = ImageDraw.Draw(image)
draw.polygon([(20, 2), (75, 2), (93, 20), (93, 75), (75, 93), (20, 93), (2, 75), (2, 20)], fill="#282b3c", outline="#111827", width=3)
draw.polygon([(23, 8), (72, 8), (87, 23), (87, 72), (72, 87), (23, 87), (8, 72), (8, 23)], fill="#4b5065", outline="#7a8799", width=3)
for angle in (0, 90, 180, 270):
    a = math.radians(angle)
    x, y = 48 + math.cos(a) * 32, 48 + math.sin(a) * 32
    draw.rounded_rectangle((x - 7, y - 7, x + 7, y + 7), radius=2, fill="#22283a", outline="#a9c8dc", width=2)
    draw.rectangle((x - 3, y - 3, x + 3, y + 3), fill="#fbd367")
draw.ellipse((17, 17, 78, 78), fill="#22283a", outline="#a9803a", width=4)
draw.ellipse((24, 24, 71, 71), fill="#30291f", outline="#fbd367", width=3)
draw.ellipse((33, 33, 62, 62), fill="#d9b35d", outline="#f6dfa3", width=3)
draw.ellipse((40, 40, 55, 55), fill="#f9e6b1")
for x, y in ((19, 19), (76, 19), (19, 76), (76, 76)):
    draw.rectangle((x - 2, y - 2, x + 2, y + 2), fill="#b5c1cc")
path = Path(__file__).resolve().parents[1] / "sprites/blocks/power/orbital-power-node.png"
path.parent.mkdir(parents=True, exist_ok=True)
image.save(path)

"""Draw the new small orbital hull. Existing player sprites are never overwritten."""
from pathlib import Path
from PIL import Image, ImageDraw

path = Path(__file__).resolve().parents[1] / "sprites/units/startrail.png"
if path.exists():
    raise SystemExit(f"Refusing to overwrite existing sprite: {path.name}")
image = Image.new("RGBA", (40, 48))
draw = ImageDraw.Draw(image)
outline, dark, mid, light = "#44413c", "#555d68", "#8796a5", "#bccbd0"
# A narrow orbital fuselage and swept solar-panel wings, facing up in the atlas.
draw.polygon([(20, 2), (25, 15), (37, 31), (36, 38), (26, 33),
              (25, 43), (15, 43), (14, 33), (4, 38), (3, 31), (15, 15)], fill=outline)
draw.polygon([(15, 20), (6, 31), (6, 34), (15, 30)], fill=mid)
draw.polygon([(25, 20), (34, 31), (34, 34), (25, 30)], fill=mid)
draw.polygon([(20, 6), (23, 16), (23, 38), (17, 38), (17, 16)], fill=mid)
draw.polygon([(20, 7), (20, 31), (17, 35), (17, 16)], fill=light)
draw.polygon([(20, 16), (22, 20), (22, 26), (18, 26), (18, 20)], fill=dark)
draw.line([(20, 19), (20, 24)], fill="#9bdcf7", width=2)
draw.line([(9, 31), (13, 28)], fill="#9bdcf7", width=2)
draw.line([(27, 28), (31, 31)], fill="#9bdcf7", width=2)
draw.rectangle((17, 38, 22, 40), fill=dark)
draw.rectangle((18, 40, 21, 42), fill="#9bdcf7")
draw.rectangle((19, 40, 20, 41), fill="#e6f8ff")
path.parent.mkdir(parents=True, exist_ok=True)
image.save(path)

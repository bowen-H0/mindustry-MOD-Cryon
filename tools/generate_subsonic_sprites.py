"""Create missing Subsonic sprites only; preserve all existing artwork."""
from pathlib import Path
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parents[1] / 'sprites/units/subsonic'
root.mkdir(parents=True, exist_ok=True)
dark, steel, light, cyan = '#263442', '#7895ab', '#c3d5df', '#78f2ed'
saved, preserved = [], []

def save_sprite(im, name):
    # Exclusive creation also protects artwork created while this script runs.
    try:
        output = (root / name).open('xb')
    except FileExistsError:
        preserved.append(name)
        return
    with output:
        im.save(output, format='PNG')
    saved.append(name)

def canvas():
    im = Image.new('RGBA', (48, 48))
    return im, ImageDraw.Draw(im)

im, d = canvas()
d.polygon([(15, 9), (20, 5), (28, 5), (33, 9), (35, 30), (29, 39),
           (19, 39), (13, 30)], fill=dark)
d.polygon([(17, 11), (22, 8), (27, 8), (31, 12), (31, 29), (27, 35),
           (20, 35), (17, 29)], fill=steel)
d.polygon([(19, 15), (24, 11), (29, 15), (27, 25), (21, 25)], fill=light)
d.rectangle((20, 13, 28, 15), fill=dark)
d.rectangle((21, 13, 27, 14), fill=cyan)
for x in [14, 31]:
    d.rectangle((x, 19, x + 2, 28), fill=light)
    d.rectangle((x, 29, x + 2, 32), fill=cyan)
d.rectangle((21, 29, 27, 32), fill=dark)
d.line((22, 33, 26, 33), fill=cyan)
save_sprite(im, 'subsonic.png')

im, d = canvas()
d.rectangle((20, 19, 28, 30), fill=dark)
d.rectangle((17, 22, 31, 27), fill=steel)
save_sprite(im, 'subsonic-base.png')

im, d = canvas()
d.polygon([(18, 15), (28, 15), (30, 32), (28, 39), (18, 39), (16, 33)], fill=dark)
d.rectangle((19, 18, 26, 31), fill=steel)
d.rectangle((20, 19, 25, 21), fill=light)
d.rectangle((18, 33, 28, 36), fill=steel)
d.rectangle((20, 33, 26, 34), fill=cyan)
save_sprite(im, 'subsonic-leg.png')

im, d = canvas()
d.rectangle((22, 26, 26, 28), fill='white')
save_sprite(im, 'subsonic-cell.png')

# Each arm segment has joint centres at y=4 and y=32 (28 px = 7 world units).
# Keep these pivots when editing: the runtime rotates each segment along the IK bone.
im = Image.new('RGBA', (16, 36))
d = ImageDraw.Draw(im)
d.rounded_rectangle((1, 0, 14, 35), radius=5, fill=dark)
d.polygon([(4, 3), (11, 3), (13, 9), (11, 25), (9, 30), (4, 29), (2, 20), (2, 8)], fill=steel)
d.polygon([(4, 5), (10, 5), (11, 9), (9, 19), (4, 18)], fill=light)
d.rectangle((4, 21, 6, 27), fill=cyan)
for y in (4, 32):
    d.ellipse((4, y - 3, 10, y + 3), fill=dark)
    d.ellipse((6, y - 1, 8, y + 1), fill=light)
save_sprite(im, 'subsonic-upper-arm.png')

im = Image.new('RGBA', (18, 36))
d = ImageDraw.Draw(im)
d.polygon([(6, 0), (12, 0), (16, 7), (15, 25), (12, 35), (5, 35), (1, 25), (2, 8)], fill=dark)
d.polygon([(6, 4), (11, 4), (14, 9), (13, 24), (10, 31), (6, 31), (3, 23), (4, 9)], fill=steel)
d.polygon([(6, 7), (11, 7), (12, 11), (11, 23), (6, 26), (5, 20)], fill=light)
d.rectangle((3, 12, 4, 23), fill=cyan)
d.line((7, 13, 11, 13), fill=dark, width=2)
d.line((7, 18, 11, 18), fill=dark, width=2)
for y in (4, 32):
    d.ellipse((5, y - 3, 11, y + 3), fill=dark)
    d.ellipse((7, y - 1, 9, y + 1), fill=steel)
save_sprite(im, 'subsonic-forearm.png')

im = Image.new('RGBA', (14, 14))
d = ImageDraw.Draw(im)
d.rounded_rectangle((1, 1, 12, 12), radius=3, fill=dark)
d.rectangle((3, 3, 10, 10), fill=steel)
d.rectangle((3, 3, 5, 8), fill=light)
for y in (4, 7, 10):
    d.line((7, y, 11, y), fill=dark)
d.rectangle((3, 10, 5, 11), fill=cyan)
save_sprite(im, 'subsonic-hand.png')

# Hilt: 4 px/world unit, +Y points toward the blade. With its centre at
# forward=7, guard y=8 maps to forward=11; hand centres y=16/32 to forward=9/5.
im = Image.new('RGBA', (24, 48))
d = ImageDraw.Draw(im)
d.rounded_rectangle((6, 7, 17, 44), radius=3, fill=dark)
d.rectangle((8, 12, 15, 38), fill='#354957')
for y in range(14, 38, 5):
    d.polygon([(8, y), (15, y + 2), (15, y + 4), (8, y + 2)], fill=steel)
    d.point((8, y), fill=light)
d.rectangle((7, 39, 16, 43), fill=steel)
d.rectangle((9, 40, 14, 42), fill=light)
d.polygon([(0, 5), (5, 6), (8, 8), (16, 8), (19, 6), (23, 5),
           (23, 11), (18, 13), (15, 11), (8, 11), (5, 13), (0, 11)], fill=dark)
d.line([(2, 7), (6, 9), (17, 9), (21, 7)], fill=light, width=2)
d.rectangle((8, 2, 15, 8), fill=steel)
d.rectangle((10, 2, 13, 6), fill=cyan)
save_sprite(im, 'subsonic-hilt.png')
print(f'Created {len(saved)} missing sprites; preserved {len(preserved)} existing sprites.')

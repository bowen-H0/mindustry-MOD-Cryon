"""Create missing Transonic artwork only. Never replace existing or user-drawn sprites."""
from pathlib import Path
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parents[1] / 'sprites/units/transonic'
root.mkdir(parents=True, exist_ok=True)
dark, steel, light, cyan, gold = '#263442', '#7895ab', '#c3d5df', '#78f2ed', '#ffe39b'
created, preserved = [], []

def save(im, suffix=''):
    name = 'transonic' + suffix + '.png'
    try:
        output = (root / name).open('xb')
    except FileExistsError:
        preserved.append(name)
        return
    with output:
        im.save(output, format='PNG')
    created.append(name)

def canvas(size=(72, 72)):
    im = Image.new('RGBA', size)
    return im, ImageDraw.Draw(im)

im, d = canvas()
d.polygon([(27, 7), (44, 7), (51, 16), (53, 49), (45, 62), (27, 62), (19, 49), (21, 16)], fill=dark)
d.polygon([(27, 13), (43, 13), (47, 20), (48, 47), (42, 56), (29, 56), (24, 47), (25, 20)], fill=steel)
d.polygon([(28, 20), (36, 14), (43, 20), (40, 35), (31, 35)], fill=light)
d.rectangle((29, 18, 42, 21), fill=dark)
d.rectangle((31, 18, 40, 19), fill=gold)
for left in [9, 49]:
    d.polygon([(left + 2, 21), (left + 10, 17), (left + 14, 22), (left + 12, 43), (left + 3, 48), (left, 42)], fill=dark)
    d.polygon([(left + 3, 25), (left + 10, 21), (left + 11, 35), (left + 7, 41), (left + 2, 39)], fill=steel)
    d.line((left + 4, 25, left + 9, 23), fill=gold, width=2)
    d.rectangle((left + 4, 31, left + 7, 36), fill=cyan)
d.ellipse((29, 38, 43, 52), fill=dark, outline=gold, width=2)
d.polygon([(36, 39), (41, 45), (36, 51), (31, 45)], fill=gold)
d.rectangle((33, 43, 38, 46), fill=light)
d.line((30, 54, 41, 54), fill=cyan, width=2)
save(im)

im, d = canvas()
d.rectangle((28, 28, 44, 46), fill=dark)
d.rectangle((22, 33, 50, 41), fill=steel)
d.rectangle((29, 34, 43, 40), fill=light)
save(im, '-base')

im, d = canvas()
d.polygon([(27, 21), (43, 21), (46, 43), (43, 58), (27, 58), (23, 47)], fill=dark)
d.polygon([(29, 25), (40, 25), (42, 43), (39, 50), (28, 50), (26, 42)], fill=steel)
d.rectangle((29, 27, 39, 30), fill=light)
d.rectangle((27, 51, 43, 54), fill=steel)
d.line((30, 51, 39, 51), fill=gold, width=2)
save(im, '-leg')

im, d = canvas()
d.rectangle((33, 31, 38, 34), fill='white')
save(im, '-cell')

# Joint centres stay 28 pixels apart; the runtime scales these bones by 1.5.
im, d = canvas((16, 36))
d.rounded_rectangle((1, 0, 14, 35), radius=4, fill=dark)
d.polygon([(4, 3), (11, 3), (13, 10), (11, 27), (5, 31), (2, 23), (2, 8)], fill=steel)
d.polygon([(4, 6), (10, 6), (11, 10), (9, 20), (4, 18)], fill=light)
d.rectangle((3, 22, 6, 27), fill=gold)
for y in [4, 32]:
    d.ellipse((5, y - 3, 11, y + 3), fill=dark)
    d.rectangle((7, y - 1, 9, y + 1), fill=light)
save(im, '-upper-arm')

im, d = canvas((18, 36))
d.polygon([(6, 0), (12, 0), (17, 8), (16, 26), (12, 35), (5, 35), (0, 25), (1, 8)], fill=dark)
d.polygon([(6, 4), (11, 4), (14, 9), (13, 25), (10, 31), (6, 31), (3, 24), (4, 9)], fill=steel)
d.polygon([(6, 8), (11, 8), (12, 12), (10, 23), (6, 26), (5, 19)], fill=light)
d.rectangle((2, 12, 4, 24), fill=gold)
for y in [12, 18, 24]:
    d.line((7, y, 11, y), fill=dark, width=2)
for y in [4, 32]:
    d.ellipse((6, y - 3, 12, y + 3), fill=dark)
    d.rectangle((8, y - 1, 10, y + 1), fill=cyan)
save(im, '-forearm')

im, d = canvas((14, 14))
d.rounded_rectangle((1, 1, 12, 12), radius=3, fill=dark)
d.rectangle((3, 3, 10, 10), fill=steel)
d.rectangle((3, 3, 5, 8), fill=light)
for y in [4, 7, 10]:
    d.line((7, y, 11, y), fill=dark)
d.rectangle((3, 10, 5, 11), fill=gold)
save(im, '-hand')

# Same grip anchors as Subsonic: guard y=8, hands y=16/32, centre at y=24.
im, d = canvas((24, 48))
d.rounded_rectangle((6, 7, 17, 44), radius=3, fill=dark)
d.rectangle((8, 12, 15, 38), fill='#354957')
for y in range(14, 38, 5):
    d.polygon([(8, y), (15, y + 2), (15, y + 4), (8, y + 2)], fill=steel)
d.rectangle((7, 39, 16, 43), fill=gold)
d.polygon([(0, 4), (5, 6), (8, 8), (16, 8), (19, 6), (23, 4), (23, 12), (18, 14), (15, 11), (8, 11), (5, 14), (0, 12)], fill=dark)
d.line([(2, 7), (6, 9), (17, 9), (21, 7)], fill=gold, width=2)
d.rectangle((8, 2, 15, 8), fill=light)
d.rectangle((10, 2, 13, 6), fill=cyan)
save(im, '-hilt')
print(f'Created {len(created)} missing Transonic sprites; preserved {len(preserved)} existing sprites.')

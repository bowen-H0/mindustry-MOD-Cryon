"""Create missing Supersonic artwork only. Never replace existing or user-drawn sprites."""
from pathlib import Path
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parents[1] / 'sprites/units/supersonic'
root.mkdir(parents=True, exist_ok=True)
dark, steel, light, cyan, gold = '#263442', '#7895ab', '#c3d5df', '#78f2ed', '#bda2ff'
created, preserved = [], []

def save(im, suffix=''):
    name = 'supersonic' + suffix + '.png'
    try:
        output = (root / name).open('xb')
    except FileExistsError:
        preserved.append(name)
        return
    with output:
        im.save(output, format='PNG')
    created.append(name)

def canvas(size=(96, 96)):
    im = Image.new('RGBA', size)
    return im, ImageDraw.Draw(im)

im, d = canvas()
d.polygon([(38, 7), (57, 7), (66, 19), (69, 62), (58, 84), (37, 84), (26, 62), (29, 19)], fill=dark)
d.polygon([(39, 14), (56, 14), (61, 23), (62, 61), (55, 76), (40, 76), (33, 61), (34, 23)], fill=steel)
d.polygon([(39, 24), (48, 15), (56, 24), (53, 43), (42, 43)], fill=light)
d.rectangle((39, 21, 56, 25), fill=dark)
d.rectangle((41, 21, 54, 22), fill=cyan)
for side in [-1, 1]:
    def point(x,y): return (48+side*x,y)
    d.polygon([point(16,25),point(28,14),point(36,8),point(32,35),point(33,62),point(24,70),point(18,56)], fill=dark)
    d.polygon([point(20,28),point(29,19),point(28,40),point(29,58),point(24,62),point(21,51)], fill=steel)
    d.line([point(22,29),point(27,24),point(26,37)], fill=gold, width=3)
    d.line([point(23,44),point(26,44),point(27,55)], fill=cyan, width=2)
    d.polygon([point(11,64),point(18,73),point(16,86),point(6,79)], fill=dark)
    d.line([point(11,69),point(14,75),point(12,80)], fill=gold, width=2)
d.polygon([(48,43),(59,52),(56,67),(48,73),(39,67),(36,52)], fill=dark, outline=gold, width=2)
d.polygon([(48,48),(54,54),(48,67),(42,54)], fill=cyan)
d.line([(44,54),(48,51),(51,54)], fill=light, width=2)
d.rectangle((40,74,55,77), fill=light)
save(im)

im, d = canvas()
d.rectangle((36,38,59,62), fill=dark)
d.rectangle((28,45,67,55), fill=steel)
d.rectangle((38,46,57,53), fill=light)
save(im, '-base')

im, d = canvas()
d.polygon([(36,28),(58,28),(63,59),(58,81),(35,81),(29,64)], fill=dark)
d.polygon([(39,34),(54,34),(57,59),(53,69),(36,69),(34,58)], fill=steel)
d.rectangle((39,37,53,41), fill=light)
d.rectangle((34,70,58,75), fill=steel)
d.line((38,70,53,70), fill=cyan, width=3)
save(im, '-leg')

im, d = canvas()
d.rectangle((44,36,51,41), fill='white')
save(im, '-cell')

# Joint centres stay 28 pixels apart; the runtime scales these bones by 2.1.
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
print(f'Created {len(created)} missing Supersonic sprites; preserved {len(preserved)} existing sprites.')

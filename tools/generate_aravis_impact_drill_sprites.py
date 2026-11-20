"""Create only missing impact drill regions; preserve every existing user sprite."""
from pathlib import Path
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parents[1] / 'sprites/blocks/production/aravis'
root.mkdir(parents=True, exist_ok=True)
base = Image.new('RGBA', (128, 128))
d = ImageDraw.Draw(base)
d.polygon([(12, 2), (115, 2), (125, 12), (125, 115), (115, 125), (12, 125),
           (2, 115), (2, 12)], fill='#26343f', outline='#a8b3a8', width=3)
d.rectangle((10, 10, 117, 117), fill='#55686c', outline='#344a55', width=3)
for x, y in ((14, 14), (87, 14), (14, 87), (87, 87)):
    d.rectangle((x, y, x+26, y+26), fill='#273d49', outline='#9aaaa5', width=2)
    for offset in (6, 13, 20):
        d.line((x+4, y+offset, x+22, y+offset), fill='#6e8589', width=3)
d.ellipse((31, 31, 97, 97), fill='#1d2e3b', outline='#a0aaa0', width=4)
for x, y in ((14, 11), (109, 11), (14, 111), (109, 111)):
    d.rectangle((x, y, x+4, y+4), fill='#c4c9ae')

top = Image.new('RGBA', (128, 128))
t = ImageDraw.Draw(top)
t.polygon([(49, 38), (79, 38), (90, 49), (90, 79), (79, 90), (49, 90),
           (38, 79), (38, 49)], fill='#a8b4ac', outline='#263a46', width=3)
t.ellipse((44, 44, 84, 84), fill='#465d68', outline='#d2cdb0', width=3)
t.ellipse((52, 52, 76, 76), fill='#c39363', outline='#253a48', width=3)
t.rectangle((60, 57, 68, 71), fill='#f6d59a')
invert = Image.new('RGBA', (128, 128))
i = ImageDraw.Draw(invert)
i.ellipse((44, 44, 84, 84), fill='#ffe9be')
glow = Image.new('RGBA', (128, 128))
ImageDraw.Draw(glow).ellipse((53, 53, 75, 75), fill='white')
arrow = Image.new('RGBA', (20, 20))
ImageDraw.Draw(arrow).polygon([(2, 4), (10, 4), (16, 10), (16, 18), (10, 12), (8, 10)], fill='white')
for suffix, image in (('', base), ('-top', top), ('-top-invert', invert),
                      ('-glow', glow), ('-arrow', arrow), ('-arrow-blur', arrow)):
    path = root / f'aravis-impact-drill{suffix}.png'
    if path.exists():
        print('Preserved', path.name)
    else:
        image.save(path)
        print('Created', path.name)

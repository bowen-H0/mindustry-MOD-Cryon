"""Create the missing Purge turret sprite without overwriting existing art."""
from pathlib import Path
from PIL import Image, ImageDraw

path = Path(__file__).resolve().parents[1] / 'sprites/blocks/turrets/aravis/purge.png'
if path.exists():
    print('Preserved existing Purge sprite')
else:
    image = Image.new('RGBA', (128, 128))
    d = ImageDraw.Draw(image)
    d.ellipse((12, 18, 116, 122), fill='#26313c', outline='#a6aba1', width=3)
    d.polygon([(31, 27), (97, 27), (117, 58), (105, 106), (87, 122),
               (41, 122), (23, 106), (11, 58)], fill='#4e5e67', outline='#202d38', width=4)
    for mirror in (False, True):
        blade = [(9, 38), (26, 5), (42, 16), (48, 65), (39, 94), (19, 87)]
        if mirror:
            blade = [(128-x, y) for x,y in blade]
        d.polygon(blade, fill='#9da9a6', outline='#27343e', width=3)
        x = 23 if not mirror else 95
        for y in (36, 48, 60):
            d.line((x-5, y, x+5, y+3), fill='#485963', width=4)
    d.rounded_rectangle((41, 30, 87, 103), radius=10, fill='#2b3a45', outline='#c4c7af', width=3)
    d.polygon([(49, 4), (79, 4), (86, 42), (80, 65), (48, 65), (42, 42)],
              fill='#6f8387', outline='#25333d', width=3)
    d.rectangle((55, 6, 73, 18), fill='#24313d')
    d.rectangle((58, 8, 70, 15), fill='#f3dc8d')
    d.ellipse((48, 38, 80, 70), fill='#7c683e', outline='#c5b06a', width=3)
    d.ellipse((55, 45, 73, 63), fill='#f3dc8d', outline='#fff2bf', width=2)
    for x in (47, 75):
        for y in (80, 89):
            d.rectangle((x, y, x+6, y+3), fill='#bfaf78')
    d.rectangle((52, 105, 76, 114), fill='#273b47', outline='#a2aea5', width=2)
    for x in (27, 97):
        d.rectangle((x, 98, x+4, 103), fill='#cbd1b7')
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)
    print('Created Purge sprite')

"""Create missing liquid-generator sprites; preserve every existing image."""
from pathlib import Path
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parents[1] / 'sprites/blocks/power/aravis'
root.mkdir(parents=True, exist_ok=True)

for fuel in ('oil', 'slag'):
    image = Image.new('RGBA', (96, 96))
    glow = Image.new('RGBA', (96, 96))
    draw, light = ImageDraw.Draw(image), ImageDraw.Draw(glow)
    draw.polygon([(10, 2), (85, 2), (94, 11), (94, 85), (85, 94), (10, 94), (2, 85), (2, 11)],
                 fill='#27333c', outline='#a0aaa2', width=2)
    draw.rectangle((8, 9, 87, 86), fill='#566469', outline='#354149', width=3)
    if fuel == 'oil':
        # A dark fuel tank feeds three cylinder banks and a generator coil.
        draw.rounded_rectangle((12, 14, 32, 80), radius=8, fill='#24272e', outline='#a2a99d', width=3)
        draw.rectangle((20, 23, 24, 69), fill='#5b4d50')
        draw.line([(30, 23), (43, 23), (43, 71), (32, 71)], fill='#c09c69', width=5)
        draw.rectangle((37, 13, 65, 79), fill='#303e47', outline='#b4beb2', width=2)
        for y in (19, 38, 57):
            draw.rounded_rectangle((40, y, 62, y+14), radius=3, fill='#889593', outline='#202f39', width=2)
            draw.rectangle((44, y+5, 58, y+8), fill='#6d523c')
            light.rectangle((45, y+6, 57, y+7), fill='white')
        draw.rounded_rectangle((69, 21, 83, 75), radius=4, fill='#293d46', outline='#aeb4a2', width=2)
        for y in range(27, 70, 7):
            draw.line((72, y, 80, y), fill='#bd9a60', width=3)
    else:
        # Armored hot channels surrounded by blue-grey cooling fins.
        draw.rounded_rectangle((27, 12, 69, 82), radius=8, fill='#27343e', outline='#b9bdae', width=3)
        for x in (35, 51):
            draw.rounded_rectangle((x, 19, x+10, 74), radius=4, fill='#9b482f', outline='#e58c49', width=2)
            draw.rectangle((x+4, 25, x+6, 67), fill='#ffc06c')
            light.rectangle((x+4, 25, x+6, 67), fill='white')
        for x in (12, 74):
            draw.rectangle((x, 20, x+10, 76), fill='#2a3b47')
            for y in range(22, 75, 7):
                draw.rectangle((x-2, y, x+12, y+3), fill='#9aaba9')
    for x in (12, 80):
        for y in (11, 81):
            draw.rectangle((x, y, x+3, y+3), fill='#d2d0b4')
    for suffix, asset in (('', image), ('-glow', glow)):
        path = root / f'aravis-{fuel}-generator{suffix}.png'
        if path.exists():
            print('Preserved', path.name)
        else:
            asset.save(path)
            print('Created', path.name)

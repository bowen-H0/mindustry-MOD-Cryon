"""Create a missing Incineration sprite; preserve all existing user artwork."""
from pathlib import Path
from PIL import Image, ImageDraw

path = Path(__file__).resolve().parents[1] / 'sprites/blocks/turrets/aravis/incineration.png'
if path.exists():
    print('Preserved existing Incineration sprite')
else:
    image = Image.new('RGBA', (128, 128))
    d = ImageDraw.Draw(image)
    d.ellipse((13, 20, 115, 122), fill='#26323c', outline='#9ca7a1', width=3)
    d.polygon([(31, 28), (97, 28), (116, 56), (108, 102), (90, 121),
               (38, 121), (20, 102), (12, 56)], fill='#56676e', outline='#22313b', width=4)
    for x in (18, 88):
        d.rounded_rectangle((x, 46, x+22, 105), radius=8, fill='#252932', outline='#9eaaa5', width=3)
        d.rectangle((x+8, 57, x+14, 92), fill='#5e4640')
        d.line([(x+11, 47), (x+11, 33), (64, 33)], fill='#b58950', width=5)
    d.rounded_rectangle((39, 33, 89, 108), radius=9, fill='#899894', outline='#293943', width=3)
    d.rectangle((46, 42, 82, 91), fill='#384953')
    for y in (73, 83, 93):
        d.rectangle((48, y, 80, y+4), fill='#c9b18a')
    d.polygon([(47, 3), (81, 3), (88, 24), (82, 65), (46, 65), (40, 24)],
              fill='#a4aeab', outline='#263640', width=3)
    d.rectangle((49, 7, 79, 23), fill='#292b30', outline='#c6a16e', width=3)
    d.rectangle((54, 12, 74, 18), fill='#a3522d')
    d.rectangle((59, 13, 69, 17), fill='#ffd18a')
    for y in (30, 40, 50):
        d.rectangle((46, y, 82, y+4), fill='#4c5a61')
    d.ellipse((56, 56, 72, 72), fill='#e7944f', outline='#fff0b9', width=2)
    for x in (29, 94):
        d.rectangle((x, 105, x+4, 110), fill='#cbd2bc')
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)
    print('Created Incineration sprite')

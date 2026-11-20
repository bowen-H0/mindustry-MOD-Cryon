"""Create only the missing generator sprite; preserve all existing user art."""
from pathlib import Path
from PIL import Image, ImageDraw
path = Path(__file__).resolve().parents[1] / 'sprites/blocks/power/magnesium-water-generator.png'
if path.exists():
    print('Preserved existing generator sprite')
else:
    im = Image.new('RGBA', (96, 96))
    d = ImageDraw.Draw(im)
    d.polygon([(12,3),(83,3),(93,13),(93,83),(83,93),(12,93),(3,83),(3,13)], fill='#242b32')
    d.polygon([(15,7),(80,7),(88,15),(88,80),(80,88),(15,88),(7,80),(7,15)], fill='#58646d')
    d.rectangle((18,13,77,82), fill='#333e49')
    for x in [12,63]:
        d.rounded_rectangle((x,14,x+21,81), radius=7, fill='#9aa9ac', outline='#26333d', width=3)
        d.rectangle((x+4,27,x+17,68), fill='#3d535d')
        d.rectangle((x+8,29,x+13,65), fill='#68acae')
        d.rectangle((x+5,19,x+16,23), fill='#d1d8ce')
        d.rectangle((x+5,73,x+16,77), fill='#6b7982')
    d.rounded_rectangle((36,18,59,77), radius=5, fill='#74868c', outline='#222e39', width=3)
    for y in [26,36,46,56,66]:
        d.rectangle((40,y,55,y+4), fill='#c7d1c7')
        d.line((40,y+4,55,y+4), fill='#3a474e', width=1)
    d.rectangle((38,5,57,14), fill='#2c3942')
    d.rectangle((42,5,53,11), fill='#9bdaf1')
    d.rectangle((38,81,57,90), fill='#2c3942')
    d.rectangle((42,84,53,90), fill='#659aba')
    for x in [12,80]:
        for y in [10,82]:
            d.rectangle((x,y,x+3,y+3), fill='#c0c7bc')
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path)
    print('Created', path.name)

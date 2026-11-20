"""Create only missing landmark bases; preserve every existing user sprite."""
from pathlib import Path
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parents[1] / 'sprites/blocks/effect'
root.mkdir(parents=True, exist_ok=True)
for name, planet in [('large-team-flag-projector', False), ('planet-projector', True)]:
    path = root / (name + '.png')
    if path.exists():
        print('KEEP', path.name)
        continue
    im = Image.new('RGBA', (128, 128))
    d = ImageDraw.Draw(im)
    d.polygon([(14,0),(114,0),(127,14),(127,114),(114,127),(14,127),(0,114),(0,14)],fill='#242333')
    d.polygon([(18,5),(110,5),(122,18),(122,110),(110,122),(18,122),(5,110),(5,18)],fill='#777383')
    d.polygon([(24,11),(104,11),(116,24),(116,104),(104,116),(24,116),(11,104),(11,24)],fill='#454151')
    for x,y in [(12,12),(100,12),(12,100),(100,100)]:
        d.rectangle((x,y,x+15,y+15),fill='#262433',outline='#b8b1c6',width=2)
        d.rectangle((x+4,y+5,x+11,y+10),fill='#a67de8')
    for box,color,width in [((23,23,105,105),'#252333',8),((29,29,99,99),'#a09aa9',3),((36,36,92,92),'#b68bef',3),((43,43,85,85),'#292836',7)]:
        d.ellipse(box,outline=color,width=width)
    for x,y in [(58,13),(58,103),(13,58),(103,58)]:
        d.rectangle((x,y,x+12,y+12),fill='#292836')
        d.rectangle((x+4,y+3,x+8,y+9),fill='#d2acff')
    if planet:
        d.ellipse((49,49,79,79),fill='#66517d',outline='#c6a2fa',width=2)
        d.ellipse((43,57,85,71),outline='#ece0ff',width=2)
        d.line((59,51,56,61,65,68,70,77),fill='#a47dd4',width=3)
    else:
        d.polygon([(64,45),(80,56),(76,76),(64,84),(52,76),(48,56)],fill='#6c548a',outline='#d6b6ff',width=2)
        d.polygon([(64,53),(70,65),(64,76),(58,65)],fill='#e5cdff')
    with path.open('xb') as stream:
        im.save(stream,format='PNG')
    print('CREATE',path.name)

"""Generate native pixel-grid reactor armor and circuit terrain assets."""
from pathlib import Path
from PIL import Image, ImageDraw
import random
root=Path(__file__).resolve().parents[1]/'sprites/blocks'
p=root/'production/nano-reactor.png';p.parent.mkdir(parents=True,exist_ok=True)
im=Image.new('RGBA',(128,128));d=ImageDraw.Draw(im)
d.polygon([(12,1),(115,1),(126,12),(126,115),(115,126),(12,126),(1,115),(1,12)],fill='#252e37',outline='#111a21',width=3)
d.rectangle((10,10,117,117),fill='#4f6268',outline='#829994',width=3)
for x,y in [(14,14),(84,14),(14,84),(84,84)]:
    d.rectangle((x,y,x+29,y+29),fill='#223936',outline='#9bac9f',width=2)
    for i in range(4): d.line((x+5,y+6+i*5,x+24,y+6+i*5),fill='#4ab47a',width=2)
for a,b in [((64,10),(64,118)),((10,64),(118,64))]:
    d.line((*a,*b),fill='#1b2e2b',width=12);d.line((*a,*b),fill='#4ce68c',width=3)
for r,fill,out in [(39,'#192829','#a4b3a0'),(32,'#49675a','#729783'),(25,'#173d2d','#4ce68c'),(16,'#2e8050','#90e8a5')]:d.ellipse((64-r,64-r,64+r,64+r),fill=fill,outline=out,width=3)
for x,y in [(8,8),(119,8),(8,119),(119,119)]:d.rectangle((x-2,y-2,x+2,y+2),fill='#cad2be')
im.save(p)
p=root/'environment';p.mkdir(parents=True,exist_ok=True)
for v in range(1,4):
    rng=random.Random(320+v)
    im=Image.new('RGBA',(32,32),'#253d34');d=ImageDraw.Draw(im)
    for y in range(0,32,4):
        for x in range(0,32,4):
            d.rectangle((x,y,x+3,y+3),fill=rng.choice(['#263d34','#2b443a','#304b3e','#355343']))
    for _ in range(8):
        x,y=rng.randrange(2,29),rng.randrange(2,29)
        d.line((x,y,x+5,y,x+5,y+4),fill='#559866',width=1)
        d.point((x,y),fill='#87d49a')
    if v <= 2:im.save(p/f'nano-floor{v}.png')
    if v==1:im.save(p/'nano-floor.png')
    im=Image.new('RGBA',(32,32),'#24332f');d=ImageDraw.Draw(im)
    d.polygon([(1,5),(6,1),(27,2),(31,7),(30,28),(25,31),(3,29)],fill='#586f5c',outline='#182c25',width=2)
    d.polygon([(5,7),(11,4),(26,5),(27,19),(19,26),(5,24)],fill='#405849')
    for _ in range(6):
        x,y=rng.randrange(6,24),rng.randrange(5,24)
        d.line((x,y,x+5,y,x+5,y+5),fill='#72b47a',width=1)
    if v <= 2:im.save(p/f'nano-terrain-wall{v}.png')
    if v==1:im.save(p/'nano-terrain-wall.png')
    im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im)
    for _ in range(12):
        x,y=rng.randrange(4,27),rng.randrange(4,27)
        d.polygon([(x,y-3),(x+3,y-1),(x+2,y+3),(x-2,y+2)],fill='#4ce68c',outline='#234c35')
        d.point((x,y-1),fill='#b4f5bc')
    im.save(p/f'nano-ore{v}.png')
    if v==1:im.save(p/'nano-ore.png')

"""Native pixel-grid EMP capacitor charge."""
from pathlib import Path
from PIL import Image, ImageDraw
im=Image.new('RGBA',(64,64));d=ImageDraw.Draw(im)
d.polygon([(8,1),(55,1),(62,8),(62,55),(55,62),(8,62),(1,55),(1,8)],fill='#263744',outline='#10222d',width=3)
d.rectangle((8,8,55,55),fill='#607b88',outline='#a0bfcd',width=2)
for x,y in [(10,10),(40,10),(10,40),(40,40)]:
    d.rectangle((x,y,x+13,y+13),fill='#1e394a',outline='#739aab')
    for j in range(3):d.line((x+3,y+3+j*3,x+10,y+3+j*3),fill='#74d3ec',width=1)
for r,c,o in [(19,'#233c4d','#b6d7df'),(14,'#26566b','#88e8ff'),(8,'#65bbd7','#d0f7ff')]:
    d.ellipse((32-r,32-r,32+r,32+r),fill=c,outline=o,width=2)
d.polygon([(34,21),(27,32),(32,32),(29,43),(38,29),(33,29)],fill='#e5ffff')
for x,y in [(6,6),(57,6),(6,57),(57,57)]:d.rectangle((x-1,y-1,x+1,y+1),fill='#c4d9df')
p=Path(__file__).resolve().parents[1]/'sprites/blocks/effect/emp-bomb.png';p.parent.mkdir(parents=True,exist_ok=True);im.save(p)

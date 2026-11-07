"""Native 7x7 repair array sprite; original layered armor and green nano reservoirs."""
from pathlib import Path
from PIL import Image, ImageDraw
import math
im=Image.new('RGBA',(224,224)); d=ImageDraw.Draw(im)
d.polygon([(26,2),(197,2),(221,26),(221,197),(197,221),(26,221),(2,197),(2,26)],fill='#252c3c',outline='#111a26',width=4)
d.polygon([(32,11),(191,11),(212,32),(212,191),(191,212),(32,212),(11,191),(11,32)],fill='#586675',outline='#8c9ca5',width=3)
for x,y in [(24,24),(150,24),(24,150),(150,150)]:
    d.rounded_rectangle((x,y,x+49,y+49),radius=5,fill='#263d3f',outline='#8ca39f',width=3)
    d.rounded_rectangle((x+9,y+7,x+40,y+42),radius=8,fill='#285d48',outline='#58b77d',width=2)
    for k in range(4): d.line((x+16,y+12+k*8,x+34,y+12+k*8),fill='#6be49b',width=3)
for a in range(0,360,90):
    r=math.radians(a)
    d.line((112+math.cos(r)*85,112+math.sin(r)*85,112+math.cos(r)*42,112+math.sin(r)*42),fill='#243d3c',width=13)
    d.line((112+math.cos(r)*85,112+math.sin(r)*85,112+math.cos(r)*42,112+math.sin(r)*42),fill='#56c38a',width=4)
for radius,fill,outline in [(66,'#24323e','#9daeb4'),(55,'#172a30','#5e9b7b'),(43,'#334b49','#a0b5aa')]:
    pts=[(112+math.cos(math.radians(i*45+22.5))*radius,112+math.sin(math.radians(i*45+22.5))*radius) for i in range(8)]
    d.polygon(pts,fill=fill,outline=outline,width=3)
d.ellipse((82,82,142,142),fill='#19513b',outline='#67d799',width=4)
d.ellipse((93,93,131,131),fill='#2b7952',outline='#9bebb4',width=3)
d.rectangle((107,96,117,128),fill='#b4f2c9');d.rectangle((96,107,128,117),fill='#b4f2c9')
for x,y in [(18,18),(205,18),(18,205),(205,205)]: d.rectangle((x-3,y-3,x+3,y+3),fill='#cad7d5')
p=Path(__file__).resolve().parents[1]/'sprites/blocks/effect/nano-regeneration-matrix.png';p.parent.mkdir(parents=True,exist_ok=True);im.save(p)

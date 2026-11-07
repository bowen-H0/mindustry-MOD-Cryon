"""Original 9x9 armored orbital defense array, drawn in native block pixels."""
from pathlib import Path
from PIL import Image, ImageDraw
import math
im = Image.new('RGBA', (288,288))
d = ImageDraw.Draw(im)
d.polygon([(35,3),(252,3),(284,35),(284,252),(252,284),(35,284),(3,252),(3,35)],fill='#202535',outline='#101623',width=5)
d.polygon([(43,13),(244,13),(274,43),(274,244),(244,274),(43,274),(13,244),(13,43)],fill='#505b6c',outline='#8795a0',width=4)
for y in range(22,266,30):
    for x in range(22,266,30):
        d.rectangle((x,y,x+22,y+22),fill='#414c5e',outline='#5e6b7c',width=1)
d.polygon([(83,33),(204,33),(254,83),(254,204),(204,254),(83,254),(33,204),(33,83)],fill='#242c3d',outline='#1a2130',width=5)
for angle in range(0,360,90):
    a=math.radians(angle)
    cx,cy=144+math.cos(a)*96,144+math.sin(a)*96
    d.rounded_rectangle((cx-25,cy-25,cx+25,cy+25),radius=5,fill='#697580',outline='#b3bcc0',width=3)
    d.rectangle((cx-19,cy-17,cx+19,cy+17),fill='#222a3c',outline='#aa914f',width=2)
    for n in range(4):
        d.line((cx-13,cy-11+n*7,cx+13,cy-11+n*7),fill='#e7bd59',width=3)
# Broad concentric armor, central emitter and four high current trunks.
for r,fill,line in [(77,'#141e2e','#7c899c'),(66,'#323c50','#a48c53'),(55,'#1c2837','#edc660'),(42,'#3a414d','#7e8a99')]:
    points=[(144+math.cos(math.radians(n*45+22.5))*r,144+math.sin(math.radians(n*45+22.5))*r) for n in range(8)]
    d.polygon(points,fill=fill,outline=line,width=4)
for a in range(0,360,90):
    r=math.radians(a)
    d.line((144+math.cos(r)*70,144+math.sin(r)*70,144+math.cos(r)*29,144+math.sin(r)*29),fill='#fbd367',width=5)
d.polygon([(144,111),(177,144),(144,177),(111,144)],fill='#9f833c',outline='#ffe6a1',width=4)
d.polygon([(144,124),(164,144),(144,164),(124,144)],fill='#fbd367')
for x,y in [(33,33),(254,33),(33,254),(254,254)]:
    d.rectangle((x-8,y-8,x+8,y+8),fill='#1b2636',outline='#a7b3bb',width=2)
    d.rectangle((x-3,y-3,x+3,y+3),fill='#fbd367')
p=Path(__file__).resolve().parents[1]/'sprites/blocks/defense/orbital-barrier-node.png'
p.parent.mkdir(parents=True,exist_ok=True)
im.save(p)

"""Generate only missing assets for the three-stage Cryo Alloy production chain."""
from pathlib import Path
from PIL import Image, ImageDraw
import math
root=Path(__file__).resolve().parents[1]/'sprites'

def save_missing(path,image):
    if not path.exists():
        path.parent.mkdir(parents=True,exist_ok=True);image.save(path);print('Created',path.name)

im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im)
d.polygon([(16,2),(29,9),(29,23),(16,30),(3,23),(3,9)],fill='#2d5d59',outline='#94e7c7',width=2)
for y in [8,15,22]:
    for x in [9,16,23]:
        d.line((x,y,x,y+5),fill='#a5ffe1',width=1)
        d.line((x,y,x+5,y+3),fill='#a5ffe1',width=1)
        d.ellipse((x-1,y-1,x+1,y+1),fill='#e5fff2')
save_missing(root/'items/perfect-lattice.png',im)
im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im)
for bounds,color in [((4,8,25,28),'#7565aa'),((10,3,28,23),'#aa94e4'),((4,11,22,25),'#c4b1fa')]:d.ellipse(bounds,fill=color)
d.arc((7,7,25,25),30,290,fill='#f0e5ff',width=2)
save_missing(root/'liquids/superphase-matter.png',im)
for name,size,kind in [('perfect-lattice-assembler',128,0),('superphase-synthesizer',160,1),('cryo-alloy-stabilizer',224,2),('gamma-crystallizer',128,3)]:
    im=Image.new('RGBA',(size,size));d=ImageDraw.Draw(im);c=size//2
    d.polygon([(15,4),(size-16,4),(size-5,15),(size-5,size-16),(size-16,size-5),(15,size-5),(4,size-16),(4,15)],fill='#222c36')
    d.rectangle((10,10,size-11,size-11),fill='#63727c',outline='#acb7b0',width=3)
    d.rectangle((20,20,size-21,size-21),fill='#35434e',outline='#202c36',width=4)
    glow=Image.new('RGBA',(size,size));g=ImageDraw.Draw(glow)
    if kind==0:
        d.rectangle((32,28,size-33,size-29),fill='#7e9894',outline='#bdcdbb',width=3)
        d.rectangle((40,36,size-41,size-37),fill='#1e3d3d')
        for y in range(43,89,15):
            for x in range(43,89,15):
                d.line((x,y,x+14,y),fill='#78a29a',width=2)
                d.line((x,y,x,y+14),fill='#78a29a',width=2)
                g.ellipse((x-2,y-2,x+2,y+2),fill='white')
        for x in [17,103]:
            for y in range(30,100,13):d.rectangle((x,y,x+7,y+7),fill='#a5b2a6')
    elif kind==1:
        for radius,color in [(53,'#a4abb9'),(46,'#303746'),(36,'#8e7ea7'),(28,'#2b2d49')]:
            d.ellipse((c-radius,c-radius,c+radius,c+radius),fill=color)
        d.polygon([(c,52),(106,c),(c,108),(54,c)],fill='#ab99ce',outline='#dbd0ee',width=3)
        for a in range(0,360,45):
            x=c+math.cos(math.radians(a))*40;y=c+math.sin(math.radians(a))*40
            g.ellipse((x-4,y-4,x+4,y+4),fill='white')
        g.ellipse((c-9,c-9,c+9,c+9),fill='white')
        d.rectangle((65,7,94,18),fill='#c7b5d7');d.rectangle((65,141,94,152),fill='#655a85')
    elif kind==2:
        for radius,color in [(85,'#9caaa7'),(76,'#313945'),(62,'#7e8b93'),(49,'#2c2d3b')]:
            d.ellipse((c-radius,c-radius,c+radius,c+radius),fill=color)
        d.polygon([(c,77),(147,c),(c,147),(77,c)],fill='#a14f68',outline='#e1b3b3',width=3)
        d.polygon([(c,91),(132,c),(c,132),(91,c)],fill='#d89299')
        g.polygon([(c,100),(124,c),(c,124),(100,c)],fill='white')
        for a in range(0,360,45):
            x=c+math.cos(math.radians(a))*69;y=c+math.sin(math.radians(a))*69
            d.rectangle((x-6,y-6,x+6,y+6),fill='#91a297',outline='#27363b',width=2)
            g.rectangle((x-2,y-2,x+2,y+2),fill='white')
    else:
        d.ellipse((25,25,102,102),fill='#8bada0',outline='#c4d8bd',width=3)
        d.ellipse((34,34,93,93),fill='#263f40',outline='#597c73',width=3)
        d.polygon([(64,39),(85,54),(78,82),(50,88),(42,59)],fill='#74b993',outline='#c5edc6',width=3)
        d.polygon([(64,48),(75,58),(70,74),(55,77),(51,61)],fill='#a9e9bf')
        for x,y in [(21,21),(94,21),(21,94),(94,94)]:
            d.rectangle((x,y,x+12,y+12),fill='#364b56',outline='#b6c2ae',width=2)
            g.rectangle((x+4,y+4,x+8,y+8),fill='white')
        g.polygon([(64,53),(71,60),(67,70),(58,72),(56,62)],fill='white')
    for x in [14,size-21]:
        for y in [14,size-21]:d.rectangle((x,y,x+6,y+6),fill='#c0c6b5')
    save_missing(root/'blocks/production'/f'{name}.png',im)
    save_missing(root/'blocks/production'/f'{name}-glow.png',glow)

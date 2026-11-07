"""Native grid sprite: powered containment coils and a cyan field projector."""
from pathlib import Path
from PIL import Image, ImageDraw
im=Image.new('RGBA',(96,96));d=ImageDraw.Draw(im)
d.polygon([(10,1),(85,1),(94,10),(94,85),(85,94),(10,94),(1,85),(1,10)],fill='#29343f',outline='#131e2a',width=3)
d.rectangle((9,9,86,86),fill='#576b78',outline='#9badb5',width=2)
for x,y in [(13,13),(65,13),(13,65),(65,65)]:
    d.rectangle((x,y,x+17,y+17),fill='#283d4c',outline='#839fa8',width=2)
    for j in range(3):d.line((x+4,y+4+j*4,x+13,y+4+j*4),fill='#89dcec',width=2)
for a,b in [((48,9),(48,87)),((9,48),(87,48))]:
    d.line((*a,*b),fill='#273d4a',width=10);d.line((*a,*b),fill='#64b4d1',width=3)
for r,c,o in [(29,'#213440','#b3c4c7'),(23,'#40596a','#70b9cb'),(16,'#143b53','#89dcec'),(9,'#5396b3','#c1f4f6')]:
    d.ellipse((48-r,48-r,48+r,48+r),fill=c,outline=o,width=2)
for x,y in [(7,7),(88,7),(7,88),(88,88)]:d.rectangle((x-2,y-2,x+2,y+2),fill='#d7dee0')
p=Path(__file__).resolve().parents[1]/'sprites/blocks/effect/nano-suppressor.png';p.parent.mkdir(parents=True,exist_ok=True);im.save(p)

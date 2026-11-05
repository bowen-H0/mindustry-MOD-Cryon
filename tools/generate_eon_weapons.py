"""Compact palette-matched mounts for the new Eon hull. Does not alter hull/cell art."""
from pathlib import Path
from PIL import Image, ImageDraw

out=Path('sprites/units')
light='#7f7b8f'; dark='#302e3b'; violet='#993fde'; deep='#7c2eb5'
for name,w,h in [('main',44,112),('rail',38,82),('gravity',32,38),('bomb',32,42)]:
    im=Image.new('RGBA',(w,h));d=ImageDraw.Draw(im);cx=w//2;cy=h//2
    # Pivot-centered armored housing, with a narrower forward barrel.
    d.polygon([(cx-12,cy-10),(cx+12,cy-10),(w-2,cy),(cx+12,h-5),
               (cx,h-2),(cx-12,h-5),(2,cy)],fill=dark)
    d.polygon([(cx-9,cy-7),(cx+9,cy-7),(w-5,cy+1),(cx+8,h-8),
               (cx,h-5),(cx-8,h-8),(5,cy+1)],fill=light)
    if name in ('main','rail'):
        d.polygon([(cx-5,4),(cx,1),(cx+5,4),(cx+7,cy+5),(cx-7,cy+5)],fill=dark)
        d.polygon([(cx-2,5),(cx+2,5),(cx+3,cy+1),(cx-3,cy+1)],fill=violet)
        d.line((cx,6,cx,cy-3),fill=light,width=1)
        for side in [-1,1]:
            for y in range(cy+5,h-12,5):
                x=cx+side*10;d.line((x,y,x+side*3,y+2),fill=dark,width=2)
    else:
        d.polygon([(cx,3),(cx+5,9),(cx+5,cy+5),(cx-5,cy+5),(cx-5,9)],fill=dark)
        d.polygon([(cx,6),(cx+2,10),(cx+2,cy+2),(cx-2,cy+2),(cx-2,10)],fill=violet if name=='bomb' else deep)
        d.polygon([(cx,cy+4),(cx+5,cy+8),(cx,cy+12),(cx-5,cy+8)],fill=deep)
    im.save(out/f'eon-{name}.png')
print('Generated four compact Eon weapon sprite types in the hull palette.')

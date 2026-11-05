"""Legacy prototype art generator. The current hull/cell are artist-supplied assets.
Use generate_eon_weapons.py for the current mounts; explicitly opt in to regenerate
this obsolete prototype so the new hull and cell cannot be overwritten by accident.
"""
import sys
if '--legacy-prototype' not in sys.argv:
    raise SystemExit('Legacy hull generator disabled. Use generate_eon_weapons.py; --legacy-prototype explicitly overwrites hull/cell.')
from PIL import Image, ImageDraw
from pathlib import Path
out = Path('sprites/units')
S=2
hull=Image.new('RGBA',(640*S,896*S)); d=ImageDraw.Draw(hull)
def poly(points, fill, outline='#111923', width=3):
    d.polygon([(int(x*S),int(y*S)) for x,y in points],fill=fill)
    if outline: d.line([(int(x*S),int(y*S)) for x,y in points+[points[0]]],fill=outline,width=width*S)
def sym(points,fill,outline='#111923'):
    poly(points,fill,outline)
    poly([(640-x,y) for x,y in points],fill,outline)
# Separated swept wings, broad armored shoulders and a spearhead bow.
sym([(318,55),(247,167),(232,304),(94,376),(26,632),(66,740),(174,661),(202,824),(279,855),(321,762)],'#303e50')
sym([(295,170),(258,248),(258,369),(115,426),(56,620),(89,652),(210,548),(226,726),(280,784),(300,674)],'#65778a')
sym([(249,381),(127,441),(82,594),(153,552),(222,487)],'#8999a7')
sym([(101,605),(54,690),(74,714),(168,633),(173,569)],'#435368')
sym([(234,539),(213,678),(238,758),(272,780),(263,588)],'#485970')
poly([(320,31),(279,176),(286,385),(263,512),(282,749),(320,823),(358,749),(377,512),(354,385),(361,176)],'#8797a7')
poly([(320,67),(302,185),(308,360),(293,452),(320,493),(347,452),(332,360),(338,185)],'#b7c2ca')
poly([(320,144),(312,235),(315,350),(320,379),(325,350),(328,235)],'#c9b5ff',None)
poly([(320,495),(291,544),(301,677),(320,723),(339,677),(349,544)],'#1d2638')
poly([(320,520),(304,555),(311,646),(320,667),(329,646),(336,555)],'#9b85de')
poly([(320,543),(314,562),(320,629),(326,562)],'#f5eaff',None)
sym([(115,467),(103,497),(196,450),(230,434),(234,420)],'#bba5ff',None)
sym([(237,611),(229,662),(249,716),(255,697)],'#bba5ff',None)
sym([(262,787),(272,819),(286,801),(278,770)],'#c9b6ff',None)
# Panel ribs.
for y in range(370,710,48):
    sym([(273,y),(286,y+10),(285,y+24),(272,y+13)],'#202b3c')
# Layered armor decks, machinery trenches and outboard drive nacelles.
sym([(127,659),(180,660),(210,735),(190,851),(121,858),(101,800)],'#1d2839')
sym([(133,675),(165,675),(188,740),(173,813),(125,813),(116,780)],'#6e7e92')
sym([(125,817),(174,817),(170,850),(130,850)],'#141d2c')
sym([(133,821),(143,821),(143,847),(133,847)],'#d1c5ff',None)
sym([(151,821),(163,821),(163,847),(151,847)],'#a590ff',None)
# Stepped, overlapping wing plates create volume rather than a single flat triangle.
for pts,col in [([(102,449),(221,395),(230,420),(119,475)],'#a4b4c1'),
                ([(84,489),(216,431),(219,461),(74,546)],'#56677d'),
                ([(74,560),(218,479),(217,506),(91,595)],'#a1aebe'),
                ([(99,604),(210,526),(211,552),(111,631)],'#405069')]:
    sym(pts,col)
# Recessed heat exchangers and luminous bus lines.
for j in range(8):
    y=493+j*10
    sym([(110,y),(154,y-20),(158,y-12),(114,y+8)],'#182335')
    sym([(113,y+2),(150,y-15),(151,y-12),(114,y+5)],'#76879b',None)
sym([(176,424),(224,401),(225,407),(178,431)],'#d9c6ff',None)
sym([(91,624),(183,561),(187,567),(96,635)],'#9176da',None)
# Raised central citadel, double spinal rails and armored bridge.
poly([(320,250),(292,307),(292,397),(306,433),(334,433),(348,397),(348,307)],'#111c2d')
poly([(320,267),(301,311),(301,394),(311,413),(329,413),(339,394),(339,311)],'#62748c')
poly([(320,281),(309,311),(310,347),(330,347),(331,311)],'#b7cad4')
for x in [306,315,324]:
    poly([(x,321),(x+5,321),(x+5,335),(x,335)],'#b8f4ff',None)
for x in [287,345]:
    poly([(x,170),(x+8,190),(x+8,285),(x,301)],'#29364c')
    poly([(x+2,195),(x+5,204),(x+5,280),(x+2,287)],'#ab95ef',None)
# Luminous reactor in a deeply inset octagonal housing.
import math
for radius,col in [(47,'#0d1727'),(42,'#75869b'),(35,'#27334c'),(29,'#8c71cb'),(23,'#161e36')]:
    poly([(320+math.cos(math.radians(22.5+i*45))*radius,
           587+math.sin(math.radians(22.5+i*45))*radius) for i in range(8)],col)
poly([(320,563),(308,579),(309,597),(320,614),(331,597),(332,579)],'#c8b2ff')
poly([(320,571),(315,582),(320,602),(325,582)],'#f5efff',None)
# Mechanical ribs, fasteners and alternating bevels along the aft spine.
for j in range(8):
    y=657+j*17
    sym([(288,y),(303,y+5),(302,y+12),(286,y+6)],'#9ba8b5')
    sym([(278,y-8),(285,y-5),(284,y+2),(276,y-1)],'#202c40')
for x,y in [(244,350),(211,405),(85,615),(206,702),(262,790)]:
    for xx in [x,640-x]:
        d.ellipse(((xx-3)*S,(y-3)*S,(xx+3)*S,(y+3)*S),fill='#131d2c')
        d.ellipse(((xx-1)*S,(y-2)*S,(xx+2)*S,(y+1)*S),fill='#a8b2bf')
# 256 x 360 world-unit silhouette at Mindustry's normal quarter-scale rendering.
hull.resize((1024,1440),Image.Resampling.LANCZOS).save(out/'eon.png')
# Unit cells are intentionally confined to the reactor core.
cell=Image.new('RGBA',(640,896));cd=ImageDraw.Draw(cell)
cd.polygon([(320,555),(311,575),(320,618),(329,575)],fill='#ffffff')
cell.resize((1024,1440),Image.Resampling.LANCZOS).save(out/'eon-cell.png')
for name,w,h,color in [('main',96,184,'#d5c5ff'),('rail',62,126,'#d8f7ff'),('gravity',72,78,'#a590ec'),('bomb',68,86,'#eee4ff')]:
    im=Image.new('RGBA',(w,h));dr=ImageDraw.Draw(im);cx=w//2
    dr.polygon([(cx,2),(cx+9,18),(cx+10,h//2),(w-5,h//2+10),(w-2,h-15),(cx,h-2),(2,h-15),(5,h//2+10),(cx-10,h//2),(cx-9,18)],fill='#536579',outline='#172131',width=3)
    dr.polygon([(cx-5,10),(cx+5,10),(cx+5,h-20),(cx-5,h-20)],fill=color)
    dr.polygon([(cx,h//2+7),(cx+15,h//2+23),(cx,h-11),(cx-15,h//2+23)],fill='#222d40',outline=color,width=2)
    dr.ellipse((cx-5,h-33,cx+5,h-23),fill=color)
    # Barrel grooves, bearing races, heat sinks and white-hot muzzle capacitors.
    for side in [-1,1]:
        xx=cx+side*11
        dr.line((xx,20,xx,h//2-3),fill='#152139',width=3)
        for yy in range(h//2+12,h-18,7):
            dr.line((cx+side*17,yy,cx+side*24,yy+3),fill='#a8b9c8',width=2)
    dr.rectangle((cx-3,4,cx+3,18),fill='#f5edff')
    im=im.resize((round(w*1.35),round(h*1.35)),Image.Resampling.LANCZOS)
    im.save(out/f'eon-{name}.png')
print('Generated Eon hull, cell and four mount sprite types.')

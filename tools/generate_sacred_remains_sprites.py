"""Add missing Sacred Remains pixel assets. Never overwrite user artwork."""
from pathlib import Path
from PIL import Image, ImageDraw
import random

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'sprites/units/aravis/sacred-remains'
OUT.mkdir(parents=True, exist_ok=True)
DARK, FLESH, MUSCLE, PALE, BONE = '#302533', '#a6687c', '#713e57', '#ca8c98', '#dfc6ad'
STEEL, PLATE, LIGHT, VOID, PURPLE, ICHOR = '#494457', '#777180', '#b0a6b6', '#211c2a', '#c49aff', '#e0b568'
rng = random.Random(584103)

def save(name, im):
    path = OUT / (name + '.png')
    if path.exists():
        print('KEEP', path.name)
        return
    with path.open('xb') as stream:
        im.save(stream, format='PNG')
    print('CREATE', path.name)

def canvas(size):
    im = Image.new('RGBA', size)
    return im, ImageDraw.Draw(im)

im, d = canvas((256, 224))
# An off-center, torn organic silhouette. Different lobes avoid a symmetric vehicle shape.
d.polygon([(101,10),(124,26),(140,17),(160,31),(191,24),(209,45),(235,60),(239,96),(226,112),(245,133),(228,155),(230,182),(197,195),(174,213),(151,198),(130,217),(104,196),(76,202),(63,182),(33,178),(20,150),(25,126),(10,103),(32,76),(29,50),(57,41),(72,18)], fill=DARK)
for box in [(30,46,123,142),(56,20,143,102),(18,82,117,176),(67,110,165,207),(110,51,189,171),(129,142,209,209)]:
    d.ellipse(box, fill=MUSCLE, outline=DARK, width=4)
    x,y,x2,y2=box
    d.ellipse((x+6,y+5,x2-8,y2-7), fill=FLESH, outline=PALE, width=2)
# Muscular ridges and folds: deliberate strips rather than random noisy pixels.
for i in range(11):
    y=47+i*12
    x=35+(i%3)*8
    d.line([(x,y),(x+15,y-7),(x+41,y+4),(x+66,y)], fill=MUSCLE, width=5)
    d.line([(x+2,y-2),(x+18,y-6),(x+42,y+2)], fill=PALE, width=2)
# Exposed rib-like arcs and a leaking seam connect flesh to machinery.
for i in range(6):
    y=66+i*18
    d.line([(106,y),(123,y-9),(145,y-4),(154,y+4)], fill=DARK, width=10)
    d.line([(106,y-2),(124,y-10),(144,y-5),(154,y+2)], fill=BONE, width=5)
# The mechanical half is a staggered harness grafted onto the organism.
d.polygon([(149,24),(188,31),(202,52),(228,61),(229,100),(213,119),(233,141),(220,176),(193,188),(161,175),(149,146),(158,120),(143,97)], fill=STEEL, outline=DARK, width=5)
for poly in [[(157,32),(185,37),(194,58),(181,78),(151,66)],[(192,69),(223,72),(222,101),(207,113),(182,95)],[(166,101),(190,100),(205,126),(192,154),(158,141)],[(201,141),(225,149),(214,172),(196,180),(186,163)]]:
    d.polygon(poly, fill=PLATE, outline=VOID, width=3)
    d.line(poly[:2], fill=LIGHT, width=2)
for x,y in [(172,49),(211,86),(178,123),(208,157)]:
    d.rectangle((x-9,y-4,x+9,y+4),fill=VOID)
    d.line((x-6,y,x+5,y),fill=PURPLE,width=2)
    for dx in (-12,12):d.ellipse((x+dx-2,y-2,x+dx+2,y+2),fill=LIGHT)
# Cables and staples bite into the organic half.
for y in (73,116,157):
    d.line([(183,y),(159,y+8),(136,y+4),(119,y+13)],fill=DARK,width=7)
    d.line([(183,y-1),(159,y+6),(136,y+2),(119,y+11)],fill='#9e849f',width=2)
for x,y in [(56,94),(79,153),(120,182),(139,48)]:
    d.ellipse((x-8,y-6,x+9,y+9),fill=MUSCLE,outline=DARK,width=2)
    d.ellipse((x-4,y-3,x+4,y+5),fill=ICHOR)
# An asymmetrical vent behind the mouth mount.
d.ellipse((61,36,103,75),fill=DARK,outline=BONE,width=3)
d.ellipse((70,42,96,66),fill=VOID,outline=FLESH,width=3)
save('sacred-remains',im)

for name, lower in [('sacred-remains-leg',False),('sacred-remains-leg-base',True)]:
    im,d=canvas((44,184))
    d.polygon([(16,2),(29,0),(37,30),(35,62),(43,100),(34,137),(30,181),(15,183),(10,151),(5,126),(10,97),(3,60),(9,29)],fill=DARK)
    d.polygon([(17,8),(28,7),(31,40),(27,68),(36,100),(28,136),(25,177),(18,176),(15,143),(11,119),(16,93),(10,58)],fill=FLESH)
    for i in range(12):
        y=12+i*13;x=12+(i%3)*3
        d.line([(x,y),(x+13,y+6),(x+15,y+11)],fill=MUSCLE,width=4)
        d.line([(x+2,y-2),(x+12,y+4)],fill=PALE,width=2)
    d.line([(24,13),(27,49),(23,82),(29,114),(22,169)],fill=BONE,width=3)
    for y in (34,125):
        d.polygon([(10,y),(34,y-3),(36,y+12),(12,y+17)],fill=STEEL,outline=VOID,width=2)
        d.line((13,y+3,32,y),fill=LIGHT,width=2)
    if lower:
        d.polygon([(26,60),(35,64),(31,117),(25,124)],fill=PLATE,outline=DARK,width=2)
        d.line((30,70,27,110),fill=PURPLE,width=2)
    save(name,im.transpose(Image.Transpose.ROTATE_90))
for name,sz in [('sacred-remains-joint',48),('sacred-remains-joint-base',44),('sacred-remains-foot',64),('sacred-remains-base',80)]:
    im,d=canvas((sz,sz));c=sz//2
    if name.endswith('foot'):
        d.polygon([(c,3),(c+10,18),(sz-4,sz-6),(c+7,sz-12),(c,sz-3),(c-9,sz-14),(3,sz-6),(c-11,20)],fill=DARK)
        d.polygon([(c,10),(c+6,23),(sz-12,sz-11),(c+4,sz-18),(c,sz-9),(c-6,sz-19),(11,sz-12),(c-6,25)],fill=FLESH)
        for x in (9,c,sz-9):d.line((c,c,x,sz-8),fill=BONE,width=3)
    else:
        d.ellipse((2,2,sz-3,sz-3),fill=DARK)
        d.ellipse((6,5,sz-7,sz-8),fill=MUSCLE,outline=PALE,width=3)
        d.arc((9,9,sz-10,sz-10),240,70,fill=BONE,width=5)
        d.ellipse((c-7,c-6,c+7,c+8),fill=STEEL,outline=LIGHT,width=2)
        d.ellipse((c-2,c-2,c+3,c+3),fill=PURPLE)
    save(name,im)
im,d=canvas((64,64))
d.polygon([(19,4),(42,7),(55,24),(48,47),(33,59),(17,52),(8,29)],fill=DARK)
d.ellipse((14,9,49,52),fill=FLESH,outline=BONE,width=3)
d.ellipse((21,16,43,42),fill=MUSCLE,outline=PALE,width=3)
d.ellipse((25,19,39,37),fill=VOID)
d.line((26,40,30,53),fill=ICHOR,width=4)
save('sacred-remains-mouth',im)

# Database/HUD icons are separate missing-only assets.
for filename, status in [('sprites/liquids/ichor.png', False), ('sprites/status/ichor-corrosion.png', True)]:
    path = ROOT / filename
    if path.exists():
        print('KEEP', path.name)
        continue
    path.parent.mkdir(parents=True, exist_ok=True)
    icon, pen = canvas((32, 32))
    pen.polygon([(16, 2), (25, 14), (28, 23), (23, 29), (10, 29), (4, 23), (7, 14)], fill=ICHOR, outline=DARK, width=2)
    pen.line([(13, 12), (9, 20), (11, 24)], fill='#f6dfab', width=3)
    if status:
        pen.line((3, 29, 29, 29), fill=ICHOR, width=2)
        pen.line((20, 12, 16, 20, 22, 20, 17, 27), fill=DARK, width=2)
    with path.open('xb') as stream:
        icon.save(stream, format='PNG')
    print('CREATE', path.name)

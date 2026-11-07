"""Original pixel hatch sprites matching the existing nuclear arbiter's steel/orange palette.
Creates separate moving components; the existing block base is not modified.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageOps
out=Path(__file__).resolve().parents[1]/'sprites/blocks/effect'
well=Image.new('RGBA',(96,96));d=ImageDraw.Draw(well)
d.polygon([(16,1),(79,1),(94,16),(94,79),(79,94),(16,94),(1,79),(1,16)],fill='#5c6e7e',outline='#293641',width=2)
d.polygon([(21,7),(74,7),(88,21),(88,74),(74,88),(21,88),(7,74),(7,21)],fill='#28343e',outline='#99a9b8',width=2)
d.polygon([(24,13),(71,13),(82,24),(82,71),(71,82),(24,82),(13,71),(13,24)],fill='#131c25',outline='#46545f',width=3)
d.polygon([(29,21),(66,21),(74,29),(74,66),(66,74),(29,74),(21,66),(21,29)],fill='#0d151e')
for y in (9,85):
    d.line((20,y,75,y),fill='#18242d',width=3)
    for x in range(22,75,10):d.line((x,y-1,x+3,y+1),fill='#a4b2bc',width=1)
for x,y in [(12,12),(83,12),(12,83),(83,83)]:d.rectangle((x-1,y-1,x+1,y+1),fill='#d1d9df')
well.save(out/'disarmament-hatch.png')
leaf=Image.new('RGBA',(42,84));d=ImageDraw.Draw(leaf)
d.polygon([(4,0),(41,0),(41,83),(4,83),(0,79),(0,4)],fill='#536574',outline='#25333e',width=2)
d.line((4,3,38,3),fill='#bdcbd6',width=2);d.line((3,5,3,78),fill='#9caeba',width=2)
d.rectangle((7,8,34,75),fill='#748797',outline='#334653',width=2)
d.polygon([(10,11),(30,11),(32,16),(32,67),(28,72),(10,72)],fill='#607583')
for y in (19,32,48,61):
    d.line((10,y,30,y),fill='#3d515e',width=2)
    d.line((10,y+2,30,y+2),fill='#91a3b1',width=1)
d.rectangle((35,7,39,76),fill='#263743')
for y in (12,22,54,64):d.rectangle((36,y,38,y+6),fill='#ed9a77')
d.rectangle((27,36,32,46),fill='#253943',outline='#b4c2ca',width=1)
d.line((29,38,29,44),fill='#edb18c',width=1)
for x,y in [(10,10),(30,10),(10,73),(30,73)]:d.rectangle((x,y,x+1,y+1),fill='#d1dbe0')
leaf.save(out/'disarmament-door-left.png');ImageOps.mirror(leaf).save(out/'disarmament-door-right.png')

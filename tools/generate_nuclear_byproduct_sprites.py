"""Create missing nuclear-byproduct assets only; never overwrite existing art."""
from pathlib import Path
from PIL import Image, ImageDraw
root=Path(__file__).resolve().parents[1]/'sprites'
path=root/'items/nuclear-byproduct.png'
if not path.exists():
    im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im)
    d.polygon([(7,5),(24,5),(28,10),(28,24),(23,28),(7,28),(3,23),(3,10)],fill='#303b40')
    d.rectangle((7,8,24,25),fill='#7e8c72',outline='#c3cb9c',width=2)
    d.rectangle((9,10,22,12),fill='#d0d9ab');d.rectangle((9,22,22,24),fill='#46534d')
    d.ellipse((12,14,19,21),fill='#d8e984')
    d.polygon([(16,17),(10,17),(12,13)],fill='#253b36')
    d.polygon([(16,17),(19,12),(22,17)],fill='#253b36')
    d.polygon([(16,17),(20,23),(12,23)],fill='#253b36')
    d.ellipse((14,15,17,18),fill='#eff7bc')
    path.parent.mkdir(parents=True,exist_ok=True);im.save(path);print('Created',path.name)
path=root/'blocks/RBMK/nuclear-byproduct-collector.png'
if not path.exists():
    im=Image.new('RGBA',(64,64));d=ImageDraw.Draw(im)
    d.polygon([(10,3),(53,3),(61,11),(61,52),(52,61),(11,61),(3,53),(3,11)],fill='#253139')
    d.rectangle((8,8,55,55),fill='#75878b',outline='#b3c3b7',width=2)
    d.rectangle((15,14,48,48),fill='#34474e',outline='#26333c',width=3)
    d.ellipse((19,18,44,43),fill='#a8bc76',outline='#d4ddb7',width=2)
    d.ellipse((25,24,38,37),fill='#354a43')
    for a,b,c in [[(31,27),(25,18),(38,18)],[(35,31),(44,25),(44,38)],[(29,35),(22,43),(36,43)]]:
        d.polygon([a,b,c],fill='#4e6751')
    d.ellipse((29,28,34,33),fill='#e0edaf')
    d.rectangle((25,3,38,10),fill='#343f42');d.rectangle((28,4,35,8),fill='#c5d3a0')
    d.rectangle((23,49,40,60),fill='#253a40');d.polygon([(26,52),(37,52),(32,57)],fill='#c9df95')
    for x in [10,50]:
        for y in [10,50]:d.rectangle((x,y,x+3,y+3),fill='#d1d6be')
    path.parent.mkdir(parents=True,exist_ok=True);im.save(path);print('Created',path.name)

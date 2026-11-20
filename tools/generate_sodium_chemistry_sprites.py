"""Generate only missing sprites for the sodium chemistry chain."""
from pathlib import Path
from PIL import Image, ImageDraw
root = Path(__file__).resolve().parents[1] / 'sprites'
for name,color in [('sodium-hydroxide','#d5e9e6'),('sodium-carbonate','#e4dcca')]:
    path=root/'items'/f'{name}.png'
    if path.exists():
        continue
    im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im)
    if name=='sodium-hydroxide':
        d.polygon([(4,20),(7,9),(15,4),(25,8),(28,20),(21,28),(11,27)],fill='#526d70')
        d.polygon([(7,19),(10,10),(15,7),(23,10),(25,20),(20,25),(12,24)],fill=color)
        d.polygon([(10,10),(15,7),(18,14),(12,19),(7,19)],fill='#f5fffa')
        d.line([(18,14),(23,10),(25,20),(20,25)],fill='#94b4af',width=2)
    else:
        d.polygon([(3,22),(6,15),(11,14),(12,7),(21,5),(27,12),(26,24),(18,29),(8,27)],fill='#7c8276')
        d.polygon([(6,21),(9,17),(14,18),(14,9),(20,8),(24,13),(23,23),(17,26),(9,24)],fill=color)
        d.polygon([(14,9),(20,8),(24,13),(18,15)],fill='#fff9ea')
        d.line([(14,18),(18,15),(23,23)],fill='#b8b6a1',width=2)
    path.parent.mkdir(parents=True,exist_ok=True);im.save(path);print('Created',path.name)
for name,size,kind in [('sodium-hydrolyzer',64,0),('soda-carbonator',64,1),('crystal-sand-furnace',96,2),('crystal-sand-sodium-extractor',96,3)]:
    path=root/'blocks/production'/f'{name}.png'
    if not path.exists():
        im=Image.new('RGBA',(size,size));d=ImageDraw.Draw(im);s=size
        d.polygon([(9,3),(s-10,3),(s-4,9),(s-4,s-10),(s-10,s-4),(9,s-4),(3,s-10),(3,9)],fill='#252d35')
        d.rectangle((8,8,s-9,s-9),fill='#67777e',outline='#a3ada8',width=2)
        d.rectangle((13,13,s-14,s-14),fill='#34434e')
        if kind==0:
            for x in [17,37]:
                d.rounded_rectangle((x,13,x+11,50),radius=4,fill='#a8b8b6',outline='#202e37',width=2)
                d.rectangle((x+3,22,x+8,40),fill='#579d9d')
            d.rectangle((26,5,37,11),fill='#b4dfe7');d.rectangle((26,52,37,58),fill='#6c9ba6')
        elif kind==1:
            d.ellipse((15,14,48,48),fill='#a3b8c1',outline='#23323e',width=3)
            d.ellipse((22,21,41,41),fill='#465866')
            d.rectangle((28,15,35,47),fill='#d0d5c3');d.rectangle((16,28,47,35),fill='#889fa7')
            d.rectangle((5,26,12,37),fill='#c5d8df');d.rectangle((51,26,58,37),fill='#d8d7c7')
        elif kind==2:
            d.rounded_rectangle((20,18,75,77),radius=10,fill='#9a9d90',outline='#202c36',width=4)
            d.rounded_rectangle((29,27,66,67),radius=6,fill='#3a3332',outline='#d0c6a7',width=3)
            d.rectangle((36,33,59,61),fill='#80694f')
            for x in [12,79]:
                for y in range(23,74,9):d.rectangle((x,y,x+4,y+5),fill='#b0b9ad')
            d.rectangle((36,7,59,16),fill='#9da69e');d.rectangle((36,79,59,88),fill='#4f473c')
        else:
            d.rounded_rectangle((16,16,39,78),radius=7,fill='#9cb4b7',outline='#23333c',width=3)
            d.rounded_rectangle((56,16,79,78),radius=7,fill='#9ca7a1',outline='#23333c',width=3)
            d.rectangle((23,29,32,64),fill='#478995')
            d.rectangle((63,29,72,64),fill='#b6d6c3')
            for y in [26,43,60]:
                d.rectangle((37,y,58,y+8),fill='#293d49')
                d.line((39,y+3,56,y+3),fill='#84b7af',width=2)
            d.rectangle((18,7,36,13),fill='#4f473c')
            d.rectangle((59,81,77,88),fill='#d7e5da')
        for x in [9,s-13]:
            for y in [9,s-13]:d.rectangle((x,y,x+3,y+3),fill='#d1d5c6')
        path.parent.mkdir(parents=True,exist_ok=True);im.save(path);print('Created',path.name)
    glow=path.with_name(f'{name}-glow.png')
    if not glow.exists():
        im=Image.new('RGBA',(size,size));d=ImageDraw.Draw(im)
        if kind==0:
            for x in [20,40]:d.rectangle((x,23,x+5,39),fill='white')
        elif kind==1:
            d.arc((20,19,43,43),30,150,fill='white',width=3);d.arc((20,19,43,43),210,330,fill='white',width=3)
        elif kind==2:
            d.rectangle((39,36,56,58),fill='white')
        else:
            d.rectangle((25,31,30,62),fill='white')
            for y in [29,46,63]:d.rectangle((40,y,55,y+2),fill='white')
        im.save(glow);print('Created',glow.name)

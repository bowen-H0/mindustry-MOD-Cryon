"""Native grid assets for grey-goo freight, clustered storage and heavy combat forms."""
from pathlib import Path
from PIL import Image, ImageDraw
import math
root=Path(__file__).resolve().parents[1]/'sprites'
def save(im,name,folder='blocks/effect'):
    p=root/folder/(name+'.png');p.parent.mkdir(parents=True,exist_ok=True);im.save(p)
def chassis(n):
    im=Image.new('RGBA',(n,n));d=ImageDraw.Draw(im);q=max(3,n//12)
    d.polygon([(q,1),(n-q-1,1),(n-2,q),(n-2,n-q-1),(n-q-1,n-2),(q,n-2),(1,n-q-1),(1,q)],fill='#263b36',outline='#13271f',width=max(2,n//40))
    d.rectangle((q,q,n-q-1,n-q-1),fill='#5c7264',outline='#a6b59e',width=max(2,n//48))
    return im,d
for name,size,kind in [('nano-warehouse',160,'store'),('nano-large-factory',160,'cargo'),('nano-combat-factory',160,'combat'),('nano-ultimate-factory',224,'ultimate')]:
    im,d=chassis(size);c=size//2
    for x,y in [(size//8,size//8),(size*5//8,size//8),(size//8,size*5//8),(size*5//8,size*5//8)]:
        w=size//4;d.rounded_rectangle((x,y,x+w,y+w),radius=4,fill='#213d2d',outline='#91b58e',width=2)
        for j in range(3):d.line((x+6,y+6+j*w//4,x+w-6,y+6+j*w//4),fill='#4ce68c',width=3)
    r=size//4
    d.regular_polygon((c,c,r),8,rotation=22.5,fill='#244332',outline='#9ace99')
    if kind=='store':
        for off in (-14,0,14):d.rectangle((c-r+10,c+off-4,c+r-10,c+off+4),fill='#a4c9a2')
    else:
        d.ellipse((c-r//2,c-r//2,c+r//2,c+r//2),fill='#427e51',outline='#8af2ab',width=3)
        d.polygon([(c,c-r//2+4),(c+r//3,c+r//3),(c-r//3,c+r//3)],fill='#a6f1b8')
        if kind=='ultimate':
            for a in range(0,360,45):
                x=c+math.cos(math.radians(a))*r;y=c+math.sin(math.radians(a))*r
                d.ellipse((x-5,y-5,x+5,y+5),fill='#c5ffe0')
    save(im,name,'blocks/units' if kind!='store' else 'blocks/effect')
im,d=chassis(32);d.line((6,16,25,16),fill='#90edab',width=6);d.polygon([(25,16),(18,9),(18,23)],fill='#c2ffcf');save(im,'grey-goo-loader','blocks/distribution')
im,d=chassis(64);d.rectangle((13,13,50,50),fill='#244b36',outline='#a7cba4',width=2);d.line((32,17,32,46),fill='#90edab',width=7);d.polygon([(32,48),(21,36),(43,36)],fill='#c2ffcf');save(im,'grey-goo-unload-point','blocks/distribution')
im=Image.new('RGBA',(64,64));d=ImageDraw.Draw(im);d.rectangle((13,13,50,50),outline='#4ce68c',width=3);save(im,'grey-goo-unload-point-top','blocks/distribution')
im,d=chassis(32);d.polygon([(5,10),(17,10),(17,5),(28,16),(17,27),(17,22),(5,22)],fill='#8ce4a4',outline='#bff5ca');save(im,'rogue-nano-unloader','blocks/distribution')
im=Image.new('RGBA',(96,96));d=ImageDraw.Draw(im);d.ellipse((16,22,80,86),fill='#35503b',outline='#a4bba0',width=3);d.rectangle((30,8,65,66),fill='#597460',outline='#c4d8b2',width=3)
for x in (36,52):d.rectangle((x,0,x+8,51),fill='#233a2d',outline='#6be794',width=2)
d.ellipse((35,53,61,79),fill='#3a8656',outline='#8bf4ae',width=2);save(im,'nano-cleaver','blocks/turrets')
im=Image.new('RGBA',(48,48));d=ImageDraw.Draw(im)
for pts in [[(18,15),(3,5),(2,27),(16,33)],[(30,15),(45,5),(46,27),(32,33)]]:d.polygon(pts,fill='#5f7669',outline='#b2c6ad');
d.ellipse((13,6,35,40),fill='#304c39',outline='#99c997',width=2);d.rectangle((18,20,30,34),fill='#2b6741',outline='#4ce68c',width=2);d.ellipse((18,9,30,17),fill='#9cf4b7');save(im,'grey-goo-carrier','units')
for name,n in [('nano-armor-breaker',128),('nano-annihilator',272)]:
    im=Image.new('RGBA',(n,n));d=ImageDraw.Draw(im);c=n//2
    for side in (-1,1):
        x=c+side*n//3
        d.rounded_rectangle((x-n//10,n//5,x+n//10,n*9//10),radius=4,fill='#3f5147',outline='#96ac93',width=3)
        for y in range(n//4,n*9//10,n//8):d.line((x-n//12,y,x+n//12,y),fill='#22382c',width=3)
    d.polygon([(c,n//12),(c+n//4,n//4),(c+n//4,n*3//4),(c,n*9//10),(c-n//4,n*3//4),(c-n//4,n//4)],fill='#6e806b',outline='#b0c4a7',width=3)
    d.ellipse((c-n//8,c-n//8,c+n//8,c+n//8),fill='#285a3a',outline='#56e891',width=3)
    d.polygon([(c-n//5,n//4),(c-n//3,0),(c-n//12,n//8),(c,n//4),(c+n//12,n//8),(c+n//3,0),(c+n//5,n//4)],fill='#9faf93',outline='#d7e2c1')
    for off in (-1,1):d.line((c+off*n//9,n//3,c+off*n//9,n*2//3),fill='#4ce68c',width=3)
    save(im,name,'units')
for name,n in [('nano-specialist-weapon',32),('nano-annihilator-weapon',56)]:
    im=Image.new('RGBA',(n,n*2));d=ImageDraw.Draw(im);d.rectangle((3,n//2,n-4,n*2-3),fill='#3d5541',outline='#a1b89a',width=2)
    for x in (n//3,n*2//3):d.rectangle((x-2,0,x+2,n*3//2),fill='#78bd8a',outline='#c5f4d0')
    save(im,name,'units')

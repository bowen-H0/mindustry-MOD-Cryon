"""LegsUnit bodies and native segmented leg/foot textures for nano combat units."""
from pathlib import Path
from PIL import Image, ImageDraw
ROOT=Path(__file__).resolve().parents[1]/'sprites/units'
def eroder_body():
    im=Image.new('RGBA',(40,40));d=ImageDraw.Draw(im)
    d.ellipse((10,8,30,35),fill='#224d34',outline='#9bd19c',width=2)
    for y in (13,19,25,31):d.line((14,y,26,y),fill='#4c8050',width=2)
    d.polygon([(12,10),(15,3),(25,3),(28,10),(24,16),(16,16)],fill='#61935e',outline='#b2d2a4')
    d.ellipse((15,6,18,9),fill='#55ff93');d.ellipse((22,6,25,9),fill='#55ff93')
    return im

def weaver_body():
    im=eroder_body();d=ImageDraw.Draw(im)
    d.ellipse((14,17,26,29),fill='#234938',outline='#b4fff0',width=2)
    d.line((16,23,24,23),fill='#83ffee',width=2);d.line((20,19,20,27),fill='#83ffee',width=2)
    return im

def heavy_body(n):
    im=Image.new('RGBA',(n,n));d=ImageDraw.Draw(im);c=n//2
    for side in (-1,1):
        for y in (n//3,n//2,n*2//3):
            x=c+side*n//4;r=max(3,n//24)
            d.ellipse((x-r,y-r,x+r,y+r),fill='#3e5b43',outline='#a6c2a0',width=2)
    d.polygon([(c,n//12),(c+n//4,n//4),(c+n//4,n*3//4),(c,n*9//10),(c-n//4,n*3//4),(c-n//4,n//4)],fill='#6e806b',outline='#b0c4a7',width=3)
    d.ellipse((c-n//8,c-n//8,c+n//8,c+n//8),fill='#285a3a',outline='#56e891',width=3)
    d.polygon([(c-n//5,n//4),(c-n//3,0),(c-n//12,n//8),(c,n//4),(c+n//12,n//8),(c+n//3,0),(c+n//5,n//4)],fill='#9faf93',outline='#d7e2c1')
    for off in (-1,1):d.line((c+off*n//9,n//3,c+off*n//9,n*2//3),fill='#4ce68c',width=3)
    return im

def legs(name,width):
    ROOT.mkdir(parents=True,exist_ok=True)
    # Native leg textures are stretched along X; their height sets rendered limb thickness.
    for suffix,shade in [('leg','#6e8971'),('leg-base','#486950')]:
        im=Image.new('RGBA',(48,width));d=ImageDraw.Draw(im)
        d.polygon([(1,width//2),(8,1),(39,1),(47,width//2),(39,width-2),(8,width-2)],fill=shade,outline='#a3c39c')
        d.line((9,width//2,39,width//2),fill='#55b77b',width=max(1,width//5))
        im.save(ROOT/f'{name}-{suffix}.png')
    for suffix,scale in [('joint',1),('joint-base',1),('foot',1.5)]:
        size=max(8,int(width*scale));im=Image.new('RGBA',(size,size));d=ImageDraw.Draw(im)
        d.regular_polygon((size/2,size/2,size/2-1),6,fill='#46684c',outline='#a6ca9f')
        d.ellipse((size*.34,size*.34,size*.66,size*.66),fill='#63d992')
        im.save(ROOT/f'{name}-{suffix}.png')

def main():
    eroder_body().save(ROOT/'nano-eroder.png')
    weaver_body().save(ROOT/'nano-weaver.png');legs('nano-weaver',6)
    for name,n in [('nano-armor-breaker',128),('nano-annihilator',272)]:heavy_body(n).save(ROOT/f'{name}.png')
    for name,width in [('nano-eroder',8),('nano-armor-breaker',16),('nano-annihilator',28)]:legs(name,width)
if __name__=='__main__':main()

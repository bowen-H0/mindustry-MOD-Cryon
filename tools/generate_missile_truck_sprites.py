"""Original Cryon missile carrier sprites: blue armour, gold sensors, five distinct launcher silhouettes."""
from pathlib import Path
from PIL import Image, ImageDraw
import math
ROOT = Path(__file__).resolve().parents[1] / 'sprites/units/missile-trucks'
ROOT.mkdir(parents=True, exist_ok=True)
for tier, (name,n) in enumerate(zip(['jinwu','xihe','fusang','yaoling','zhulong'],[56,80,112,160,224])):
    def image(): return Image.new('RGBA',(n,n))
    def save(im,suffix=''): im.save(ROOT / (name+suffix+'.png'))
    def rect(d,box,fill,outline=None,w=1): d.rectangle(tuple(round(v) for v in box),fill,outline,width=w)
    c=n/2; q=max(1,n//40)
    im=image();d=ImageDraw.Draw(im)
    # Narrow cab in front, long reinforced platform behind; tread pods stay visible.
    d.polygon([(n*.23,n*.09),(n*.39,n*.02),(n*.61,n*.02),(n*.77,n*.09),(n*.78,n*.82),(n*.67,n*.94),(n*.33,n*.94),(n*.22,n*.82)],fill='#252e40',outline='#151b29',width=q*2)
    d.polygon([(n*.28,n*.14),(n*.4,n*.07),(n*.6,n*.07),(n*.72,n*.14),(n*.72,n*.8),(n*.64,n*.88),(n*.36,n*.88),(n*.28,n*.8)],fill='#607b92',outline='#aac2d2',width=q)
    rect(d,(n*.34,n*.1,n*.66,n*.21),'#293f52','#dbe7e5',q)
    rect(d,(n*.37,n*.12,n*.63,n*.17),'#f7d775')
    for side in [.27,.68]:
        for y in [.3,.47,.64,.8]:rect(d,(n*side,n*y,n*(side+.05),n*(y+.08)),'#a6bcc6','#263548',q)
    for x in [.32,.64]:rect(d,(n*x,n*.84,n*(x+.04),n*.91),'#e29762')
    # A small team-colour plate uses the normal white team-region convention.
    team=image();td=ImageDraw.Draw(team);rect(td,(n*.44,n*.07,n*.56,n*.1),'white');save(team,'-cell')
    save(im)
    tread=image();t=ImageDraw.Draw(tread)
    for left in [.04,.77]:
        rect(t,(n*left,n*.1,n*(left+.19),n*.9),'#202734','#111827',q)
        for y in range(int(n*.13),int(n*.88),max(3,n//12)):
            rect(t,(n*(left+.02),y,n*(left+.17),y+q*2),'#607383','#9aabb1',q)
    save(tread,'-treads')
    # Animated atlas frames consumed by UnitType's native tank renderer.
    for r in range(1):
        for f in range(4):
            frame=image();fd=ImageDraw.Draw(frame)
            for left in [.04,.77]:
                rect(fd,(n*left,n*.1,n*(left+.19),n*.9),'#202734','#111827',q)
                for y in range(int(n*.1)-n//10,int(n*.9),max(3,n//12)):
                    yy=y+f*max(1,n//48)
                    if n*.12<yy<n*.87:rect(fd,(n*(left+.02),yy,n*(left+.17),yy+q*2),'#607383','#9aabb1',q)
            save(frame.crop(tuple(round(v) for v in (n*.04,n*.1,n*.23,n*.9))),f'-treads{r}-{f}')
    launcher=image();p=ImageDraw.Draw(launcher)
    p.ellipse((n*.28,n*.31,n*.72,n*.75),fill='#273344',outline='#abc1cd',width=q)
    if tier==0:
        for x in [.32,.56]:
            rect(p,(n*x,n*.24,n*(x+.12),n*.78),'#344b5e','#afcad3',q)
            rect(p,(n*(x+.025),n*.28,n*(x+.095),n*.68),'#e6be67')
            p.polygon([(n*(x+.02),n*.28),(n*(x+.06),n*.15),(n*(x+.1),n*.28)],fill='#edf0de')
    elif tier in (1,2):
        rows,cols=(2,2) if tier==1 else (4,3)
        rect(p,(n*.24,n*.22,n*.76,n*.81),'#344457','#9cb3bf',q)
        for row in range(rows):
            for col in range(cols):
                x=n*(.28+col*.44/cols);y=n*(.27+row*.49/rows);w=n*.36/cols;h=n*.39/rows
                rect(p,(x,y,x+w,y+h),'#152331','#bba26c',q)
                p.ellipse((x+w*.25,y+h*.25,x+w*.75,y+h*.75),fill='#ffe4a0',outline='#71899a',width=q)
    elif tier==3:
        p.ellipse((n*.24,n*.24,n*.76,n*.76),fill='#213140',outline='#f5d383',width=q*3)
        p.ellipse((n*.38,n*.38,n*.62,n*.62),fill='#b2c9d2',outline='#e5b962',width=q)
        for i in range(32):
            a=i*math.tau/32;x=c+math.cos(a)*n*.2;y=c+math.sin(a)*n*.2
            p.ellipse((x-q,y-q,x+q,y+q),fill='#ffe6a0')
        for x in [.28,.64]:rect(p,(n*x,n*.73,n*(x+.08),n*.84),'#a4c1cd','#f6d487',q)
    else:
        rect(p,(n*.23,n*.25,n*.77,n*.84),'#283247','#b3bdcb',q)
        p.polygon([(c,n*.1),(n*.63,n*.33),(n*.63,n*.75),(n*.57,n*.85),(n*.43,n*.85),(n*.37,n*.75),(n*.37,n*.33)],fill='#e6e9e4',outline='#2a293b',width=q*2)
        p.polygon([(c,n*.1),(c,n*.8),(n*.57,n*.85),(n*.63,n*.75),(n*.63,n*.33)],fill='#303044')
        p.ellipse((n*.43,n*.43,n*.57,n*.57),fill='#f8d778',outline='#faf7df',width=q)
        for x in [.27,.68]:rect(p,(n*x,n*.32,n*(x+.05),n*.76),'#d3b777')
    launcher.save(ROOT/(name+'-launcher.png'))
print('Generated five missile carrier bodies, launchers, cells and native tread frames.')

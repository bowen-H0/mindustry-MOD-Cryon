"""Extend the existing tank art system; only write missing sprite files."""
from pathlib import Path
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parents[1] / 'sprites/units/aravis/siege'
outline, dark, armor, light = '#282333', '#393747', '#777387', '#b8b3c7'
accent, lamp = '#d5aa72', '#ffe3ac'

def save(image, name):
    root.mkdir(parents=True, exist_ok=True)
    path = root / (name + '.png')
    if path.exists():
        print('Preserved', path.name)
    else:
        with path.open('xb') as stream:
            image.save(stream, format='PNG')

for tier, (kind, n) in enumerate(zip(('abrade', 'rattle', 'usurp', 'deter', 'overbear'), (48, 68, 92, 124, 168)), 1):
    name = 'aravis-' + kind
    def rect(draw, box, fill, edge=None, width=1):
        draw.rectangle(tuple(round(v*n) for v in box), fill=fill, outline=edge, width=width)
    def poly(draw, points, fill, edge=outline, width=2):
        draw.polygon([(round(x*n), round(y*n)) for x,y in points], fill=fill, outline=edge, width=width)
    treads = Image.new('RGBA', (n,n))
    td = ImageDraw.Draw(treads)
    for x in (.06,.74):
        rect(td,(x,.16,x+.20,.84),dark,outline,2)
        for y in range(round(n*.18),round(n*.83),max(4,n//18)):
            td.rectangle((round((x+.025)*n),y,round((x+.175)*n),y+max(2,n//40)), fill='#6b6a78')
    save(treads,name+'-treads')
    body = Image.new('RGBA',(n,n)); d = ImageDraw.Draw(body)
    poly(d,[(.24,.11),(.76,.11),(.87,.30),(.84,.80),(.72,.91),(.28,.91),(.16,.80),(.13,.30)],armor)
    poly(d,[(.31,.16),(.69,.16),(.76,.34),(.72,.77),(.28,.77),(.24,.34)],dark,light,1)
    for x in (.17,.73):
        rect(d,(x,.26,x+.10,.77),armor,outline)
        for j in range(tier+1):
            y=.30+j*.4/(tier+1)
            rect(d,(x+.02,y,x+.08,y+.025),accent)
    rect(d,(.35,.73,.65,.85),armor,outline)
    for x in (.39,.47,.55):rect(d,(x,.77,x+.04,.82),dark)
    for x in (.28,.66):rect(d,(x,.17,x+.05,.21),lamp)
    save(body,name)
    cell = Image.new('RGBA',(n,n)); cd=ImageDraw.Draw(cell)
    rect(cd,(.44,.65,.56,.69),'white')
    save(cell,name+'-cell')
    gun = Image.new('RGBA',(n,n)); gd=ImageDraw.Draw(gun)
    # Heavier tiers gain more turret armor and longer barrels; T4 uses twin guns.
    poly(gd,[(.36,.33),(.64,.33),(.72,.47),(.66,.65),(.34,.65),(.28,.47)],armor,outline)
    poly(gd,[(.42,.36),(.58,.36),(.64,.48),(.58,.59),(.42,.59),(.36,.48)],light,dark,1)
    for center in ((.37,.63) if tier==4 else (.5,)):
        half=.045 if tier<4 else .035
        rect(gd,(center-half,.035,center+half,.47),dark,outline)
        rect(gd,(center-half+.012,.055,center+half-.012,.38),light)
        rect(gd,(center-half-.01,.025,center+half+.01,.10),armor,outline)
        rect(gd,(center-half+.008,.025,center+half-.008,.047),dark)
    if tier==5:
        for x in (.31,.63):rect(gd,(x,.41,x+.06,.59),accent,outline)
    rect(gd,(.46,.48,.54,.55),accent,outline)
    save(gun,name+'-weapon')
    icon=treads.copy();icon.alpha_composite(body);icon.alpha_composite(gun)
    if tier==5:
        aux=Image.new('RGBA',(48,48)); ad=ImageDraw.Draw(aux)
        ad.ellipse((10,17,38,43),fill=armor,outline=outline,width=2)
        ad.rectangle((20,1,28,31),fill=light,outline=outline,width=2)
        save(aux,name+'-aux')
        for x in (.19,.81):icon.alpha_composite(aux,(round(n*x)-24,round(n*.62)-24))
    save(icon,name+'-full')
print('Siege tank sprites ready; existing artwork preserved')

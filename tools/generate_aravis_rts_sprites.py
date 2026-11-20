"""Extend the project's pixel sprite pipeline. Existing artwork is never overwritten."""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
UNITS = ROOT / "sprites/units/aravis/rts"
BLOCKS = ROOT / "sprites/blocks/units/aravis"
OUTLINE, DARK, ARMOR, LIGHT = "#282333", "#393747", "#777387", "#b8b3c7"
PURPLE, GLOW, GLASS = "#a27ce5", "#d8c1ff", "#343f56"
created = 0


def save_missing(image, directory, name):
    global created
    directory.mkdir(parents=True, exist_ok=True)
    path = directory / (name + ".png")
    if path.exists():
        print("KEEP", path.relative_to(ROOT))
        return
    with path.open("xb") as stream:
        image.save(stream, format="PNG")
    created += 1


def canvas(n):
    image = Image.new("RGBA", (n, n))
    return image, ImageDraw.Draw(image)


def polygon(draw, n, points, fill=ARMOR, outline=OUTLINE, width=2):
    draw.polygon([(round(x*n), round(y*n)) for x, y in points], fill=fill, outline=outline, width=width)


def rect(draw, n, box, fill, outline=None, width=1):
    draw.rectangle(tuple(round(value*n) for value in box), fill=fill, outline=outline, width=width)


def unit(kind, n):
    name = "aravis-" + kind
    image, draw = canvas(n)
    if kind in ("violet-falcon", "needlewing"):
        # Swept interceptor versus short, cheap attack-plane wings.
        wing = .08 if kind == "violet-falcon" else .12
        polygon(draw, n, [(.5,.04),(.61,.36),(1-wing,.67),(.91,.77),(.62,.62),(.65,.88),(.55,.83),(.5,.96),(.45,.83),(.35,.88),(.38,.62),(.09,.77),(wing,.67),(.39,.36)])
        for left in (.18,.72):
            rect(draw,n,(left,.62,left+.1,.75),DARK,LIGHT)
            rect(draw,n,(left+.02,.65,left+.08,.73),PURPLE)
        polygon(draw,n,[(.5,.1),(.56,.39),(.54,.72),(.46,.72),(.44,.39)],LIGHT)
        polygon(draw,n,[(.5,.22),(.55,.4),(.5,.49),(.45,.4)],GLASS,PURPLE,1)
        for left in (.39,.56):
            rect(draw,n,(left,.76,left+.05,.87),GLOW)
    elif kind == "ash-kite":
        polygon(draw,n,[(.42,.12),(.58,.12),(.65,.35),(.61,.65),(.54,.75),(.53,.94),(.47,.94),(.46,.75),(.39,.65),(.35,.35)])
        rect(draw,n,(.29,.42,.71,.53),DARK,LIGHT)
        for left in (.23,.68):
            rect(draw,n,(left,.36,left+.09,.66),PURPLE,OUTLINE)
            rect(draw,n,(left+.02,.38,left+.07,.48),LIGHT)
        polygon(draw,n,[(.44,.19),(.56,.19),(.59,.35),(.41,.35)],GLASS,PURPLE,1)
        rect(draw,n,(.32,.87,.68,.91),LIGHT,OUTLINE)
        rotor, blade = canvas(96)
        polygon(blade,96,[(.08,.46),(.92,.46),(.86,.51),(.08,.54)],"#777387cc",OUTLINE,1)
        polygon(blade,96,[(.46,.08),(.54,.08),(.51,.92),(.46,.86)],"#777387cc",OUTLINE,1)
        blade.ellipse((43,43,53,53),fill=LIGHT,outline=OUTLINE,width=2)
        save_missing(rotor,UNITS,name+"-rotor")
    elif kind == "shorekeeper":
        polygon(draw,n,[(.2,.09),(.8,.09),(.92,.32),(.9,.85),(.76,.95),(.24,.95),(.1,.85),(.08,.32)],DARK)
        for left in (.1,.76):
            rect(draw,n,(left,.28,left+.14,.84),ARMOR,LIGHT,2)
            rect(draw,n,(left+.035,.35,left+.1,.75),PURPLE)
            for y in (.39,.5,.61,.72):rect(draw,n,(left+.04,y,left+.095,y+.025),GLOW)
        rect(draw,n,(.28,.24,.72,.82),"#24232c",LIGHT,2)
        for y in (.3,.41,.52,.63,.74):rect(draw,n,(.3,y,.7,y+.025),ARMOR)
        polygon(draw,n,[(.28,.09),(.72,.09),(.76,.25),(.24,.25)],LIGHT)
        rect(draw,n,(.37,.13,.63,.2),GLASS,PURPLE)
        rect(draw,n,(.32,.84,.68,.91),PURPLE,LIGHT)
    else:
        hover = kind == "hover-tank"
        polygon(draw,n,[(.3,.12),(.7,.12),(.8,.28),(.76,.83),(.65,.9),(.35,.9),(.24,.83),(.2,.28)],ARMOR)
        polygon(draw,n,[(.34,.19),(.66,.19),(.7,.32),(.66,.76),(.34,.76),(.3,.32)],LIGHT,DARK,1)
        if hover:
            for left in (.07,.77):
                polygon(draw,n,[(left,.24),(left+.12,.18),(left+.16,.35),(left+.12,.82),(left,.82)],DARK,LIGHT,1)
                rect(draw,n,(left+.03,.38,left+.1,.75),PURPLE)
                rect(draw,n,(left+.04,.45,left+.08,.65),GLOW)
        else:
            tread, td = canvas(n)
            for left in (.06,.76):
                rect(td,n,(left,.16,left+.18,.84),DARK,OUTLINE,2)
                for y in range(round(n*.2),round(n*.81),max(4,n//10)):
                    td.rectangle((round(n*(left+.02)),y,round(n*(left+.16)),y+2),fill=ARMOR)
            save_missing(tread,UNITS,name+"-treads")
        for left in (.28,.66):rect(draw,n,(left,.34,left+.05,.68),PURPLE)
        if kind == "flak-car":
            for left in (.07,.77):
                for y in (.25,.48,.71):rect(draw,n,(left,y,left+.14,y+.1),OUTLINE,ARMOR)
    cell, cd = canvas(n)
    rect(cd,n,(.46,.57,.54,.64),"white")
    save_missing(cell,UNITS,name+"-cell")
    save_missing(image,UNITS,name)
    if kind == "shorekeeper":
        return
    gun, gd = canvas(n)
    if kind in ("light-tank","hover-tank","field-artillery","flak-car"):
        gd.ellipse(tuple(round(n*v) for v in (.34,.36,.66,.68)),fill=DARK,outline=LIGHT,width=1)
        rect(gd,n,(.4,.4,.6,.64),ARMOR,LIGHT)
        if kind == "flak-car":
            for left in (.39,.54):
                rect(gd,n,(left,.18,left+.07,.52),DARK,LIGHT)
                rect(gd,n,(left,.18,left+.07,.25),PURPLE)
        else:
            top=.02 if kind=="field-artillery" else .13
            rect(gd,n,(.46,top,.54,.48),DARK,LIGHT)
            rect(gd,n,(.44,top,.56,top+.08),PURPLE,OUTLINE)
    else:
        rect(gd,n,(.47,.46,.53,.57),DARK,LIGHT)
        rect(gd,n,(.48,.4,.52,.49),PURPLE)
    save_missing(gun,UNITS,name+"-weapon")


def headquarters(air):
    n=128
    name="aravis-"+("air" if air else "army")+"-headquarters"
    image,d=canvas(n)
    rect(d,n,(.02,.02,.98,.98),OUTLINE,LIGHT,2)
    rect(d,n,(.09,.09,.91,.91),ARMOR,DARK,3)
    rect(d,n,(.2,.14,.8,.86),"#30303b",LIGHT,2)
    for left in (.1,.84):
        for y in (.13,.32,.51,.7):rect(d,n,(left,y,left+.06,y+.12),PURPLE,OUTLINE)
    if air:
        for y in (.22,.39,.56,.73):rect(d,n,(.48,y,.52,y+.08),GLOW)
        polygon(d,n,[(.5,.25),(.66,.6),(.54,.54),(.54,.7),(.46,.7),(.46,.54),(.34,.6)],PURPLE,OUTLINE,1)
    else:
        for y in (.24,.38,.52,.66):rect(d,n,(.29,y,.71,y+.035),ARMOR)
        rect(d,n,(.35,.28,.65,.72),PURPLE,LIGHT,2)
        rect(d,n,(.46,.18,.54,.46),DARK,LIGHT)
    save_missing(image,BLOCKS,name)
    top,td=canvas(n)
    for box in ((.02,.02,.98,.09),(.02,.02,.09,.98),(.91,.02,.98,.98),(.02,.91,.98,.98)):
        rect(td,n,box,DARK,LIGHT)
    for left in (.12,.78):rect(td,n,(left,.03,left+.1,.07),GLOW)
    save_missing(top,BLOCKS,name+"-top")
    out,od=canvas(n)
    for left in (.05,.87):rect(od,n,(left,.34,left+.08,.67),PURPLE,LIGHT)
    save_missing(out,BLOCKS,name+"-out")


def full_icon(kind):
    name="aravis-"+kind
    paths=[UNITS/(name+suffix+".png") for suffix in ("-treads","","-weapon","-rotor")]
    layers=[Image.open(path).convert("RGBA") for path in paths if path.exists()]
    n=max(max(layer.size) for layer in layers)
    icon=Image.new("RGBA",(n,n))
    for layer in layers:
        icon.alpha_composite(layer,((n-layer.width)//2,(n-layer.height)//2))
    save_missing(icon,UNITS,name+"-full")


if __name__ == "__main__":
    for kind,n in [("violet-falcon",64),("ash-kite",64),("needlewing",48),("shorekeeper",104),
                   ("field-artillery",64),("hover-tank",64),("light-tank",48),("flak-car",56)]:
        unit(kind,n)
        full_icon(kind)
    headquarters(False)
    headquarters(True)
    print(f"Created {created} missing RTS sprites; existing files retained.")

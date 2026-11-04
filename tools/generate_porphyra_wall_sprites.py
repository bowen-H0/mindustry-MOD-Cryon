"""Vanilla wall palette swaps, preserving source geometry, dimensions and alpha."""
from pathlib import Path
from PIL import Image, ImageDraw
ROOT=Path(__file__).resolve().parents[1]
SPECS=[('rhenium','titanium',(203,182,218)),('gamma','thorium',(128,232,177)),('neptunium','tungsten',(135,114,189))]


def recolor(image, tint):
    result=image.copy()
    colors=[]
    for r,g,b,a in image.getdata():
        lum=(r+g+b)/3/255
        if lum<0.65:
            rgb=tuple(round(c*lum/0.65) for c in tint)
        else:
            rgb=tuple(round(c+(255-c)*(lum-0.65)/0.35) for c in tint)
        colors.append(rgb+(a,))
    result.putdata(colors)
    assert result.getchannel('A').tobytes()==image.getchannel('A').tobytes()
    return result


def main():
    preview=Image.new('RGB',(768,352),'#242832')
    d=ImageDraw.Draw(preview)
    for column,(name,source,tint) in enumerate(SPECS):
        d.text((column*256+64,5),name.upper(),fill='#e0e3ea')
        for row,suffix in enumerate(['','-large']):
            image=Image.open(ROOT/f'tools/sprite-sources/vanilla/walls/{source}-wall{suffix}.png').convert('RGBA')
            result=recolor(image,tint)
            destination=ROOT/f'sprites/blocks/porphyra/porphyra-{name}-wall{suffix}.png'
            result.save(destination)
            large=result.resize((128,128),Image.Resampling.NEAREST)
            preview.paste(large,(column*256+64,row*176+24),large)
    output=ROOT/'output/porphyra-walls.png'
    output.parent.mkdir(parents=True,exist_ok=True)
    preview.save(output)


if __name__=='__main__':main()

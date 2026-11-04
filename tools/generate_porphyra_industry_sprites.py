"""Original Mindustry sprites recolored for Porphyra's trade/industry expansion."""
from pathlib import Path
from shutil import copyfile
from PIL import Image, ImageDraw
from generate_porphyra_wall_sprites import recolor
ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'tools/sprite-sources/vanilla/porphyra-industry'
VANILLA=Path('/path/to/mindustry-assets/sprites')
SPECS=[
 ('liquids/gamma-brine.png',['items/liquid-water.png'],(105,203,184)),
 ('liquids/crystal-slurry.png',['items/liquid-slag.png'],(185,104,182)),
 ('blocks/environment/porphyra/porphyra-gamma-brine1.png',['blocks/environment/shallow-water.png'],(105,203,184)),
 ('blocks/environment/porphyra/porphyra-gamma-brine-deep1.png',['blocks/environment/deep-water.png'],(46,110,108)),
 ('blocks/environment/porphyra/porphyra-crystal-slurry1.png',['blocks/environment/tainted-water.png'],(115,72,133)),
 ('blocks/environment/porphyra/porphyra-crystal-slurry-deep1.png',['blocks/environment/deep-tainted-water.png'],(92,58,112)),
 ('blocks/porphyra/porphyra-shore-pump.png',['blocks/liquid/rotary-pump.png'],(105,203,184)),
 ('items/rhenium-plate.png',['items/item-titanium.png'],(203,182,218)),
 ('blocks/porphyra/porphyra-plate-press.png',['blocks/production/pyratite-mixer.png'],(203,182,218)),
 ('items/protocol-point.png',['items/item-silicon.png'],(121,184,239)),
 ('items/laminated-rhenium.png',['items/item-graphite.png'],(180,173,201)),
 ('items/phase-crystal.png',['items/item-phase-fabric.png'],(185,240,219)),
 ('items/porphyra-alloy.png',['items/item-surge-alloy.png'],(228,185,239)),
 ('blocks/porphyra/porphyra-protocol-encoder.png',['blocks/logic/logic-processor.png'],(121,184,239)),
 ('blocks/porphyra/porphyra-trade-tower.png',['blocks/campaign/launch-pad.png'],(108,186,221)),
 ('blocks/porphyra/porphyra-rhenium-press.png',['blocks/production/plastanium-compressor.png','blocks/production/plastanium-compressor-top.png'],(180,173,201)),
 ('blocks/porphyra/porphyra-crystal-stabilizer.png',['blocks/production/phase-weaver-bottom.png','blocks/production/phase-weaver.png'],(156,226,201)),
 ('blocks/porphyra/porphyra-alloy-forge.png',['blocks/production/surge-smelter.png','blocks/production/surge-smelter-top.png'],(213,164,230)),
 ('blocks/porphyra/porphyra-plasma-condenser.png',['blocks/production/cryofluid-mixer-bottom.png','blocks/production/cryofluid-mixer.png'],(161,123,214)),
 ('blocks/porphyra/porphyra-laser-defense.png',['blocks/defense/segment.png'],(128,232,177)),
 ('blocks/porphyra/porphyra-laser-defense-base.png',['blocks/turrets/bases/block-2.png'],(164,174,196)),
]


# Two native water shades, recolored directly instead of applying the wall ramp.
# The slag cache shader injects hardcoded orange/yellow patches, so every basin
# uses the ordinary water cache and its own subdued, opaque surface palette.
FLOOR_PALETTES = {
    "porphyra-gamma-brine1.png": ((66,151,139),(79,165,148)),
    "porphyra-gamma-brine-deep1.png": ((47,112,112),(54,122,122)),
    "porphyra-crystal-slurry1.png": ((115,72,133),(127,83,146)),
    "porphyra-crystal-slurry-deep1.png": ((92,58,112),(103,67,125)),
}


def liquid_surface(image, palette):
    shades = sorted({(r,g,b) for r,g,b,a in image.getdata() if a},key=sum)
    assert len(shades)==2, "Vanilla water surface palette changed"
    mapping=dict(zip(shades,palette))
    result=image.copy()
    result.putdata([mapping.get((r,g,b),(r,g,b))+(a,) for r,g,b,a in image.getdata()])
    assert result.getchannel('A').tobytes()==image.getchannel('A').tobytes()
    return result


def main():
    SOURCE.mkdir(parents=True,exist_ok=True)
    preview=Image.new('RGB',(768,((len(SPECS)+3)//4)*212),'#242832')
    d=ImageDraw.Draw(preview)
    for index,(destination,layers,tint) in enumerate(SPECS):
        result=None
        for name in layers:
            local=SOURCE/Path(name).name
            if not local.exists():copyfile(VANILLA/name,local)
            image=Image.open(local).convert('RGBA')
            result=image if result is None else Image.alpha_composite(result,image)
        floor_palette=FLOOR_PALETTES.get(Path(destination).name)
        result=liquid_surface(result,floor_palette) if floor_palette else recolor(result,tint)
        target=ROOT/'sprites'/destination
        target.parent.mkdir(parents=True,exist_ok=True)
        result.save(target)
        if floor_palette:
            # Keep the original pixel geometry; the alternate tile reduces obvious tiling.
            result.transpose(Image.Transpose.ROTATE_180).save(target.with_name(target.stem[:-1]+'2.png'))
        cellx=(index%4)*192;celly=(index//4)*212
        large=result.resize((128,128),Image.Resampling.NEAREST)
        preview.paste(large,(cellx+32,celly+20),large)
        label=Path(destination).stem.replace('porphyra-','')
        d.text((cellx+8,celly+164),label,fill='#e4e4ed')
    out=ROOT/'output/porphyra-industry.png'
    out.parent.mkdir(parents=True,exist_ok=True)
    preview.save(out)


if __name__=='__main__':main()

"""Cryon deep-sea palette variants of native hulls, plants and geological textures."""
from pathlib import Path
from shutil import copyfile
import json
from PIL import Image, ImageDraw
ROOT=Path(__file__).resolve().parents[1]
NATIVE=Path('/path/to/mindustry-assets/sprites')
SOURCE=ROOT/'tools/sprite-sources/vanilla/deepsea-content'
TERRAIN=ROOT/'content/blocks/environment/cryon-depths'
SPRITES=ROOT/'sprites/blocks/environment/cryon-depths'
LABELS={}

def source(relative):
    p=SOURCE/relative
    if not p.exists():
        p.parent.mkdir(parents=True,exist_ok=True);copyfile(NATIVE/relative,p)
    return Image.open(p).convert('RGBA')

def recolor(image,color):
    values=[sum(p[:3])/3 for p in image.getdata() if p[3]]
    lo,hi=min(values),max(values)
    def shade(p):
        f=.5+.65*(sum(p[:3])/3-lo)/max(hi-lo,1)
        return tuple(max(0,min(255,round(c*f))) for c in color)+(p[3],)
    out=image.copy();out.putdata([shade(p) for p in image.getdata()])
    assert out.getchannel('A').tobytes()==image.getchannel('A').tobytes()
    return out

def asset(native,out,color,resize=None):
    image=recolor(source(native),color)
    if resize:image=image.resize(resize,Image.Resampling.NEAREST)
    out.parent.mkdir(parents=True,exist_ok=True);image.save(out)
    return image

def block(name,zh,en,config,base,color,variants=3,folder='environment'):
    LABELS[name]=(zh,en)
    config={'type':'Floor','variants':variants,**config}
    (TERRAIN/(name+'.hjson')).write_text(json.dumps(config,indent=2)+'\n')
    for i in (range(1,variants+1) if variants else [0]):asset(f'blocks/{folder}/{base}{i if variants else ""}.png',SPRITES/f'{name}{i if variants else ""}.png',color)

FLOORS=[
('abyss-pillow-basalt','枕状玄武岩','Pillow basalt','basalt',(61,87,99),{}),
('abyss-pressure-slate','深压板岩','Pressure slate','shale',(73,87,111),{}),
('abyss-obsidian','深海黑曜岩','Abyssal obsidian','basalt',(46,56,79),{}),
('abyss-limestone','海相石灰岩','Marine limestone','dacite',(142,159,154),{}),
('abyss-black-silt','深海黑泥','Abyssal black silt','darksand',(51,69,80),{}),
('abyss-white-clay','白色沉积黏土','White sedimentary clay','dacite',(164,176,180),{}),
('abyss-frozen-silt','冻凝沉积层','Frozen sediment','ice-snow',(126,157,175),{}),
('abyss-silica-crust','硅质硬壳','Siliceous crust','crystalline-stone',(115,151,170),{}),
('abyss-ice-crystal-floor','深海冰晶层','Abyssal ice crystals','ice',(111,178,186),{}),
('abyss-titanium-bed','富钛海底矿床','Rich titanium seabed','crystalline-stone',(113,129,184),{'itemDrop':'titanium','playerUnmineable':False}),
('abyss-nickel-bed','富镍海底矿床','Rich nickel seabed','basalt',(178,167,118),{'itemDrop':'nickel','playerUnmineable':False}),
('abyss-metal-nodules','镍质结核层','Nickel nodule bed','shale',(137,142,108),{'itemDrop':'nickel'}),
]
WALLS=[
('abyss-basalt-wall','枕状玄武岩壁','Pillow basalt wall','stone-wall',(61,87,99)),
('abyss-slate-wall','深压板岩壁','Pressure slate wall','shale-wall',(73,87,111)),
('abyss-limestone-wall','海相石灰岩壁','Marine limestone wall','dacite-wall',(142,159,154)),
('abyss-ice-wall','深海冰晶壁','Abyssal ice wall','ice-wall',(111,178,186)),
('abyss-nodule-wall','金属结核岩壁','Metal nodule wall','shale-wall',(137,142,108)),
]
PLANTS=[
('abyss-glass-kelp','玻璃海带','Glass kelp','redweed',(110,196,197),'Seaweed',3),
('abyss-ribbon-kelp','暗纹带藻','Dark ribbon kelp','redweed',(57,119,128),'Seaweed',3),
('abyss-lantern-weed','灯笼藻','Lantern weed','redweed',(134,139,207),'Seaweed',3),
('abyss-frost-fan','霜羽海扇','Frost sea fan','pur-bush',(133,192,212),'SeaBush',0),
('abyss-glow-anemone','辉光海葵','Glowing anemone','yellowcoral',(99,192,174),'SeaBush',0),
('abyss-lantern-coral','灯笼珊瑚','Lantern coral','yellowcoral',(204,160,120),'SeaBush',0),
('abyss-tube-sponge','深压管海绵','Pressure tube sponge','spore-cluster',(126,152,153),'Prop',3),
('abyss-ice-fern','冰晶海蕨','Ice-crystal sea fern','crystal-cluster',(119,181,212),'Prop',3),
]
PROPS=[
('abyss-basalt-boulder','深海玄武岩块','Abyssal basalt boulder','basalt-boulder',(61,87,99),2),
('abyss-nickel-nodule','镍质结核','Nickel nodule','shale-boulder',(178,167,118),2),
('abyss-titanium-crystal','钛质晶簇','Titanium crystal cluster','crystal-cluster',(113,129,184),3),
('abyss-brine-crystal','盐卤晶簇','Brine crystal cluster','crystal-cluster',(144,194,178),3),
('abyss-rift-spire','深海裂隙石柱','Abyssal rift spire','crystal-blocks',(64,94,120),3),
]

def main():
    TERRAIN.mkdir(parents=True,exist_ok=True);SPRITES.mkdir(parents=True,exist_ok=True)
    for name,zh,en,base,color,config in FLOORS:
        block(name,zh,en,{'cacheLayer':'normal','itemDrop':'abyssal-sediment','playerUnmineable':False,**config},base,color)
    for name,zh,en,base,color in WALLS:block(name,zh,en,{'type':'StaticWall'},base,color,variants=2)
    for name,zh,en,base,color,kind,n in PLANTS:
        config={'type':kind,'solid':False,'breakable':True}
        if kind=='SeaBush':config.update(lobesMin=4,lobesMax=6,magMin=3,magMax=9)
        block(name,zh,en,config,base,color,variants=n,folder='environment' if base=='yellowcoral' else 'props')
        if kind=='SeaBush':
            for suffix in ['-bot','-center']:
                relative=f'blocks/{"environment" if base=="yellowcoral" else "props"}/{base}{suffix}.png'
                if (NATIVE/relative).exists():asset(relative,SPRITES/f'{name}{suffix}.png',color)
    for name,zh,en,base,color,n in PROPS:block(name,zh,en,{'type':'Prop'},base,color,variants=n,folder='props')
    # Keep the old floor name loadable for existing saves, without vent attributes or effects.
    for i in range(1,4):asset(f'blocks/environment/basalt{i}.png',SPRITES/f'abyss-vent{i}.png',(61,87,99))
    for name,base,color in [('minimus','retusa',(88,155,167)),('danae','oxynoe',(82,146,163)),('pammelas','cyerce',(74,132,154)),('schmidti','aegires',(65,120,145)),('sloani','navanax',(58,111,137))]:
        asset(f'units/{base}.png',ROOT/f'sprites/units/{name}.png',color)
        p=ROOT/f'sprites/units/{name}-cell.png';p.parent.mkdir(parents=True,exist_ok=True);source(f'units/{base}-cell.png').save(p)
    asset('blocks/liquid/rotary-pump.png',ROOT/'sprites/blocks/liquid/powered-pump.png',(120,164,176),(64,64))
    asset('blocks/liquid/rotary-pump-liquid.png',ROOT/'sprites/blocks/liquid/powered-pump-liquid.png',(120,164,176),(64,64))
    asset('blocks/defense/shock-mine.png',ROOT/'sprites/blocks/defense/abyssal-mine.png',(99,181,186))
    # Reuse the existing environment icon geometry, in its own named palette.
    for name,color in [('deep-diving',(100,191,199)),('depth-pressure',(88,118,175))]:
        src=Image.open(ROOT/'sprites/status/cold-stress.png').convert('RGBA')
        recolor(src,color).save(ROOT/f'sprites/status/{name}.png')
    (ROOT/'tools/deepsea-terrain-catalog.json').write_text(json.dumps(LABELS,ensure_ascii=False,indent=2)+'\n')
    print(f'Generated {len(LABELS)} editor terrain/plant/geology types, five submarine hulls, pump and mine assets.')

if __name__=='__main__':main()

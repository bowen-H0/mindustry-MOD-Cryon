"""Validate actual sprite alpha over every turret angle and recoil state against the hull.

Reads actual loaded mounts exported by EonContentCheck. Swept volumes are checked pairwise,
so independently turning mirrored guns cannot overlap each other.
"""
from pathlib import Path
from PIL import Image, ImageChops, ImageFilter, ImageOps
import math, json, sys

# Exported from loaded Weapon instances by EonContentCheck, not inferred from Java source.
layout_path=Path(sys.argv[1] if len(sys.argv)>1 else '/tmp/cryon-eon-runtime-layout.json')
if not layout_path.exists():
    raise SystemExit('Run EonContentCheck first to export the actual loaded mount layout.')
assert layout_path.stat().st_mtime>=Path('src/cryon/Content/EonUnit.java').stat().st_mtime, 'Runtime layout is stale; rerun EonContentCheck.'
live=json.loads(layout_path.read_text())
hull_scale=live.get('hullScale',1.0) if isinstance(live,dict) else 1.0
mounts=live['mounts'] if isinstance(live,dict) else live
world_per_pixel=0.25*hull_scale
layout=[(w['kind'],w['x'],w['y'],w['recoil'],w['rotate']) for w in mounts]
hull=Image.open('sprites/units/eon.png')
opaque=hull.getchannel('A').point(lambda a:255 if a>=100 else 0)
assert len(layout)==7, f'Expected seven actual mounts, found {len(layout)}'

sweeps=[]
for kind,x,y,recoil,rotate in layout:
    raw=Image.open(f'sprites/units/eon-{kind}.png').getchannel('A').point(lambda a:255 if a>=100 else 0)
    # Include the game's three-pixel weapon outline, including space outside the source image.
    raw=ImageOps.expand(raw,border=3).filter(ImageFilter.MaxFilter(7))
    if hull_scale != 1:
        raw=raw.resize((round(raw.width/hull_scale),round(raw.height/hull_scale)),Image.Resampling.NEAREST)
    sweep=Image.new('L',hull.size)
    for degrees in (range(360) if rotate else [0]):
        tile=raw.rotate(degrees,expand=True,resample=Image.Resampling.BILINEAR)
        for kick in [0,recoil/2,recoil]:
            angle=math.radians(degrees)
            cx=hull.width/2+x/world_per_pixel+math.sin(angle)*kick/world_per_pixel
            cy=hull.height/2-y/world_per_pixel+math.cos(angle)*kick/world_per_pixel
            frame=Image.new('L',hull.size)
            frame.paste(tile,(round(cx-tile.width/2),round(cy-tile.height/2)))
            sweep=ImageChops.lighter(sweep,frame)
    outside=ImageChops.subtract(sweep,opaque).point(lambda a:255 if a>=100 else 0)
    assert outside.getbbox() is None, f'{kind} at {x},{y} extends beyond hull: {outside.getbbox()}'
    sweeps.append(sweep)
for i in range(len(sweeps)):
    for j in range(i):
        overlap=ImageChops.darker(sweeps[i],sweeps[j]).point(lambda a:255 if a>=100 else 0)
        assert overlap.getbbox() is None, f'Mount swept volumes overlap: {layout[j]} and {layout[i]}'
print('PASS: all seven mounts stay inside hull and never overlap through full rotation and maximum recoil, including outlines.')

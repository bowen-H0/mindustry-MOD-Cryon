#!/usr/bin/env python3
"""Prepare the downloaded inverted violet armored starship for a strict five-entry palette.

Usage:
    python3 tools/prepare_violet_starship.py INPUT.png OUTPUT.png

Output is always 640x896, with its original aspect ratio centered on transparent
padding. Index 0 is fully transparent; indices 1..4 are the specified opaque inks.
Edges are rebuilt as horizontal/vertical/exact-45-degree polygon segments.
No dithering, JPEG intermediates, partial alpha, or source-file overwrites.
Requires Pillow and NumPy.
"""
from __future__ import annotations

import argparse
from collections import Counter
import json
from pathlib import Path
import struct

import numpy as np
from PIL import Image, ImageOps, ImageDraw

SIZE = (640, 896)
COLORS = ('7f7b8f', '302e3b', '993fde', '7c2eb5')
INKS = np.array([tuple(bytes.fromhex(color)) for color in COLORS], dtype=np.uint8)


def oklab(rgb: np.ndarray) -> np.ndarray:
    """Compare appearance, rather than raw channel distances that confuse dark violet with gray."""
    c = rgb.astype(np.float32) / 255.0
    c = np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)
    lms = c @ np.array([[.4122214708, .2119034982, .0883024619],
                        [.5363325363, .6806995451, .2817188376],
                        [.0514459929, .1073969566, .6299787005]], dtype=np.float32)
    return np.cbrt(lms) @ np.array([[.2104542553, 1.9779984951, .0259040371],
                                   [.7936177850, -2.4285922050, .7827717662],
                                   [-.0040720468, .4505937099, -.8086757660]], dtype=np.float32)


def nearest_ink(rgb: np.ndarray) -> np.ndarray:
    lab = oklab(rgb)
    targets = oklab(INKS)
    distances = np.sum((lab[..., None, :] - targets) ** 2, axis=-1)
    return (np.argmin(distances, axis=-1) + 1).astype(np.uint8)


def png_palette_entries(path: Path) -> int:
    """Check actual PLTE entries, not just how many colors happen to be used."""
    with path.open('rb') as stream:
        if stream.read(8) != b'\x89PNG\r\n\x1a\n':
            raise ValueError('Expected PNG output')
        while header := stream.read(8):
            length, kind = struct.unpack('>I4s', header)
            if kind == b'PLTE':
                return length // 3
            stream.seek(length + 4, 1)
    raise ValueError('Indexed PNG is missing its palette')


# Octilinear contour reconstruction (no dependencies beyond Pillow and NumPy).
def area(points):
    p=np.asarray(points,dtype=np.float64)
    return float(np.sum(p[:,0]*np.roll(p[:,1],-1)-p[:,1]*np.roll(p[:,0],-1))/2)


def contours(mask):
    height,width=mask.shape
    p=np.pad(mask,1)
    edge_map={}
    def add(a,b): edge_map.setdefault(a,[]).append(b)
    for y,x in np.argwhere(mask & ~p[:-2,1:-1]): add((int(x),int(y)),(int(x+1),int(y)))
    for y,x in np.argwhere(mask & ~p[1:-1,2:]): add((int(x+1),int(y)),(int(x+1),int(y+1)))
    for y,x in np.argwhere(mask & ~p[2:,1:-1]): add((int(x+1),int(y+1)),(int(x),int(y+1)))
    for y,x in np.argwhere(mask & ~p[1:-1,:-2]): add((int(x),int(y+1)),(int(x),int(y)))
    direction={(1,0):0,(0,1):1,(-1,0):2,(0,-1):3}
    loops=[]
    while edge_map:
        start=next(iter(edge_map)); current=start; loop=[]; previous=None
        while True:
            loop.append(current)
            options=edge_map[current]
            if previous is None or len(options)==1:
                following=options.pop()
            else:
                incoming=direction[(current[0]-previous[0],current[1]-previous[1])]
                priority={1:0,0:1,3:2,2:3} # keep the filled pixel on the right, split corner touches
                index=min(range(len(options)),key=lambda i:priority[(direction[(options[i][0]-current[0],options[i][1]-current[1])]-incoming)%4])
                following=options.pop(index)
            if not options: del edge_map[current]
            previous,current=current,following
            if current==start: break
        if len(loop)>=3: loops.append(loop)
    return loops


def compact(points):
    points=[tuple(map(int,p)) for p in points]
    changed=True
    while changed and len(points)>3:
        changed=False; reduced=[]
        for i,b in enumerate(points):
            a,c=points[i-1],points[(i+1)%len(points)]
            ab=(b[0]-a[0],b[1]-a[1]); bc=(c[0]-b[0],c[1]-b[1])
            if ab==(0,0) or (ab[0]*bc[1]==ab[1]*bc[0] and ab[0]*bc[0]+ab[1]*bc[1]>=0):
                changed=True
            else: reduced.append(b)
        if len(reduced)<3: break
        points=reduced
    return points


def rdp(points,tolerance):
    p=np.asarray(points,dtype=np.float64)
    if len(p)<=2: return p
    delta=p[-1]-p[0]
    denom=float(delta@delta)
    if denom==0: distance=np.linalg.norm(p-p[0],axis=1)
    else:
        t=np.clip(((p-p[0])@delta)/denom,0,1)
        distance=np.linalg.norm(p-(p[0]+t[:,None]*delta),axis=1)
    i=int(np.argmax(distance))
    if distance[i]<=tolerance: return p[[0,-1]]
    return np.concatenate((rdp(p[:i+1],tolerance)[:-1],rdp(p[i:],tolerance)))


def simplify_closed(points,tolerance):
    p=np.asarray(compact(points),dtype=np.float64)
    split=int(np.argmax(np.sum((p-p[0])**2,axis=1)))
    a=rdp(p[:split+1],tolerance)
    b=rdp(np.concatenate((p[split:],p[:1])),tolerance)
    simplified=np.rint(np.concatenate((a[:-1],b[:-1]))).astype(int)
    if len(simplified)<3 or abs(area(simplified))<0.5: return compact(points)
    return compact(simplified)


def snap_octilinear(points):
    result=[]
    for i,a in enumerate(points):
        b=points[(i+1)%len(points)]
        dx,dy=b[0]-a[0],b[1]-a[1]
        sx=1 if dx>0 else -1; sy=1 if dy>0 else -1
        ax,ay=abs(dx),abs(dy)
        result.append(a)
        if dx==0 or dy==0 or ax==ay: continue
        # A centered chamfer keeps both original corner locations and limits drift.
        if ax>ay:
            half=(ax-ay)//2
            p=(a[0]+sx*half,a[1]); q=(p[0]+sx*ay,p[1]+sy*ay)
        else:
            half=(ay-ax)//2
            p=(a[0],a[1]+sy*half); q=(p[0]+sx*ax,p[1]+sy*ax)
        if p!=a: result.append(p)
        if q!=p and q!=b: result.append(q)
    result=compact(result)
    for i,a in enumerate(result):
        b=result[(i+1)%len(result)]
        dx,dy=abs(b[0]-a[0]),abs(b[1]-a[1])
        assert dx==0 or dy==0 or dx==dy, (a,b)
    return result


def corrected_mask(mask,tolerance):
    loops=contours(mask)
    records=[]
    for loop in loops:
        old_area=area(loop)
        path=snap_octilinear(simplify_closed(loop,tolerance))
        if len(path)>=3: records.append((abs(old_area),old_area>0,path))
    # Largest outlines first, then their holes and nested islands.
    records.sort(key=lambda item:item[0],reverse=True)
    scale=4
    canvas=Image.new('L',(mask.shape[1]*scale,mask.shape[0]*scale))
    draw=ImageDraw.Draw(canvas)
    for _,fill,path in records:
        draw.polygon([(x*scale,y*scale) for x,y in path],fill=255 if fill else 0)
    raster=np.asarray(canvas.resize((mask.shape[1],mask.shape[0]),Image.Resampling.BOX))>=128
    return raster,[{'filled':fill,'points':path} for _,fill,path in records]


def correct_panels(indices,tolerance=1.1):
    hull,hull_paths=corrected_mask(indices!=0,tolerance)
    result=np.where(hull,2,0).astype(np.uint8)
    paths={'hull':hull_paths}
    # Rebuild solid panels over dark hull. Clip each panel to the corrected hull.
    for ink in [1,4,3]:
        panel,poly=corrected_mask(indices==ink,tolerance)
        result[panel & hull]=ink
        paths[str(ink)]=poly
    return result,{
        'algorithm':'contour tracing + polygon simplification + exact octilinear chamfers',
        'tolerance_pixels':tolerance,
        'allowed_angles_degrees':[0,45,90,135,180,225,270,315],
        'corrected_pixels':int(np.count_nonzero(result!=indices)),
        'opaque_pixels_before':int(np.count_nonzero(indices)),
        'opaque_pixels_after':int(np.count_nonzero(result)),
        'contours':sum(len(v) for v in paths.values()),
    },paths


def prepare(input_path: Path, output_path: Path, black_threshold: int = 18, edge_tolerance: float = 1.1) -> dict:
    if input_path.resolve() == output_path.resolve():
        raise ValueError('Choose a separate output path; the source image is preserved.')
    if not 0 <= black_threshold <= 31:
        raise ValueError('Black threshold must be 0..31 to protect this ship\'s dark armor.')
    if not 0 <= edge_tolerance <= 4:
        raise ValueError('Edge tolerance must be between 0 and 4 pixels.')
    image = ImageOps.exif_transpose(Image.open(input_path)).convert('RGBA')
    original_size = image.size
    source = np.asarray(image).copy()
    alpha = source[..., 3]
    # Remove black and near-black everywhere, including gaps between armor plates.
    visible = (alpha >= 128) & (source[..., :3].max(axis=-1) > black_threshold)
    if not visible.any():
        raise ValueError('No visible ship pixels remain; check the input and black threshold.')
    source[..., 3] = np.where(visible, 255, 0)
    source[~visible, :3] = 0

    # Snap broad source surfaces to solid inks BEFORE reducing the picture. This
    # avoids leaving source gradients/noisy almost-equal shades inside one panel.
    native_indices = nearest_ink(source[..., :3])
    snapped = np.zeros_like(source)
    snapped[..., :3] = INKS[native_indices - 1]
    snapped[..., 3] = source[..., 3]
    snapped[~visible] = 0

    scale = min(SIZE[0] / image.width, SIZE[1] / image.height)
    fitted_size = (max(1, round(image.width * scale)), max(1, round(image.height * scale)))
    # RGBA Lanczos resampling is premultiplied by Pillow, so transparent black
    # cannot introduce a dark fringe. Re-quantize afterwards to remove new shades.
    fitted = Image.fromarray(snapped).resize(fitted_size, Image.Resampling.LANCZOS)
    reduced = np.asarray(fitted)
    indices = nearest_ink(reduced[..., :3])
    indices[reduced[..., 3] < 128] = 0
    canvas = np.zeros((SIZE[1], SIZE[0]), dtype=np.uint8)
    offset = ((SIZE[0] - fitted_size[0]) // 2, (SIZE[1] - fitted_size[1]) // 2)
    canvas[offset[1]:offset[1]+fitted_size[1], offset[0]:offset[0]+fitted_size[0]] = indices

    canvas, edge_report, edge_paths = correct_panels(canvas, edge_tolerance)

    result = Image.fromarray(canvas, mode='P')
    # Exactly five actual PNG palette entries, including the transparent one.
    result.putpalette([0, 0, 0] + INKS.flatten().tolist())
    result.info['transparency'] = 0
    output_path.parent.mkdir(parents=True, exist_ok=True)
    result.save(output_path, format='PNG', optimize=True, transparency=0)

    with Image.open(output_path) as saved:
        rgba = np.asarray(saved.convert('RGBA'))
        used = Counter(map(tuple, rgba.reshape(-1, 4)))
        allowed = {(0, 0, 0, 0)} | {(*map(int, ink), 255) for ink in INKS}
        assert saved.size == SIZE
        assert set(used) <= allowed, f'Unexpected output colors: {set(used) - allowed}'
        assert set(np.unique(rgba[..., 3])) <= {0, 255}, 'Partial alpha remains'
        assert png_palette_entries(output_path) == 5, 'PNG palette has extra entries'
        bounds = saved.convert('RGBA').getbbox()
    report = {
        'input': str(input_path.resolve()), 'output': str(output_path.resolve()),
        'source_size': original_size, 'output_size': SIZE,
        'fit': 'contain, no stretching or cropping', 'scaled_source_size': fitted_size,
        'content_bounds': bounds, 'black_threshold': black_threshold,
        'palette_entries': png_palette_entries(output_path), 'used_colors': len(used),
        'palette': ['transparent'] + ['#'+color for color in COLORS],
        'edge_correction': edge_report,
        'pixel_counts': {('transparent' if a == 0 else '#%02x%02x%02x' % (r,g,b)): count
                         for (r,g,b,a),count in used.items()},
    }
    output_path.with_suffix('.edges.json').write_text(json.dumps(edge_paths, separators=(',', ':'))+'\n')
    output_path.with_suffix('.json').write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n')
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('input', type=Path)
    parser.add_argument('output', type=Path)
    parser.add_argument('--black-threshold', type=int, default=18,
                        help='Remove pixels whose RGB channels are all this value or lower (default: 18).')
    parser.add_argument('--edge-tolerance', type=float, default=1.1,
                        help='Contour simplification tolerance before 0/45/90-degree correction (default: 1.1 pixels).')
    args = parser.parse_args()
    if not 0 <= args.edge_tolerance <= 4:
        parser.error('--edge-tolerance must be between 0 and 4 pixels')
    print(json.dumps(prepare(args.input, args.output, args.black_threshold, args.edge_tolerance), ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()

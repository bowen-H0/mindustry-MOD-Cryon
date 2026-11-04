"""Recolor vanilla thorium item and ore sprites without changing their geometry.

Original sources: Mindustry item-thorium.png and ore-thorium1.png.
Only the mineral palette is replaced; alpha, dimensions and other colors stay intact.
"""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'tools/sprite-sources/vanilla'
LIGHT, DARK = (128, 232, 177), (73, 166, 142)
PALETTE = {(249, 163, 199): LIGHT, (203, 142, 191): DARK,
           (205, 159, 207): LIGHT, (165, 118, 167): DARK}


def recolor(source):
    result = source.copy()
    result.putdata([PALETTE.get((r, g, b), (r, g, b)) + (a,)
                    for r, g, b, a in source.getdata()])
    assert result.size == source.size
    assert result.getchannel('A').tobytes() == source.getchannel('A').tobytes()
    return result


def main():
    pairs = [('item-thorium.png', 'sprites/items/gamma-crystal.png'),
             ('ore-thorium1.png', 'sprites/blocks/environment/porphyra/ore-gamma-crystal1.png')]
    preview = Image.new('RGB', (640, 352), '#242832')
    d = ImageDraw.Draw(preview)
    d.text((112, 5), 'VANILLA THORIUM', fill='#e0e3ea')
    d.text((420, 5), 'GAMMA RECOLOR', fill='#e0e3ea')
    for row, (original, destination) in enumerate(pairs):
        source = Image.open(SOURCE / original).convert('RGBA')
        result = recolor(source)
        result.save(ROOT / destination)
        for column, image in enumerate([source, result]):
            large = image.resize((128, 128), Image.Resampling.NEAREST)
            preview.paste(large, (column * 320 + 96, row * 176 + 24), large)
        print(f'{destination}: {result.size}, original alpha and geometry preserved')
    output = ROOT / 'output/gamma-crystal-vanilla-recolor.png'
    output.parent.mkdir(parents=True, exist_ok=True)
    preview.save(output)


if __name__ == '__main__':
    main()

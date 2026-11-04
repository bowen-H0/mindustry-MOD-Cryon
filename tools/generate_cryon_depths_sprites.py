"""Submerge the original Tantros floor pixels; keep their geometry and alpha unchanged."""
from pathlib import Path
from shutil import copyfile
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
NATIVE = Path('/path/to/mindustry-assets/sprites/blocks/environment')
SOURCE = ROOT / 'tools/sprite-sources/vanilla/cryon-depths'
OUTPUT = ROOT / 'sprites/blocks/environment/cryon-depths'
FLOORS = {
    'abyss-blue-bed': ('bluemat', 3, (59, 86, 110)),
    'abyss-red-bed': ('redmat', 3, (101, 80, 100)),
    'abyss-silt-bed': ('darksand', 3, (54, 75, 89)),
    'abyss-shallows': ('bluemat', 3, (100, 134, 139)),
    'abyss-vent': ('stone-vent', 2, (85, 116, 127)),
}


def main():
    SOURCE.mkdir(parents=True, exist_ok=True)
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for name, (base, variants, color) in FLOORS.items():
        for index in range(1, variants + 1):
            original = SOURCE / f'{base}{index}.png'
            if not original.exists():
                copyfile(NATIVE / original.name, original)
            image = Image.open(original).convert('RGBA')
            values = [sum(pixel[:3]) / 3 for pixel in image.getdata() if pixel[3]]
            low, high = min(values), max(values)
            def shade(pixel):
                r, g, b, a = pixel
                factor = 0.78 + 0.36 * ((r + g + b) / 3 - low) / max(high - low, 1)
                return tuple(max(0, min(255, round(c * factor))) for c in color) + (a,)
            output = image.copy()
            output.putdata([shade(pixel) for pixel in image.getdata()])
            assert output.getchannel('A').tobytes() == image.getchannel('A').tobytes()
            output.save(OUTPUT / f'{name}{index}.png')
    print('Generated 14 seabed/vent variants from original Tantros and native vent textures.')


if __name__ == '__main__':
    main()

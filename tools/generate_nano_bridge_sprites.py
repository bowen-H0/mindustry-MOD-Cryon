"""Native-grid textures for the colony's ItemBridge (base, end, bridge and arrow)."""
from pathlib import Path
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parents[1] / 'sprites/blocks/distribution'

def main():
    root.mkdir(parents=True, exist_ok=True)
    im = Image.new('RGBA', (32, 32)); d = ImageDraw.Draw(im)
    d.rectangle((2, 2, 29, 29), fill='#243e33', outline='#a8c7a6', width=2)
    d.rectangle((6, 6, 25, 25), fill='#53755b', outline='#173b2a', width=2)
    d.ellipse((10, 10, 21, 21), fill='#153c2a', outline='#61ef9f', width=2)
    for x, y in [(4, 4), (24, 4), (4, 24), (24, 24)]:
        d.rectangle((x, y, x+3, y+3), fill='#86ad8c')
    im.save(root / 'nano-bridge.png')
    im = Image.new('RGBA', (32, 32)); d = ImageDraw.Draw(im)
    d.rectangle((6, 12, 25, 20), fill='#8cab88', outline='#254a36', width=2)
    d.line((9, 15, 22, 15), fill='#70f3ab', width=2)
    im.save(root / 'nano-bridge-end.png')
    im = Image.new('RGBA', (32, 8)); d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 31, 7), fill='#345c44', outline='#9ebe9b')
    d.line((0, 3, 31, 3), fill='#62e999', width=2)
    im.save(root / 'nano-bridge-bridge.png')
    im = Image.new('RGBA', (10, 10)); d = ImageDraw.Draw(im)
    d.polygon([(1, 1), (8, 5), (1, 9), (3, 5)], fill='#b8ffca')
    im.save(root / 'nano-bridge-arrow.png')

if __name__ == '__main__':
    main()

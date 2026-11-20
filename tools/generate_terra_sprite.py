"""Create Terra's missing sprite only; never replace existing artwork."""
from pathlib import Path
from PIL import Image, ImageDraw

path = Path(__file__).resolve().parents[1] / 'sprites/blocks/turrets/aravis/terra.png'
if path.exists():
    print('Preserved existing Terra sprite')
else:
    image = Image.new('RGBA', (96, 96))
    draw = ImageDraw.Draw(image)
    draw.ellipse((13, 20, 83, 90), fill='#202832', outline='#8a9292', width=3)
    draw.polygon([(22, 28), (74, 28), (85, 47), (77, 81), (65, 89),
                  (31, 89), (19, 81), (11, 47)], fill='#424e56', outline='#202832', width=3)
    draw.polygon([(28, 31), (68, 31), (75, 49), (68, 78), (28, 78), (21, 49)],
                 fill='#7e898b', outline='#aeb6af', width=2)
    for x in (20, 60):
        draw.rectangle((x, 3, x + 16, 60), fill='#222d37')
        draw.rectangle((x + 3, 7, x + 13, 55), fill='#8d999c')
        draw.rectangle((x + 6, 11, x + 10, 49), fill='#455d68')
        draw.rectangle((x + 4, 0, x + 12, 8), fill='#bbd5d5')
        for y in (17, 27, 37):
            draw.line((x + 1, y, x + 15, y), fill='#303c46', width=3)
    draw.rounded_rectangle((37, 34, 58, 74), radius=4, fill='#293842', outline='#c5ccbd', width=2)
    for y, color in zip((40, 48, 56, 64), ('#ff535b', '#ffab48', '#55aaff', '#66ef9b')):
        draw.rectangle((42, y, 53, y + 4), fill=color)
    for x in (23, 69):
        draw.rectangle((x, 71, x + 4, 76), fill='#d0d0b8')
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)
    print('Created Terra sprite')

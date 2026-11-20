"""Create only the missing Emberglow sprite, preserving existing user art."""
from pathlib import Path
from PIL import Image, ImageDraw

path = Path(__file__).resolve().parents[1] / 'sprites/blocks/turrets/aravis/emberglow.png'
if path.exists():
    print('Preserved existing Emberglow sprite')
else:
    image = Image.new('RGBA', (96, 96))
    draw = ImageDraw.Draw(image)
    draw.ellipse((10, 14, 86, 90), fill='#222c34', outline='#87918c', width=3)
    draw.polygon([(24, 24), (72, 24), (84, 47), (76, 81), (63, 91),
                  (33, 91), (20, 81), (12, 47)], fill='#566368', outline='#202b34', width=3)
    for left in (True, False):
        pts = [(12, 14), (26, 5), (36, 28), (34, 65), (20, 72), (10, 48)]
        if not left:
            pts = [(96-x, y) for x, y in pts]
        draw.polygon(pts, fill='#9ba5a0', outline='#27343e', width=3)
        x = 19 if left else 66
        for y in (26, 36, 46):
            draw.rectangle((x, y, x+10, y+4), fill='#3b4b53')
    draw.rounded_rectangle((32, 22, 64, 78), radius=8, fill='#303e46', outline='#c1c4ab', width=2)
    draw.ellipse((35, 26, 61, 57), fill='#a34c2e', outline='#f7a954', width=3)
    draw.ellipse((40, 30, 56, 50), fill='#ffc16d')
    draw.ellipse((44, 32, 52, 46), fill='#fff1ba')
    draw.rectangle((43, 56, 53, 70), fill='#d78242')
    draw.rectangle((46, 57, 50, 68), fill='#ffd184')
    draw.rounded_rectangle((27, 74, 69, 85), radius=4, fill='#29353f', outline='#7e8c8a', width=2)
    draw.rectangle((36, 78, 60, 81), fill='#4d4243')
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)
    print('Created Emberglow sprite')

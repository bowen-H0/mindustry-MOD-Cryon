"""Draw Cryon's original 32px environment status sprites; no external artwork."""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / 'sprites/status'
SIZE = (32, 32)


def canvas():
    image = Image.new('RGBA', SIZE)
    return image, ImageDraw.Draw(image)


def heat():
    image, d = canvas()
    edge, shade, body, light = '#643945', '#bd5757', '#ed856a', '#ffe4b0'
    # A heavy flame silhouette and three broken heat rays.
    d.polygon([(6, 23), (6, 18), (9, 13), (10, 18), (13, 14),
               (13, 8), (18, 2), (18, 10), (22, 15), (23, 11),
               (27, 18), (27, 24), (23, 29), (10, 29)], fill=edge)
    d.polygon([(8, 22), (8, 18), (10, 16), (11, 21), (15, 15),
               (15, 9), (17, 6), (17, 11), (21, 17), (24, 16),
               (25, 19), (25, 23), (22, 27), (11, 27)], fill=body)
    d.polygon([(9, 23), (12, 25), (22, 25), (24, 22), (23, 26),
               (21, 28), (11, 28)], fill=shade)
    d.polygon([(13, 24), (13, 21), (16, 17), (16, 21), (19, 19),
               (21, 23), (20, 26), (15, 26)], fill=light)
    d.line([(3, 12), (2, 9), (4, 6), (4, 3)], fill=body, width=2)
    d.line([(28, 11), (27, 8), (29, 5), (29, 2)], fill=body, width=2)
    return image


def cold():
    image, d = canvas()
    edge, shade, body, light = '#385970', '#668fb8', '#a6d9f5', '#edfbff'
    # Six-armed snowflake, its core split by a jagged freeze fracture.
    branches = [((16, 3), (16, 28)), ((5, 9), (27, 22)), ((5, 22), (27, 9)),
                ((11, 5), (16, 10), (21, 5)), ((11, 26), (16, 21), (21, 26)),
                ((5, 15), (10, 12), (10, 6)), ((22, 6), (22, 12), (27, 15)),
                ((5, 17), (10, 20), (10, 26)), ((22, 26), (22, 20), (27, 17))]
    for points in branches:
        d.line(points, fill=edge, width=5)
    for points in branches:
        d.line(points, fill=body, width=3)
    d.polygon([(16, 10), (21, 13), (21, 19), (16, 22), (11, 19), (11, 13)], fill=shade)
    d.polygon([(16, 12), (19, 14), (19, 18), (16, 20), (13, 18), (13, 14)], fill=light)
    d.line([(16, 10), (14, 15), (17, 16), (15, 22)], fill=edge, width=2)
    return image


def crystal():
    image, d = canvas()
    edge, shade, body, light = '#49365f', '#78539f', '#ad80dc', '#e9daff'
    # Three distinct shards, with flat facets readable at native status-icon size.
    d.polygon([(3, 13), (8, 9), (13, 14), (15, 27), (8, 29)], fill=edge)
    d.polygon([(5, 14), (8, 12), (11, 15), (13, 26), (9, 27)], fill=body)
    d.polygon([(8, 12), (11, 15), (13, 26), (10, 23)], fill=shade)
    d.line([(5, 14), (8, 12), (10, 15)], fill=light, width=1)
    d.polygon([(21, 15), (27, 10), (30, 15), (25, 28), (18, 29)], fill=edge)
    d.polygon([(23, 16), (27, 13), (28, 16), (24, 26), (21, 27)], fill=body)
    d.polygon([(27, 13), (28, 16), (24, 26), (24, 20)], fill=shade)
    d.line([(23, 16), (27, 13)], fill=light, width=1)
    d.polygon([(11, 9), (17, 1), (23, 9), (21, 27), (16, 30), (11, 25)], fill=edge)
    d.polygon([(13, 10), (17, 4), (21, 10), (19, 25), (16, 27), (13, 24)], fill=body)
    d.polygon([(17, 4), (21, 10), (19, 25), (16, 27), (17, 12)], fill=shade)
    d.line([(13, 10), (17, 4), (18, 10)], fill=light, width=1)
    d.line([(13, 11), (14, 21)], fill=light, width=1)
    return image


def main():
    images = {'heat-stress': heat(), 'cold-stress': cold(), 'crystallized': crystal()}
    DEST.mkdir(parents=True, exist_ok=True)
    for name, image in images.items():
        assert image.size == SIZE and image.mode == 'RGBA'
        assert image.getbbox() and image.getextrema()[3] == (0, 255)
        image.save(DEST / f'{name}.png', optimize=True)
        print(f'{name}: 32x32 RGBA')
    preview = Image.new('RGB', (576, 242), '#202631')
    d = ImageDraw.Draw(preview)
    for index, (name, image) in enumerate(images.items()):
        x = index * 192
        preview.paste(image.resize((128, 128), Image.Resampling.NEAREST), (x + 32, 16),
                      image.resize((128, 128), Image.Resampling.NEAREST))
        preview.paste(image, (x + 80, 162), image)
        d.text((x + 40, 212), name, fill='#e8e8ec')
    path = ROOT / 'output/environment-status-icons.png'
    path.parent.mkdir(parents=True, exist_ok=True)
    preview.save(path)


if __name__ == '__main__':
    main()

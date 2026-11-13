"""Create missing orbital capital hulls only. Never overwrite player artwork."""
from pathlib import Path
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parents[1] / "sprites/units"
outline, dark, mid, light = "#44413c", "#505d6b", "#8796a5", "#c1d0d6"
for name, width, height, strategic in [("aphelion", 128, 160, False), ("firmament", 196, 224, True)]:
    path = root / f"{name}.png"
    if path.exists():
        print(f"Preserving existing sprite: {path.name}")
        continue
    image = Image.new("RGBA", (width, height))
    draw = ImageDraw.Draw(image)
    cx = width // 2
    for side in [-1, 1]:
        def points(coords):
            return [(cx + side * int(x * width), int(y * height)) for x, y in coords]
        draw.polygon(points([(0.09,.12),(.22,.06),(.32,.19),(.43,.32),(.46,.78),(.33,.91),(.15,.86),(.12,.34)]), fill=outline)
        draw.polygon(points([(.16,.16),(.22,.11),(.29,.23),(.38,.34),(.40,.74),(.32,.83),(.20,.80),(.17,.35)]), fill=mid)
        draw.polygon(points([(.19,.17),(.22,.13),(.26,.24),(.30,.37),(.28,.74),(.22,.78),(.20,.35)]), fill=light)
        for row in range(4):
            y = int(height * (.36 + row * .12))
            x = cx + side * int(width * .34)
            rw, rh = (10, 7) if not strategic else (14, 10)
            draw.rectangle((x-rw, y-rh, x+rw, y+rh), fill=outline)
            draw.rectangle((x-rw+2, y-rh+2, x+rw-2, y+rh-2), fill=dark)
            if strategic:
                draw.ellipse((x-5,y-5,x+5,y+5), fill=mid)
                draw.line((x,y-2,x,y-10), fill="#c89bff", width=3)
            else:
                for lane in [-4,4]:
                    draw.rectangle((x+lane-1,y-3,x+lane+1,y+3), fill="#9bdcf7")
    draw.polygon([(cx,3),(cx+int(width*.13),int(height*.23)),(cx+int(width*.16),int(height*.80)),
                  (cx+int(width*.10),height-9),(cx-int(width*.10),height-9),
                  (cx-int(width*.16),int(height*.80)),(cx-int(width*.13),int(height*.23))], fill=outline)
    draw.polygon([(cx,10),(cx+int(width*.08),int(height*.25)),(cx+int(width*.10),int(height*.77)),
                  (cx+int(width*.06),height-17),(cx-int(width*.06),height-17),
                  (cx-int(width*.10),int(height*.77)),(cx-int(width*.08),int(height*.25))], fill=mid)
    draw.polygon([(cx,12),(cx,int(height*.80)),(cx-int(width*.07),int(height*.73)),(cx-int(width*.06),int(height*.26))], fill=light)
    draw.polygon([(cx,int(height*.24)),(cx+6,int(height*.31)),(cx+6,int(height*.37)),
                  (cx-6,int(height*.37)),(cx-6,int(height*.31))], fill=dark)
    draw.line((cx,int(height*.27),cx,int(height*.34)), fill="#9bdcf7", width=3)
    if strategic:
        for x, y in [(cx,int(height*.23)),(cx-40,int(height*.62)),(cx+40,int(height*.62))]:
            draw.ellipse((x-12,y-12,x+12,y+12), fill=outline)
            draw.ellipse((x-9,y-9,x+9,y+9), fill=light)
            draw.ellipse((x-6,y-6,x+6,y+6), fill=dark)
            draw.ellipse((x-3,y-3,x+3,y+3), fill="#a8f2ed")
    draw.rectangle((cx-7,height-21,cx+7,height-12), fill=dark)
    root.mkdir(parents=True, exist_ok=True)
    image.save(path)

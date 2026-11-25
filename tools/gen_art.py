import sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter
res = sys.argv[1]
BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
RED = (229, 9, 20)

def gradient(w, h):
    img = Image.new("RGB", (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            t = x / w
            r = int(11 + (60 - 11) * (1 - t) * 0.9)
            g = int(11 + 4 * t)
            b = int(15 + 25 * t)
            px[x, y] = (r, g, b)
    return img

# TV banner 320x180 (xhdpi)
w, h = 320, 180
img = gradient(w, h)
d = ImageDraw.Draw(img)
glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
gd = ImageDraw.Draw(glow)
gd.ellipse((-60, 40, 160, 260), fill=(229, 9, 20, 110))
glow = glow.filter(ImageFilter.GaussianBlur(40))
img.paste(glow, (0, 0), glow)
d = ImageDraw.Draw(img)
f1 = ImageFont.truetype(BOLD, 64)
f2 = ImageFont.truetype(BOLD, 26)
d.text((24, 34), "SAM", font=f1, fill=RED)
d.text((26, 108), "ONLINE TV", font=f2, fill=(245, 245, 245))
d.rectangle((26, 146, 120, 150), fill=RED)
img.save(f"{res}/drawable-xhdpi/tv_banner.png")

# Launcher icons
sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
for name, s in sizes.items():
    big = 512
    ic = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    dd = ImageDraw.Draw(ic)
    dd.rounded_rectangle((16, 16, big - 16, big - 16), radius=110, fill=(14, 14, 20, 255))
    f = ImageFont.truetype(BOLD, 300)
    bbox = dd.textbbox((0, 0), "S", font=f)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    dd.text(((big - tw) / 2 - bbox[0], (big - th) / 2 - bbox[1] - 10), "S", font=f, fill=RED)
    dd.rectangle((150, 420, 362, 438), fill=RED)
    ic.resize((s, s), Image.LANCZOS).save(f"{res}/mipmap-{name}/ic_launcher.png")
print("ok")

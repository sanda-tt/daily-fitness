# -*- coding: utf-8 -*-
"""小黑盒推文封面：粉色背景 + 真实截图手机样机 + 大标题。"""
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import os

W, H = 1920, 1080
ASSETS = r"D:\developer\androidstudio\.design\gym\推文素材"
SHOTS = os.path.join(ASSETS, "screenshots")
OUT = os.path.join(ASSETS, "cover.jpg")
FONT_BOLD = r"C:\Windows\Fonts\msyhbd.ttc"
FONT_REG = r"C:\Windows\Fonts\msyh.ttc"

# ---------- 背景渐变 ----------
top = (253, 233, 240)
bottom = (245, 196, 212)
bg = Image.new("RGB", (W, H))
px = bg.load()
for y in range(H):
    t = y / H
    r = int(top[0] + (bottom[0] - top[0]) * t)
    g = int(top[1] + (bottom[1] - top[1]) * t)
    b = int(top[2] + (bottom[2] - top[2]) * t)
    for x in range(W):
        px[x, y] = (r, g, b)

# 柔色圆斑
blob = Image.new("RGBA", (W, H), (0, 0, 0, 0))
bd = ImageDraw.Draw(blob)
bd.ellipse((-200, -220, 520, 420), fill=(255, 255, 255, 90))
bd.ellipse((1500, -260, 2200, 360), fill=(232, 112, 140, 45))
bd.ellipse((1450, 720, 2250, 1300), fill=(255, 255, 255, 70))
bd.ellipse((-260, 700, 360, 1260), fill=(232, 112, 140, 40))
bg = Image.alpha_composite(bg.convert("RGBA"), blob)

# 小装饰：加号 / 星星 / 波浪线
dec = ImageDraw.Draw(bg)
pink = (232, 112, 140, 200)
for cx, cy, s in [(960, 130, 22), (200, 980, 18), (900, 940, 16), (1750, 560, 18)]:
    dec.line((cx - s, cy, cx + s, cy), fill=pink, width=6)
    dec.line((cx, cy - s, cx, cy + s), fill=pink, width=6)


# ---------- 手机样机 ----------
def phone(path, target_h, angle):
    shot = Image.open(path).convert("RGB")
    ratio = target_h / shot.height
    tw = int(shot.width * ratio)
    shot = shot.resize((tw, target_h))
    frame, radius = 14, 46
    cw, ch = tw + frame * 2, target_h + frame * 2
    canvas = Image.new("RGBA", (cw + 60, ch + 60), (0, 0, 0, 0))
    # 阴影
    shadow = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    sd.rounded_rectangle((0, 0, cw - 1, ch - 1), radius=radius, fill=(120, 40, 70, 110))
    shadow = shadow.filter(ImageFilter.GaussianBlur(18))
    canvas.alpha_composite(shadow, (34, 40))
    # 白色边框
    fd = ImageDraw.Draw(canvas)
    fd.rounded_rectangle((30, 30, 30 + cw - 1, 30 + ch - 1), radius=radius, fill=(255, 255, 255, 255))
    # 截图（圆角）
    mask = Image.new("L", (tw, target_h), 0)
    md = ImageDraw.Draw(mask)
    md.rounded_rectangle((0, 0, tw - 1, target_h - 1), radius=radius - frame, fill=255)
    canvas.paste(shot, (30 + frame, 30 + frame), mask)
    return canvas.rotate(angle, resample=Image.BICUBIC, expand=True)


p1 = phone(os.path.join(SHOTS, "02-today-weights.png"), 800, -7)
bg.alpha_composite(p1, (980, 120))
p2 = phone(os.path.join(SHOTS, "05-chart-deadlift.png"), 700, 6)
bg.alpha_composite(p2, (1330, 260))

# ---------- 文案 ----------
draw = ImageDraw.Draw(bg)
f_title = ImageFont.truetype(FONT_BOLD, 118)
f_sub = ImageFont.truetype(FONT_BOLD, 42)
f_badge = ImageFont.truetype(FONT_BOLD, 30)
f_pill = ImageFont.truetype(FONT_BOLD, 32)

LX = 120

# 顶部贴纸徽章
badge = "开源 · 无广告 · 不联网"
bw = draw.textlength(badge, font=f_badge)
draw.rounded_rectangle((LX, 96, LX + bw + 56, 156), radius=30, fill=(232, 112, 140, 255))
draw.text((LX + 28, 108), badge, font=f_badge, fill=(255, 255, 255))

# 标题两行（第二行加荧光笔底）
line1 = "我上次硬拉"
line2 = "多少来着？"
y1 = 210
draw.text((LX, y1), line1, font=f_title, fill=(34, 34, 34))
y2 = y1 + 140
hl_x2 = LX + draw.textlength(line2, font=f_title) + 24
draw.rounded_rectangle((LX - 10, y2 + 28, hl_x2, y2 + 118), radius=18, fill=(255, 224, 130, 230))
draw.text((LX, y2), line2, font=f_title, fill=(34, 34, 34))

# 副标题
sub = "一个专治健身人记性的「猛男粉」App"
draw.text((LX, y2 + 165), sub, font=f_sub, fill=(217, 92, 122))

# 底部标签药丸
pills = ["今日计划", "滑动打卡", "重量折线图", "纯本地"]
px0 = LX
py = y2 + 250
for p in pills:
    tw = draw.textlength(p, font=f_pill)
    draw.rounded_rectangle((px0, py, px0 + tw + 48, py + 58), radius=29,
                           fill=(255, 255, 255, 220), outline=(232, 112, 140, 255), width=3)
    draw.text((px0 + 24, py + 10), p, font=f_pill, fill=(217, 92, 122))
    px0 += int(tw) + 68

bg.convert("RGB").save(OUT, quality=90)
print("cover saved", os.path.getsize(OUT))

# -*- coding: utf-8 -*-
"""给梗图原图加经典上下白字黑边中文文案。"""
from PIL import Image, ImageDraw, ImageFont
import os

MEME_DIR = r"D:\developer\androidstudio\.design\gym\推文素材\memes"
FONT_PATH = r"C:\Windows\Fonts\msyhbd.ttc"


def fit_font(text, max_width, start_size):
    size = start_size
    while size > 14:
        font = ImageFont.truetype(FONT_PATH, size)
        if font.getlength(text) <= max_width:
            return font
        size -= 2
    return ImageFont.truetype(FONT_PATH, 14)


def wrap(text, font, max_width):
    lines, cur = [], ""
    for ch in text:
        if font.getlength(cur + ch) > max_width and cur:
            lines.append(cur)
            cur = ch
        else:
            cur += ch
    if cur:
        lines.append(cur)
    return lines


def draw_caption(img, text, position, start_ratio=0.085):
    draw = ImageDraw.ImageDraw(img)
    W, H = img.size
    font = fit_font(text, int(W * 0.92), int(H * start_ratio))
    lines = wrap(text, font, int(W * 0.92))
    lh = int(font.size * 1.18)
    total_h = lh * len(lines)
    margin = int(H * 0.025)
    y = margin if position == "top" else H - total_h - margin
    for line in lines:
        tw = font.getlength(line)
        x = (W - tw) / 2
        # 黑描边
        for dx in range(-3, 4):
            for dy in range(-3, 4):
                if dx * dx + dy * dy <= 11:
                    draw.text((x + dx, y + dy), line, font=font, fill=(0, 0, 0))
        draw.text((x, y), line, font=font, fill=(255, 255, 255))
        y += lh


def cover_bottom(img, ratio, color=(255, 255, 255)):
    W, H = img.size
    h = int(H * ratio)
    bar = Image.new("RGB", (W, h), color)
    img.paste(bar, (0, H - h))


jobs = [
    # (输入, 输出, 顶文案, 底文案, 预处理)
    ("raw-zhenxiang.jpg", "meme-01-zhenxiang.jpg",
     "健身计划还用App记？我脑子够使",
     "三天后：等等，我昨天练的啥来着", None),
    ("raw-question.jpg", "meme-02-question.jpg",
     "杠铃片都装好了，突然想起一个问题：",
     "我上次卧推，是60还是70来着？？？", None),
    ("raw-psyduck.jpg", "meme-03-psyduck.jpg",
     "打开别的健身App",
     "广告三连 注册登录 记重量还得开会员", "psyduck"),
    ("raw-fair.jpg", "meme-04-fair.jpg",
     "无广告 无账号 无内购 还TMD开源",
     None, None),
    ("raw-want.jpg", "meme-05-want.jpg",
     "免费 开源 不联网 还能看重量涨没涨",
     None, None),
]

import sys
idx = int(sys.argv[1]) if len(sys.argv) > 1 else None

for i, (src, dst, top, bottom, prep) in enumerate(jobs):
    if idx is not None and i != idx:
        continue
    img = Image.open(os.path.join(MEME_DIR, src)).convert("RGB")
    if prep == "psyduck":
        cover_bottom(img, 0.27)
    if top:
        draw_caption(img, top, "top")
    if bottom:
        draw_caption(img, bottom, "bottom")
    img.save(os.path.join(MEME_DIR, dst), quality=90)
    print(dst, img.size)

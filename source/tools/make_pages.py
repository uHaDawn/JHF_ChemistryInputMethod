# -*- coding: utf-8 -*-
"""生成 0.0.9 单页更新图（每项更新一张，白底 + 青色主题）。"""
import os
from PIL import Image, ImageDraw, ImageFont

BASE = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SHOTS = os.path.join(BASE, "shots")
OUT_DIR = SHOTS

TEAL = (15, 163, 163)
TEAL_DARK = (10, 120, 122)
TEAL_SOFT = (232, 247, 246)
INK = (26, 33, 38)
GRAY = (108, 122, 129)
LINE = (226, 236, 235)
WHITE = (255, 255, 255)

F_REG = r"C:\Windows\Fonts\msyh.ttc"
F_BOLD = r"C:\Windows\Fonts\msyhbd.ttc"
f_app = ImageFont.truetype(F_BOLD, 32)
f_ver = ImageFont.truetype(F_BOLD, 24)
f_title = ImageFont.truetype(F_BOLD, 44)
f_desc = ImageFont.truetype(F_REG, 29)
f_page = ImageFont.truetype(F_BOLD, 24)
f_foot = ImageFont.truetype(F_REG, 22)

W, H = 1080, 1440
M = 72
CW = W - 2 * M

# 截图裁剪区域：候选栏 + 整个键盘，六页统一，保证视觉一致
CROP = (0, 1555, 1220, 2656)
IMG_W = CW
IMG_H = int(IMG_W * (CROP[3] - CROP[1]) / (CROP[2] - CROP[0]))

# 雅黑缺少部分上/下标字符（⁺ ⁻ ⁰ ⁶ ⁹ ₀₁₂₃ ᴺ），逐字符回退到 Segoe UI
_MISS = {}
_FB = {}


def _is_script(ch):
    o = ord(ch)
    return 0x2070 <= o <= 0x209F or o == 0x1D3A


def _missing(font, ch):
    key = (font.path, font.size, ch)
    if key in _MISS:
        return _MISS[key]

    def render(c):
        im = Image.new("L", (56, 56), 255)
        ImageDraw.Draw(im).text((8, 8), c, font=font, fill=0)
        return im.tobytes()

    res = render(ch) == render("\uE000")
    _MISS[key] = res
    return res


def _fallback(font, ch):
    script = _is_script(ch)
    key = (font.size, "bd" if "bd" in font.path.lower() else "rg", script)
    if key not in _FB:
        name = "segoeuib.ttf" if key[1] == "bd" else "segoeui.ttf"
        size = int(round(font.size * (1.5 if script else 1.0)))
        _FB[key] = ImageFont.truetype(os.path.join(r"C:\Windows\Fonts", name), size)
    return _FB[key]


def _font_for(font, ch):
    return _fallback(font, ch) if _missing(font, ch) else font


def text_len(text, font):
    return sum(_font_for(font, ch).getlength(ch) for ch in text)


def draw_text(d, xy, text, font, fill):
    x = float(xy[0])
    for ch in text:
        f = _font_for(font, ch)
        d.text((x, xy[1]), ch, font=f, fill=fill)
        x += f.getlength(ch)


NO_START = "。，、；：？！）】》」』”’%‰·,.;:?!)]}"
NO_END = "（【《「『“‘([{"


def wrap(text, font, maxw):
    lines, cur = [], ""
    for ch in text:
        if ch == "\n":
            lines.append(cur)
            cur = ""
            continue
        if text_len(cur + ch, font) > maxw and cur:
            if ch in NO_START:
                lines.append(cur + ch)
                cur = ""
            elif cur[-1] in NO_END:
                lines.append(cur[:-1])
                cur = cur[-1] + ch
            else:
                lines.append(cur)
                cur = ch
        else:
            cur += ch
    if cur:
        lines.append(cur)
    return lines


PAGES = [
    ("沉淀 / 气体 / 离子联想",
     "上屏沉淀化合物自动在联想栏给出 ↓，气体给出 ↑，单独元素给出常见离子电荷（Fe → ²⁺、³⁺）。点选符号即续写为 AgCl↓、Fe³⁺，不必再切到符号页。",
     "v9_agcl.png"),
    ("N 键长按新增 ⁺ / ⁻",
     "带「+ / −」气泡的 N 键，长按浮层里补充上标电荷符号 ⁺ 与 ⁻，候选项为 + − ⁺ ⁻ ᴺ N n。写电荷不必再进符号页翻找。",
     "v9_npop.png"),
    ("单质气体与冷门化合物补全",
     "新增氢气、氧气、氮气、氯气、氟气与氦氖氩氪氙氡等稀有气体，并补入过碳酸钠、王水、碱石灰、红矾、灰锰氧等冷门化合物与俗名。全拼、首字母均可命中：qingqi / qq → 氢气，yangqi / yq → 氧气。",
     "v9_h2.png"),
    ("同位素输出",
     "氕 ¹H、氘 ²H、氚 ³H，以及碳十四 ¹⁴C、碘一三一 ¹³¹I、铀二三五 ²³⁵U 等 24 条核素。直通模式下输入「数字 + 元素」自动把质量数转成上标：2H → ²H、235U → ²³⁵U。",
     "v9_dao.png"),
    ("符号页精简为 4 个分页",
     "符号键盘只保留度量 / 物质 / 反应 / 其他四个分页，删除「角标」页；左侧分类卡自动均分四格，常用符号少翻一层。",
     "v9_sym.png"),
    ("按键触控范围外扩",
     "每排上下各探半个行距，列方向取最近按键中心并左右外扩半个间距。点击落在按键缝隙里也会命中相邻按键，输入更跟手。",
     None),
]


def dash_line(d, p0, p1, color, width=3, dash=14, gap=10):
    import math
    x0, y0 = p0
    x1, y1 = p1
    total = math.hypot(x1 - x0, y1 - y0)
    if total == 0:
        return
    ux, uy = (x1 - x0) / total, (y1 - y0) / total
    t = 0.0
    while t < total:
        a = t
        b = min(t + dash, total)
        d.line((x0 + ux * a, y0 + uy * a, x0 + ux * b, y0 + uy * b), fill=color, width=width)
        t += dash + gap


def dash_rect(d, box, color, width=3):
    x0, y0, x1, y1 = box
    dash_line(d, (x0, y0), (x1, y0), color, width)
    dash_line(d, (x1, y0), (x1, y1), color, width)
    dash_line(d, (x1, y1), (x0, y1), color, width)
    dash_line(d, (x0, y1), (x0, y0), color, width)


def draw_hit_diagram(img, d):
    """第 6 页：命中范围外扩示意（没有能直接拍到「缝隙命中」的截图）。"""
    d.rectangle((M, 470, W - M, 1315), fill=(248, 252, 251), outline=LINE, width=2)
    d.text((M + 40, 522), "点在按键缝隙里，也会命中最近的按键", font=ImageFont.truetype(F_BOLD, 30), fill=INK)

    kw, kh, gap = 180, 120, 16
    x0 = M + (CW - (4 * kw + 3 * gap)) // 2
    labels = [("Q", "W", "E", "R"), ("A", "S", "D", "F")]
    for r, row in enumerate(labels):
        ky = 700 + r * (kh + gap)
        for c, lab in enumerate(row):
            kx = x0 + c * (kw + gap)
            d.rectangle((kx, ky, kx + kw, ky + kh), fill=WHITE,
                        outline=(214, 228, 226), width=2)
            f = ImageFont.truetype(F_BOLD, 40)
            lw = f.getlength(lab)
            d.text((kx + kw / 2 - lw / 2, ky + 34), lab, font=f, fill=(92, 106, 112))

    # W 键外扩半格后的实际命中范围
    hx = x0 + (kw + gap)
    hy = 700
    dash_rect(d, (hx - gap / 2, hy - gap / 2, hx + kw + gap / 2, hy + kh + gap / 2), TEAL, 3)
    d.rectangle((hx, hy, hx + kw, hy + kh), outline=TEAL, width=3)

    note = "虚线框 = 实际命中范围，上下各外扩半个行距、左右各外扩半个间距"
    f = ImageFont.truetype(F_REG, 23)
    draw_text(d, (M + (CW - text_len(note, f)) / 2, 1010), note, f, GRAY)
    tip = "触控无死角"
    f2 = ImageFont.truetype(F_BOLD, 26)
    d.rectangle((M + (CW - 220) / 2, 1090, M + (CW + 220) / 2, 1156), outline=TEAL, width=2)
    draw_text(d, (M + (CW - text_len(tip, f2)) / 2, 1104), tip, f2, TEAL_DARK)


def make_page(idx, title, desc, shot):
    img = Image.new("RGB", (W, H), WHITE)
    d = ImageDraw.Draw(img)

    # 顶部：应用名 + 版本
    d.text((M, 54), "接化发 · 化学输入法", font=f_app, fill=INK)
    pill = "v0.0.9"
    pw = text_len(pill, f_ver) + 34
    d.rectangle((W - M - pw, 50, W - M, 92), fill=TEAL)
    draw_text(d, (W - M - pw + 17, 57), pill, f_ver, WHITE)
    d.line((M, 118, W - M, 118), fill=TEAL, width=3)

    # 编号徽章 + 标题
    bx, by = M, 156
    d.rectangle((bx, by, bx + 68, by + 68), fill=TEAL)
    num = "%02d" % idx
    nw = f_page.getlength(num)
    d.text((bx + 34 - nw / 2, by + 18), num, font=f_page, fill=WHITE)
    draw_text(d, (M + 96, 162), title, f_title, INK)

    # 描述
    ty = 254
    for ln in wrap(desc, f_desc, CW):
        draw_text(d, (M, ty), ln, f_desc, GRAY)
        ty += 46

    # 实机截图（最后一页改为命中范围示意）
    if shot:
        src = Image.open(os.path.join(SHOTS, shot)).convert("RGB").crop(CROP)
        src = src.resize((IMG_W, IMG_H), Image.LANCZOS)
        iy = 470
        d.rectangle((M - 5, iy - 5, M + IMG_W + 5, iy + IMG_H + 5), outline=LINE, width=2)
        img.paste(src, (M, iy))
    else:
        draw_hit_diagram(img, d)

    # 页脚
    d.line((M, 1352, W - M, 1352), fill=LINE, width=2)
    d.text((M, 1378), "接化发 · 化学输入法　v0.0.9　·　2026-10-27", font=f_foot, fill=GRAY)
    label = "%02d / %02d" % (idx, len(PAGES))
    lw = f_page.getlength(label)
    d.text((W - M - lw, 1374), label, font=f_page, fill=TEAL)

    out = os.path.join(OUT_DIR, "v9_page_%02d.png" % idx)
    img.save(out, "PNG")
    print(out, img.size)


for i, (t, s, sh) in enumerate(PAGES, 1):
    make_page(i, t, s, sh)

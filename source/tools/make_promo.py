# -*- coding: utf-8 -*-
"""生成 0.0.9 更新说明长图（白底 + 青色主题）。"""
import os
from PIL import Image, ImageDraw, ImageFont

BASE = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SHOTS = os.path.join(BASE, "shots")
OUT = os.path.join(SHOTS, "v9_feature_long.png")

TEAL = (15, 163, 163)
TEAL_DARK = (10, 120, 122)
TEAL_SOFT = (232, 247, 246)
INK = (26, 33, 38)
GRAY = (108, 122, 129)
LINE = (226, 236, 235)
WHITE = (255, 255, 255)

F_REG = r"C:\Windows\Fonts\msyh.ttc"
F_BOLD = r"C:\Windows\Fonts\msyhbd.ttc"
f_title = ImageFont.truetype(F_BOLD, 42)
f_ver = ImageFont.truetype(F_BOLD, 30)
f_sub = ImageFont.truetype(F_REG, 26)
f_sec = ImageFont.truetype(F_BOLD, 30)
f_card_title = ImageFont.truetype(F_BOLD, 30)
f_body = ImageFont.truetype(F_REG, 24)
f_num = ImageFont.truetype(F_BOLD, 30)
f_cap = ImageFont.truetype(F_REG, 21)
f_foot = ImageFont.truetype(F_REG, 21)

W = 1080
M = 72
CW = W - 2 * M

FEATURES = [
    ("沉淀 / 气体 / 离子联想",
     "上屏沉淀化合物自动给出 ↓，气体给出 ↑，单独元素给出常见离子电荷（Fe → ²⁺、³⁺），点选即续写为 AgCl↓、Fe³⁺。"),
    ("N 键长按新增 ⁺ / ⁻",
     "带「+ / −」气泡的 N 键，长按浮层补充上标电荷符号，候选项为 + − ⁺ ⁻ ᴺ N n。"),
    ("单质气体与冷门化合物补全",
     "新增氢气、氧气、氮气、氯气、氟气及稀有气体，过碳酸钠、王水、碱石灰等；全拼与首字母均可命中：qingqi / qq → 氢气，yangqi / yq → 氧气。"),
    ("同位素输出",
     "氕 ¹H、氘 ²H、氚 ³H，以及碳十四 ¹⁴C、碘一三一 ¹³¹I、铀二三五 ²³⁵U 等 24 条核素；直通模式「数字 + 元素」自动转上标：2H → ²H、235U → ²³⁵U。"),
    ("符号页精简为 4 个分页",
     "符号键盘保留度量 / 物质 / 反应 / 其他四页，删除「角标」页；左侧分类卡自动均分四格。"),
    ("按键触控范围外扩",
     "每排上下各探半个行距、列方向取最近按键中心并左右外扩半个间距；点在按键缝隙里也会命中相邻键。"),
]

SHOTS_LIST = [
    ("v9_h2.png", "qingqi → 氢气 H₂"),
    ("v9_dao.png", "dao → 氘 ²H"),
    ("v9_agcl.png", "AgCl → ↓ 沉淀符号"),
    ("v9_fe.png", "Fe → ²⁺ / ³⁺ 离子联想"),
    ("v9_sym.png", "符号页四个分页"),
    ("v9_npop.png", "N 键长按浮层"),
]

THUMB_W = (CW - 24) // 2
THUMB_H = int(THUMB_W * 1136 / 1220)

# 雅黑缺少部分上/下标字符（⁺ ⁻ ⁰ ⁶ ⁹ ₀₁₂₃ ᴺ），逐字符回退到 Segoe UI；
# 上下标需要放大一些才不至于在中文里显得过小
_MISS = {}
_FB = {}


def _is_script(ch):
    o = ord(ch)
    return 0x2070 <= o <= 0x209F or o == 0x1D3A


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


def text_len(text, font):
    n = 0.0
    for ch in text:
        n += _font_for(font, ch).getlength(ch)
    return n


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


# 先画在足够高的画布上，最后按实际内容裁切
H = 4400
img = Image.new("RGB", (W, H), WHITE)
d = ImageDraw.Draw(img)

y = 58
# 顶部：标题 + 版本
d.text((M, y), "接化发 · 化学输入法", font=f_title, fill=INK)
tw = text_len("接化发 · 化学输入法", f_title)
pill = "v0.0.9"
pw = text_len(pill, f_ver) + 40
d.rectangle((M + tw + 36, y + 4, M + tw + 36 + pw, y + 52), fill=TEAL)
d.text((M + tw + 56, y + 11), pill, font=f_ver, fill=WHITE)
y += 60
d.text((M, y), "2026-10-27 更新说明 · 联想符号、单质气体与同位素", font=f_sub, fill=GRAY)
y += 64
d.line((M, y, W - M, y), fill=TEAL, width=3)
y += 40

# 小标题
d.rectangle((M, y + 4, M + 8, y + 34), fill=TEAL)
d.text((M + 24, y), "更新内容", font=f_sec, fill=TEAL_DARK)
y += 62

# 功能卡
for i, (title, desc) in enumerate(FEATURES, 1):
    lines = wrap(desc, f_body, CW - 110 - 56)
    card_h = 30 + 24 + len(lines) * 38 + 34
    d.rectangle((M, y, W - M, y + card_h), fill=(250, 253, 252),
                outline=LINE, width=2)
    d.rectangle((M, y + 12, M + 5, y + card_h - 12), fill=TEAL)
    cx, cy = M + 30, y + 22
    d.rectangle((cx, cy, cx + 52, cy + 52), outline=TEAL, width=2)
    n = str(i)
    d.text((cx + 26 - f_num.getlength(n) / 2, cy + 8), n, font=f_num, fill=TEAL)
    draw_text(d, (M + 110, y + 24), title, f_card_title, INK)
    ty = y + 24 + 50
    for ln in lines:
        draw_text(d, (M + 110, ty), ln, f_body, GRAY)
        ty += 38
    y += card_h + 18

y += 12
d.rectangle((M, y + 4, M + 8, y + 34), fill=TEAL)
d.text((M + 24, y), "实机演示", font=f_sec, fill=TEAL_DARK)
y += 62

ty = y
for idx, (name, cap) in enumerate(SHOTS_LIST):
    col = idx % 2
    row = idx // 2
    x = M + col * (THUMB_W + 24)
    yy = y + row * (THUMB_H + 44 + 18)
    src = Image.open(os.path.join(SHOTS, name)).convert("RGB")
    src = src.crop((0, 1520, 1220, 2656))
    src = src.resize((THUMB_W, THUMB_H), Image.LANCZOS)
    d.rectangle((x - 6, yy - 6, x + THUMB_W + 6, yy + THUMB_H + 6),
                outline=LINE, width=2)
    img.paste(src, (x, yy))
    draw_text(d, (x + 2, yy + THUMB_H + 12), cap, f_cap, GRAY)

rows = (len(SHOTS_LIST) + 1) // 2
y += rows * (THUMB_H + 44) + (rows - 1) * 18 + 30
d.line((M, y, W - M, y), fill=LINE, width=2)
y += 28
d.text((M, y), "安装包  JieHuaFa-0.0.9.apk（227,316 字节）　·　词库 3948 条　·　自检 117/117",
       font=f_foot, fill=GRAY)
y += 34
d.text((M, y), "适配 Android 6.0+（minSdk 24）　·　可直接覆盖安装 0.0.8",
       font=f_foot, fill=GRAY)

y += 56
img = img.crop((0, 0, W, int(y)))
img.save(OUT, "PNG")
print(OUT, img.size)

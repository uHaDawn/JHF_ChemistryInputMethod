# -*- coding: utf-8 -*-
"""生成 0.0.10 单页更新图（白底 + 青色主题、直角元素）。

第 1~4 页：平板三布局 / 分离排版 / 悬浮拖动 / 手机自动适配 —— 用示意图绘制
（本次无平板实机截图）；第 5 页：真实截图 + 气泡示意；第 6 页：修复项对照。
"""
import math
import os
from PIL import Image, ImageDraw, ImageFont

BASE = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SHOTS = os.path.join(BASE, "shots")
OUT_DIR = SHOTS
VER = "0.0.10"
DATE = "2026-10-28"

TEAL = (15, 163, 163)
TEAL_DARK = (10, 120, 122)
TEAL_SOFT = (232, 247, 246)
PANEL = (248, 252, 251)
KEY = (255, 255, 255)
KEY_LINE = (196, 216, 214)
INK = (26, 33, 38)
GRAY = (108, 122, 129)
MUTED = (150, 162, 168)
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
f_lab = ImageFont.truetype(F_BOLD, 27)
f_note = ImageFont.truetype(F_REG, 23)
f_small = ImageFont.truetype(F_REG, 21)
f_chip = ImageFont.truetype(F_BOLD, 25)

W, H = 1080, 1440
M = 72
CW = W - 2 * M

# 雅黑缺字（上/下标、⁺ ⁻ 等）逐字符回退 Segoe UI
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


def ctext(d, cx, y, text, font, fill):
    draw_text(d, (cx - text_len(text, font) / 2, y), text, font, fill)


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


def dash_line(d, p0, p1, color, width=3, dash=14, gap=10):
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


def arrow(d, p0, p1, color=TEAL, width=3, head=12):
    d.line((p0[0], p0[1], p1[0], p1[1]), fill=color, width=width)
    ang = math.atan2(p1[1] - p0[1], p1[0] - p0[0])
    for s in (-0.5, 0.5):
        d.line((p1[0], p1[1],
                p1[0] - head * math.cos(ang + s), p1[1] - head * math.sin(ang + s)),
               fill=color, width=width)


def key_grid(d, box, cols, rows, gap=9, accent_rows=()):
    """在 box 内画一块直角键盘；accent_rows 指定哪几行末键用主题色。"""
    x, y, w, h = box
    kh = (h - (rows - 1) * gap) / rows
    kw = (w - (cols - 1) * gap) / cols
    for r in range(rows):
        for c in range(cols):
            kx = x + c * (kw + gap)
            ky = y + r * (kh + gap)
            accent = (r, c) in accent_rows
            d.rectangle((kx, ky, kx + kw, ky + kh),
                        fill=TEAL if accent else KEY,
                        outline=TEAL if accent else KEY_LINE, width=2)


def draw_flat_kb(d, box, gap=9):
    key_grid(d, box, 7, 4, gap)


def draw_split_kb(d, box):
    """分离模式：左 34.5% / 中 31% 留白 / 右 34.5%，右簇删除键 2.2 倍宽。"""
    x, y, w, h = box
    cw = w * 0.345
    key_grid(d, (x, y, cw, h), 4, 4, 9, accent_rows=((3, 3),))

    rx = x + w - cw
    gapk = 9
    rows = 4
    kh = (h - (rows - 1) * gapk) / rows
    for r in range(3):
        key_grid(d, (rx, y + r * (kh + gapk), cw, kh), 4, 1, gapk)
    # 第 4 行：3 个常规键 + 2.2 倍宽删除键
    sw = (cw - 3 * gapk) / 5.2
    for c in range(3):
        kx = rx + c * (sw + gapk)
        d.rectangle((kx, y + 3 * (kh + gapk), kx + sw, y + 3 * (kh + gapk) + kh),
                    fill=KEY, outline=KEY_LINE, width=2)
    bx = rx + 3 * (sw + gapk)
    d.rectangle((bx, y + 3 * (kh + gapk), bx + 2.2 * sw, y + 3 * (kh + gapk) + kh),
                fill=TEAL, outline=TEAL, width=2)
    return (bx, y + 3 * (kh + gapk), bx + 2.2 * sw, y + 3 * (kh + gapk) + kh)


def draw_float_kb(d, box):
    """悬浮：候选栏 + 一张卡片（卡片内为键盘）。"""
    x, y, w, h = box
    bar_h = 34
    d.rectangle((x, y, x + w, y + bar_h), fill=WHITE, outline=LINE, width=2)
    d.rectangle((x + 12, y + 10, x + 12 + 92, y + bar_h - 10), fill=TEAL_SOFT, outline=LINE, width=1)
    d.rectangle((x + 116, y + 10, x + 116 + 128, y + bar_h - 10), fill=TEAL_SOFT, outline=LINE, width=1)
    cy = y + bar_h + 12
    d.rectangle((x, cy, x + w, y + h), fill=WHITE, outline=TEAL, width=2)
    key_grid(d, (x + 14, cy + 14, w - 28, y + h - cy - 28), 7, 3, 8)


def device_frame(d, box, fill=PANEL):
    d.rectangle(box, fill=fill, outline=LINE, width=2)


def page_shell(idx, title, desc):
    img = Image.new("RGB", (W, H), WHITE)
    d = ImageDraw.Draw(img)
    d.text((M, 54), "接化发 · 化学输入法", font=f_app, fill=INK)
    pill = "v" + VER
    pw = text_len(pill, f_ver) + 34
    d.rectangle((W - M - pw, 50, W - M, 92), fill=TEAL)
    draw_text(d, (W - M - pw + 17, 57), pill, f_ver, WHITE)
    d.line((M, 118, W - M, 118), fill=TEAL, width=3)

    bx, by = M, 156
    d.rectangle((bx, by, bx + 68, by + 68), fill=TEAL)
    num = "%02d" % idx
    d.text((bx + 34 - f_page.getlength(num) / 2, by + 18), num, font=f_page, fill=WHITE)
    draw_text(d, (M + 96, 162), title, f_title, INK)

    ty = 254
    for ln in wrap(desc, f_desc, CW):
        draw_text(d, (M, ty), ln, f_desc, GRAY)
        ty += 46
    return img, d


def page_footer(d, idx, total):
    d.line((M, 1352, W - M, 1352), fill=LINE, width=2)
    d.text((M, 1378), "接化发 · 化学输入法　v%s　·　%s" % (VER, DATE), font=f_foot, fill=GRAY)
    label = "%02d / %02d" % (idx, total)
    d.text((W - M - f_page.getlength(label), 1374), label, font=f_page, fill=TEAL)


def chip(d, cx, y, text, filled=False):
    w = text_len(text, f_chip) + 40
    box = (cx - w / 2, y, cx + w / 2, y + 44)
    d.rectangle(box, fill=TEAL if filled else WHITE, outline=TEAL, width=2)
    ctext(d, cx, y + 7, text, f_chip, WHITE if filled else TEAL_DARK)


# ---------- 01 平板三种布局 ----------
def page1(idx, total, title, desc):
    img, d = page_shell(idx, title, desc)
    top = 470
    gap = 24
    pw = (CW - 2 * gap) / 3
    ph = 470
    labels = ["平铺", "悬浮", "分离"]
    for i in range(3):
        x = M + i * (pw + gap)
        device_frame(d, (x, top, x + pw, top + ph))
        if i == 0:
            draw_flat_kb(d, (x + 14, top + ph - 260, pw - 28, 240))
        elif i == 1:
            draw_float_kb(d, (x + 34, top + ph - 330, pw - 68, 282))
        else:
            draw_split_kb(d, (x + 14, top + ph - 260, pw - 28, 240))
        ctext(d, x + pw / 2, top + ph + 26, labels[i], f_lab, INK)

    note = "平板设置页出现「键盘布局」卡片，三种模式切换后键盘即时重建"
    ctext(d, W / 2, top + ph + 140, note, f_note, GRAY)
    chip(d, W / 2, top + ph + 200, "平板专属", filled=True)
    page_footer(d, idx, total)
    return img


# ---------- 02 分离模式排版 ----------
def page2(idx, total, title, desc):
    img, d = page_shell(idx, title, desc)
    top = 470
    x, w = M, CW
    box = (x, top, x + w, top + 430)
    device_frame(d, box)
    x0, ww = x + 16, w - 32
    kb_box = (x0, top + 170, ww, 230)
    draw_split_kb(d, kb_box)

    # 宽度标注（与键盘实际分段对齐）
    ly = top + 100
    cw = ww * 0.345
    gapw = ww * 0.31
    segs = [(x0, cw, "34.5%", TEAL_DARK), (x0 + cw, gapw, "31% 握持留白", MUTED),
            (x0 + cw + gapw, cw, "34.5%", TEAL_DARK)]
    for sx, sw, lab, col in segs:
        d.line((sx, ly, sx + sw, ly), fill=col, width=2)
        for ex in (sx, sx + sw):
            d.line((ex, ly - 6, ex, ly + 6), fill=col, width=2)
        ctext(d, sx + sw / 2, ly + 12, lab, f_small, col)

    ty = top + 470
    notes = [
        "左右两簇各含完整按键，中间 31% 留作握持区，避免误触",
        "右簇删除键加宽至 2.2 倍键宽，便于单手盲打",
    ]
    y = ty
    for n in notes:
        d.rectangle((M, y + 6, M + 8, y + 22), fill=TEAL)
        draw_text(d, (M + 24, y), n, f_note, GRAY)
        y += 52
    chip(d, W / 2, y + 24, "分离模式", filled=True)
    page_footer(d, idx, total)
    return img


# ---------- 03 悬浮键盘任意拖动 ----------
def page3(idx, total, title, desc):
    img, d = page_shell(idx, title, desc)
    top = 470
    fw, fh = 420, 640
    fx = (W - fw) / 2
    device_frame(d, (fx, top, fx + fw, top + fh))
    card = (fx + 40, top + 210, fx + fw - 40, top + 500)
    draw_float_kb(d, (card[0] + 8, card[1] - 56, card[2] - card[0] - 16, card[3] - card[1] + 56))

    # 拖动范围虚线与四向箭头
    dash_rect(d, (fx + 14, top + 14, fx + fw - 14, top + fh - 14), MUTED, 2)
    cx = (card[0] + card[2]) / 2
    cy = (card[1] + card[3]) / 2
    arrow(d, (cx, cy - 90), (cx, top + 34))
    arrow(d, (cx, cy + 96), (cx, top + fh - 34))
    arrow(d, (card[0] - 6, cy), (fx + 40, cy))
    arrow(d, (card[2] + 6, cy), (fx + fw - 40, cy))
    lab = "上下左右任意拖动"
    lw2 = text_len(lab, f_chip) + 20
    d.rectangle((W / 2 - lw2 / 2, top + 34, W / 2 + lw2 / 2, top + 78), fill=WHITE)
    ctext(d, W / 2, top + 44, lab, f_chip, TEAL_DARK)

    y = top + fh + 30
    notes = [
        "候选栏固定在卡片上方，随拖动同步移动，不再出现空白",
        "横竖屏统一 420dp 宽（上限为屏宽减 16dp），浮窗尺寸一致",
    ]
    for n in notes:
        d.rectangle((M, y + 6, M + 8, y + 22), fill=TEAL)
        draw_text(d, (M + 24, y), n, f_note, GRAY)
        y += 52
    page_footer(d, idx, total)
    return img


# ---------- 04 手机自动适配 ----------
def page4(idx, total, title, desc):
    img, d = page_shell(idx, title, desc)
    top = 470
    # 竖屏
    pw, ph = 300, 470
    px = M + 90
    device_frame(d, (px, top, px + pw, top + ph))
    draw_flat_kb(d, (px + 12, top + ph - 180, pw - 24, 160))
    ctext(d, px + pw / 2, top + 26, "竖屏", f_lab, INK)
    ctext(d, px + pw / 2, top + ph + 20, "自动使用平铺", f_small, TEAL_DARK)

    # 横屏
    lw, lh = 440, 260
    lx = W - M - lw
    ly = top + 140
    device_frame(d, (lx, ly, lx + lw, ly + lh))
    draw_float_kb(d, (lx + 90, ly + 70, 260, 160))
    ctext(d, lx + lw / 2, ly - 40, "横屏", f_lab, INK)
    ctext(d, lx + lw / 2, ly + lh + 20, "自动使用悬浮", f_small, TEAL_DARK)

    y = top + ph + 90
    draw_text(d, (M, y), "手机设备自动隐藏「键盘布局」卡片，无需手动切换，也不提供手动入口", f_note, GRAY)
    chip(d, W / 2, y + 60, "手机自适应", filled=True)
    page_footer(d, idx, total)
    return img


# ---------- 05 深浅色跟随系统 ----------
def page5(idx, total, title, desc):
    img, d = page_shell(idx, title, desc)
    top = 470
    CROP = (0, 700, 1220, 1150)
    src = Image.open(os.path.join(BASE, "tmp_s7.png")).convert("RGB").crop(CROP)
    iw = CW
    ih = int(iw * (CROP[3] - CROP[1]) / (CROP[2] - CROP[0]))
    src = src.resize((iw, ih), Image.LANCZOS)
    d.rectangle((M - 4, top - 4, M + iw + 4, top + ih + 4), outline=LINE, width=2)
    img.paste(src, (M, top))

    # 气泡示意：浅色 / 深色 / 跟随系统
    by = top + ih + 46
    bw = 420
    bx = (W - bw) / 2
    opts = ["浅色", "深色", "跟随系统"]
    rows = len(opts)
    bh = 48 * rows + 66
    d.rectangle((bx, by, bx + bw, by + bh), fill=WHITE, outline=TEAL, width=2)
    draw_text(d, (bx + 24, by + 16), "深浅色模式", f_chip, INK)
    oy = by + 72
    for i, o in enumerate(opts):
        row = (bx, oy - 8, bx + bw, oy + 40)
        if o == "跟随系统":
            d.rectangle(row, fill=TEAL_SOFT)
        draw_text(d, (bx + 24, oy), o, f_chip, TEAL_DARK if o == "跟随系统" else INK)
        if o == "跟随系统":
            mx = bx + bw - 56
            d.line((mx, oy + 16, mx + 8, oy + 25), fill=TEAL, width=3)
            d.line((mx + 8, oy + 25, mx + 22, oy + 5), fill=TEAL, width=3)
        oy += 48
    tip = "新增第三项，随系统深浅色自动切换"
    ctext(d, W / 2, by + bh + 24, tip, f_small, MUTED)
    page_footer(d, idx, total)
    return img


# ---------- 06 修复项 ----------
def page6(idx, total, title, desc):
    img, d = page_shell(idx, title, desc)
    top = 470
    items = [
        ("悬浮模式候选栏空白",
         "浮窗改为铺满整屏的输入视图，卡片作为子 View 呈现，候选栏正常显示",
         "修复前：候选栏高度为 0", "修复后：候选正常显示"),
        ("拖动只能上下",
         "修正窗口坐标换算，改用 WindowManager 取屏幕尺寸，拖动中不再刷新触摸区域",
         "修复前：只能上下移动", "修复后：四向任意拖动"),
    ]
    y = top
    for name, fix, before, after in items:
        d.rectangle((M, y, W - M, y + 232), fill=PANEL, outline=LINE, width=2)
        d.rectangle((M, y, M + 8, y + 232), fill=TEAL)
        draw_text(d, (M + 32, y + 26), name, f_lab, INK)
        yy = y + 80
        for ln in wrap(fix, f_small, CW - 80):
            draw_text(d, (M + 32, yy), ln, f_small, GRAY)
            yy += 36
        bchip = before
        achip = after
        bw = text_len(bchip, f_small) + 32
        ctext(d, M + 32 + bw / 2, y + 176, bchip, f_small, MUTED)
        d.rectangle((M + 32 + bw + 8, y + 180, M + 32 + bw + 40, y + 182), fill=MUTED)
        arrow(d, (M + 32 + bw + 44, y + 181), (M + 32 + bw + 78, y + 181), MUTED, 2, 8)
        ctext(d, M + 32 + bw + 44 + 52 + text_len(achip, f_small) / 2, y + 176, achip, f_small, TEAL_DARK)
        y += 258
    chip(d, W / 2, y + 40, "悬浮模式恢复正常", filled=True)
    ctext(d, W / 2, y + 116, "两处问题都在实机上复测通过，悬浮模式的候选与拖动现在均正常", f_note, GRAY)
    page_footer(d, idx, total)
    return img


PAGES = [
    ("平板键盘三种布局",
     "平板设置页新增「键盘布局」卡片，可选平铺、悬浮、分离三种模式，切换到哪一项键盘就即时重建，不必重启输入法。",
     page1),
    ("分离模式的排版",
     "分离模式下左右两簇按键各占可用宽度 34.5%，中间留出 31% 给双手握持；两侧都带完整按键，右簇删除键加宽到 2.2 倍键宽。",
     page2),
    ("悬浮键盘可任意拖动",
     "悬浮模式不再只能上下挪动：整张卡片可上下左右拖动，候选栏始终贴在卡片上方一起走；横竖屏统一 420dp 宽，横屏不再过宽。",
     page3),
    ("手机自动适配布局",
     "检测到手机设备时自动隐藏「键盘布局」卡片，横屏切到悬浮布局、竖屏切回平铺布局，全程无需手动设置。",
     page4),
    ("深浅色模式跟随系统",
     "深浅色模式由两态扩为浅色、深色、跟随系统三选一；选「跟随系统」后随系统设置自动在深浅色之间切换。",
     page5),
    ("顺带修掉的两处问题",
     "悬浮模式候选栏显示空白、键盘只能上下拖动，这两处阻断使用的问题本版一并修复，悬浮模式现在可以正常拖动和查看候选。",
     page6),
]

if __name__ == "__main__":
    for i, (t, s, fn) in enumerate(PAGES, 1):
        img = fn(i, len(PAGES), t, s)
        out = os.path.join(OUT_DIR, "v10_page_%02d.png" % i)
        img.save(out, "PNG")
        print(out, img.size)

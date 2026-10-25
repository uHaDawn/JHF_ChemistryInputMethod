# -*- coding: utf-8 -*-
"""校验符号页左侧分类 tab 的高亮状态。

用法：
    python tools/verify_tabs.py 截图.png:期望下标 [更多...]
    python tools/verify_tabs.py --scan 截图.png      # 打印主题色像素的纵向分布

原理：分类 tab 选中态为主题色填充，未选中为白底；在每项中心取样一条横带，
统计主题色像素占比，占比最高者即为当前高亮项。
"""
import io
import os
import sys

import numpy as np
from PIL import Image

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

# 与 ChemKeyboardView 的 tabW/tabGap 及 IME 窗口布局对应（1080x2400 @420dpi）
# 分类栏是一张卡片，5 项在卡内均分（无间隙），底边与右侧功能行底边对齐
TAB_X0, TAB_X1 = 30, 320
TAB_CENTERS_Y = [1602, 1717, 1832, 1947, 2062]
CAT_NAMES = ["度量", "物质", "反应", "角标", "其他"]
ACCENT = np.array([5, 190, 193])          # CuCl₂ 主题色
TOL = 40
SCAN_FROM, SCAN_TO = 1540, 2130           # 只扫 tab 区域，避开候选栏的 化|符 芯片与底部 ⇌ 条


def accent_rows(path):
    """返回 {y: 该行 tab 区域内主题色占比}。"""
    a = np.asarray(Image.open(path).convert("RGB")).astype(int)
    sub = a[SCAN_FROM:SCAN_TO, TAB_X0:TAB_X1]
    m = (np.abs(sub - ACCENT).sum(axis=2) < TOL).mean(axis=1)
    return {y + SCAN_FROM: float(v) for y, v in enumerate(m)}


def scan(path):
    rows = accent_rows(path)
    hot = [y for y, v in rows.items() if v > 0.5]
    print(f"== {os.path.basename(path)} 主题色行区间 ==")
    if not hot:
        print("  无（该页可能不是符号页，或高亮色不同）")
        return
    # 合并连续区间
    start = prev = hot[0]
    for y in hot[1:] + [None]:
        if y != prev + 1:
            print(f"  y {start} .. {prev}   中心 {(start + prev) // 2}")
            start = y
        prev = y


def ratios_at_tabs(path):
    rows = accent_rows(path)
    return [round(float(np.mean([rows.get(y + d, 0.0) for d in range(-10, 11)])), 3)
            for y in TAB_CENTERS_Y]


def check(spec):
    path, _, expect = spec.rpartition(":")
    expect = int(expect)
    ratios = ratios_at_tabs(path)
    best = int(np.argmax(ratios))
    ok = (best == expect) and ratios[expect] > 0.5
    print(f"{os.path.basename(path):24s} 期望={CAT_NAMES[expect]:<4s} 实测={CAT_NAMES[best]:<4s} "
          f"占比={ratios} {'OK' if ok else 'FAIL'}")
    return ok


if __name__ == "__main__":
    args = sys.argv[1:]
    if not args:
        print(__doc__)
        sys.exit(1)
    if args[0] == "--scan":
        for p in args[1:]:
            scan(p)
        sys.exit(0)
    results = [check(s) for s in args]      # 先全部执行，避免 all() 短路
    print("==> " + ("ALL OK" if all(results) else "HAS FAIL"))
    sys.exit(0 if all(results) else 1)

# -*- coding: utf-8 -*-
"""校验主题基线截图。

客户意见 9 之后：状态栏与页面背景同色（不再用主题深色），
所以这里改为断言「状态栏 == 背景 == 深浅色对应值」，
主题色则改用「当前主题」条的填充色来验证（它在深浅色下都是主题色）。

用法：
    python tools/verify_theme.py [基线目录，默认 tests/golden]
"""
import io
import os
import sys

import numpy as np
from PIL import Image

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

ACCENT = {
    "agbr": (231, 196, 0),      # #E7C400
    "kmno4": (143, 45, 185),    # #8F2DB9
    "cucl2": (5, 190, 193),     # #05BEC1
    "fe2o3": (185, 45, 45),     # #B92D2D
}
BG = {"light": (234, 234, 234), "dark": (49, 49, 49)}
STATUS_Y, BG_Y, ABOUT_BG_Y, X = 30, 1600, 1800, 540
THEME_BAR_Y, THEME_BAR_X = 650, 540     # 「当前主题」条的主题色填充区


def px(img, y, x):
    return tuple(int(v) for v in np.asarray(img.convert("RGB"))[y, x])


def near(a, b, tol=12):
    return sum(abs(int(p) - int(q)) for p, q in zip(a, b)) <= tol


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "tests/golden"
    ok = True
    print("== 主界面：4 主题 x 深浅色 ==")
    for mode in ("light", "dark"):
        for theme in ("agbr", "kmno4", "cucl2", "fe2o3"):
            p = os.path.join(d, f"main_{mode}_{theme}.png")
            if not os.path.exists(p):
                print(f"MISS main_{mode}_{theme}.png")
                ok = False
                continue
            im = Image.open(p)
            s, b, t = px(im, STATUS_Y, X), px(im, BG_Y, X), px(im, THEME_BAR_Y, THEME_BAR_X)
            good = near(s, BG[mode]) and near(b, BG[mode]) and near(t, ACCENT[theme])
            ok = ok and good
            print(f"{'OK  ' if good else 'FAIL'} main_{mode}_{theme:6s} "
                  f"状态栏{s}(期望={BG[mode]}) | 背景{b} | 主题条{t}(期望={ACCENT[theme]})")
    print("== 关于页 ==")
    for mode in ("light", "dark"):
        p = os.path.join(d, f"about_{mode}.png")
        if not os.path.exists(p):
            print(f"MISS about_{mode}.png")
            ok = False
            continue
        im = Image.open(p)
        s = px(im, STATUS_Y, X)
        b = px(im, ABOUT_BG_Y, X)
        good = near(b, BG[mode]) and near(s, BG[mode])
        ok = ok and good
        print(f"{'OK  ' if good else 'FAIL'} about_{mode:5s} 状态栏{s} 背景{b} 期望{BG[mode]}")
    print("==> " + ("ALL OK" if ok else "HAS FAIL"))
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()

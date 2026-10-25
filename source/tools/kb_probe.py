# -*- coding: utf-8 -*-
"""从 adb 截图里定位键盘键位，输出可直接喂给 `adb shell input tap` 的屏幕坐标。

原理：按键是纯白 (#FFFFFF)，键盘底是 #EAEAEA。先按行投影找出「键行」，
再在键行内按列投影找出每个「键」的水平区间，取行/列中心即键中心。
不依赖 dp 换算，直接反映真实渲染结果，因此也可用于布局回归校验。

用法：
    python tools/kb_probe.py shot.png            # 列出所有键
    python tools/kb_probe.py shot.png --row 0    # 只看第 0 行（Q 行）
"""
import argparse
import io
import sys

import numpy as np
from PIL import Image

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")


def find_keys(img, white_min=248, row_ratio=0.35, col_ratio=0.3, min_key_px=40):
    a = np.asarray(img.convert("RGB"))
    h, w, _ = a.shape
    mask = (a[:, :, 0] >= white_min) & (a[:, :, 1] >= white_min) & (a[:, :, 2] >= white_min)

    # --- 行投影：找键行 ---
    rowsum = mask.sum(axis=1)
    rows = []
    y = 0
    while y < h:
        if rowsum[y] > w * row_ratio:
            y0 = y
            while y < h and rowsum[y] > w * row_ratio:
                y += 1
            rows.append((y0, y))
        else:
            y += 1

    out = []
    for ri, (y0, y1) in enumerate(rows):
        band = mask[y0:y1, :]
        colsum = band.sum(axis=0)
        thr = (y1 - y0) * col_ratio
        keys = []
        x = 0
        while x < w:
            if colsum[x] > thr:
                x0 = x
                while x < w and colsum[x] > thr:
                    x += 1
                if x - x0 >= min_key_px:
                    keys.append((x0, x))
            else:
                x += 1
        if keys:
            cy = (y0 + y1) // 2
            out.append({
                "row": ri, "y": (y0, y1), "cy": cy,
                "keys": [((x0 + x1) // 2, cy, (x0, x1)) for x0, x1 in keys],
            })
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("png")
    ap.add_argument("--row", type=int, default=None)
    args = ap.parse_args()

    img = Image.open(args.png)
    print(f"截图尺寸: {img.size[0]}x{img.size[1]}")
    for r in find_keys(img):
        if args.row is not None and r["row"] != args.row:
            continue
        print(f'\n行 {r["row"]}  y={r["y"][0]}..{r["y"][1]} (中心 {r["cy"]})  '
              f'共 {len(r["keys"])} 键')
        print("  中心坐标: " + "  ".join(f"{x},{y}" for x, y, _ in r["keys"]))


if __name__ == "__main__":
    main()

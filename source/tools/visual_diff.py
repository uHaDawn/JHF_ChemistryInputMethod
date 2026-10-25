# -*- coding: utf-8 -*-
"""截图视觉回归比对。

用法：
    python tools/visual_diff.py 基线.png 当前.png
    python tools/visual_diff.py 基线.png 当前.png --out diff.png --threshold 0.5
    python tools/visual_diff.py tests/golden tests/current   # 整目录成对比较

判定：单通道差超过 tolerance 的像素占比 <= threshold(%) 视为通过。
差异图把超差像素标成洋红，便于肉眼定位。
"""
import argparse
import io
import os
import sys

import numpy as np
from PIL import Image

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

# 键盘类截图（kbd_* / it_* 的键盘用例）里，IME 窗口从 y≈1390 起。
# 上半屏是宿主 App（Chrome），会随剪贴板提示、搜索建议行等随机内容变化，不参与比对。
# 之前取 1326 偏小，Chrome 内容带（1326~1386）被算进来，实测造成 5.87% 假差异。
KB_IGNORE_TOP = 1390
KEYBOARD_SHOT_PREFIXES = ("kbd_", "it_letter_page", "it_symbol_page",
                          "it_pinyin_candidates", "it_before_longpress",
                          "it_longpress_popup", "it_symtab_")


def is_keyboard_shot(name):
    return name.startswith(KEYBOARD_SHOT_PREFIXES)


def compare(a_path, b_path, out_path, threshold, tolerance, ignore_top=0, quiet=False):
    A = np.asarray(Image.open(a_path).convert("RGB")).astype(int)
    B = np.asarray(Image.open(b_path).convert("RGB")).astype(int)
    name = os.path.basename(a_path)
    if A.shape != B.shape:
        print(f"FAIL {name}: 尺寸不同 {A.shape} vs {B.shape}")
        return False
    if ignore_top:
        if ignore_top >= A.shape[0]:
            print(f"FAIL {name}: 忽略区 {ignore_top}px 已超过图高 {A.shape[0]}px，无可比区域")
            return False
        A, B = A[ignore_top:], B[ignore_top:]
    mask = np.abs(A - B).max(axis=2) > tolerance
    ratio = float(mask.mean()) * 100
    ok = ratio <= threshold
    if not quiet or not ok:
        print(f"{'OK  ' if ok else 'FAIL'} {name:32s} 差异 {ratio:7.3f}%  (容忍 {threshold}%)")
    if out_path is not None and not ok:
        vis = A.copy()
        vis[mask] = [255, 0, 255]
        Image.fromarray(vis.astype(np.uint8)).save(out_path)
    return ok


def compare_dirs(golden_dir, current_dir, out_dir, threshold, tolerance, ignore_top=0):
    ok_all = True
    # 跳过 _ 开头的临时文件（如键盘可见性探测图）
    names = sorted(f for f in os.listdir(golden_dir)
                   if f.endswith(".png") and not f.startswith("_"))
    if not names:
        print(f"基线目录为空：{golden_dir}")
        return False
    if out_dir:
        os.makedirs(out_dir, exist_ok=True)
    for f in names:
        b = os.path.join(current_dir, f)
        if not os.path.exists(b):
            print(f"FAIL {f:32s} 缺少当前截图")
            ok_all = False
            continue
        # 键盘基线只比对键盘区域：上半屏是 Chrome 页面，会随剪贴板提示等随机内容变化
        top = KB_IGNORE_TOP if is_keyboard_shot(f) else ignore_top
        out = os.path.join(out_dir, "diff_" + f) if out_dir else None
        if not compare(os.path.join(golden_dir, f), b, out, threshold, tolerance, top):
            ok_all = False
            if out:
                print(f"     差异图 -> {out}")
    return ok_all


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("golden")
    ap.add_argument("current")
    ap.add_argument("--out")
    ap.add_argument("--threshold", type=float, default=0.5, help="允许差异像素占比(%)")
    ap.add_argument("--tolerance", type=int, default=12, help="单通道容忍差(0-255)")
    ap.add_argument("--ignore-top", type=int, default=63,
                    help="忽略顶部像素行数（默认 63 = 系统状态栏，其时钟/图标非本产品 UI）")
    args = ap.parse_args()

    if os.path.isdir(args.golden):
        ok = compare_dirs(args.golden, args.current, args.out,
                          args.threshold, args.tolerance, args.ignore_top)
    else:
        out = args.out or (os.path.splitext(args.current)[0] + "_diff.png")
        ok = compare(args.golden, args.current, out,
                     args.threshold, args.tolerance, args.ignore_top)
        if not ok:
            print(f"     差异图 -> {out}")
    print("==> " + ("ALL OK" if ok else "HAS FAIL"))
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()

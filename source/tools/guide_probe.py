"""操作引导面板对齐校验。

用户反馈「左侧文案与右侧按钮不垂直对齐」，但 uiautomator dump 给的是控件外框
（文案 TextView 高度 = 行高 57dp），反映不出文字基线的偏移，所以必须回到像素。

原理：在文案控件的矩形内找深色文字像素、在按钮控件内缩后找按钮文字像素，
比较两者的垂直中心。差值 > 2px 即判定错位。

用法:
    python tools/guide_probe.py <shot.png> <dump.xml>
"""
import io
import re
import sys

import numpy as np
from PIL import Image

# 深色文字（文案 / 未完成态）与按钮文字（主题色，亮度约 135）的阈值
TEXT_MAX_LUMA = 150
BTN_MAX_LUMA = 220
BTN_INSET = 6          # 按钮内缩，排除 1.3dp 边框（约 3.4px）
TOLERANCE = 2.0


def nodes(xml_path):
    text = io.open(xml_path, encoding="utf-8").read()
    for m in re.finditer(r"<node[^>]*>", text):
        s = m.group(0)
        t = re.search(r'text="([^"]*)"', s)
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', s)
        if t is None or b is None:
            continue
        x1, y1, x2, y2 = map(int, b.groups())
        if x2 > x1 and y2 > y1:
            yield t.group(1), (x1, y1, x2, y2)


def luma(img, box):
    x1, y1, x2, y2 = box
    a = np.asarray(img.convert("RGB"))[y1:y2, x1:x2].astype(float)
    return 0.299 * a[:, :, 0] + 0.587 * a[:, :, 1] + 0.114 * a[:, :, 2]


def band(img, box, max_luma):
    """返回 [首个命中行, 末个命中行]（绝对 y），无命中返回 None。"""
    l = luma(img, box)
    rows = np.where((l < max_luma).sum(axis=1) > 0)[0]
    if rows.size == 0:
        return None
    return box[1] + int(rows[0]), box[1] + int(rows[-1])


def check(img, xml_path, label_text, btn_x_range=None):
    found = dict()
    for t, box in nodes(xml_path):
        if t == label_text:
            found.setdefault("label", box)
    if "label" not in found:
        return None
    lbox = found["label"]
    # 按钮 = 与文案同一水平带、位于文案右侧的控件。
    # 必须取重叠最大者：同一屏还有别的右对齐控件（如底部细条的 ⌄），
    # 只判断“在右侧 + 更窄”会误匹配，导致偏差算成 -152px。
    best, best_ov = None, 0
    for t, box in nodes(xml_path):
        if not t:
            continue
        bx1, by1, bx2, by2 = box
        if bx1 < lbox[2] - 8 or (bx2 - bx1) >= (lbox[2] - lbox[0]):
            continue
        ov = min(lbox[3], by2) - max(lbox[1], by1)
        if ov > best_ov:
            best, best_ov = (t, box), ov
    if best is None or best_ov < (lbox[3] - lbox[1]) * 0.3:
        return None
    found["btn"], found["btn_text"] = best[1], best[0]

    b = found["btn"]
    inner = (b[0] + BTN_INSET, b[1] + BTN_INSET, b[2] - BTN_INSET, b[3] - BTN_INSET)

    lb = band(img, lbox, TEXT_MAX_LUMA)
    bb = band(img, inner, BTN_MAX_LUMA)
    if lb is None or bb is None:
        return None

    lcen = (lb[0] + lb[1]) / 2.0
    bcen = (bb[0] + bb[1]) / 2.0
    return {
        "label": label_text,
        "label_band": lb, "label_center": lcen,
        "btn_text": found["btn_text"], "btn_box": b,
        "btn_band": bb, "btn_center": bcen,
        "delta": lcen - bcen,
    }


def main():
    # Windows 控制台默认 GBK，输出到特殊符号（如 ⌄）会抛 UnicodeEncodeError
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass
    if len(sys.argv) < 3:
        print(__doc__)
        return 2
    shot, dump = sys.argv[1], sys.argv[2]
    img = Image.open(shot)
    rows = []
    for text in ("启用 接化发输入法", "切换到 接化发输入法"):
        r = check(img, dump, text)
        if r:
            rows.append(r)

    if not rows:
        print("未在截图中定位到引导面板（输入法已启用时该面板不显示）")
        return 1

    print(f"{'行文案':<22s} {'文案中心':>8s} {'按钮中心':>8s} {'偏差':>8s}  判定")
    worst = 0.0
    for r in rows:
        ok = abs(r["delta"]) <= TOLERANCE
        worst = max(worst, abs(r["delta"]))
        print(f"{r['label']:<22s} {r['label_center']:>8.1f} {r['btn_center']:>8.1f} "
              f"{r['delta']:>+8.1f}  {'OK' if ok else 'FAIL'}")
        print(f"    文案[{r['label_band'][0]}..{r['label_band'][1]}]  "
              f"{r['btn_text']} 按钮[{r['btn_band'][0]}..{r['btn_band'][1]}]  "
              f"外框{r['btn_box']}")
    print(f"\n最大偏差 {worst:.1f}px（容忍 {TOLERANCE}px） -> "
          f"{'对齐通过' if worst <= TOLERANCE else '存在错位'}")
    return 0 if worst <= TOLERANCE else 1


if __name__ == "__main__":
    sys.exit(main())

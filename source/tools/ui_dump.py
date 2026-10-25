# -*- coding: utf-8 -*-
"""解析 `adb shell uiautomator dump` 产出的 UI 层级 XML。

用法：
    python tools/ui_dump.py ui.xml                 # 打印全部节点
    python tools/ui_dump.py ui.xml --find EditText # 只看某类节点
    python tools/ui_dump.py ui.xml --text 搜索     # 看文本含关键字的节点
输出含 center 坐标，可直接喂给 `adb shell input tap x y`。
"""
import argparse
import io
import re
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

NODE = re.compile(r"<node[^>]*>")
ATTR = re.compile(r'(\w[\w-]*)="([^"]*)"')


def bounds_center(b):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", b or "")
    if not m:
        return None
    x1, y1, x2, y2 = map(int, m.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("xml")
    ap.add_argument("--find", default=None, help="按 class 名过滤（子串匹配）")
    ap.add_argument("--text", default=None, help="按 text 过滤（子串匹配）")
    ap.add_argument("--clickable", action="store_true", help="只列可点击节点")
    ap.add_argument("--limit", type=int, default=200)
    args = ap.parse_args()

    src = io.open(args.xml, encoding="utf-8").read()
    shown = 0
    for raw in NODE.findall(src):
        a = dict(ATTR.findall(raw))
        cls = a.get("class", "").split(".")[-1]
        text = a.get("text", "")
        desc = a.get("content-desc", "")
        clickable = a.get("clickable") == "true"
        if args.find and args.find.lower() not in a.get("class", "").lower():
            continue
        if args.text and args.text not in text and args.text not in desc:
            continue
        if args.clickable and not clickable:
            continue
        c = bounds_center(a.get("bounds"))
        flag = "CLICK" if clickable else "     "
        print(f"{flag} {a.get('package',''):38s} {cls:20s} "
              f"{str(c):14s} {a.get('bounds',''):24s} text={text!r} desc={desc!r}")
        shown += 1
        if shown >= args.limit:
            print(f"... 已达上限 {args.limit}")
            break
    print(f"[共 {shown} 个节点]")


if __name__ == "__main__":
    main()

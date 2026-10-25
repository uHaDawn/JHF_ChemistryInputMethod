# -*- coding: utf-8 -*-
"""设计还原几何校验：把 uiautomator dump 的真实控件位置与 Figma 规格逐项比对。

用法：
    python tools/geometry_check.py --page main  dump/main.xml
    python tools/geometry_check.py --page about dump/about.xml
    python tools/geometry_check.py --page main --spec tools/figma_spec.json dump/main.xml

校验两类：
  size —— 控件自身宽高（dp），容差 tolerance_px
  gap  —— 上元素底边 → 下元素顶边的间距（dp），用于验证设计稿的卡片/行间距
"""
import argparse
import io
import json
import os
import re
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

NODE = re.compile(r"<node[^>]*>")
ATTR = re.compile(r'(\w[\w-]*)="([^"]*)"')
BOUNDS = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")


def parse(xml_path):
    """返回节点列表，每项含 text / class / bounds / 宽高。"""
    src = io.open(xml_path, encoding="utf-8").read()
    nodes = []
    for raw in NODE.findall(src):
        a = dict(ATTR.findall(raw))
        m = BOUNDS.match(a.get("bounds", ""))
        if not m:
            continue
        x1, y1, x2, y2 = map(int, m.groups())
        nodes.append({
            "text": a.get("text", ""),
            "cls": a.get("class", ""),
            "x1": x1, "y1": y1, "x2": x2, "y2": y2,
            "w": x2 - x1, "h": y2 - y1,
        })
    return nodes


def find(nodes, text, index):
    hits = [n for n in nodes if n["text"] == text]
    return hits[index] if index < len(hits) else None


class Report:
    def __init__(self):
        self.total = 0
        self.failed = []

    def add(self, ok, line):
        self.total += 1
        print(line)
        if not ok:
            self.failed.append(line)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("xml")
    ap.add_argument("--page", required=True, choices=["main", "about"])
    ap.add_argument("--spec", default=os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                                   "figma_spec.json"))
    args = ap.parse_args()

    spec = json.load(io.open(args.spec, encoding="utf-8"))
    dens = spec["density"]
    tol = spec["tolerance_px"]
    nodes = parse(args.xml)
    rep = Report()

    print(f"== 规格校验：{args.page}  ({os.path.basename(args.xml)}) "
          f"density={dens} 容差=±{tol}px ==")

    for c in spec["size_checks"]:
        if c["page"] != args.page:
            continue
        n = find(nodes, c["text"], 0)
        if n is None:
            rep.add(False, f"[MISS] {c['name']}: 未找到文本 {c['text']!r}")
            continue
        ew, eh = c["w"] * dens, c["h"] * dens
        dw, dh = abs(n["w"] - ew), abs(n["h"] - eh)
        ok = dw <= tol and dh <= tol
        rep.add(ok, f"[{'OK ' if ok else 'BAD'}] {c['name']:26s} 实测 {n['w']}x{n['h']}px "
                     f"期望 {ew:.0f}x{eh:.0f}px ({c['w']}x{c['h']}dp) 偏差 {dw:.0f}/{dh:.0f}px")

    for c in spec["gap_checks"]:
        if c["page"] != args.page:
            continue
        a = find(nodes, c["above"][0], c["above"][1])
        b = find(nodes, c["below"][0], c["below"][1])
        if a is None or b is None:
            rep.add(False, f"[MISS] {c['name']}: 元素未找到")
            continue
        actual_px = b["y1"] - a["y2"]
        actual = actual_px / dens
        d = abs(actual - c["gap"]) * dens
        ok = d <= tol
        rep.add(ok, f"[{'OK ' if ok else 'BAD'}] {c['name']:38s} 实测 {actual:.1f}dp "
                     f"期望 {c['gap']}dp 偏差 {d:.1f}px")

    print(f"==> {rep.total - len(rep.failed)}/{rep.total} 通过")
    sys.exit(0 if not rep.failed else 1)


if __name__ == "__main__":
    main()

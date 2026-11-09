#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""由 assets/pinyin_dict.txt 生成 Rime 加权词库 assets/rime/jhf_pinyin.dict.yaml。

背景：Rime 内置的 luna_pinyin.dict.yaml 是 librime 的 minimal 测试词库，
只有 29k 条、按字形顺序排列、除多音字外没有词频（「你好」「中国」都不在库里），
候选因此全是生僻字。接化发自带的 pinyin_dict.txt 有 57589 条按词频降序的词条，
这里把它转成 Rime 词典：同一词条的多读音各生成一条，主读音保留原词频，
次读音按 10% 降权，避免「长/重/行」等多音字压过常用读音。
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app", "src", "main", "assets", "pinyin_dict.txt")
DST = os.path.join(ROOT, "app", "src", "main", "assets", "rime", "jhf_pinyin.dict.yaml")

CODE_RE = re.compile(r"^[a-z]+(?: [a-z]+)*$")

HEADER = """\
# 接化发 · 普通输入法词库（由 source/tools/gen_rime_dict.py 自动生成，请勿手改）
# encoding: utf-8
name: jhf_pinyin
version: "1.0"
sort: by_weight
use_preset_vocabulary: false
...
"""


def main():
    entries = {}
    skipped = 0
    with open(SRC, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            parts = line.split("\t")
            if len(parts) != 3:
                skipped += 1
                continue
            text, codes, freq_s = parts
            try:
                freq = int(freq_s)
            except ValueError:
                skipped += 1
                continue
            if freq <= 0:
                continue
            readings = [c.strip() for c in codes.split("|") if c.strip()]
            for i, code in enumerate(readings):
                code = re.sub(r"\s+", " ", code)
                if not CODE_RE.match(code):
                    skipped += 1
                    continue
                weight = freq if i == 0 else max(1, freq // 10)
                key = (text, code)
                if key in entries and entries[key] >= weight:
                    continue
                entries[key] = weight

    lines = sorted(entries.items(), key=lambda kv: (-kv[1], kv[0][0]))
    os.makedirs(os.path.dirname(DST), exist_ok=True)
    with open(DST, "w", encoding="utf-8", newline="\n") as f:
        f.write(HEADER)
        for (text, code), weight in lines:
            f.write("%s\t%s\t%d\n" % (text, code, weight))
    print("written %s: %d entries (skipped %d bad lines)" % (DST, len(lines), skipped))


if __name__ == "__main__":
    sys.exit(main())

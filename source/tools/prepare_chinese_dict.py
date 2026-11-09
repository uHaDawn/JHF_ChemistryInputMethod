#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""由 CyIME 的权重表生成 rime-ice 1:1 词库（source/app/src/main/assets/rime）。

对齐 CyIME `app/build-logic/tasks-chinese.gradle.kts` 的 prepareChineseDictionaries：
  1. reweight：base / ext / 8105 / tencent 四表的词频整体换成 app/data 下的语料权重表；
  2. 领域词下限：cyime_domain_terms.tsv + cyime_user_fixes.tsv，已有词抬到下限，缺失词进 cyime_terms；
  3. 垃圾词剔除：cyime_removed_words.txt.gz（17.36 万条）；
  4. 造句模式「…完了/好了 + 吗/没」的下限（吗 1000，没 500）；
  5. cyime_di：把「慢慢地/默默地」这类副词「地」按 di 再收一份。

与 CyIME 的差异：CyIME 是就地改写上游 *.dict.yaml；本仓库上游副本下载不完整（base 16.6 MB
只拿到 3.4 MB），而权重表本身就是「词 + 编码 + 权重」的全量键集（8105 表 8757 条与上游
8105.dict.yaml 逐条一致），所以这里直接由权重表重建四张表，结果与就地改写等价。

编码约定沿用上游：üe 用 nve/lve（8105 字表注音即如此），不做 nue/lue 重映射。
"""
import gzip
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DELIVERY = os.path.dirname(ROOT)
DATA = os.path.join(DELIVERY, "rime", "app", "data")
RIME_ASSETS = os.path.join(ROOT, "app", "src", "main", "assets", "rime")
OUT_DIR = os.path.join(RIME_ASSETS, "cn_dicts")

# 表名 -> (权重表, 上游版本号)
TABLES = {
    "8105": ("rime_ice_8105_weights", "2026-08-16"),
    "base": ("rime_ice_base_weights", "2026-09-11"),
    "ext": ("rime_ice_ext_weights", "2026-09-11"),
    "tencent": ("rime_ice_tencent_weights", "2026-09-11"),
}
NO_CODE_TABLES = {"tencent"}
REMOVED_TABLES = ("base", "ext", "tencent")

RESULT_QUESTION = re.compile(r"^(.{1,3})(完了|好了)(吗|没)$")

CYIME_TERMS = [
    ("浮窗", "fu chuang", 10000), ("悬浮工具栏", "xuan fu gong ju lan", 5000),
    ("语音上屏", "yu yin shang ping", 5000), ("自动互联", "zi dong hu lian", 5000),
    ("候选窗", "hou xuan chuang", 5000), ("候选框", "hou xuan kuang", 5000),
    ("流式识别", "liu shi shi bie", 5000), ("语音转写", "yu yin zhuan xie", 5000),
    ("九宫格输入", "jiu gong ge shu ru", 5000), ("数字小键盘", "shu zi xiao jian pan", 5000),
    ("语言键", "yu yan jian", 5000), ("换行键", "huan hang jian", 5000),
    ("表情键", "biao qing jian", 5000), ("中英键", "zhong ying jian", 5000),
    ("上档键", "shang dang jian", 5000), ("大小写键", "da xiao xie jian", 5000),
    ("逗号键", "dou hao jian", 5000), ("句号键", "ju hao jian", 5000),
    ("剪贴板键", "jian tie ban jian", 5000), ("收起键", "shou qi jian", 5000),
    ("全选键", "quan xuan jian", 5000), ("撤销键", "che xiao jian", 5000),
    ("拼音键", "pin yin jian", 5000), ("手写键", "shou xie jian", 5000),
    ("地球键", "di qiu jian", 5000), ("搜索键", "sou suo jian", 5000),
    ("十四键", "shi si jian", 5000), ("悬浮键盘", "xuan fu jian pan", 5000),
    ("单手键盘", "dan shou jian pan", 5000), ("键盘高度", "jian pan gao du", 5000),
]


def read_weights(name):
    """权重表 -> {(词, 编码): 权重}；同一键取最大权重。"""
    path = os.path.join(DATA, name + ".tsv.gz")
    out = {}
    with gzip.open(path, "rt", encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line or line.startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) == 3:
                key, weight = (parts[0], parts[1]), parts[2]
            elif len(parts) == 2:
                key, weight = (parts[0], None), parts[1]
            else:
                continue
            try:
                w = int(weight)
            except ValueError:
                continue
            if not key[0] or w <= 0:
                continue
            if key not in out or out[key] < w:
                out[key] = w
    return out


def read_floors():
    """领域词下限：{(词, 编码): 权重}，同键取最大。"""
    floors = {}
    for fn in ("cyime_domain_terms.tsv", "cyime_user_fixes.tsv"):
        with open(os.path.join(DATA, fn), "r", encoding="utf-8") as f:
            for line in f:
                line = line.rstrip("\n")
                if not line or line.startswith("#"):
                    continue
                parts = line.split("\t")
                if len(parts) != 3:
                    print("  [warn] %s 行格式不对，跳过: %r" % (fn, line))
                    continue
                try:
                    w = int(parts[2])
                except ValueError:
                    continue
                key = (parts[0], parts[1])
                if key not in floors or floors[key] < w:
                    floors[key] = w
    return floors


def read_removed():
    path = os.path.join(DATA, "cyime_removed_words.txt.gz")
    with gzip.open(path, "rt", encoding="utf-8") as f:
        return {line.rstrip("\n") for line in f if line.strip()}


def write_dict(name, version, entries, columns=None):
    path = os.path.join(OUT_DIR, name + ".dict.yaml")
    lines = [
        "# 接化发 · 中文词库（由 source/tools/prepare_chinese_dict.py 生成，请勿手改）",
        "# 数据来源：rime-ice（iDvel/rime-ice @9e66b072）+ CyIME 语料权重表",
        "---",
        "name: " + name,
        'version: "%s"' % version,
        "sort: by_weight",
    ]
    if columns:
        lines.append("columns:")
        lines.extend("  - " + c for c in columns)
    lines.append("...")
    head = "\n".join(lines) + "\n"
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(head)
        for word, code, weight in entries:
            if code:
                f.write("%s\t%s\t%d\n" % (word, code, weight))
            else:
                f.write("%s\t%d\n" % (word, weight))
    return path, len(entries)


def main():
    os.makedirs(OUT_DIR, exist_ok=True)

    tables = {name: read_weights(src) for name, (src, _) in TABLES.items()}
    for name, entries in tables.items():
        print("table %-8s %d" % (name, len(entries)))

    # 音节白名单：8105 字表的编码列（nve/lve 即上游标准写法）
    whitelist = {code for (_, code) in tables["8105"] if code}
    print("syllable whitelist %d" % len(whitelist))

    floors = read_floors()
    # 「测完了吗 / 没」这类句式：已有条目抬到 1000（吗）/ 500（没）
    for name in ("base", "ext"):
        for (word, code), weight in tables[name].items():
            if code and RESULT_QUESTION.match(word):
                floor = 1000 if word.endswith("吗") else 500
                key = (word, code)
                if key not in floors or floors[key] < floor:
                    floors[key] = floor
    print("domain floors %d" % len(floors))

    removed = read_removed()

    out = {}
    reweighted = {}
    dropped_syllable = 0
    dropped_removed = 0
    for name, entries in tables.items():
        syllabic = name not in NO_CODE_TABLES
        kept = []
        for (word, code), weight in entries.items():
            if syllabic and code:
                syllables = code.split(" ")
                if any(s not in whitelist for s in syllables):
                    dropped_syllable += 1
                    continue
            reweighted[name] = reweighted.get(name, 0) + 1
            if name in REMOVED_TABLES and word in removed:
                dropped_removed += 1
                continue
            kept.append((word, code or "", weight))
        out[name] = kept

    # 领域词下限 + 记录已有键，缺失的进 cyime_terms
    present = set()
    domain_texts = {}
    for (word, code) in floors:
        domain_texts[word] = (word, code)
    for name, entries in out.items():
        for i, (word, code, weight) in enumerate(entries):
            key = (word, code) if code else domain_texts.get(word)
            floor = floors.get(key) if key else None
            if floor is None:
                continue
            present.add(key)
            if weight < floor:
                entries[i] = (word, code, floor)

    # cyime_terms：固定词表 + 上游缺失的领域词
    terms = list(CYIME_TERMS)
    for (word, code), weight in floors.items():
        if (word, code) not in present:
            terms.append((word, code, weight))

    # cyime_di：副词「地」按 di 再收一份
    di = []
    for name in ("base", "ext"):
        for word, code, weight in out[name]:
            if len(word) >= 2 and word.endswith("地") and code.endswith(" de"):
                di.append((word, code[:-3] + " di", weight))

    # 断言：四表重写条数 ≥ 权重表 99%，防止上游换版后静默失效
    for name, entries in tables.items():
        got = reweighted.get(name, 0)
        need = len(entries) * 99 // 100
        if got < need:
            print("FAIL %s: %d entries < %d (99%% of table)" % (name, got, need))
            return 1

    if len(di) < 500:
        print("FAIL cyime_di: only %d entries" % len(di))
        return 1

    sizes = {}
    for name, entries in out.items():
        columns = ["text", "weight"] if name in NO_CODE_TABLES else None
        rows = sorted(entries, key=lambda e: (-e[2], e[0]))
        path, n = write_dict(name, TABLES[name][1], rows, columns)
        sizes[name] = (n, os.path.getsize(path))
    rows = sorted(set(terms), key=lambda e: (-e[2], e[0]))
    path, n = write_dict("cyime_terms", "2026-10-01", rows)
    sizes["cyime_terms"] = (n, os.path.getsize(path))
    rows = sorted(set(di), key=lambda e: (-e[2], e[0]))
    path, n = write_dict("cyime_di", "2026-10-19", rows)
    sizes["cyime_di"] = (n, os.path.getsize(path))

    container = os.path.join(RIME_ASSETS, "jhf_pinyin.dict.yaml")
    with open(container, "w", encoding="utf-8", newline="\n") as f:
        f.write(
            "# 接化发 · 中文主词库（由 source/tools/prepare_chinese_dict.py 生成，请勿手改）\n"
            "# 1:1 复刻 CyIME/rime-ice：四张 rime-ice 表 + CyIME 领域词 + 副词「地」\n"
            "---\n"
            "name: jhf_pinyin\n"
            'version: "1.1"\n'
            "sort: by_weight\n"
            "use_preset_vocabulary: false\n"
            "import_tables:\n"
            "  - cn_dicts/8105\n"
            "  - cn_dicts/base\n"
            "  - cn_dicts/ext\n"
            "  - cn_dicts/tencent\n"
            "  - cn_dicts/cyime_terms\n"
            "  - cn_dicts/cyime_di\n"
            "...\n")

    print("\nwrote cn_dicts/:")
    total = 0
    for name, (n, size) in sizes.items():
        print("  %-12s %8d entries  %6.2f MB" % (name, n, size / 1048576.0))
        total += n
    print("  total        %8d entries  %6.2f MB" % (total, os.path.getsize(container) / 1048576.0))
    print("dropped: removed %d, unknown-syllable %d" % (dropped_removed, dropped_syllable))
    return 0


if __name__ == "__main__":
    sys.exit(main())

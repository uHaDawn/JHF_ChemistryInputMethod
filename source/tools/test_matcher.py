# -*- coding: utf-8 -*-
"""词库 + 拼音匹配引擎自检。

镜像 app 内 PinyinMatcher 的 DP 逻辑（含 lü 的 v/u 写法兼容），在 JVM 之外快速验证
词库数据质量与匹配正确性，避免每次改动都出包上机。

用法：
    python tools/test_matcher.py
退出码：0 全通过 / 1 有失败
"""
import io
import json
import os
import random
import sys

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DICT = os.path.join(ROOT, "app", "src", "main", "assets", "chem_dict.json")

RANK_EXACT, RANK_PREFIX, RANK_INI, RANK_INI_PREFIX = 0, 1, 2, 3


# ------------------------------------------------------------------ 引擎镜像
def syl_eq(syl, piece):
    """音节等价：'lv'(lü) 兼容用户打 'lu'；其余要求完全一致。"""
    if syl == piece:
        return True
    return syl.replace("v", "u") == piece.replace("v", "u")


def full_match(t, syls):
    memo = {}

    def go(i, j):
        if j == len(t):
            return i == len(syls)
        if i >= len(syls):
            return False
        key = (i, j)
        if key in memo:
            return memo[key]
        syl = syls[i]
        ok = False
        for L in range(1, min(6, len(t) - j) + 1):
            if syl_eq(syl, t[j:j + L]) and go(i + 1, j + L):
                ok = True
                break
        memo[key] = ok
        return ok

    return go(0, 0)


def prefix_match(t, syls):
    memo = {}

    def go(i, j):
        if j == len(t):
            return i >= 1
        if i >= len(syls):
            return False
        key = (i, j)
        if key in memo:
            return memo[key]
        syl = syls[i]
        ok = False
        for L in range(1, min(6, len(t) - j) + 1):
            piece = t[j:j + L]
            if syl_eq(syl, piece):
                if go(i + 1, j + L):
                    ok = True
                    break
            elif syl.startswith(piece) and j + L == len(t):
                ok = True
                break
        memo[key] = ok
        return ok

    return go(0, 0)


def best_rank(t, e):
    """与 Java bestRank 同序：任一读音全拼精确 → 前缀 → 首字母精确 → 首字母前缀。"""
    for syls in e["_pinyins"]:
        if full_match(t, syls):
            return RANK_EXACT
    for syls in e["_pinyins"]:
        if prefix_match(t, syls):
            return RANK_PREFIX
    for ini in e["_inis"]:
        if ini == t:
            return RANK_INI
    for ini in e["_inis"]:
        if ini.startswith(t):
            return RANK_INI_PREFIX
    return -1


def match(entries, typed_raw, limit=20):
    """与 Java PinyinMatcher.match 同序：全拼精确 → 全拼前缀 → 首字母精确 → 首字母前缀。"""
    t = "".join(c for c in typed_raw.lower() if "a" <= c <= "z")
    if not t:
        return []
    groups = [[], [], [], []]
    for e in entries:
        r = best_rank(t, e)
        if r >= 0:
            groups[r].append(e)
    out = []
    for rank, g in enumerate(groups):
        g.sort(key=lambda e: e["_len"])  # 同长度保持词库顺序（常用词优先）
        for e in g:
            out.append((e["n"], e["f"][0], rank))
            if len(out) >= limit:
                return out
    return out


# ------------------------------------------------------------------ 断言
def main():
    entries = json.load(io.open(DICT, encoding="utf-8"))
    for e in entries:
        e["_pinyins"] = [alt.split() for alt in e["p"].split("|")]
        e["_inis"] = ["".join(s[0] for s in syls) for syls in e["_pinyins"]]
        e["_len"] = len(e["n"])

    passed, failed = 0, []

    def ok(cond, desc):
        nonlocal passed
        if cond:
            passed += 1
        else:
            failed.append(desc)
        print(f"{'OK  ' if cond else 'FAIL'} {desc}")

    print("========== 1. 词库规模与数据质量 ==========")
    ok(len(entries) >= 3000, f"词条总数 {len(entries)} >= 3000")
    ok(all(e["n"] and e["p"] and e["f"] and e["f"][0] for e in entries),
       "无空名称 / 空拼音 / 空化学式")
    bad_syl = [e["n"] for e in entries for syls in e["_pinyins"] for s in syls
               if not s.isascii() or not s.isalpha() or len(s) > 6]
    ok(not bad_syl, f"音节全部合法（如非法：{bad_syl[:5]}）")
    ok(len({e["n"] for e in entries}) == len(entries), "词名无重复")
    dup_f = {}
    for e in entries:
        dup_f.setdefault(e["f"][0], []).append(e["n"])
    multi = {k: v for k, v in dup_f.items() if len(v) > 1}
    ok(len(multi) > 30, f"存在同式多名词条（俗名/学名）共 {len(multi)} 组，如 "
                        f"{list(multi.items())[0] if multi else '无'}")
    covers = ["硫酸铜", "氢氧化钠", "高锰酸钾", "葡萄糖", "五水硫酸铜", "硫酸根",
              "甲基", "纯碱", "胆矾", "尿素", "苯", "干冰",
              "亚硝酸", "高铁酸钾", "硫代硫酸钠", "偏磷酸钠", "二氧化氯",
              "硬脂酸钠", "谷氨酸钠", "亮氨酸", "聚四氟乙烯", "偏钛酸钡",
              "孔雀石", "普鲁士蓝", "氢化铝锂", "二硫化碳"]
    missing = [n for n in covers if n not in {e["n"] for e in entries}]
    ok(not missing, f"高频词覆盖完整（缺：{missing}）")
    multi_py = [e for e in entries if "|" in e["p"]]
    ok(len(multi_py) >= 300, f"多读音词条 {len(multi_py)} 条 >= 300")
    by_full = {}
    for e in entries:
        for alt in e["p"].split("|"):
            by_full.setdefault(alt.replace(" ", ""), set()).add(e["n"])
    max_exact = max(len(v) for v in by_full.values())
    ok(max_exact <= 20, f"同全拼词条最多 {max_exact} 条 <= 20（保证整串输入不会被候选上限截断）")

    print("\n========== 2. 全拼精确匹配 ==========")
    exact_cases = [
        ("liusuantong", "硫酸铜"), ("qingyanghuana", "氢氧化钠"),
        ("tansuanqingna", "碳酸氢钠"), ("gaomengsuanjia", "高锰酸钾"),
        ("liusuan", "硫酸"), ("xiaosuan", "硝酸"), ("yisuan", "乙酸"),
        ("jiawan", "甲烷"), ("yichun", "乙醇"), ("bingtong", "丙酮"),
        ("benfen", "苯酚"), ("niaosu", "尿素"), ("dianfen", "淀粉"),
        ("putaotang", "葡萄糖"), ("ganbing", "干冰"), ("jiujing", "酒精"),
        ("chunjian", "纯碱"), ("xiaosuda", "小苏打"), ("shaojian", "烧碱"),
        ("liusuangen", "硫酸根"), ("qingyanggen", "氢氧根"),
        ("anji", "氨基"), ("suoji", "羧基"), ("shui", "水"),
        ("ganyou", "甘油"), ("lvfan", "绿矾"), ("mingfan", "明矾"),
        ("yaxiaosuan", "亚硝酸"), ("gaotiesuanjia", "高铁酸钾"),
        ("liudailiusuanna", "硫代硫酸钠"), ("pianlinsuanna", "偏磷酸钠"),
        ("eryanghualv", "二氧化氯"), ("yingzhisuanna", "硬脂酸钠"),
        ("guansuanna", "谷氨酸钠"), ("liangansuan", "亮氨酸"),
        ("jusifuyixi", "聚四氟乙烯"), ("piantaisuanbei", "偏钛酸钡"),
        ("kongqueshi", "孔雀石"), ("pulushilan", "普鲁士蓝"),
        ("qinghualvli", "氢化铝锂"), ("erliuhuatan", "二硫化碳"),
        ("zhonggesuanjia", "重铬酸钾"), ("luosuanjia", "铬酸钾"),
        ("lvhuabei", "氯化铍"), ("chouhuana", "溴化钠"),
        ("shuiyangsuan", "水杨酸"), ("benhuangsuan", "苯磺酸"),
        # 单质气体 / 冷门化合物 / 俗名（2026-10-27 补全）
        ("qingqi", "氢气"), ("yangqi", "氧气"), ("danqi", "氮气"),
        ("lvqi", "氯气"), ("fuqi", "氟气"),
        ("guotansuanna", "过碳酸钠"), ("guoerliusuanna", "过二硫酸钠"),
        ("yalvsuanna", "亚氯酸钠"), ("huimengyang", "灰锰氧"),
        ("hongfan", "红矾"), ("wangshui", "王水"),
        # 同位素（2026-10-27）：氕/氘/氚 + 常见核素
        ("pie", "氕"), ("dao", "氘"), ("chuan", "氚"),
        ("tanshisi", "碳十四"), ("youersanwu", "铀二三五"),
        ("dianyisanyi", "碘一三一"),
    ]
    for q, want in exact_cases:
        res = match(entries, q, limit=40)
        hit = next((r for r in res if r[0] == want), None)
        ok(hit is not None, f"{q:22s} → {want}" + (f"（rank {hit[2]}）" if hit else " 未命中"))

    print("\n========== 3. 分词 / 连字符 / 混合输入 ==========")
    for q, want in [("liusu-antong", "硫酸铜"),
                    ("wushuiliusu-antong", "五水硫酸铜"),
                    ("qing-yang-hua-na", "氢氧化钠")]:
        res = match(entries, q, limit=40)
        ok(any(r[0] == want for r in res), f"{q:24s} → {want}")

    print("\n========== 4. lü 的 v/u 写法兼容 ==========")
    for q in ("lvhuana", "luhuana", "lv-hua-na"):
        res = match(entries, q, limit=40)
        ok(any(r[0] == "氯化钠" for r in res), f"{q:12s} → 氯化钠")
    res = match(entries, "lv", limit=40)
    ok(any(r[0] in ("铝", "氯") for r in res), "lv → 铝 / 氯")

    print("\n========== 4b. 多音字 / 常见异读 ==========")
    alias_cases = [("zhonggesuanjia", "重铬酸钾"), ("chonggesuanjia", "重铬酸钾"),
                   ("luosuanjia", "铬酸钾"), ("gesuanjia", "铬酸钾"),
                   ("lvhuabei", "氯化铍"), ("lvhuapi", "氯化铍"),
                   ("chouhuana", "溴化钠"), ("xiuhuana", "溴化钠"),
                   ("zhongjingshi", "重晶石"), ("chongjingshi", "重晶石")]
    for q, want in alias_cases:
        res = match(entries, q, limit=40)
        hit = next((r for r in res if r[0] == want), None)
        ok(hit is not None, f"{q:22s} → {want}" + (f"（rank {hit[2]}）" if hit else " 未命中"))

    print("\n========== 5. 首字母缩写 ==========")
    ini_cases = [("lsg", ("硫酸钙", "硫酸根", "磷酸钙")),
                 ("qyhn", ("氢氧化钠",)),
                 ("gmsj", ("高锰酸钾",)),
                 ("ns", ("尿素", "尿酸", "牛磺酸")),
                 ("jq", ("甲醛",)),
                 ("qq", ("氢气",)), ("yq", ("氧气",)), ("dq", ("氮气",)),
                 ("lq", ("氯气",)), ("gtsn", ("过碳酸钠",))]
    for q, wants in ini_cases:
        res = match(entries, q, limit=40)
        ok(any(r[0] in wants for r in res),
           f"{q:6s} → {'/'.join(wants)}（实际前 5：{[r[0] for r in res[:5]]}）")

    print("\n========== 6. 排序稳定性 ==========")
    res = match(entries, "tansuanna", limit=20)
    ok(res and res[0][0] == "碳酸钠", f"碳酸钠排首选（实际 {res[0][0] if res else '无'}）")
    res = match(entries, "liusuantie", limit=20)
    ok(res and res[0][0] in ("硫酸铁", "硫酸亚铁"),
       f"硫酸铁类排在前（实际 {[r[0] for r in res[:3]]}）")

    print("\n========== 7. 化学式配平抽查 ==========")
    form_cases = [("氯化钠", "NaCl"), ("硫酸钠", "Na₂SO₄"),
                  ("硫酸铝", "Al₂(SO₄)₃"), ("碳酸铵", "(NH₄)₂CO₃"),
                  ("硝酸铜", "Cu(NO₃)₂"), ("氢氧化钙", "Ca(OH)₂"),
                  ("磷酸钙", "Ca₃(PO₄)₂"), ("氯化铁", "FeCl₃"),
                  ("硫酸亚铁", "FeSO₄"), ("高锰酸钾", "KMnO₄"),
                  ("硫酸氢钠", "NaHSO₄"), ("乙酸钙", "(CH₃COO)₂Ca"),
                  ("乙酸钠", "CH₃COONa"), ("重铬酸钾", "K₂Cr₂O₇"),
                  ("十二水硫酸铝钾", "KAl(SO₄)₂·12H₂O")]
    by_name = {e["n"]: e["f"][0] for e in entries}
    for n, want in form_cases:
        got = by_name.get(n)
        ok(got == want, f"{n:12s} = {got}（期望 {want}）")

    print("\n========== 8. 全库抽样自匹配（每个读音都应命中自己）==========")
    random.seed(20261007)
    sample = random.sample(entries, 240)
    miss = []
    for e in sample:
        for alt in e["p"].split("|"):
            typed = alt.replace(" ", "")
            res = match(entries, typed, limit=60)
            if not any(r[0] == e["n"] for r in res):
                miss.append((e["n"], typed))
    ok(not miss, f"240 条随机抽样（含异读）全部自匹配（未命中 {len(miss)} 条：{miss[:5]}）")

    total = passed + len(failed)
    print(f"\n========== 结果：{passed}/{total} 通过 ==========")
    if failed:
        print("失败项：")
        for f in failed:
            print("  - " + f)
    sys.exit(0 if not failed else 1)


if __name__ == "__main__":
    main()

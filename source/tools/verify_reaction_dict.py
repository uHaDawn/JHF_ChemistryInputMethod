# -*- coding: utf-8 -*-
"""
反应库全量校验（2026-11-08）。

校验项：
  1) reaction_dict.json：coeffs 长度、原子/电荷守恒、equation 与 coeffs 一致、
     反应物 key 重复；
  2) ion_reaction_dict.json：字段齐备、完整离子式与净离子式守恒、
     净离子式不应残留两侧同种旁观离子；
  3) 上下标格式：化学式内不得出现 ASCII 数字（原子数必须下标）、不得残留 ^ 电荷；
  4) 覆盖率统计：按 tags 分类统计条目数。

用法：python verify_reaction_dict.py
"""
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import chem_balance as cb

_SUB_REV = {v: k for k, v in cb._SUB.items()}
_SUP_REV = {v: k for k, v in cb._SUP.items()}


def unpretty(s):
    """显示串 → ASCII 化学式串（下标转数字、上标电荷转 ^n+/-，去掉 ↑↓）。"""
    out = []
    i = 0
    while i < len(s):
        ch = s[i]
        if ch in _SUB_REV:
            out.append(_SUB_REV[ch])
            i += 1
        elif ch in _SUP_REV or ch in "⁺⁻":
            digits = ""
            j = i
            while j < len(s) and s[j] in _SUP_REV:
                digits += _SUP_REV[s[j]]
                j += 1
            sign = ""
            if j < len(s) and s[j] in "⁺⁻":
                sign = "+" if s[j] == "⁺" else "-"
                j += 1
            if sign:
                out.append("^" + digits + sign)
            else:
                out.append(digits)
            i = j
        elif ch in "↑↓":
            i += 1
        else:
            out.append(ch)
            i += 1
    return "".join(out)


def parse_side(side):
    """解析一侧 '2H2O + SO4^2-' → ([化学式], [系数])。"""
    species = []
    coeffs = []
    for item in side.split(" + "):
        item = item.strip()
        if not item:
            continue
        m = re.match(r'^(\d+)(.*)$', item)
        if m:
            c = int(m.group(1))
            f = m.group(2)
        else:
            c = 1
            f = item
        species.append(unpretty(f).strip())
        coeffs.append(c)
    return species, coeffs


_RE_COND_FORMULA = re.compile(r'[A-Z][A-Za-z0-9()\[\]·]*')


def pretty_cond(cond):
    """条件文本中的化学式转下标，与 equation 里的显示串保持一致。"""
    return _RE_COND_FORMULA.sub(lambda m: cb.pretty_formula(m.group(0)), cond)


def parse_equation(eq, conditions=""):
    """拆 '左 = (条件) 右'（可逆反应亦支持 ⇌），返回 (左物种, 右物种, 系数) 或 None。"""
    sep = "=" if "=" in eq else ("⇌" if "⇌" in eq else None)
    if not sep:
        return None
    left, right = eq.split(sep, 1)
    right = right.strip()
    if conditions:
        for c in (conditions, pretty_cond(conditions)):
            prefix = "(%s)" % c
            if right.startswith(prefix):
                right = right[len(prefix):].strip()
                break
    ls, lc = parse_side(left.strip())
    rs, rc = parse_side(right.strip())
    return ls, rs, lc + rc


def check_format(text):
    """化学式格式：不允许 ASCII 原子数，不允许残留 ^ 电荷。

    条件括号（紧跟在等号后的 (点燃)/(MnO2,加热)）是文字说明，不参与检查。
    """
    problems = []
    if "^" in text:
        problems.append("残留 ASCII 电荷符号 ^")
    text = re.sub(r'=\([^)]*\)', '=', text)
    for item in re.split(r'\s*(?:=|⇌)\s*|\s*\+\s*', text):
        if not item:
            continue
        if re.search(r'[A-Za-z)]\d', item):
            problems.append("ASCII 原子数未转下标: " + item)
    return problems


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def verify_reaction_dict(data):
    errs = []
    seen = {}
    for i, e in enumerate(data):
        r, p, c = e["reactants"], e["products"], e["coeffs"]
        if len(c) != len(r) + len(p):
            errs.append("[%d] coeffs 长度不符: %s" % (i, e.get("equation", "")))
            continue
        if not cb.verify(r, p, c):
            errs.append("[%d] 原子/电荷不守恒: %s" % (i, e.get("equation", "")))
        parsed = parse_equation(e.get("equation", ""), e.get("conditions", ""))
        if not parsed:
            errs.append("[%d] equation 缺少等号" % i)
        else:
            ls, rs, vc = parsed
            if not cb.verify(ls, rs, vc):
                errs.append("[%d] equation 与显示串不守恒: %s" % (i, e.get("equation", "")))
            if ls != r or rs != p:
                errs.append("[%d] equation 物种与 reactants/products 不一致" % i)
        key = (tuple(sorted(r)), tuple(p), e.get("conditions", ""))
        if key in seen:
            errs.append("[%d] 与第 %d 条重复: %s" % (i, seen[key], e.get("equation", "")))
        seen[key] = i
        for msg in check_format(e.get("equation", "")):
            errs.append("[%d] 格式问题(%s)" % (i, msg))
    return errs


def verify_ion_dict(data):
    errs = []
    required = ("reactants", "products", "coeffs", "conditions", "type",
                "equation", "molecular", "full_ionic", "net_ionic", "tags")
    for i, e in enumerate(data):
        for k in required:
            if k not in e:
                errs.append("[%d] 缺少字段 %s" % (i, k))
        for field in ("full_ionic", "net_ionic", "molecular"):
            text = e.get(field, "")
            if "=" not in text and "⇌" not in text:
                errs.append("[%d] %s 缺少等号" % (i, field))
                continue
            parsed = parse_equation(text, e.get("conditions", ""))
            if not parsed:
                errs.append("[%d] %s 解析失败" % (i, field))
                continue
            ls, rs, vc = parsed
            if not cb.verify(ls, rs, vc):
                errs.append("[%d] %s 原子/电荷不守恒: %s" % (i, field, text))
            for msg in check_format(text):
                errs.append("[%d] %s 格式问题(%s): %s" % (i, field, msg, text))
            if field == "net_ionic":
                common = set(ls) & set(rs)
                if common:
                    errs.append("[%d] 净离子式未约去旁观离子 %s: %s"
                                % (i, "、".join(sorted(common)), text))
    return errs


def main():
    base = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                        "..", "app", "src", "main", "assets")
    rp = os.path.join(base, "reaction_dict.json")
    ip = os.path.join(base, "ion_reaction_dict.json")

    reactions = load(rp)
    ions = load(ip)

    r_errs = verify_reaction_dict(reactions)
    i_errs = verify_ion_dict(ions)

    print("reaction_dict: %d 条，问题 %d 个" % (len(reactions), len(r_errs)))
    for e in r_errs:
        print("  " + e)

    print("ion_reaction_dict: %d 条，问题 %d 个" % (len(ions), len(i_errs)))
    for e in i_errs:
        print("  " + e)

    # 覆盖率统计（reaction_dict 不输出 tags，改用含 tags 的离子库统计）
    by_tag = {}
    for e in ions:
        tags = e.get("tags") or ["未分类"]
        for t in tags:
            by_tag[t] = by_tag.get(t, 0) + 1
    print("覆盖率分类：")
    for k, v in sorted(by_tag.items(), key=lambda x: -x[1]):
        print("  %-12s %d" % (k, v))

    ionic_count = sum(1 for e in reactions if e.get("type") == "ionic")
    print("分子条目 %d，离子条目 %d，离子库 %d"
          % (len(reactions) - ionic_count, ionic_count, len(ions)))

    if r_errs or i_errs:
        sys.exit(1)


if __name__ == "__main__":
    main()

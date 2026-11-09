# -*- coding: utf-8 -*-
"""
生成 assets/ion_reaction_dict.json（2026-11-08）。

输入：gen_reaction_dict.all_entries() 合并后的全部反应（分子 + 离子）。
过程：
  1) 纯离子条目：直接作为净离子式，补全完整化学方程式；
  2) 分子条目：按 ion_rules 判定哪些物质拆成离子，生成完整离子式；
     约去两侧相同的旁观离子，得到净离子式；
  3) 用 chem_balance.verify 校验原子与电荷守恒；
  4) 输出带上下标的显示串（原子数下标、电荷上标）。

输出条目字段：
  reactants / products / coeffs / conditions / type / equation /
  molecular（完整化学方程式）/ full_ionic（完整离子式）/ net_ionic（净离子式）/
  tags / ion_kind
"""
import json
import os
import re
import sys
from collections import OrderedDict
from math import gcd

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import chem_balance as cb
import ion_rules as ir
import gen_reaction_dict as g


def has_charge(species):
    return bool(re.search(r'\^\d*[+-]', species))


def aggregate(items):
    """[(化学式, 系数)] → 有序 {化学式: 系数}。"""
    d = OrderedDict()
    for f, c in items:
        d[f] = d.get(f, 0) + c
    return d


def expand_side(species_list, coeffs, side):
    """把一侧物质展开成离子项；不可拆物质原样保留。"""
    items = []
    for f, c in zip(species_list, coeffs):
        ions = ir.dissociate(f, side)
        if ions:
            for ion, mult in ions:
                items.append((ion, c * mult))
        else:
            items.append((f, c))
    return items


def cancel(left, right):
    """约去两侧完全相同的旁观离子，返回新的左右有序字典（系数已约分）。"""
    left = OrderedDict(left)
    right = OrderedDict(right)
    for f in list(left.keys()):
        if f in right:
            m = min(left[f], right[f])
            left[f] -= m
            right[f] -= m
    left = OrderedDict((f, c) for f, c in left.items() if c > 0)
    right = OrderedDict((f, c) for f, c in right.items() if c > 0)

    all_c = list(left.values()) + list(right.values())
    if all_c:
        g0 = 0
        for c in all_c:
            g0 = gcd(g0, c)
        if g0 > 1:
            left = OrderedDict((f, c // g0) for f, c in left.items())
            right = OrderedDict((f, c // g0) for f, c in right.items())
    return left, right


def term(coeff, formula, mark=""):
    s = (str(coeff) if coeff != 1 else "") + cb.pretty_formula(formula)
    return s + mark


def format_side(items, marks=None):
    parts = []
    for f, c in items.items():
        mark = ""
        if marks and f in marks:
            mark = marks[f]
        parts.append(term(c, f, mark))
    return " + ".join(parts)


def product_marks(entry):
    """产物分子 → ↑↓ 标记（只对未拆分的分子生效）。"""
    marks = {}
    n_r = len(entry["reactants"])
    for i, p in enumerate(entry["products"]):
        idx = n_r + i
        m = ""
        if idx < len(entry["gas"]) and entry["gas"][idx]:
            m += "↑"
        if idx < len(entry["precip"]) and entry["precip"][idx]:
            m += "↓"
        if m:
            marks[p] = m
    return marks


def validate(spec_l, spec_r, coeffs):
    return cb.verify(list(spec_l), list(spec_r), list(coeffs))


def build_molecular_derived(entry):
    """分子反应 → 完整离子式 + 净离子式。返回 (ok, full_ionic, net_ionic, reason)。"""
    r, p, coeffs = entry["reactants"], entry["products"], entry["coeffs"]

    if ir.is_non_ionic_condition(entry.get("conditions", "")):
        return False, "", "", "非水溶液条件"

    left_full = aggregate(expand_side(r, coeffs[:len(r)], "reactant"))
    right_full = aggregate(expand_side(p, coeffs[len(r):], "product"))

    # 一个可拆物质都没有 → 没有离子式
    if not any(ir.dissociate(f, "reactant") for f in r) and \
       not any(ir.dissociate(f, "product") for f in p):
        return False, "", "", "无可拆电解质"

    marks = product_marks(entry)
    # 条件里点名的反应物写进括号（NaOH(过量) 这类）；被拆成离子的不强行标注。
    notes, _ = g.split_condition_notes(entry.get("conditions", ""), r)
    left_marks = dict(marks)
    for f, n in notes.items():
        left_marks[f] = "(" + n + ")"

    # 完整离子式：不约去旁观离子
    left_c = list(left_full.values())
    right_c = list(right_full.values())
    if not validate(left_full.keys(), right_full.keys(), left_c + right_c):
        return False, "", "", "完整离子式守恒校验失败"
    full = format_side(left_full, left_marks) + " = " + format_side(right_full, marks)

    # 净离子式：约去旁观离子
    left_net, right_net = cancel(left_full, right_full)
    if not left_net or not right_net:
        return False, "", "", "约去后一侧为空"
    left_c = list(left_net.values())
    right_c = list(right_net.values())
    if not validate(left_net.keys(), right_net.keys(), left_c + right_c):
        return False, "", "", "净离子式守恒校验失败"
    net = format_side(left_net, left_marks) + " = " + format_side(right_net, marks)
    return True, full, net, ""


def pretty_equation(eq, notes=None):
    """ASCII 化学方程式 → 显示串（支持 '=' 与可逆符号 '⇌'）。

    notes 为「反应物化学式 → 标注」（如 {"CO2": "少量"}），只作用于左侧，
    用于把条件写到反应物旁边：CO2(少量) + 2OH^- = CO3^2- + H2O。
    """
    if "⇌" in eq:
        sep = " ⇌ "
        left, right = eq.split("⇌", 1)
    elif "=" in eq:
        sep = " = "
        left, right = eq.split("=", 1)
    else:
        return eq

    def conv(side, with_notes=False):
        parts = []
        for item in side.split(" + "):
            item = item.strip()
            if not item:
                continue
            m = re.match(r'^(\d+)(.*)$', item)
            if m:
                c, f = m.group(1), m.group(2)
            else:
                c, f = "", item
            mark = ""
            if f and f[-1] in "↑↓":
                mark = f[-1]
                f = f[:-1]
            label = ""
            if with_notes and notes and f in notes:
                label = "(" + notes[f] + ")"
            parts.append(c + cb.pretty_formula(f) + label + mark)
        return " + ".join(parts)

    return conv(left.strip(), True) + sep + conv(right.strip())


def build_ionic_only(entry):
    """纯离子条目：equation 即净离子式，补完整化学方程式。"""
    r = entry["reactants"]
    molecular = pretty_equation(ir.ionic_only_molecular(r))
    eq = entry["equation"]
    return molecular or eq, eq, "纯离子条目"


def main():
    entries, fails = g.build_results(verbose=False)
    if fails:
        print("警告：有 %d 条分子反应配平失败，先修复再生成离子库" % len(fails))
        return

    out = []
    stats = {
        "molecular_derived": 0,
        "ionic_only": 0,
        "manual": 0,
        "skipped": {},
        "errors": [],
    }

    for idx, entry in enumerate(entries):
        r = entry["reactants"]
        p = entry["products"]
        mode = entry.get("ion_mode", "auto")

        if mode == "ionic" or any(has_charge(f) for f in r):
            mol, net, _ = build_ionic_only(entry)
            full = net
            kind = "ionic_only"
            stats["ionic_only"] += 1
        elif mode == "none":
            stats["skipped"]["显式标记无离子式"] = stats["skipped"].get("显式标记无离子式", 0) + 1
            continue
        elif mode == "manual" or entry.get("net_override"):
            notes, _ = g.split_condition_notes(entry.get("conditions", ""), r)
            net = pretty_equation(entry.get("net_override", ""), notes)
            full = pretty_equation(entry.get("full_override", entry.get("net_override", "")), notes)
            if not net:
                stats["errors"].append("manual 条目缺少 net_override: " + entry["equation"])
                continue
            mol = entry["equation"]
            kind = "manual"
            stats["manual"] += 1
        else:
            ok, full, net, reason = build_molecular_derived(entry)
            if not ok:
                stats["skipped"][reason] = stats["skipped"].get(reason, 0) + 1
                continue
            mol = entry["equation"]
            kind = "molecular_derived"
            stats["molecular_derived"] += 1

        out.append({
            "id": "%s#%d" % (ir.sort_key(r), idx),
            "reactants": r,
            "products": p,
            "coeffs": entry["coeffs"],
            "conditions": entry.get("conditions", ""),
            "type": "ionic",
            "equation": entry["equation"],
            "molecular": mol,
            "full_ionic": full,
            "net_ionic": net,
            "tags": entry.get("tags", []),
            "ion_kind": kind,
        })

    out_dir = os.path.join(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
        "app", "src", "main", "assets")
    os.makedirs(out_dir, exist_ok=True)
    out_path = os.path.join(out_dir, "ion_reaction_dict.json")
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=2)

    print("离子库共 %d 条 → %s" % (len(out), out_path))
    print("  分子派生 %d，纯离子 %d，手动 %d" %
          (stats["molecular_derived"], stats["ionic_only"], stats["manual"]))
    for k, v in sorted(stats["skipped"].items(), key=lambda x: -x[1]):
        print("  跳过 %d 条：%s" % (v, k))
    for e in stats["errors"]:
        print("  ERROR: " + e)


if __name__ == "__main__":
    main()

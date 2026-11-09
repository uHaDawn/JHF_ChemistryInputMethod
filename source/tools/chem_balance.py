# -*- coding: utf-8 -*-
"""
化学式自动配平与产物预测工具（2026-11-08）。

定位：
  1) 公式解析：把 H2O / CuSO4·5H2O / [Cu(NH3)4]2+ 等拆成「元素计数 + 净电荷」。
  2) 自动配平：给定反应物 + 产物，用化学计量矩阵的零空间求最小正整数系数
     （支持离子方程式：电荷作为额外的一行参与守恒）。
  3) 产物预测（反应库的补充/兜底）：对几类基础反应按规则推断产物
     （燃烧、复分解、酸碱、酸+金属、金属置换、化合等）。
  4) 反应库辅助：批量读取 JSON，对每条 (反应物 → 产物) 自动算系数并校验守恒，
     输出可直接打包进 assets 的配平结果。

用法：
  python chem_balance.py "Na + H2O = NaOH + H2"
  python chem_balance.py "Fe + Cu^2+ = Fe^2+ + Cu"          # 离子方程式
  python chem_balance.py --predict "CH4 + O2"                # 只给反应物，预测产物并配平
  python chem_balance.py --balance-lib reactions.json out.json

解析约定（ASCII 存储，显示时再转上下标）：
  - 下标直接写数字：H2O, Fe2O3
  - 电荷用 ^：Cu^2+, SO4^2-, H+, OH^-, Fe^3+
  - 水合物用 ·：CuSO4·5H2O
  - 复杂离子用 [ ]：[Cu(NH3)4]^2+
"""
import json
import re
import sys
from fractions import Fraction
from math import gcd


# --------------------------------------------------------------------------- #
# 一、化学式解析
# --------------------------------------------------------------------------- #

_RE_CHARGE = re.compile(r'\^?(\d*)([+-])$')
_RE_NUM = re.compile(r'\d+')


def parse_formula(formula):
    """把化学式解析为 (元素计数字典, 净电荷)。"""
    s = formula.strip().replace(' ', '')
    if not s:
        return {}, 0

    # 1) 提取末尾电荷：^2+ / 2+ / + / -
    charge = 0
    m = _RE_CHARGE.search(s)
    if m:
        mag = int(m.group(1)) if m.group(1) else 1
        charge = mag if m.group(2) == '+' else -mag
        s = s[:m.start()]

    # 2) 主体（支持 ( )、[ ]、·）
    counts = {}
    _parse_group(s, 0, counts)
    return counts, charge


def _parse_group(s, i, counts):
    while i < len(s):
        c = s[i]
        if c in '([{':
            close = {')': '([{'.index(c), ']': 1, '}': 2}
            sub = {}
            i = _parse_group(s, i + 1, sub)
            # i 现在指向对应的闭合括号
            if i < len(s) and s[i] in ')]}':
                i += 1
            mult = 1
            m = _RE_NUM.match(s, i)
            if m:
                mult = int(m.group())
                i += len(m.group())
            for el, n in sub.items():
                counts[el] = counts.get(el, 0) + n * mult
        elif c in ')]}':
            return i
        elif c == '·':  # 水合物分隔
            i += 1
            sub = {}
            i = _parse_group(s, i, sub)
            for el, n in sub.items():
                counts[el] = counts.get(el, 0) + n
        elif c.isupper():
            el = c
            i += 1
            if i < len(s) and s[i].islower():
                el += s[i]
                i += 1
            mult = 1
            m = _RE_NUM.match(s, i)
            if m:
                mult = int(m.group())
                i += len(m.group())
            counts[el] = counts.get(el, 0) + mult
        else:
            i += 1
    return i


def pretty_formula(formula):
    """把 ASCII 化学式转成显示串（数字→下标，^电荷→上标）。

    水合物 `·` 后面的数字是结晶水系数，保持正体（如 CuSO₄·5H₂O、
    Na₂CO₃·10H₂O），只有元素后的原子数才转下标。
    """
    s = formula
    # 电荷：^2+ → ²⁺
    s = re.sub(r'\^?(\d*)([+-])', lambda m: _sup(m.group(1)) + ('⁺' if m.group(2) == '+' else '⁻'), s)
    # 按水合物分隔符分段：第一段全部下标化；后续段开头的系数数字保持正体
    parts = s.split('·')
    out = []
    for idx, part in enumerate(parts):
        if idx == 0:
            out.append(re.sub(r'\d', lambda m: _SUB[m.group()], part))
        else:
            m = re.match(r'^(\d+)(.*)', part)
            if m:
                out.append(m.group(1) + re.sub(r'\d', lambda mm: _SUB[mm.group()], m.group(2)))
            else:
                out.append(re.sub(r'\d', lambda mm: _SUB[mm.group()], part))
    return '·'.join(out)


_SUB = {'0': '₀', '1': '₁', '2': '₂', '3': '₃', '4': '₄',
        '5': '₅', '6': '₆', '7': '₇', '8': '₈', '9': '₉'}
_SUP = {'0': '⁰', '1': '¹', '2': '²', '3': '³', '4': '⁴',
        '5': '⁵', '6': '⁶', '7': '⁷', '8': '⁸', '9': '⁹'}


def _sup(digits):
    return ''.join(_SUP[d] for d in digits)


# --------------------------------------------------------------------------- #
# 二、自动配平（计量矩阵零空间）
# --------------------------------------------------------------------------- #

def balance(reactants, products):
    """给定反应物与产物（不含前系数的化学式列表），返回系数列表 [c...] 或 None。

    顺序：先反应物后产物。含电荷时按离子方程式处理。
    """
    all_species = list(reactants) + list(products)
    if not all_species:
        return None

    parsed = [parse_formula(f) for f in all_species]
    elements = sorted({el for p, _ in parsed for el in p})
    n = len(all_species)
    nR = len(reactants)

    rows = []
    for el in elements:
        row = []
        for j, (p, _) in enumerate(parsed):
            v = p.get(el, 0)
            row.append(v if j < nR else -v)
        rows.append(row)

    if any(ch for _, ch in parsed):
        rows.append([ch if j < nR else -ch for j, (_, ch) in enumerate(parsed)])

    return _solve_nullspace(rows, n)


def _solve_nullspace(rows, n):
    mat = [[Fraction(x) for x in row] for row in rows]
    m = len(mat)
    r = 0
    pivots = []
    for col in range(n):
        if r >= m:
            break
        pivot = None
        for k in range(r, m):
            if mat[k][col] != 0:
                pivot = k
                break
        if pivot is None:
            continue
        mat[r], mat[pivot] = mat[pivot], mat[r]
        pivots.append(col)
        piv_val = mat[r][col]
        mat[r] = [x / piv_val for x in mat[r]]
        for k in range(m):
            if k != r and mat[k][col] != 0:
                f = mat[k][col]
                mat[k] = [mat[k][c] - f * mat[r][c] for c in range(n)]
        r += 1

    free = [c for c in range(n) if c not in pivots]
    if not free:
        return None

    basis = []
    for f in free:
        vec = [Fraction(0)] * n
        vec[f] = Fraction(1)
        for i, col in enumerate(pivots):
            vec[col] = -mat[i][f]
        basis.append(vec)

    if len(basis) == 1:
        return _to_positive_ints(basis[0])

    return _find_positive_combination(basis)


def _to_positive_ints(vec):
    if all(x <= 0 for x in vec):
        vec = [-x for x in vec]
    # 配平结果必须所有物种系数都为正整数（不允许 0）
    if any(x <= 0 for x in vec):
        return None
    denoms = [x.denominator for x in vec]
    lcm = denoms[0]
    for d in denoms[1:]:
        lcm = lcm * d // gcd(lcm, d)
    nums = [int(x * lcm) for x in vec]
    g = abs(nums[0])
    for x in nums[1:]:
        g = gcd(g, abs(x))
    return [x // g for x in nums]


def _find_positive_combination(basis, limit=20):
    """零空间多维时，枚举小整数组合求全正解（兜底，罕见）。"""
    k = len(basis)
    if k > 3:
        return None
    if k == 1:
        return _to_positive_ints(basis[0])
    from itertools import product
    best = None
    for tup in product(range(-limit, limit + 1), repeat=k):
        if all(t == 0 for t in tup):
            continue
        vec = [Fraction(0)] * len(basis[0])
        for t, b in zip(tup, basis):
            if t == 0:
                continue
            for i, x in enumerate(b):
                vec[i] += x * t
        if not all(x >= 0 for x in vec) or all(x == 0 for x in vec):
            continue
        res = _to_positive_ints(vec)
        if res is not None:
            if best is None or sum(res) < sum(best):
                best = res
    return best


def verify(reactants, products, coeffs):
    """校验原子（及电荷）守恒，返回 True/False。"""
    if coeffs is None:
        return False
    parsed = [parse_formula(f) for f in reactants] + [parse_formula(f) for f in products]
    nR = len(reactants)
    elements = sorted({el for p, _ in parsed for el in p})
    for el in elements:
        lhs = sum(p.get(el, 0) * coeffs[j] for j, (p, _) in enumerate(parsed[:nR]))
        rhs = sum(p.get(el, 0) * coeffs[nR + j] for j, (p, _) in enumerate(parsed[nR:]))
        if lhs != rhs:
            return False
    if any(ch for _, ch in parsed):
        lhs = sum(ch * coeffs[j] for j, (_, ch) in enumerate(parsed[:nR]))
        rhs = sum(ch * coeffs[nR + j] for j, (_, ch) in enumerate(parsed[nR:]))
        if lhs != rhs:
            return False
    return True


# --------------------------------------------------------------------------- #
# 三、产物预测（反应库的兜底补充）
# --------------------------------------------------------------------------- #

# 常见离子的电荷（用于复分解 / 酸碱推断）
_ION_CHARGE = {
    'H': 1, 'Na': 1, 'K': 1, 'Ag': 1, 'NH4': 1,
    'Ca': 2, 'Mg': 2, 'Ba': 2, 'Zn': 2, 'Fe': 2, 'Cu': 2, 'Pb': 2,
    'Al': 3, 'Fe': 3,
    'Cl': -1, 'Br': -1, 'I': -1, 'NO3': -1, 'OH': -1, 'MnO4': -1,
    'O': -2, 'S': -2, 'SO4': -2, 'CO3': -2, 'SO3': -2,
    'PO4': -3,
}

# 金属活动性（前置换后）
_ACTIVITY = ['K', 'Ca', 'Na', 'Mg', 'Al', 'Zn', 'Fe', 'Sn', 'Pb', 'H',
             'Cu', 'Hg', 'Ag', 'Pt', 'Au']


def _is_acid(f):
    p, ch = parse_formula(f)
    return ch == 0 and p.get('H', 0) >= 1 and any(el in p for el in ('Cl', 'S', 'N', 'P')) and 'O' in p or (f.startswith('H') and any(a in f for a in ('Cl', 'Br', 'I', 'SO4', 'NO3', 'PO4', 'CO3')))


def _cation_anion(f):
    """粗暴拆分盐为 (阳离子, 阴离子, 阳离子电荷, 阴离子电荷)。"""
    for cat in ('NH4', 'K', 'Na', 'Ag', 'Ca', 'Mg', 'Ba', 'Zn', 'Fe', 'Cu', 'Pb', 'Al', 'H'):
        if f.startswith(cat) and cat in _ION_CHARGE:
            an = f[len(cat):]
            if an and an in _ION_CHARGE:
                return cat, an, _ION_CHARGE[cat], _ION_CHARGE[an]
    return None


def predict_products(reactants):
    """给反应物列表，按基础反应类型预测产物（返回候选列表，每项是产物列表）。

    这是反应库的兜底，只覆盖高置信度的几类，复杂反应请进反应库。
    """
    reactants = [r.strip() for r in reactants if r.strip()]
    if not reactants:
        return []

    candidates = []

    # 燃烧：烃/含氧有机物 + O2 -> CO2 + H2O
    if len(reactants) == 2 and 'O2' in reactants:
        fuel = [r for r in reactants if r != 'O2'][0]
        p, ch = parse_formula(fuel)
        if ch == 0 and p.get('C', 0) >= 1 and p.get('H', 0) >= 1:
            candidates.append(['CO2', 'H2O'])

    # 酸碱中和：酸 + 碱 -> 盐 + H2O
    if len(reactants) == 2:
        a, b = reactants
        if _is_acid(a) and b.endswith('OH'):
            base_metal = b[:-2] if b.endswith('OH') else b
            acid_anion = a[1:] if a.startswith('H') else a
            if base_metal in _ION_CHARGE and acid_anion in _ION_CHARGE:
                salt = _form_salt(base_metal, acid_anion)
                if salt:
                    candidates.append([salt, 'H2O'])
        elif _is_acid(b) and a.endswith('OH'):
            base_metal = a[:-2]
            acid_anion = b[1:] if b.startswith('H') else b
            if base_metal in _ION_CHARGE and acid_anion in _ION_CHARGE:
                salt = _form_salt(base_metal, acid_anion)
                if salt:
                    candidates.append([salt, 'H2O'])

    # 酸 + 活泼金属 -> 盐 + H2
    if len(reactants) == 2:
        for acid, metal in ((a, b) for a in reactants for b in reactants if a != b):
            if _is_acid(acid) and metal in _ION_CHARGE and _ION_CHARGE[metal] > 0:
                if _activity_index(metal) < _activity_index('H'):
                    anion = acid[1:] if acid.startswith('H') else acid
                    if anion in _ION_CHARGE:
                        salt = _form_salt(metal, anion)
                        if salt:
                            candidates.append([salt, 'H2'])

    # 复分解：盐 + 盐 -> 两种新盐（交换阴阳离子）
    if len(reactants) == 2:
        ca1 = _cation_anion(reactants[0])
        ca2 = _cation_anion(reactants[1])
        if ca1 and ca2:
            c1, a1, _, _ = ca1
            c2, a2, _, _ = ca2
            s1 = _form_salt(c1, a2)
            s2 = _form_salt(c2, a1)
            if s1 and s2:
                candidates.append([s1, s2])

    # 金属置换：活泼金属 + 盐 -> 新盐 + 被置换金属
    if len(reactants) == 2:
        for metal, salt in ((a, b) for a in reactants for b in reactants if a != b):
            if metal in _ION_CHARGE and _ION_CHARGE[metal] > 0:
                ca = _cation_anion(salt)
                if ca:
                    c2, a2, _, _ = ca
                    if _activity_index(metal) < _activity_index(c2):
                        new_salt = _form_salt(metal, a2)
                        if new_salt:
                            candidates.append([new_salt, c2])

    # 去重
    seen = []
    uniq = []
    for c in candidates:
        key = tuple(sorted(c))
        if key not in seen:
            seen.append(key)
            uniq.append(c)
    return uniq


def _activity_index(metal):
    return _ACTIVITY.index(metal) if metal in _ACTIVITY else 99


def _form_salt(cation, anion):
    """根据电荷交叉写出盐的化学式。"""
    if cation not in _ION_CHARGE or anion not in _ION_CHARGE:
        return None
    cp = _ION_CHARGE[cation]
    ap = abs(_ION_CHARGE[anion])
    lcm = cp * ap // gcd(cp, ap)
    nc = lcm // cp
    na = lcm // ap
    c = cation if nc == 1 else (cation if cation not in ('NH4',) else f'({cation})') + str(nc)
    if nc > 1 and cation in ('NH4',):
        c = f'({cation}){nc}'
    elif nc > 1:
        c = f'{cation}{nc}'
    else:
        c = cation
    if na > 1 and anion in ('SO4', 'NO3', 'OH', 'CO3', 'PO4', 'SO3', 'MnO4'):
        a = f'({anion}){na}'
    elif na > 1:
        a = f'{anion}{na}'
    else:
        a = anion
    return c + a


# --------------------------------------------------------------------------- #
# 四、表达式解析（命令行 / 反应库用）
# --------------------------------------------------------------------------- #

_SPLIT = re.compile(r'[=→⇌=]')
_STRIP = re.compile(r'\(([slg]|aq)\)|[↑↓△]')


def split_expr(expr):
    """把 "Na + H2O = NaOH + H2" 拆成 (reactants, products)。

    注意：离子电荷 Cu^2+ 里的 + 不能被当作分隔符，只切「非电荷」的加号。
    """
    parts = _SPLIT.split(expr, maxsplit=1)
    lhs = parts[0]
    rhs = parts[1] if len(parts) > 1 else ''
    reactants = [_clean(t) for t in _TERM_SPLIT.split(lhs)]
    products = [_clean(t) for t in _TERM_SPLIT.split(rhs)] if rhs else []
    return [r for r in reactants if r], [p for p in products if p]


# 切分「项与项之间的 +」：其后必须跟着一个物种的开头（大写字母 / 数字 / ( / [），
# 这样电荷里的 +（Cu^2+、H+）不会被误切；开头的数字是前系数，会在 _clean 中去掉。
_TERM_SPLIT = re.compile(r'\s*\+(?=\s*[A-Z\d(\[])\s*')


def _clean(term):
    term = _STRIP.sub('', term)
    term = re.sub(r'^\d+', '', term)  # 去掉前导系数
    return term.strip()


def format_equation(reactants, products, coeffs, conditions=''):
    """输出配平后的显示串：2Na + 2H₂O =(点燃) 2NaOH + H₂↑。"""
    left = ' + '.join(_coeff_term(c, f) for c, f in zip(coeffs[:len(reactants)], reactants))
    right = ' + '.join(_coeff_term(c, f) for c, f in zip(coeffs[len(reactants):], products))
    eq = '=' + (f'({conditions})' if conditions else '')
    return f'{left} {eq} {right}'


def _coeff_term(c, f):
    return (str(c) if c != 1 else '') + pretty_formula(f)


# --------------------------------------------------------------------------- #
# 五、命令行入口
# --------------------------------------------------------------------------- #

def _cli():
    args = sys.argv[1:]
    if not args:
        print('用法见文件头部 docstring')
        return

    if args[0] == '--balance-lib':
        inp, outp = args[1], args[2]
        with open(inp, encoding='utf-8') as f:
            data = json.load(f)
        results = []
        for entry in data:
            r = entry['reactants']
            p = entry['products']
            cond = entry.get('conditions', '')
            coeffs = balance(r, p)
            ok = verify(r, p, coeffs) if coeffs else False
            results.append({**entry, 'coeffs': coeffs, 'balanced': ok,
                            'equation': format_equation(r, p, coeffs, cond) if ok else None})
        with open(outp, 'w', encoding='utf-8') as f:
            json.dump(results, f, ensure_ascii=False, indent=2)
        total = len(results)
        ok = sum(1 for x in results if x['balanced'])
        print(f'共 {total} 条，配平成功 {ok} 条，失败 {total - ok} 条')
        for x in results:
            if not x['balanced']:
                print('  失败:', ' + '.join(x['reactants']), '→', ' + '.join(x['products']))
        return

    if args[0] == '--predict':
        reactants = [t.strip() for t in args[1].split('+')]
        for products in predict_products(reactants):
            coeffs = balance(reactants, products)
            if coeffs and verify(reactants, products, coeffs):
                print(format_equation(reactants, products, coeffs))
        return

    expr = args[0]
    reactants, products = split_expr(expr)
    coeffs = balance(reactants, products)
    if coeffs and verify(reactants, products, coeffs):
        print(format_equation(reactants, products, coeffs))
    else:
        print('无法配平（无解或不唯一）')


if __name__ == '__main__':
    _cli()

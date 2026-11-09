# -*- coding: utf-8 -*-
"""
生成 assets/reaction_dict.json（2026-11-08）。

输入：本文件内的 reactions_raw 列表（反应物/产物/条件，化学式用 ASCII 存储）。
过程：调 chem_balance.balance 算系数 → verify 校验 → pretty_formula 生成显示串。
输出：../app/src/main/assets/reaction_dict.json

化学式 ASCII 约定：
  - 下标直接写数字：H2O, Fe2O3
  - 电荷用 ^：Cu^2+, SO4^2-, H+, OH^-
  - 水合物用 ·：CuSO4·5H2O
"""
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import chem_balance as cb

# 首批反应库：中学常见反应（分子 + 离子），含条件与气体/沉淀标记。
# gas/precip 与 reactants+products 同序。
reactions_raw = [
    # ---- 碱金属与水 ----
    {"reactants": ["Na", "H2O"], "products": ["NaOH", "H2"], "conditions": "",
     "gas": [False, False, False, True], "precip": [False, False, False, False]},
    {"reactants": ["K", "H2O"], "products": ["KOH", "H2"], "conditions": "",
     "gas": [False, False, False, True], "precip": [False, False, False, False]},
    {"reactants": ["Li", "H2O"], "products": ["LiOH", "H2"], "conditions": "",
     "gas": [False, False, False, True], "precip": [False, False, False, False]},
    {"reactants": ["Ca", "H2O"], "products": ["Ca(OH)2", "H2"], "conditions": "",
     "gas": [False, False, False, True], "precip": [False, False, False, False]},

    # ---- 燃烧 ----
    {"reactants": ["H2", "O2"], "products": ["H2O"], "conditions": "点燃",
     "gas": [False, False, False], "precip": [False, False, False]},
    {"reactants": ["C", "O2"], "products": ["CO2"], "conditions": "点燃",
     "gas": [False, False, False], "precip": [False, False, False]},
    {"reactants": ["S", "O2"], "products": ["SO2"], "conditions": "点燃",
     "gas": [False, False, False], "precip": [False, False, False]},
    {"reactants": ["P", "O2"], "products": ["P2O5"], "conditions": "点燃",
     "gas": [False, False, False], "precip": [False, False, False]},
    {"reactants": ["Fe", "O2"], "products": ["Fe3O4"], "conditions": "点燃",
     "gas": [False, False, False], "precip": [False, False, False]},
    {"reactants": ["Mg", "O2"], "products": ["MgO"], "conditions": "点燃",
     "gas": [False, False, False], "precip": [False, False, False]},
    {"reactants": ["Al", "O2"], "products": ["Al2O3"], "conditions": "点燃",
     "gas": [False, False, False], "precip": [False, False, False]},
    {"reactants": ["CH4", "O2"], "products": ["CO2", "H2O"], "conditions": "点燃",
     "gas": [False, False, False, False], "precip": [False, False, False, False]},
    {"reactants": ["C2H5OH", "O2"], "products": ["CO2", "H2O"], "conditions": "点燃",
     "gas": [False, False, False, False], "precip": [False, False, False, False]},
    {"reactants": ["C2H4", "O2"], "products": ["CO2", "H2O"], "conditions": "点燃",
     "gas": [False, False, False, False], "precip": [False, False, False, False]},
    {"reactants": ["C2H2", "O2"], "products": ["CO2", "H2O"], "conditions": "点燃",
     "gas": [False, False, False, False], "precip": [False, False, False, False]},
    {"reactants": ["CO", "O2"], "products": ["CO2"], "conditions": "点燃",
     "gas": [False, False, False], "precip": [False, False, False]},

    # ---- 金属 + 酸 ----
    {"reactants": ["Zn", "H2SO4"], "products": ["ZnSO4", "H2"], "conditions": "",
     "gas": [False, False, False, True], "precip": [False, False, False, False]},
    {"reactants": ["Fe", "H2SO4"], "products": ["FeSO4", "H2"], "conditions": "",
     "gas": [False, False, False, True], "precip": [False, False, False, False]},
    {"reactants": ["Mg", "H2SO4"], "products": ["MgSO4", "H2"], "conditions": "",
     "gas": [False, False, False, True], "precip": [False, False, False, False]},
    {"reactants": ["Al", "H2SO4"], "products": ["Al2(SO4)3", "H2"], "conditions": "",
     "gas": [False, False, False, True], "precip": [False, False, False, False]},
    {"reactants": ["Zn", "HCl"], "products": ["ZnCl2", "H2"], "conditions": "",
     "gas": [False, False, False, True], "precip": [False, False, False, False]},
    {"reactants": ["Fe", "HCl"], "products": ["FeCl2", "H2"], "conditions": "",
     "gas": [False, False, False, True], "precip": [False, False, False, False]},
    {"reactants": ["Mg", "HCl"], "products": ["MgCl2", "H2"], "conditions": "",
     "gas": [False, False, False, True], "precip": [False, False, False, False]},
    {"reactants": ["Al", "HCl"], "products": ["AlCl3", "H2"], "conditions": "",
     "gas": [False, False, False, True], "precip": [False, False, False, False]},

    # ---- 金属 + 盐（置换） ----
    {"reactants": ["Fe", "CuSO4"], "products": ["FeSO4", "Cu"], "conditions": "",
     "gas": [False, False, False, False], "precip": [False, False, False, False]},
    {"reactants": ["Zn", "CuSO4"], "products": ["ZnSO4", "Cu"], "conditions": "",
     "gas": [False, False, False, False], "precip": [False, False, False, False]},
    {"reactants": ["Fe", "CuCl2"], "products": ["FeCl2", "Cu"], "conditions": "",
     "gas": [False, False, False, False], "precip": [False, False, False, False]},
    {"reactants": ["Cu", "AgNO3"], "products": ["Cu(NO3)2", "Ag"], "conditions": "",
     "gas": [False, False, False, False], "precip": [False, False, False, False]},
    {"reactants": ["Zn", "FeSO4"], "products": ["ZnSO4", "Fe"], "conditions": "",
     "gas": [False, False, False, False], "precip": [False, False, False, False]},

    # ---- 酸碱中和 ----
    {"reactants": ["HCl", "NaOH"], "products": ["NaCl", "H2O"], "conditions": "",
     "gas": [False, False, False, False], "precip": [False, False, False, False]},
    {"reactants": ["H2SO4", "NaOH"], "products": ["Na2SO4", "H2O"], "conditions": "",
     "gas": [False, False, False, False], "precip": [False, False, False, False]},
    {"reactants": ["HNO3", "NaOH"], "products": ["NaNO3", "H2O"], "conditions": "",
     "gas": [False, False, False, False], "precip": [False, False, False, False]},
    {"reactants": ["HCl", "KOH"], "products": ["KCl", "H2O"], "conditions": "",
     "gas": [False, False, False, False], "precip": [False, False, False, False]},
    {"reactants": ["H2SO4", "KOH"], "products": ["K2SO4", "H2O"], "conditions": "",
     "gas": [False, False, False, False], "precip": [False, False, False, False]},
    {"reactants": ["HCl", "Ca(OH)2"], "products": ["CaCl2", "H2O"], "conditions": "",
     "gas": [False, False, False, False], "precip": [False, False, False, False]},
    {"reactants": ["H2SO4", "Ca(OH)2"], "products": ["CaSO4", "H2O"], "conditions": "",
     "gas": [False, False, False, False], "precip": [False, False, False, False]},
    {"reactants": ["HCl", "Ba(OH)2"], "products": ["BaCl2", "H2O"], "conditions": "",
     "gas": [False, False, False, False], "precip": [False, False, False, False]},
    {"reactants": ["H2SO4", "Ba(OH)2"], "products": ["BaSO4", "H2O"], "conditions": "",
     "gas": [False, False, False, False], "precip": [False, False, True, False]},

    # ---- 酸 + 盐 ----
    {"reactants": ["HCl", "CaCO3"], "products": ["CaCl2", "H2O", "CO2"], "conditions": "",
     "gas": [False, False, False, False, True], "precip": [False, False, False, False, False]},
    {"reactants": ["H2SO4", "Na2CO3"], "products": ["Na2SO4", "H2O", "CO2"], "conditions": "",
     "gas": [False, False, False, False, True], "precip": [False, False, False, False, False]},
    {"reactants": ["HCl", "Na2CO3"], "products": ["NaCl", "H2O", "CO2"], "conditions": "",
     "gas": [False, False, False, False, True], "precip": [False, False, False, False, False]},
    {"reactants": ["HNO3", "CaCO3"], "products": ["Ca(NO3)2", "H2O", "CO2"], "conditions": "",
     "gas": [False, False, False, False, True], "precip": [False, False, False, False, False]},
    {"reactants": ["H2SO4", "BaCl2"], "products": ["BaSO4", "HCl"], "conditions": "",
     "gas": [False, False, False, False], "precip": [False, False, True, False]},
    {"reactants": ["HCl", "AgNO3"], "products": ["AgCl", "HNO3"], "conditions": "",
     "gas": [False, False, False, False], "precip": [False, False, True, False]},

    # ---- 盐 + 盐（复分解） ----
    {"reactants": ["NaCl", "AgNO3"], "products": ["AgCl", "NaNO3"], "conditions": "",
     "gas": [False, False, False, False], "precip": [False, False, True, False]},
    {"reactants": ["BaCl2", "Na2SO4"], "products": ["BaSO4", "NaCl"], "conditions": "",
     "gas": [False, False, False, False], "precip": [False, False, True, False]},
    {"reactants": ["CaCl2", "Na2CO3"], "products": ["CaCO3", "NaCl"], "conditions": "",
     "gas": [False, False, False, False], "precip": [False, False, True, False]},
    {"reactants": ["BaCl2", "Na2CO3"], "products": ["BaCO3", "NaCl"], "conditions": "",
     "gas": [False, False, False, False], "precip": [False, False, True, False]},
    {"reactants": ["AgNO3", "NaCl"], "products": ["AgCl", "NaNO3"], "conditions": "",
     "gas": [False, False, False, False], "precip": [False, False, True, False]},
    {"reactants": ["CuSO4", "NaOH"], "products": ["Cu(OH)2", "Na2SO4"], "conditions": "",
     "gas": [False, False, False, False], "precip": [False, False, True, False]},
    {"reactants": ["FeCl3", "NaOH"], "products": ["Fe(OH)3", "NaCl"], "conditions": "",
     "gas": [False, False, False, False], "precip": [False, False, True, False]},
    {"reactants": ["MgCl2", "NaOH"], "products": ["Mg(OH)2", "NaCl"], "conditions": "",
     "gas": [False, False, False, False], "precip": [False, False, True, False]},
    {"reactants": ["CuSO4", "BaCl2"], "products": ["BaSO4", "CuCl2"], "conditions": "",
     "gas": [False, False, False, False], "precip": [False, False, True, False]},

    # ---- 氧化物 + 酸 ----
    {"reactants": ["CuO", "H2SO4"], "products": ["CuSO4", "H2O"], "conditions": "",
     "gas": [False, False, False, False], "precip": [False, False, False, False]},
    {"reactants": ["Fe2O3", "HCl"], "products": ["FeCl3", "H2O"], "conditions": "",
     "gas": [False, False, False, False], "precip": [False, False, False, False]},
    {"reactants": ["Fe2O3", "H2SO4"], "products": ["Fe2(SO4)3", "H2O"], "conditions": "",
     "gas": [False, False, False, False], "precip": [False, False, False, False]},
    {"reactants": ["CaO", "HCl"], "products": ["CaCl2", "H2O"], "conditions": "",
     "gas": [False, False, False, False], "precip": [False, False, False, False]},
    {"reactants": ["MgO", "HCl"], "products": ["MgCl2", "H2O"], "conditions": "",
     "gas": [False, False, False, False], "precip": [False, False, False, False]},
    {"reactants": ["ZnO", "H2SO4"], "products": ["ZnSO4", "H2O"], "conditions": "",
     "gas": [False, False, False, False], "precip": [False, False, False, False]},

    # ---- 氧化物 + 水 ----
    {"reactants": ["CaO", "H2O"], "products": ["Ca(OH)2"], "conditions": "",
     "gas": [False, False, False], "precip": [False, False, False]},
    {"reactants": ["Na2O", "H2O"], "products": ["NaOH"], "conditions": "",
     "gas": [False, False, False], "precip": [False, False, False]},
    {"reactants": ["SO3", "H2O"], "products": ["H2SO4"], "conditions": "",
     "gas": [False, False, False], "precip": [False, False, False]},
    {"reactants": ["CO2", "H2O"], "products": ["H2CO3"], "conditions": "",
     "gas": [False, False, False], "precip": [False, False, False]},
    {"reactants": ["SO2", "H2O"], "products": ["H2SO3"], "conditions": "",
     "gas": [False, False, False], "precip": [False, False, False]},

    # ---- 非金属氧化物 + 碱 ----
    {"reactants": ["CO2", "NaOH"], "products": ["Na2CO3", "H2O"], "conditions": "",
     "gas": [False, False, False, False], "precip": [False, False, False, False]},
    {"reactants": ["CO2", "Ca(OH)2"], "products": ["CaCO3", "H2O"], "conditions": "",
     "gas": [False, False, False, False], "precip": [False, False, True, False]},
    {"reactants": ["SO2", "NaOH"], "products": ["Na2SO3", "H2O"], "conditions": "",
     "gas": [False, False, False, False], "precip": [False, False, False, False]},
    {"reactants": ["SO3", "NaOH"], "products": ["Na2SO4", "H2O"], "conditions": "",
     "gas": [False, False, False, False], "precip": [False, False, False, False]},

    # ---- 还原反应 ----
    {"reactants": ["Fe2O3", "CO"], "products": ["Fe", "CO2"], "conditions": "高温",
     "gas": [False, False, False, False], "precip": [False, False, False, False]},
    {"reactants": ["Fe2O3", "C"], "products": ["Fe", "CO2"], "conditions": "高温",
     "gas": [False, False, False, False], "precip": [False, False, False, False]},
    {"reactants": ["Fe2O3", "H2"], "products": ["Fe", "H2O"], "conditions": "加热",
     "gas": [False, False, False, False], "precip": [False, False, False, False]},
    {"reactants": ["CuO", "H2"], "products": ["Cu", "H2O"], "conditions": "加热",
     "gas": [False, False, False, False], "precip": [False, False, False, False]},
    {"reactants": ["CuO", "C"], "products": ["Cu", "CO2"], "conditions": "高温",
     "gas": [False, False, False, False], "precip": [False, False, False, False]},
    {"reactants": ["CuO", "CO"], "products": ["Cu", "CO2"], "conditions": "加热",
     "gas": [False, False, False, False], "precip": [False, False, False, False]},
    {"reactants": ["Fe3O4", "CO"], "products": ["Fe", "CO2"], "conditions": "高温",
     "gas": [False, False, False, False], "precip": [False, False, False, False]},
    {"reactants": ["WO3", "H2"], "products": ["W", "H2O"], "conditions": "高温",
     "gas": [False, False, False, False], "precip": [False, False, False, False]},

    # ---- 分解反应 ----
    {"reactants": ["H2O2"], "products": ["H2O", "O2"], "conditions": "MnO2",
     "gas": [False, False, True], "precip": [False, False, False]},
    {"reactants": ["KClO3"], "products": ["KCl", "O2"], "conditions": "MnO2,加热",
     "gas": [False, False, True], "precip": [False, False, False]},
    {"reactants": ["KMnO4"], "products": ["K2MnO4", "MnO2", "O2"], "conditions": "加热",
     "gas": [False, False, False, True], "precip": [False, False, False, False]},
    {"reactants": ["CaCO3"], "products": ["CaO", "CO2"], "conditions": "高温",
     "gas": [False, False, True], "precip": [False, False, False]},
    {"reactants": ["H2CO3"], "products": ["H2O", "CO2"], "conditions": "",
     "gas": [False, False, True], "precip": [False, False, False]},
    {"reactants": ["NH4HCO3"], "products": ["NH3", "H2O", "CO2"], "conditions": "加热",
     "gas": [False, True, False, True], "precip": [False, False, False, False]},
    {"reactants": ["HgO"], "products": ["Hg", "O2"], "conditions": "加热",
     "gas": [False, False, True], "precip": [False, False, False]},

    # ---- 铵盐 + 碱 ----
    {"reactants": ["NH4Cl", "NaOH"], "products": ["NaCl", "H2O", "NH3"], "conditions": "加热",
     "gas": [False, False, False, False, True], "precip": [False, False, False, False, False]},
    {"reactants": ["(NH4)2SO4", "NaOH"], "products": ["Na2SO4", "H2O", "NH3"], "conditions": "加热",
     "gas": [False, False, False, False, True], "precip": [False, False, False, False, False]},
    {"reactants": ["NH4NO3", "NaOH"], "products": ["NaNO3", "H2O", "NH3"], "conditions": "加热",
     "gas": [False, False, False, False, True], "precip": [False, False, False, False, False]},

    # ---- 其他 ----
    {"reactants": ["CO2", "C"], "products": ["CO"], "conditions": "高温",
     "gas": [False, False, False], "precip": [False, False, False]},
    {"reactants": ["Cl2", "H2O"], "products": ["HCl", "HClO"], "conditions": "",
     "gas": [False, False, False, False], "precip": [False, False, False, False]},
    {"reactants": ["Na", "Cl2"], "products": ["NaCl"], "conditions": "点燃",
     "gas": [False, False, False], "precip": [False, False, False]},
    {"reactants": ["Fe", "Cl2"], "products": ["FeCl3"], "conditions": "点燃",
     "gas": [False, False, False], "precip": [False, False, False]},
    {"reactants": ["Cu", "Cl2"], "products": ["CuCl2"], "conditions": "点燃",
     "gas": [False, False, False], "precip": [False, False, False]},
    {"reactants": ["H2", "Cl2"], "products": ["HCl"], "conditions": "点燃",
     "gas": [False, False, False], "precip": [False, False, False]},
    {"reactants": ["N2", "H2"], "products": ["NH3"], "conditions": "高温高压催化剂",
     "gas": [False, False, False], "precip": [False, False, False]},

    # ---- 离子方程式 ----
    {"reactants": ["H^+", "OH^-"], "products": ["H2O"], "conditions": "",
     "gas": [False, False, False], "precip": [False, False, False]},
    {"reactants": ["Fe", "Cu^2+"], "products": ["Fe^2+", "Cu"], "conditions": "",
     "gas": [False, False, False, False], "precip": [False, False, False, False]},
    {"reactants": ["Zn", "Cu^2+"], "products": ["Zn^2+", "Cu"], "conditions": "",
     "gas": [False, False, False, False], "precip": [False, False, False, False]},
    {"reactants": ["Cl^-", "Ag^+"], "products": ["AgCl"], "conditions": "",
     "gas": [False, False, False], "precip": [False, False, True]},
    {"reactants": ["Ba^2+", "SO4^2-"], "products": ["BaSO4"], "conditions": "",
     "gas": [False, False, False], "precip": [False, False, True]},
    {"reactants": ["Ca^2+", "CO3^2-"], "products": ["CaCO3"], "conditions": "",
     "gas": [False, False, False], "precip": [False, False, True]},
    {"reactants": ["H^+", "CO3^2-"], "products": ["H2O", "CO2"], "conditions": "",
     "gas": [False, False, False, True], "precip": [False, False, False, False]},
    {"reactants": ["H^+", "HCO3^-"], "products": ["H2O", "CO2"], "conditions": "",
     "gas": [False, False, False, True], "precip": [False, False, False, False]},
    {"reactants": ["Ag^+", "Cl^-"], "products": ["AgCl"], "conditions": "",
     "gas": [False, False, False], "precip": [False, False, True]},
    {"reactants": ["NH4^+", "OH^-"], "products": ["H2O", "NH3"], "conditions": "",
     "gas": [False, False, False, True], "precip": [False, False, False, False]},
    {"reactants": ["Fe^3+", "OH^-"], "products": ["Fe(OH)3"], "conditions": "",
     "gas": [False, False, False], "precip": [False, False, True]},
    {"reactants": ["Cu^2+", "OH^-"], "products": ["Cu(OH)2"], "conditions": "",
     "gas": [False, False, False], "precip": [False, False, True]},
    {"reactants": ["Fe", "H^+"], "products": ["Fe^2+", "H2"], "conditions": "",
     "gas": [False, False, False, True], "precip": [False, False, False, False]},
    {"reactants": ["Zn", "H^+"], "products": ["Zn^2+", "H2"], "conditions": "",
     "gas": [False, False, False, True], "precip": [False, False, False, False]},
    {"reactants": ["Al", "H^+"], "products": ["Al^3+", "H2"], "conditions": "",
     "gas": [False, False, False, True], "precip": [False, False, False, False]},
    {"reactants": ["Cu", "Ag^+"], "products": ["Cu^2+", "Ag"], "conditions": "",
     "gas": [False, False, False, False], "precip": [False, False, False, False]},
    {"reactants": ["CO2", "OH^-"], "products": ["CO3^2-", "H2O"], "conditions": "",
     "gas": [False, False, False, False], "precip": [False, False, False, False]},
    {"reactants": ["CO3^2-", "H2O", "CO2"], "products": ["HCO3^-"], "conditions": "",
     "gas": [False, False, False, False], "precip": [False, False, False, False]},
    {"reactants": ["Fe^2+", "OH^-"], "products": ["Fe(OH)2"], "conditions": "",
     "gas": [False, False, False], "precip": [False, False, True]},
]


_RE_CHARGE_ANY = re.compile(r'\^\d*[+-]')


def detect_type(r, p):
    """含任意电荷（^+、^-、^2+、^3-……）即判为离子条目。"""
    joined = " ".join(r + p)
    if _RE_CHARGE_ANY.search(joined):
        return "ionic"
    return "molecular"


_RE_COND_FORMULA = re.compile(r'[A-Z][A-Za-z0-9()\[\]·]*')


def pretty_condition(cond):
    """把条件文本中的化学式（MnO2、FeBr3、V2O5…）转成带下标的显示串。"""
    if not cond:
        return cond

    def conv(m):
        return cb.pretty_formula(m.group(0))

    return _RE_COND_FORMULA.sub(conv, cond)


# ---------------------- 条件归属（2026-11-08 客户要求） ---------------------- #
# 「浓 / 稀 / 少量 / 过量 / 足量」这类针对某个反应物的要求，必须写在该反应物后面：
#   2KMnO4 + 16HCl(浓) = 2KCl + 2MnCl2 + 5Cl2↑ + 8H2O
#   CO2(少量) + 2OH^- = CO3^2- + H2O
# 不能把整串条件挂到等号后面写成「=(浓盐酸)」。
# 只有当条件里点名的物质真的出现在反应物里才归属，否则视为外加试剂 / 催化剂
# （例如酯化反应里的浓硫酸），仍按原方式挂在等号后。

# 条件里用中文写物质名时的映射（覆盖中学常见试剂即可）。
COND_NAME_FORMULA = {
    "盐酸": "HCl",
    "硫酸": "H2SO4",
    "硝酸": "HNO3",
    "磷酸": "H3PO4",
    "氢氧化钠": "NaOH",
    "氢氧化钾": "KOH",
    "氢氧化钙": "Ca(OH)2",
    "氢氧化钡": "Ba(OH)2",
    "氨水": "NH3·H2O",
    "二氧化碳": "CO2",
    "二氧化硫": "SO2",
    "硫化氢": "H2S",
    "氯气": "Cl2",
    "氧气": "O2",
    "氯化铝": "AlCl3",
    "氯化铁": "FeCl3",
    "氯化亚铁": "FeCl2",
    "硫酸铜": "CuSO4",
    "碳酸钠": "Na2CO3",
    "碳酸氢钠": "NaHCO3",
    "偏铝酸钠": "NaAlO2",
    "硫化钠": "Na2S",
    "亚硫酸钠": "Na2SO3",
    "硅酸钠": "Na2SiO3",
    "苯酚": "C6H5OH",
}

# 长词在前：极稀 必须先于 稀 匹配。
COND_LABELS = ("极稀", "少量", "过量", "足量", "微量", "浓", "稀")

_RE_FORMULA_NAME = re.compile(r'^[A-Z][A-Za-z0-9()\[\]·]*$')


def resolve_condition_formula(name):
    """条件里的物质名 → 化学式；认不出返回 None。"""
    name = (name or "").strip()
    if not name:
        return None
    if name in COND_NAME_FORMULA:
        return COND_NAME_FORMULA[name]
    if _RE_FORMULA_NAME.match(name):
        return name
    return None


def split_condition_notes(cond, reactants):
    """把条件拆成（归属到反应物的标注, 仍挂在等号后的条件）。

    返回 (notes, rest)：notes 形如 {"HCl": "浓"}、{"CO2": "少量"}；
    rest 是剩余条件（如 "加热"），可为空串。
    """
    if not cond:
        return {}, ""
    notes, rest = {}, []
    for seg in [s.strip() for s in cond.split(",") if s.strip()]:
        hit = None
        for label in COND_LABELS:
            if not seg.startswith(label):
                continue
            f = resolve_condition_formula(seg[len(label):])
            if f and f in reactants:
                hit = (f, label)
            break
        if hit:
            notes[hit[0]] = hit[1]
        else:
            rest.append(seg)
    return notes, ",".join(rest)


def equation_with_marks(r, p, coeffs, cond, gas, precip):
    """生成带 ↑↓ 标记与反应物标注的显示串。

    条件里点名的反应物（浓硫酸、少量CO2…）写成「16HCl(浓)」「CO2(少量)」，
    其余条件（点燃、加热…）仍挂在等号后面。
    """
    notes, rest = split_condition_notes(cond, r)
    all_species = r + p
    terms = []
    for i, f in enumerate(all_species):
        c = coeffs[i]
        term = (str(c) if c != 1 else "") + cb.pretty_formula(f)
        if i < len(r) and f in notes:
            term += "(" + notes[f] + ")"
        if i < len(gas) and gas[i]:
            term += "↑"
        if i < len(precip) and precip[i]:
            term += "↓"
        terms.append(term)
    left = " + ".join(terms[:len(r)])
    right = " + ".join(terms[len(r):])
    eq = "=" + (f"({pretty_condition(rest)})" if rest else "")
    return f"{left} {eq} {right}"


def all_entries():
    """原有 112 条 + 分批扩充数据（reactions_extra / b2 / b3 / b4）。"""
    entries = list(reactions_raw)
    for name in ("reactions_extra", "reactions_b2", "reactions_b3",
                 "reactions_b4", "reactions_b5", "reactions_b6"):
        try:
            mod = __import__(name)
        except ImportError:
            continue
        entries.extend(getattr(mod, "EXTRA_REACTIONS", []))
    return entries


PUBLIC_KEYS = ("reactants", "products", "coeffs", "conditions",
               "gas", "precip", "type", "equation")


def build_results(entries=None, verbose=True):
    """配平并构造输出记录。返回 (results, fail_list)。

    results 中额外保留 ion_mode/tags/net_override/full_override，
    供 gen_ion_reactions 使用；写 reaction_dict.json 时只输出 PUBLIC_KEYS。
    """
    if entries is None:
        entries = all_entries()
    results = []
    fails = []
    # 同一反应若另有一条带条件的写法（少量/过量/浓/稀…），没有条件的重复项一并继承该条件：
    # 否则查询时可能命中没标注的那条，出现「CO₂ + 2OH⁻ = CO₃²⁻ + H₂O」这类丢条件的结果。
    cond_index = {}
    for entry in entries:
        c = (entry.get("conditions") or "").strip()
        if c:
            k = (tuple(sorted(entry["reactants"])), tuple(entry["products"]))
            cond_index.setdefault(k, c)
    seen = set()
    for entry in entries:
        r = entry["reactants"]
        p = entry["products"]
        cond = (entry.get("conditions") or "").strip()
        if not cond:
            cond = cond_index.get((tuple(sorted(r)), tuple(p)), "")
        dedup_key = (tuple(sorted(r)), tuple(p), cond)
        if dedup_key in seen:
            continue
        seen.add(dedup_key)
        coeffs = entry.get("coeffs") or cb.balance(r, p)
        ok = cb.verify(r, p, coeffs) if coeffs else False
        if not ok:
            fails.append(entry)
            if verbose:
                print("FAIL:", " + ".join(r), "→", " + ".join(p))
            continue
        gas = entry.get("gas", [False] * (len(r) + len(p)))
        precip = entry.get("precip", [False] * (len(r) + len(p)))
        equation = equation_with_marks(r, p, coeffs, cond, gas, precip)
        rec = {
            "reactants": r,
            "products": p,
            "coeffs": coeffs,
            "conditions": cond,
            "gas": gas,
            "precip": precip,
            "type": detect_type(r, p),
            "equation": equation,
        }
        for k in ("ion_mode", "tags", "net_override", "full_override"):
            if k in entry:
                rec[k] = entry[k]
        results.append(rec)
    return results, fails


def main():
    results, fails = build_results()
    out_dir = os.path.join(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
        "app", "src", "main", "assets")
    os.makedirs(out_dir, exist_ok=True)
    out_path = os.path.join(out_dir, "reaction_dict.json")
    public = [{k: r[k] for k in PUBLIC_KEYS} for r in results]
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(public, f, ensure_ascii=False, indent=2)
    print(f"共 {len(results)} 条成功，{len(fails)} 条失败 → {out_path}")


if __name__ == "__main__":
    main()

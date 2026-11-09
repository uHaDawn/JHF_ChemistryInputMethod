# -*- coding: utf-8 -*-
"""
离子反应拆分规则库（2026-11-08）。

用途：
  - 给 gen_ion_reactions.py 提供「哪些物质在水溶液里拆成离子」的判定；
  - 统一强酸/强碱/可溶盐、弱电解质、难溶物、气体、氧化物的处理口径；
  - 维护纯离子条目到完整化学方程式的映射。

约定：
  - 化学式用 ASCII：H2SO4、CuSO4、NH3·H2O；电荷用 ^：H^+、SO4^2-；
  - 可拆物质映射到「离子 + 个数」列表，个数对应一个物质分子解体后的离子数；
  - 不在 DISSOCIATION 里的物质默认不拆（弱酸、弱碱、难溶物、气体、单质、氧化物、有机物）。
"""
from collections import OrderedDict

# --------------------------------------------------------------------------- #
# 一、强电解质拆分表
# --------------------------------------------------------------------------- #
# 值：[(离子式, 离子个数), ...]
DISSOCIATION = OrderedDict()

def _add(formula, ions):
    DISSOCIATION[formula] = list(ions)

# ---- 强酸 ----
_add("HCl", [("H^+", 1), ("Cl^-", 1)])
_add("HBr", [("H^+", 1), ("Br^-", 1)])
_add("HI", [("H^+", 1), ("I^-", 1)])
_add("HNO3", [("H^+", 1), ("NO3^-", 1)])
_add("H2SO4", [("H^+", 2), ("SO4^2-", 1)])
_add("HClO4", [("H^+", 1), ("ClO4^-", 1)])
_add("HBrO4", [("H^+", 1), ("BrO4^-", 1)])
_add("HIO4", [("H^+", 1), ("IO4^-", 1)])

# ---- 强碱 ----
_add("NaOH", [("Na^+", 1), ("OH^-", 1)])
_add("KOH", [("K^+", 1), ("OH^-", 1)])
_add("LiOH", [("Li^+", 1), ("OH^-", 1)])
_add("RbOH", [("Rb^+", 1), ("OH^-", 1)])
_add("CsOH", [("Cs^+", 1), ("OH^-", 1)])
_add("Ba(OH)2", [("Ba^2+", 1), ("OH^-", 2)])
_add("Sr(OH)2", [("Sr^2+", 1), ("OH^-", 2)])
_add("Ca(OH)2", [("Ca^2+", 1), ("OH^-", 2)])

# ---- 盐酸盐 / 溴化物 / 碘化物（除 AgX、HgX2、PbX2 外可溶） ----
for _cat, _ion, _n in [("Na", "Na^+", 1), ("K", "K^+", 1), ("Li", "Li^+", 1),
                       ("NH4", "NH4^+", 1)]:
    for _hal, _an in [("Cl", "Cl^-"), ("Br", "Br^-"), ("I", "I^-")]:
        _add(_cat + _hal, [(_ion, 1), (_an, 1)])

_add("MgCl2", [("Mg^2+", 1), ("Cl^-", 2)])
_add("MgBr2", [("Mg^2+", 1), ("Br^-", 2)])
_add("MgI2", [("Mg^2+", 1), ("I^-", 2)])
_add("CaCl2", [("Ca^2+", 1), ("Cl^-", 2)])
_add("CaBr2", [("Ca^2+", 1), ("Br^-", 2)])
_add("CaI2", [("Ca^2+", 1), ("I^-", 2)])
_add("BaCl2", [("Ba^2+", 1), ("Cl^-", 2)])
_add("BaBr2", [("Ba^2+", 1), ("Br^-", 2)])
_add("BaI2", [("Ba^2+", 1), ("I^-", 2)])
_add("ZnCl2", [("Zn^2+", 1), ("Cl^-", 2)])
_add("ZnBr2", [("Zn^2+", 1), ("Br^-", 2)])
_add("FeCl2", [("Fe^2+", 1), ("Cl^-", 2)])
_add("FeBr2", [("Fe^2+", 1), ("Br^-", 2)])
_add("FeI2", [("Fe^2+", 1), ("I^-", 2)])
_add("CuCl2", [("Cu^2+", 1), ("Cl^-", 2)])
_add("CuBr2", [("Cu^2+", 1), ("Br^-", 2)])
_add("MnCl2", [("Mn^2+", 1), ("Cl^-", 2)])
_add("SnCl2", [("Sn^2+", 1), ("Cl^-", 2)])
_add("Pb(NO3)2", [("Pb^2+", 1), ("NO3^-", 2)])
_add("AlCl3", [("Al^3+", 1), ("Cl^-", 3)])
_add("AlBr3", [("Al^3+", 1), ("Br^-", 3)])
_add("FeCl3", [("Fe^3+", 1), ("Cl^-", 3)])
_add("FeBr3", [("Fe^3+", 1), ("Br^-", 3)])
_add("CrCl3", [("Cr^3+", 1), ("Cl^-", 3)])

# ---- 硝酸盐（几乎全溶） ----
for _f, _i in [
    ("NaNO3", [("Na^+", 1), ("NO3^-", 1)]),
    ("KNO3", [("K^+", 1), ("NO3^-", 1)]),
    ("LiNO3", [("Li^+", 1), ("NO3^-", 1)]),
    ("NH4NO3", [("NH4^+", 1), ("NO3^-", 1)]),
    ("Mg(NO3)2", [("Mg^2+", 1), ("NO3^-", 2)]),
    ("Ca(NO3)2", [("Ca^2+", 1), ("NO3^-", 2)]),
    ("Ba(NO3)2", [("Ba^2+", 1), ("NO3^-", 2)]),
    ("Zn(NO3)2", [("Zn^2+", 1), ("NO3^-", 2)]),
    ("Fe(NO3)2", [("Fe^2+", 1), ("NO3^-", 2)]),
    ("Cu(NO3)2", [("Cu^2+", 1), ("NO3^-", 2)]),
    ("Mn(NO3)2", [("Mn^2+", 1), ("NO3^-", 2)]),
    ("Pb(NO3)2", [("Pb^2+", 1), ("NO3^-", 2)]),
    ("Fe(NO3)3", [("Fe^3+", 1), ("NO3^-", 3)]),
    ("Al(NO3)3", [("Al^3+", 1), ("NO3^-", 3)]),
    ("Cr(NO3)3", [("Cr^3+", 1), ("NO3^-", 3)]),
    ("AgNO3", [("Ag^+", 1), ("NO3^-", 1)]),
]:
    _add(_f, _i)

# ---- 硫酸盐（BaSO4、PbSO4 难溶；CaSO4、Ag2SO4 微溶，产物侧不拆） ----
for _f, _i in [
    ("Na2SO4", [("Na^+", 2), ("SO4^2-", 1)]),
    ("K2SO4", [("K^+", 2), ("SO4^2-", 1)]),
    ("Li2SO4", [("Li^+", 2), ("SO4^2-", 1)]),
    ("(NH4)2SO4", [("NH4^+", 2), ("SO4^2-", 1)]),
    ("MgSO4", [("Mg^2+", 1), ("SO4^2-", 1)]),
    ("ZnSO4", [("Zn^2+", 1), ("SO4^2-", 1)]),
    ("FeSO4", [("Fe^2+", 1), ("SO4^2-", 1)]),
    ("CuSO4", [("Cu^2+", 1), ("SO4^2-", 1)]),
    ("MnSO4", [("Mn^2+", 1), ("SO4^2-", 1)]),
    ("Fe2(SO4)3", [("Fe^3+", 2), ("SO4^2-", 3)]),
    ("Al2(SO4)3", [("Al^3+", 2), ("SO4^2-", 3)]),
    ("Cr2(SO4)3", [("Cr^3+", 2), ("SO4^2-", 3)]),
    ("NaHSO4", [("Na^+", 1), ("H^+", 1), ("SO4^2-", 1)]),
    ("KHSO4", [("K^+", 1), ("H^+", 1), ("SO4^2-", 1)]),
]:
    _add(_f, _i)

# ---- 碳酸盐 / 碳酸氢盐 ----
for _f, _i in [
    ("Na2CO3", [("Na^+", 2), ("CO3^2-", 1)]),
    ("K2CO3", [("K^+", 2), ("CO3^2-", 1)]),
    ("Li2CO3", [("Li^+", 2), ("CO3^2-", 1)]),
    ("(NH4)2CO3", [("NH4^+", 2), ("CO3^2-", 1)]),
    ("NaHCO3", [("Na^+", 1), ("HCO3^-", 1)]),
    ("KHCO3", [("K^+", 1), ("HCO3^-", 1)]),
    ("NH4HCO3", [("NH4^+", 1), ("HCO3^-", 1)]),
]:
    _add(_f, _i)

# ---- 亚硫酸盐 / 亚硫酸氢盐 ----
for _f, _i in [
    ("Na2SO3", [("Na^+", 2), ("SO3^2-", 1)]),
    ("K2SO3", [("K^+", 2), ("SO3^2-", 1)]),
    ("(NH4)2SO3", [("NH4^+", 2), ("SO3^2-", 1)]),
    ("NaHSO3", [("Na^+", 1), ("HSO3^-", 1)]),
    ("KHSO3", [("K^+", 1), ("HSO3^-", 1)]),
]:
    _add(_f, _i)

# ---- 磷酸盐 ----
for _f, _i in [
    ("Na3PO4", [("Na^+", 3), ("PO4^3-", 1)]),
    ("K3PO4", [("K^+", 3), ("PO4^3-", 1)]),
    ("(NH4)3PO4", [("NH4^+", 3), ("PO4^3-", 1)]),
    ("Na2HPO4", [("Na^+", 2), ("HPO4^2-", 1)]),
    ("K2HPO4", [("K^+", 2), ("HPO4^2-", 1)]),
    ("NaH2PO4", [("Na^+", 1), ("H2PO4^-", 1)]),
    ("KH2PO4", [("K^+", 1), ("H2PO4^-", 1)]),
    ("Ca(H2PO4)2", [("Ca^2+", 1), ("H2PO4^-", 2)]),
]:
    _add(_f, _i)

# ---- 硫化物 / 硫氢化物（钠钾铵可溶） ----
for _f, _i in [
    ("Na2S", [("Na^+", 2), ("S^2-", 1)]),
    ("K2S", [("K^+", 2), ("S^2-", 1)]),
    ("(NH4)2S", [("NH4^+", 2), ("S^2-", 1)]),
    ("NaHS", [("Na^+", 1), ("HS^-", 1)]),
    ("KHS", [("K^+", 1), ("HS^-", 1)]),
]:
    _add(_f, _i)

# ---- 卤素的含氧酸盐 / 其他可溶盐 ----
for _f, _i in [
    ("NaClO", [("Na^+", 1), ("ClO^-", 1)]),
    ("KClO", [("K^+", 1), ("ClO^-", 1)]),
    ("Ca(ClO)2", [("Ca^2+", 1), ("ClO^-", 2)]),
    ("NaClO3", [("Na^+", 1), ("ClO3^-", 1)]),
    ("KClO3", [("K^+", 1), ("ClO3^-", 1)]),
    ("NaClO4", [("Na^+", 1), ("ClO4^-", 1)]),
    ("KClO4", [("K^+", 1), ("ClO4^-", 1)]),
    ("NaBrO3", [("Na^+", 1), ("BrO3^-", 1)]),
    ("KBrO3", [("K^+", 1), ("BrO3^-", 1)]),
    ("NaIO3", [("Na^+", 1), ("IO3^-", 1)]),
    ("KIO3", [("K^+", 1), ("IO3^-", 1)]),
    ("NaF", [("Na^+", 1), ("F^-", 1)]),
    ("KF", [("K^+", 1), ("F^-", 1)]),
    ("NH4F", [("NH4^+", 1), ("F^-", 1)]),
    ("NaCN", [("Na^+", 1), ("CN^-", 1)]),
    ("KCN", [("K^+", 1), ("CN^-", 1)]),
    ("NaSCN", [("Na^+", 1), ("SCN^-", 1)]),
    ("KSCN", [("K^+", 1), ("SCN^-", 1)]),
    ("Na2SiO3", [("Na^+", 2), ("SiO3^2-", 1)]),
    ("K2SiO3", [("K^+", 2), ("SiO3^2-", 1)]),
    ("NaAlO2", [("Na^+", 1), ("AlO2^-", 1)]),
    ("KAlO2", [("K^+", 1), ("AlO2^-", 1)]),
    ("Na2ZnO2", [("Na^+", 2), ("ZnO2^2-", 1)]),
    ("K2ZnO2", [("K^+", 2), ("ZnO2^2-", 1)]),
    ("KMnO4", [("K^+", 1), ("MnO4^-", 1)]),
    ("NaMnO4", [("Na^+", 1), ("MnO4^-", 1)]),
    ("K2Cr2O7", [("K^+", 2), ("Cr2O7^2-", 1)]),
    ("Na2Cr2O7", [("Na^+", 2), ("Cr2O7^2-", 1)]),
    ("K2CrO4", [("K^+", 2), ("CrO4^2-", 1)]),
    ("Na2CrO4", [("Na^+", 2), ("CrO4^2-", 1)]),
    ("Na2S2O3", [("Na^+", 2), ("S2O3^2-", 1)]),
    ("Na2S2O8", [("Na^+", 2), ("S2O8^2-", 1)]),
    ("CH3COONa", [("Na^+", 1), ("CH3COO^-", 1)]),
    ("CH3COOK", [("K^+", 1), ("CH3COO^-", 1)]),
    ("CH3COONH4", [("NH4^+", 1), ("CH3COO^-", 1)]),
    ("HCOONa", [("Na^+", 1), ("HCOO^-", 1)]),
    ("HCOOK", [("K^+", 1), ("HCOO^-", 1)]),
    ("Na2C2O4", [("Na^+", 2), ("C2O4^2-", 1)]),
    ("K2C2O4", [("K^+", 2), ("C2O4^2-", 1)]),
    ("Na2B4O7", [("Na^+", 2), ("B4O7^2-", 1)]),
    ("Na2CO3", [("Na^+", 2), ("CO3^2-", 1)]),
]:
    _add(_f, _i)

# ---- 配离子盐（竞赛） ----
for _f, _i in [
    ("[Cu(NH3)4]SO4", [("[Cu(NH3)4]^2+", 1), ("SO4^2-", 1)]),
    ("[Ag(NH3)2]OH", [("[Ag(NH3)2]^+", 1), ("OH^-", 1)]),
    ("[Ag(NH3)2]NO3", [("[Ag(NH3)2]^+", 1), ("NO3^-", 1)]),
    ("K3[Fe(CN)6]", [("K^+", 3), ("[Fe(CN)6]^3-", 1)]),
    ("K4[Fe(CN)6]", [("K^+", 4), ("[Fe(CN)6]^4-", 1)]),
    ("KSCN", [("K^+", 1), ("SCN^-", 1)]),
]:
    _add(_f, _i)

# 可溶配合物（整体作离子，不再二次拆分）
COMPLEX_IONS = {
    "[Cu(NH3)4]^2+", "[Ag(NH3)2]^+", "[Fe(CN)6]^3-", "[Fe(CN)6]^4-",
    "[Zn(NH3)4]^2+", "[CuCl4]^2-", "[Ag(CN)2]^-", "[Fe(SCN)]^2+",
}

# --------------------------------------------------------------------------- #
# 二、产物侧不拆的微溶物
# --------------------------------------------------------------------------- #
# 微溶物作为产物时按分子/沉淀处理；作为反应物（澄清溶液）允许拆。
PRODUCT_NO_DISSOCIATE = {
    "CaSO4", "Ag2SO4", "MgCO3", "CaCO3", "PbCl2", "Ca(OH)2", "Mg(OH)2",
}

# 反应条件里出现这些关键字时，不按水溶液离子反应处理。
NON_IONIC_CONDITIONS = (
    "点燃", "高温", "灼烧", "熔融", "固体", "浓硫酸", "浓盐酸", "浓硝酸", "浓H2SO4",
)

# --------------------------------------------------------------------------- #
# 三、常见气体 / 沉淀（用于自动 ↑↓ 标注）
# --------------------------------------------------------------------------- #
GAS_SPECIES = {
    "H2", "O2", "N2", "Cl2", "F2", "CO2", "SO2", "SO3", "NO", "NO2", "N2O",
    "N2O3", "N2O4", "N2O5", "NH3", "H2S", "HBr", "HI", "HCl", "CH4", "C2H2",
    "C2H4", "C2H6", "C3H6", "C3H8", "C4H8", "C4H10", "PH3", "SiH4", "CO",
}

PRECIPITATES = {
    "AgCl", "AgBr", "AgI", "Ag2S", "Ag2CO3", "Ag2CrO4", "Ag3PO4", "AgOH",
    "BaSO4", "BaSO3", "BaCO3", "BaCrO4", "Ba3(PO4)2", "BaSiO3",
    "CaCO3", "CaSO4", "CaSO3", "CaSiO3", "Ca3(PO4)2", "CaF2", "CaC2O4",
    "Cu(OH)2", "CuCO3", "CuS", "Cu2S", "Cu2O", "CuCl", "CuI",
    "Fe(OH)3", "Fe(OH)2", "FeS", "FeCO3", "FeC2O4",
    "Mg(OH)2", "MgCO3", "Mg3(PO4)2", "MgC2O4",
    "Al(OH)3", "AlPO4", "Al2S3",
    "FePO4", "Ag2HPO4",
    "Zn(OH)2", "ZnS", "ZnCO3", "Zn3(PO4)2",
    "PbSO4", "PbCO3", "PbS", "PbI2", "PbCl2", "PbCrO4", "Pb(OH)2",
    "HgS", "HgI2", "Hg2Cl2", "Bi2S3", "CdS", "SnS", "SnS2", "Sb2S3",
    "MnS", "MnCO3", "Mn(OH)2", "NiS", "CoS", "Cr(OH)3",
    "Ni(OH)2", "Co(OH)2", "Cd(OH)2", "NiCO3", "CoCO3", "CdCO3",
    "H2SiO3", "H4SiO4", "BaSO4", "SrSO4", "SrCO3", "BaHPO4",
}

# 难溶但通常写作分子的弱酸（不拆）
WEAK_ACIDS = {
    "HClO", "HF", "H2CO3", "H2SO3", "H2S", "CH3COOH", "HCOOH",
    "H2C2O4", "H3PO4", "HCN", "H2SiO3", "HAlO2", "H2B4O7",
    "C6H5OH", "HNO2", "H2C2H3O2", "H2O", "H2O2",
}

# 弱碱（不拆）
WEAK_BASES = {
    "NH3·H2O", "NH3", "Fe(OH)2", "Fe(OH)3", "Cu(OH)2", "Al(OH)3",
    "Mg(OH)2", "Zn(OH)2", "AgOH", "Pb(OH)2", "Cr(OH)3", "Mn(OH)2",
}

# 纯离子条目 → 对应完整化学方程式（现有反应库中的离子条目补全用）
IONIC_ONLY_MOLECULAR = {
    "H^+|OH^-": "HCl + NaOH = NaCl + H2O",
    "Cu^2+|Fe": "Fe + CuSO4 = FeSO4 + Cu",
    "Cu^2+|Zn": "Zn + CuSO4 = ZnSO4 + Cu",
    "Ag^+|Cl^-": "AgNO3 + NaCl = AgCl↓ + NaNO3",
    "Ba^2+|SO4^2-": "BaCl2 + Na2SO4 = BaSO4↓ + 2NaCl",
    "CO3^2-|Ca^2+": "CaCl2 + Na2CO3 = CaCO3↓ + 2NaCl",
    "CO3^2-|H^+": "Na2CO3 + 2HCl = 2NaCl + H2O + CO2↑",
    "HCO3^-|H^+": "NaHCO3 + HCl = NaCl + H2O + CO2↑",
    "NH4^+|OH^-": "NH4Cl + NaOH = NaCl + H2O + NH3↑",
    "Fe^3+|OH^-": "FeCl3 + 3NaOH = Fe(OH)3↓ + 3NaCl",
    "Cu^2+|OH^-": "CuSO4 + 2NaOH = Cu(OH)2↓ + Na2SO4",
    "Fe|H^+": "Fe + 2HCl = FeCl2 + H2↑",
    "H^+|Zn": "Zn + 2HCl = ZnCl2 + H2↑",
    "Al|H^+": "2Al + 6HCl = 2AlCl3 + 3H2↑",
    "Ag^+|Cu": "Cu + 2AgNO3 = Cu(NO3)2 + 2Ag",
    "CO2|OH^-": "CO2 + 2NaOH = Na2CO3 + H2O",
    "CO2|CO3^2-|H2O": "Na2CO3 + H2O + CO2 = 2NaHCO3",
    "Fe^2+|OH^-": "FeSO4 + 2NaOH = Fe(OH)2↓ + Na2SO4",
}


# --------------------------------------------------------------------------- #
# 四、对外接口
# --------------------------------------------------------------------------- #
def dissociate(formula, side="reactant"):
    """返回物质的离子拆分列表；不可拆返回 None。

    side="product" 时，微溶物按分子处理（如 CaSO4、Ca(OH)2）。
    """
    f = formula.strip()
    if side == "product" and f in PRODUCT_NO_DISSOCIATE:
        return None
    if f in DISSOCIATION:
        return DISSOCIATION[f]
    return None


def is_non_ionic_condition(conditions):
    """条件关键字判定是否按非水溶液反应处理。"""
    c = conditions or ""
    return any(k in c for k in NON_IONIC_CONDITIONS)


def is_gas(formula):
    return formula.strip() in GAS_SPECIES


def is_precipitate(formula):
    return formula.strip() in PRECIPITATES


def ionic_only_molecular(reactants):
    """纯离子条目的反应物集合 → 完整化学方程式（无匹配返回空串）。"""
    key = "|".join(sorted(f.strip() for f in reactants))
    return IONIC_ONLY_MOLECULAR.get(key, "")


def sort_key(formulas):
    """反应物规范 key（排序后用 | 连接），与 ReactionEngine.reactantKey 一致。"""
    return "|".join(sorted(f.strip() for f in formulas))

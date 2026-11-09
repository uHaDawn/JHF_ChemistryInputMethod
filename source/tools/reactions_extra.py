# -*- coding: utf-8 -*-
"""
初高中化学扩充反应库（批次 1：酸碱 / 复分解 / 盐 / 气体 / 氧化物）。

数据约定：
  - 化学式 ASCII：H2SO4、CuSO4、NH3·H2O；电荷 ^：H^+、SO4^2-；
  - R(...) 自动按产物判断 ↑↓，也可显式传入 gas/precip 覆盖；
  - coeffs 留空时由 chem_balance 自动配平；难配平的反应显式给出系数；
  - ion_mode：auto（自动拆写离子式）/ none（无离子式）/ manual（用 override）。
"""
import ion_rules as ir


def R(reactants, products, conditions="", gas=None, precip=None, tags=None,
      ion_mode="auto", net_override=None, full_override=None, coeffs=None):
    n_r = len(reactants)
    if gas is None:
        gas = [False] * n_r + [ir.is_gas(p) and p != "HCl" for p in products]
    if precip is None:
        precip = [False] * n_r + [ir.is_precipitate(p) for p in products]
    entry = {
        "reactants": list(reactants),
        "products": list(products),
        "conditions": conditions,
        "gas": gas,
        "precip": precip,
        "ion_mode": ion_mode,
    }
    if tags:
        entry["tags"] = tags
    if net_override:
        entry["net_override"] = net_override
    if full_override:
        entry["full_override"] = full_override
    if coeffs:
        entry["coeffs"] = coeffs
    return entry


# --------------------------------------------------------------------------- #
# 一、酸碱中和（补充组合，含弱酸弱碱）
# --------------------------------------------------------------------------- #
B1_NEUTRAL = [
    R(["HNO3", "KOH"], ["KNO3", "H2O"], tags=["中和"]),
    R(["HNO3", "Ca(OH)2"], ["Ca(NO3)2", "H2O"], tags=["中和"]),
    R(["HNO3", "Ba(OH)2"], ["Ba(NO3)2", "H2O"], tags=["中和"]),
    R(["HBr", "NaOH"], ["NaBr", "H2O"], tags=["中和"]),
    R(["HBr", "KOH"], ["KBr", "H2O"], tags=["中和"]),
    R(["HI", "NaOH"], ["NaI", "H2O"], tags=["中和"]),
    R(["HI", "KOH"], ["KI", "H2O"], tags=["中和"]),
    R(["HClO4", "NaOH"], ["NaClO4", "H2O"], tags=["中和"]),
    R(["HClO4", "KOH"], ["KClO4", "H2O"], tags=["中和"]),

    # 弱酸 + 强碱
    R(["CH3COOH", "NaOH"], ["CH3COONa", "H2O"], tags=["中和", "弱酸"]),
    R(["CH3COOH", "KOH"], ["CH3COOK", "H2O"], tags=["中和", "弱酸"]),
    R(["CH3COOH", "Ca(OH)2"], ["(CH3COO)2Ca", "H2O"], tags=["中和", "弱酸"]),
    R(["HCOOH", "NaOH"], ["HCOONa", "H2O"], tags=["中和", "弱酸"]),
    R(["HCOOH", "KOH"], ["HCOOK", "H2O"], tags=["中和", "弱酸"]),
    R(["HClO", "NaOH"], ["NaClO", "H2O"], tags=["中和", "弱酸"]),
    R(["HClO", "KOH"], ["KClO", "H2O"], tags=["中和", "弱酸"]),
    R(["HClO", "Ca(OH)2"], ["Ca(ClO)2", "H2O"], tags=["中和", "弱酸"]),
    R(["HF", "NaOH"], ["NaF", "H2O"], tags=["中和", "弱酸"]),
    R(["HF", "KOH"], ["KF", "H2O"], tags=["中和", "弱酸"]),
    R(["HCN", "NaOH"], ["NaCN", "H2O"], tags=["中和", "弱酸"]),
    R(["HCN", "KOH"], ["KCN", "H2O"], tags=["中和", "弱酸"]),
    R(["H2S", "NaOH"], ["Na2S", "H2O"], tags=["中和", "弱酸"]),
    R(["H2S", "KOH"], ["K2S", "H2O"], tags=["中和", "弱酸"]),
    R(["H2SO3", "NaOH"], ["Na2SO3", "H2O"], tags=["中和", "弱酸"]),
    R(["H2SO3", "KOH"], ["K2SO3", "H2O"], tags=["中和", "弱酸"]),
    R(["H3PO4", "NaOH"], ["Na3PO4", "H2O"], tags=["中和", "弱酸"]),
    R(["H3PO4", "Ca(OH)2"], ["Ca3(PO4)2", "H2O"], tags=["中和", "弱酸"]),
    R(["H2C2O4", "NaOH"], ["Na2C2O4", "H2O"], tags=["中和", "弱酸"]),

    # 弱碱 + 强酸
    R(["NH3·H2O", "HCl"], ["NH4Cl", "H2O"], tags=["中和", "弱碱"]),
    R(["NH3·H2O", "HNO3"], ["NH4NO3", "H2O"], tags=["中和", "弱碱"]),
    R(["NH3·H2O", "H2SO4"], ["(NH4)2SO4", "H2O"], tags=["中和", "弱碱"]),
    R(["NH3·H2O", "CH3COOH"], ["CH3COONH4", "H2O"], tags=["中和", "弱碱", "弱酸"]),
    R(["NH3·H2O", "H2CO3"], ["(NH4)2CO3", "H2O"], tags=["中和", "弱碱", "弱酸"]),

    # 难溶碱 + 酸
    R(["Cu(OH)2", "HCl"], ["CuCl2", "H2O"], tags=["中和", "难溶碱"]),
    R(["Cu(OH)2", "H2SO4"], ["CuSO4", "H2O"], tags=["中和", "难溶碱"]),
    R(["Cu(OH)2", "HNO3"], ["Cu(NO3)2", "H2O"], tags=["中和", "难溶碱"]),
    R(["Fe(OH)3", "HCl"], ["FeCl3", "H2O"], tags=["中和", "难溶碱"]),
    R(["Fe(OH)3", "H2SO4"], ["Fe2(SO4)3", "H2O"], tags=["中和", "难溶碱"]),
    R(["Fe(OH)3", "HNO3"], ["Fe(NO3)3", "H2O"], tags=["中和", "难溶碱"]),
    R(["Mg(OH)2", "HCl"], ["MgCl2", "H2O"], tags=["中和", "难溶碱"]),
    R(["Mg(OH)2", "H2SO4"], ["MgSO4", "H2O"], tags=["中和", "难溶碱"]),
    R(["Fe(OH)2", "HCl"], ["FeCl2", "H2O"], tags=["中和", "难溶碱"]),
    R(["Fe(OH)2", "H2SO4"], ["FeSO4", "H2O"], tags=["中和", "难溶碱"]),
    R(["Al(OH)3", "HCl"], ["AlCl3", "H2O"], tags=["中和", "两性"]),
    R(["Al(OH)3", "H2SO4"], ["Al2(SO4)3", "H2O"], tags=["中和", "两性"]),
    R(["Zn(OH)2", "HCl"], ["ZnCl2", "H2O"], tags=["中和", "两性"]),
    R(["Zn(OH)2", "H2SO4"], ["ZnSO4", "H2O"], tags=["中和", "两性"]),

    # 两性氢氧化物 + 强碱
    R(["Al(OH)3", "NaOH"], ["NaAlO2", "H2O"], tags=["两性"]),
    R(["Al(OH)3", "KOH"], ["KAlO2", "H2O"], tags=["两性"]),
    R(["Zn(OH)2", "NaOH"], ["Na2ZnO2", "H2O"], tags=["两性"]),
    R(["Zn(OH)2", "KOH"], ["K2ZnO2", "H2O"], tags=["两性"]),
]

# --------------------------------------------------------------------------- #
# 二、酸 + 盐（弱酸盐、挥发性酸盐、硅酸盐、偏铝酸盐）
# --------------------------------------------------------------------------- #
B1_ACID_SALT = [
    R(["HCl", "NaHCO3"], ["NaCl", "H2O", "CO2"], tags=["复分解", "酸式盐"]),
    R(["H2SO4", "NaHCO3"], ["Na2SO4", "H2O", "CO2"], tags=["复分解", "酸式盐"]),
    R(["HNO3", "NaHCO3"], ["NaNO3", "H2O", "CO2"], tags=["复分解", "酸式盐"]),
    R(["HCl", "KHCO3"], ["KCl", "H2O", "CO2"], tags=["复分解", "酸式盐"]),
    R(["CH3COOH", "NaHCO3"], ["CH3COONa", "H2O", "CO2"], tags=["复分解", "弱酸"]),
    R(["CH3COOH", "Na2CO3"], ["CH3COONa", "H2O", "CO2"], tags=["复分解", "弱酸"]),

    R(["HCl", "MgCO3"], ["MgCl2", "H2O", "CO2"], tags=["复分解"]),
    R(["HCl", "BaCO3"], ["BaCl2", "H2O", "CO2"], tags=["复分解"]),
    R(["H2SO4", "MgCO3"], ["MgSO4", "H2O", "CO2"], tags=["复分解"]),
    R(["H2SO4", "CaCO3"], ["CaSO4", "H2O", "CO2"], tags=["复分解"]),
    R(["H2SO4", "BaCO3"], ["BaSO4", "H2O", "CO2"], tags=["复分解"]),
    R(["HNO3", "Na2CO3"], ["NaNO3", "H2O", "CO2"], tags=["复分解"]),
    R(["HNO3", "BaCO3"], ["Ba(NO3)2", "H2O", "CO2"], tags=["复分解"]),

    R(["HCl", "Na2SO3"], ["NaCl", "H2O", "SO2"], tags=["复分解"]),
    R(["H2SO4", "Na2SO3"], ["Na2SO4", "H2O", "SO2"], tags=["复分解"]),
    R(["HCl", "K2SO3"], ["KCl", "H2O", "SO2"], tags=["复分解"]),
    R(["HNO3", "Na2SO3"], ["NaNO3", "H2O", "SO2"], tags=["复分解"]),
    R(["HCl", "NaHSO3"], ["NaCl", "H2O", "SO2"], tags=["复分解", "酸式盐"]),
    R(["H2SO4", "NaHSO3"], ["Na2SO4", "H2O", "SO2"], tags=["复分解", "酸式盐"]),

    R(["HCl", "Na2S"], ["NaCl", "H2S"], tags=["复分解"]),
    R(["H2SO4", "Na2S"], ["Na2SO4", "H2S"], tags=["复分解"]),
    R(["HCl", "K2S"], ["KCl", "H2S"], tags=["复分解"]),
    R(["CH3COOH", "Na2S"], ["CH3COONa", "H2S"], tags=["复分解", "弱酸"]),
    R(["HCl", "NaHS"], ["NaCl", "H2S"], tags=["复分解", "酸式盐"]),

    R(["HCl", "Na2SiO3"], ["NaCl", "H2SiO3"], tags=["复分解", "弱酸"]),
    R(["H2SO4", "Na2SiO3"], ["Na2SO4", "H2SiO3"], tags=["复分解", "弱酸"]),
    R(["CO2", "Na2SiO3", "H2O"], ["Na2CO3", "H2SiO3"], tags=["复分解", "弱酸"]),

    R(["HCl", "NaAlO2", "H2O"], ["NaCl", "Al(OH)3"], tags=["复分解", "弱酸"]),
    R(["H2SO4", "NaAlO2", "H2O"], ["Na2SO4", "Al(OH)3"], tags=["复分解", "弱酸"]),
    R(["CO2", "NaAlO2", "H2O"], ["NaHCO3", "Al(OH)3"], tags=["复分解", "弱酸"]),

    R(["HCl", "NaClO"], ["NaCl", "HClO"], tags=["复分解", "弱酸"]),
    R(["H2SO4", "NaClO"], ["Na2SO4", "HClO"], tags=["复分解", "弱酸"]),
    R(["HCl", "NaF"], ["NaCl", "HF"], tags=["复分解", "弱酸"]),
    R(["H2SO4", "NaF"], ["Na2SO4", "HF"], tags=["复分解", "弱酸"]),
    R(["H2SO4", "CaF2"], ["CaSO4", "HF"], conditions="浓硫酸", ion_mode="none",
      tags=["复分解", "弱酸"]),

    R(["HCl", "CH3COONa"], ["NaCl", "CH3COOH"], tags=["复分解", "弱酸"]),
    R(["H2SO4", "CH3COONa"], ["Na2SO4", "CH3COOH"], tags=["复分解", "弱酸"]),
    R(["HCl", "HCOONa"], ["NaCl", "HCOOH"], tags=["复分解", "弱酸"]),
    R(["HCl", "KCN"], ["KCl", "HCN"], tags=["复分解", "弱酸"]),
    R(["H2SO4", "KCN"], ["K2SO4", "HCN"], tags=["复分解", "弱酸"]),

    R(["HCl", "Na2S2O3"], ["NaCl", "H2O", "SO2", "S"], tags=["复分解", "歧化"]),

    R(["HCl", "Na3PO4"], ["NaCl", "NaH2PO4"], tags=["复分解", "酸式盐"]),
    R(["HCl", "Na2HPO4"], ["NaCl", "NaH2PO4"], tags=["复分解", "酸式盐"]),
    R(["HCl", "NaH2PO4"], ["NaCl", "H3PO4"], tags=["复分解", "弱酸"]),

    R(["H2SO4", "NaNO3"], ["NaHSO4", "HNO3"], conditions="微热", ion_mode="none",
      tags=["复分解", "挥发性酸"]),
    R(["H2SO4", "NaCl"], ["NaHSO4", "HCl"], conditions="浓硫酸", ion_mode="none",
      tags=["复分解", "挥发性酸"]),
]

# --------------------------------------------------------------------------- #
# 三、盐 + 盐（复分解，重点补全）
# --------------------------------------------------------------------------- #
B1_SALT_SALT = [
    # 次氯酸钠 / 漂白粉 + 酸（用户点名缺失条目）
    R(["NaClO", "HCl"], ["NaCl", "Cl2", "H2O"], tags=["复分解", "归中"]),
    R(["Ca(ClO)2", "HCl"], ["CaCl2", "Cl2", "H2O"], tags=["复分解", "归中"]),
    R(["NaClO", "NaCl", "H2SO4"], ["Na2SO4", "Cl2", "H2O"], tags=["复分解", "归中"]),
    R(["NaClO", "H2O", "CO2"], ["NaHCO3", "HClO"], tags=["复分解", "弱酸"]),
    R(["Ca(ClO)2", "H2O", "CO2"], ["CaCO3", "HClO"], tags=["复分解", "弱酸"]),

    # 银盐
    R(["AgNO3", "KCl"], ["AgCl", "KNO3"], tags=["复分解"]),
    R(["AgNO3", "KBr"], ["AgBr", "KNO3"], tags=["复分解"]),
    R(["AgNO3", "KI"], ["AgI", "KNO3"], tags=["复分解"]),
    R(["AgNO3", "NaBr"], ["AgBr", "NaNO3"], tags=["复分解"]),
    R(["AgNO3", "NaI"], ["AgI", "NaNO3"], tags=["复分解"]),
    R(["AgNO3", "NH4Cl"], ["AgCl", "NH4NO3"], tags=["复分解"]),
    R(["AgNO3", "MgCl2"], ["AgCl", "Mg(NO3)2"], tags=["复分解"]),
    R(["AgNO3", "CaCl2"], ["AgCl", "Ca(NO3)2"], tags=["复分解"]),
    R(["AgNO3", "FeCl3"], ["AgCl", "Fe(NO3)3"], tags=["复分解"]),
    R(["AgNO3", "Na3PO4"], ["Ag3PO4", "NaNO3"], tags=["复分解"]),
    R(["AgNO3", "Na2HPO4"], ["Ag2HPO4", "NaNO3"], tags=["复分解"]),
    R(["AgNO3", "Na2CO3"], ["Ag2CO3", "NaNO3"], tags=["复分解"]),
    R(["AgNO3", "Na2SO4"], ["Ag2SO4", "NaNO3"], tags=["复分解"]),
    R(["AgNO3", "Na2CrO4"], ["Ag2CrO4", "NaNO3"], tags=["复分解"]),
    R(["AgNO3", "Na2S"], ["Ag2S", "NaNO3"], tags=["复分解"]),
    R(["AgNO3", "NaOH"], ["Ag2O", "NaNO3", "H2O"], tags=["复分解", "难溶碱"]),

    # 钡盐
    R(["BaCl2", "K2SO4"], ["BaSO4", "KCl"], tags=["复分解"]),
    R(["BaCl2", "(NH4)2SO4"], ["BaSO4", "NH4Cl"], tags=["复分解"]),
    R(["BaCl2", "MgSO4"], ["BaSO4", "MgCl2"], tags=["复分解"]),
    R(["BaCl2", "ZnSO4"], ["BaSO4", "ZnCl2"], tags=["复分解"]),
    R(["BaCl2", "FeSO4"], ["BaSO4", "FeCl2"], tags=["复分解"]),
    R(["BaCl2", "Fe2(SO4)3"], ["BaSO4", "FeCl3"], tags=["复分解"]),
    R(["BaCl2", "Al2(SO4)3"], ["BaSO4", "AlCl3"], tags=["复分解"]),
    R(["BaCl2", "NaHSO4"], ["BaSO4", "NaCl", "HCl"], tags=["复分解"]),
    R(["Ba(NO3)2", "Na2SO4"], ["BaSO4", "NaNO3"], tags=["复分解"]),
    R(["Ba(OH)2", "Na2SO4"], ["BaSO4", "NaOH"], tags=["复分解"]),
    R(["Ba(OH)2", "MgSO4"], ["BaSO4", "Mg(OH)2"], tags=["复分解"]),
    R(["Ba(OH)2", "CuSO4"], ["BaSO4", "Cu(OH)2"], tags=["复分解"]),
    R(["Ba(OH)2", "Fe2(SO4)3"], ["BaSO4", "Fe(OH)3"], tags=["复分解"]),
    R(["BaCl2", "K2CO3"], ["BaCO3", "KCl"], tags=["复分解"]),
    R(["BaCl2", "(NH4)2CO3"], ["BaCO3", "NH4Cl"], tags=["复分解"]),
    R(["BaCl2", "Na2SO3"], ["BaSO3", "NaCl"], tags=["复分解"]),
    R(["BaCl2", "K2SO3"], ["BaSO3", "KCl"], tags=["复分解"]),
    R(["BaCl2", "Na2CrO4"], ["BaCrO4", "NaCl"], tags=["复分解"]),

    # 钙盐
    R(["CaCl2", "K2CO3"], ["CaCO3", "KCl"], tags=["复分解"]),
    R(["CaCl2", "(NH4)2CO3"], ["CaCO3", "NH4Cl"], tags=["复分解"]),
    R(["Ca(NO3)2", "Na2CO3"], ["CaCO3", "NaNO3"], tags=["复分解"]),
    R(["CaCl2", "NaF"], ["CaF2", "NaCl"], tags=["复分解"]),
    R(["CaCl2", "Na2C2O4"], ["CaC2O4", "NaCl"], tags=["复分解"]),
    R(["CaCl2", "Na2SO3"], ["CaSO3", "NaCl"], tags=["复分解"]),

    # 铜盐
    R(["CuSO4", "KOH"], ["Cu(OH)2", "K2SO4"], tags=["复分解"]),
    R(["CuCl2", "NaOH"], ["Cu(OH)2", "NaCl"], tags=["复分解"]),
    R(["Cu(NO3)2", "NaOH"], ["Cu(OH)2", "NaNO3"], tags=["复分解"]),
    R(["CuSO4", "Na2S"], ["CuS", "Na2SO4"], tags=["复分解"]),
    R(["CuSO4", "K2S"], ["CuS", "K2SO4"], tags=["复分解"]),
    R(["CuCl2", "Na2S"], ["CuS", "NaCl"], tags=["复分解"]),
    R(["Cu(NO3)2", "Na2CO3"], ["CuCO3", "NaNO3"], tags=["复分解"]),

    # 铁盐
    R(["FeCl3", "KOH"], ["Fe(OH)3", "KCl"], tags=["复分解"]),
    R(["FeCl3", "Na2HPO4"], ["FePO4", "NaCl", "NaH2PO4"], tags=["复分解"]),
    R(["FeSO4", "NaOH"], ["Fe(OH)2", "Na2SO4"], tags=["复分解"]),
    R(["FeSO4", "KOH"], ["Fe(OH)2", "K2SO4"], tags=["复分解"]),
    R(["FeSO4", "Na2S"], ["FeS", "Na2SO4"], tags=["复分解"]),
    R(["FeSO4", "Na2CO3"], ["FeCO3", "Na2SO4"], tags=["复分解"]),
    R(["FeCl3", "Na2CO3", "H2O"], ["Fe(OH)3", "CO2", "NaCl"], tags=["双水解"]),
    R(["AlCl3", "Na2CO3", "H2O"], ["Al(OH)3", "CO2", "NaCl"], tags=["双水解"]),
    R(["Al2(SO4)3", "NaHCO3"], ["Al(OH)3", "CO2", "Na2SO4"], tags=["双水解"]),

    # 锌 / 镁 / 铝 / 锰 / 铅
    R(["ZnSO4", "NaOH"], ["Zn(OH)2", "Na2SO4"], tags=["复分解"]),
    R(["ZnSO4", "KOH"], ["Zn(OH)2", "K2SO4"], tags=["复分解"]),
    R(["ZnSO4", "Na2S"], ["ZnS", "Na2SO4"], tags=["复分解"]),
    R(["ZnCl2", "Na2CO3"], ["ZnCO3", "NaCl"], tags=["复分解"]),
    R(["MgCl2", "KOH"], ["Mg(OH)2", "KCl"], tags=["复分解"]),
    R(["MgSO4", "NaOH"], ["Mg(OH)2", "Na2SO4"], tags=["复分解"]),
    R(["MgSO4", "Na2CO3"], ["MgCO3", "Na2SO4"], tags=["复分解"]),
    R(["AlCl3", "NaOH"], ["Al(OH)3", "NaCl"], tags=["复分解"]),
    R(["Al2(SO4)3", "NaOH"], ["Al(OH)3", "Na2SO4"], tags=["复分解"]),
    R(["AlCl3", "KOH"], ["Al(OH)3", "KCl"], tags=["复分解"]),
    R(["MnCl2", "Na2S"], ["MnS", "NaCl"], tags=["复分解"]),
    R(["MnSO4", "Na2CO3"], ["MnCO3", "Na2SO4"], tags=["复分解"]),
    R(["Pb(NO3)2", "KI"], ["PbI2", "KNO3"], tags=["复分解"]),
    R(["Pb(NO3)2", "NaCl"], ["PbCl2", "NaNO3"], tags=["复分解"]),
    R(["Pb(NO3)2", "Na2SO4"], ["PbSO4", "NaNO3"], tags=["复分解"]),
    R(["Pb(NO3)2", "Na2S"], ["PbS", "NaNO3"], tags=["复分解"]),
    R(["Pb(NO3)2", "Na2CO3"], ["PbCO3", "NaNO3"], tags=["复分解"]),
    R(["Na3PO4", "CaCl2"], ["Ca3(PO4)2", "NaCl"], tags=["复分解"]),
    R(["Na3PO4", "MgCl2"], ["Mg3(PO4)2", "NaCl"], tags=["复分解"]),
    R(["Na3PO4", "FeCl3"], ["FePO4", "NaCl"], tags=["复分解"]),

    # 铵盐 / 钠盐
    R(["Na2CO3", "MgCl2"], ["MgCO3", "NaCl"], tags=["复分解"]),
    R(["Na2CO3", "ZnSO4"], ["ZnCO3", "Na2SO4"], tags=["复分解"]),
]

# --------------------------------------------------------------------------- #
# 四、氧化物 + 酸 / 碱 / 水，非金属氧化物 + 碱
# --------------------------------------------------------------------------- #
B1_OXIDE = [
    R(["CuO", "HCl"], ["CuCl2", "H2O"], tags=["金属氧化物"]),
    R(["CuO", "HNO3"], ["Cu(NO3)2", "H2O"], tags=["金属氧化物"]),
    R(["MgO", "H2SO4"], ["MgSO4", "H2O"], tags=["金属氧化物"]),
    R(["MgO", "HNO3"], ["Mg(NO3)2", "H2O"], tags=["金属氧化物"]),
    R(["CaO", "HNO3"], ["Ca(NO3)2", "H2O"], tags=["金属氧化物"]),
    R(["CaO", "H2SO4"], ["CaSO4", "H2O"], tags=["金属氧化物"]),
    R(["BaO", "HCl"], ["BaCl2", "H2O"], tags=["金属氧化物"]),
    R(["FeO", "HCl"], ["FeCl2", "H2O"], tags=["金属氧化物"]),
    R(["FeO", "H2SO4"], ["FeSO4", "H2O"], tags=["金属氧化物"]),
    R(["Fe3O4", "HCl"], ["FeCl2", "FeCl3", "H2O"], tags=["金属氧化物"]),
    R(["Fe3O4", "H2SO4"], ["FeSO4", "Fe2(SO4)3", "H2O"], tags=["金属氧化物"]),
    R(["Al2O3", "HCl"], ["AlCl3", "H2O"], tags=["金属氧化物", "两性"]),
    R(["Al2O3", "H2SO4"], ["Al2(SO4)3", "H2O"], tags=["金属氧化物", "两性"]),
    R(["Al2O3", "NaOH"], ["NaAlO2", "H2O"], tags=["两性"]),
    R(["Al2O3", "KOH"], ["KAlO2", "H2O"], tags=["两性"]),
    R(["ZnO", "HCl"], ["ZnCl2", "H2O"], tags=["金属氧化物", "两性"]),
    R(["ZnO", "NaOH"], ["Na2ZnO2", "H2O"], tags=["两性"]),
    R(["ZnO", "KOH"], ["K2ZnO2", "H2O"], tags=["两性"]),
    R(["PbO", "HCl"], ["PbCl2", "H2O"], tags=["金属氧化物"]),
    R(["PbO", "NaOH"], ["Na2PbO2", "H2O"], tags=["两性"]),

    R(["SO2", "Ca(OH)2"], ["CaSO3", "H2O"], tags=["酸性氧化物"]),
    R(["SO3", "Ca(OH)2"], ["CaSO4", "H2O"], tags=["酸性氧化物"]),
    R(["CO2", "KOH"], ["K2CO3", "H2O"], tags=["酸性氧化物"]),
    R(["SO2", "Ba(OH)2"], ["BaSO3", "H2O"], tags=["酸性氧化物"]),
    R(["CO2", "Ba(OH)2"], ["BaCO3", "H2O"], tags=["酸性氧化物"]),
    R(["SO3", "Ba(OH)2"], ["BaSO4", "H2O"], tags=["酸性氧化物"]),
    R(["SO2", "KOH"], ["K2SO3", "H2O"], tags=["酸性氧化物"]),
    R(["SO3", "KOH"], ["K2SO4", "H2O"], tags=["酸性氧化物"]),

    R(["CO2", "CaCO3", "H2O"], ["Ca(HCO3)2"], tags=["酸性氧化物"]),
    R(["CO2", "Na2CO3", "H2O"], ["NaHCO3"], tags=["酸性氧化物"]),
    R(["SO2", "Na2SO3", "H2O"], ["NaHSO3"], tags=["酸性氧化物"]),

    R(["P2O5", "H2O"], ["H3PO4"], tags=["酸性氧化物"]),
    R(["P2O5", "NaOH"], ["Na3PO4", "H2O"], tags=["酸性氧化物"]),

    R(["Na2O2", "H2O"], ["NaOH", "O2"], tags=["过氧化物"]),
    R(["Na2O2", "CO2"], ["Na2CO3", "O2"], tags=["过氧化物"]),
    R(["Na2O2", "HCl"], ["NaCl", "H2O2"], tags=["过氧化物"]),
    R(["Na2O2", "H2SO4"], ["Na2SO4", "H2O2"], tags=["过氧化物"]),

    R(["CaO", "SiO2"], ["CaSiO3"], conditions="高温", ion_mode="none",
      tags=["硅酸盐"]),
    R(["SiO2", "NaOH"], ["Na2SiO3", "H2O"], conditions="高温", ion_mode="none",
      tags=["硅酸盐"]),
    R(["SiO2", "CaCO3"], ["CaSiO3", "CO2"], conditions="高温", ion_mode="none",
      tags=["硅酸盐"]),
]

# --------------------------------------------------------------------------- #
# 五、铵盐 + 碱
# --------------------------------------------------------------------------- #
B1_AMMONIUM = [
    R(["NH4Cl", "KOH"], ["KCl", "H2O", "NH3"], conditions="加热", tags=["铵盐"]),
    R(["NH4NO3", "KOH"], ["KNO3", "H2O", "NH3"], conditions="加热", tags=["铵盐"]),
    R(["(NH4)2SO4", "KOH"], ["K2SO4", "H2O", "NH3"], conditions="加热", tags=["铵盐"]),
    R(["NH4Cl", "Ca(OH)2"], ["CaCl2", "H2O", "NH3"], conditions="加热", tags=["铵盐"]),
    R(["NH4NO3", "Ca(OH)2"], ["Ca(NO3)2", "H2O", "NH3"], conditions="加热", tags=["铵盐"]),
    R(["(NH4)2SO4", "Ca(OH)2"], ["CaSO4", "H2O", "NH3"], conditions="加热", tags=["铵盐"]),
    R(["NH4HCO3", "NaOH"], ["Na2CO3", "H2O", "NH3"], conditions="加热", tags=["铵盐"]),
    R(["NH4HCO3", "KOH"], ["K2CO3", "H2O", "NH3"], conditions="加热", tags=["铵盐"]),
    R(["NH4HSO4", "NaOH"], ["Na2SO4", "H2O", "NH3"], tags=["铵盐"]),
    R(["NH4HSO4", "KOH"], ["K2SO4", "H2O", "NH3"], tags=["铵盐"]),
]


EXTRA_REACTIONS = (
    B1_NEUTRAL + B1_ACID_SALT + B1_SALT_SALT + B1_OXIDE + B1_AMMONIUM
)

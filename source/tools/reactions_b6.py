# -*- coding: utf-8 -*-
"""
初高中化学扩充反应库（批次 6：元素化合物系统组合 / 结晶水 / 滴定 / 电化学 / 选修）。

约定同 reactions_extra.R。本批大量条目由组合表循环生成，保证覆盖密度。
"""
from reactions_extra import R

_G = []


def S(salt, other, p1, p2, tags=("复分解",), ion_mode="auto"):
    _G.append(R([salt, other], [p1, p2], ion_mode=ion_mode, tags=list(tags)))


def D(reactant, products, conditions="", tags=("分解",), ion_mode="none"):
    _G.append(R([reactant], products, conditions=conditions,
                 ion_mode=ion_mode, tags=list(tags)))


# --------------------------------------------------------------------------- #
# 一、可溶盐 + 强碱 → 难溶氢氧化物（循环生成）
# --------------------------------------------------------------------------- #
for _salt, _hyd, _by in [
    ("CuSO4", "Cu(OH)2", "Na2SO4"),
    ("CuCl2", "Cu(OH)2", "NaCl"),
    ("Cu(NO3)2", "Cu(OH)2", "NaNO3"),
    ("FeCl3", "Fe(OH)3", "NaCl"),
    ("Fe2(SO4)3", "Fe(OH)3", "Na2SO4"),
    ("FeSO4", "Fe(OH)2", "Na2SO4"),
    ("FeCl2", "Fe(OH)2", "NaCl"),
    ("MgCl2", "Mg(OH)2", "NaCl"),
    ("MgSO4", "Mg(OH)2", "Na2SO4"),
    ("Mg(NO3)2", "Mg(OH)2", "NaNO3"),
    ("ZnCl2", "Zn(OH)2", "NaCl"),
    ("ZnSO4", "Zn(OH)2", "Na2SO4"),
    ("AlCl3", "Al(OH)3", "NaCl"),
    ("Al2(SO4)3", "Al(OH)3", "Na2SO4"),
    ("NiSO4", "Ni(OH)2", "Na2SO4"),
    ("NiCl2", "Ni(OH)2", "NaCl"),
    ("CoCl2", "Co(OH)2", "NaCl"),
    ("MnSO4", "Mn(OH)2", "Na2SO4"),
    ("CdSO4", "Cd(OH)2", "Na2SO4"),
    ("Pb(NO3)2", "Pb(OH)2", "NaNO3"),
]:
    S(_salt, "NaOH", _hyd, _by, tags=["复分解", "难溶碱"])

# --------------------------------------------------------------------------- #
# 二、可溶盐 + 碳酸钠 → 碳酸盐沉淀
# --------------------------------------------------------------------------- #
for _salt, _carb, _by in [
    ("CuSO4", "CuCO3", "Na2SO4"),
    ("ZnSO4", "ZnCO3", "Na2SO4"),
    ("FeSO4", "FeCO3", "Na2SO4"),
    ("MnSO4", "MnCO3", "Na2SO4"),
    ("NiSO4", "NiCO3", "Na2SO4"),
    ("AgNO3", "Ag2CO3", "NaNO3"),
    ("CaCl2", "CaCO3", "NaCl"),
    ("BaCl2", "BaCO3", "NaCl"),
    ("MgSO4", "MgCO3", "Na2SO4"),
    ("Pb(NO3)2", "PbCO3", "NaNO3"),
]:
    S(_salt, "Na2CO3", _carb, _by, tags=["复分解"])

# --------------------------------------------------------------------------- #
# 三、可溶盐 + 硫化钠 → 硫化物沉淀
# --------------------------------------------------------------------------- #
for _salt, _sul, _by in [
    ("CuSO4", "CuS", "Na2SO4"),
    ("ZnSO4", "ZnS", "Na2SO4"),
    ("FeSO4", "FeS", "Na2SO4"),
    ("Pb(NO3)2", "PbS", "NaNO3"),
    ("CdSO4", "CdS", "Na2SO4"),
    ("AgNO3", "Ag2S", "NaNO3"),
    ("HgCl2", "HgS", "NaCl"),
    ("MnSO4", "MnS", "Na2SO4"),
    ("NiSO4", "NiS", "Na2SO4"),
    ("CoCl2", "CoS", "NaCl"),
]:
    S(_salt, "Na2S", _sul, _by, tags=["复分解"])

# --------------------------------------------------------------------------- #
# 四、金属 + 盐溶液（置换，循环生成）
# --------------------------------------------------------------------------- #
for _m, _salt, _p1, _p2 in [
    ("Fe", "CuCl2", "FeCl2", "Cu"),
    ("Fe", "Cu(NO3)2", "Fe(NO3)2", "Cu"),
    ("Fe", "AgNO3", "Fe(NO3)2", "Ag"),
    ("Fe", "Pb(NO3)2", "Fe(NO3)2", "Pb"),
    ("Zn", "CuCl2", "ZnCl2", "Cu"),
    ("Zn", "AgNO3", "Zn(NO3)2", "Ag"),
    ("Zn", "Pb(NO3)2", "Zn(NO3)2", "Pb"),
    ("Mg", "CuSO4", "MgSO4", "Cu"),
    ("Mg", "FeSO4", "MgSO4", "Fe"),
    ("Mg", "ZnSO4", "MgSO4", "Zn"),
    ("Mg", "AgNO3", "Mg(NO3)2", "Ag"),
    ("Al", "CuSO4", "Al2(SO4)3", "Cu"),
    ("Al", "FeSO4", "Al2(SO4)3", "Fe"),
    ("Al", "CuCl2", "AlCl3", "Cu"),
    ("Cu", "Hg(NO3)2", "Cu(NO3)2", "Hg"),
]:
    _G.append(R([_m, _salt], [_p1, _p2], ion_mode="auto",
                tags=["置换", "金属"]))

# --------------------------------------------------------------------------- #
# 五、碳酸盐 / 氢氧化物 / 硝酸盐热分解（循环生成）
# --------------------------------------------------------------------------- #
for _c, _o in [("MgCO3", "MgO"), ("BaCO3", "BaO"), ("ZnCO3", "ZnO"),
               ("CuCO3", "CuO"), ("FeCO3", "FeO"), ("MnCO3", "MnO"),
               ("Ag2CO3", "Ag2O"), ("PbCO3", "PbO")]:
    D(_c, [_o, "CO2"], conditions="高温", tags=["分解", "碳酸盐"])

for _h, _o in [("Cu(OH)2", "CuO"), ("Mg(OH)2", "MgO"), ("Zn(OH)2", "ZnO"),
               ("Ni(OH)2", "NiO"), ("Mn(OH)2", "MnO")]:
    D(_h, [_o, "H2O"], conditions="加热", tags=["分解", "氢氧化物"])

D("Fe(OH)3", ["Fe2O3", "H2O"], conditions="加热", tags=["分解", "氢氧化物"])
D("Fe(OH)2", ["FeO", "H2O"], conditions="隔绝空气,加热", tags=["分解", "氢氧化物"])
D("Al(OH)3", ["Al2O3", "H2O"], conditions="加热", tags=["分解", "氢氧化物"])

for _n, _o in [("KNO3", "KNO2"), ("NaNO3", "NaNO2")]:
    D(_n, [_o, "O2"], conditions="加热", tags=["分解", "硝酸盐"])

for _n, _o in [("Fe(NO3)3", "Fe2O3"), ("Al(NO3)3", "Al2O3"),
               ("Zn(NO3)2", "ZnO"), ("Pb(NO3)2", "PbO"),
               ("Mg(NO3)2", "MgO"), ("Ba(NO3)2", "BaO"),
               ("Ca(NO3)2", "CaO")]:
    D(_n, [_o, "NO2", "O2"], conditions="加热", tags=["分解", "硝酸盐"])

# --------------------------------------------------------------------------- #
# 六、结晶水合物
# --------------------------------------------------------------------------- #
B6_CRYSTAL = [
    R(["CuSO4·5H2O"], ["CuSO4", "H2O"], conditions="加热", ion_mode="none",
      tags=["结晶水"]),
    R(["CuSO4", "H2O"], ["CuSO4·5H2O"], ion_mode="none", tags=["结晶水"]),
    R(["Na2CO3·10H2O"], ["Na2CO3", "H2O"], conditions="风化", ion_mode="none",
      tags=["结晶水"]),
    R(["Na2SO4·10H2O"], ["Na2SO4", "H2O"], conditions="加热", ion_mode="none",
      tags=["结晶水"]),
    R(["CaSO4·2H2O"], ["CaSO4", "H2O"], conditions="加热", ion_mode="none",
      tags=["结晶水"]),
    R(["MgCl2·6H2O"], ["MgCl2", "H2O"], conditions="加热", ion_mode="none",
      tags=["结晶水"]),
]

# --------------------------------------------------------------------------- #
# 七、卤素 / 拟卤素 / 归中与滴定
# --------------------------------------------------------------------------- #
B6_HALOGEN = [
    R(["KIO3", "KI", "H2SO4"], ["I2", "K2SO4", "H2O"], ion_mode="manual",
      net_override="IO3^- + 5I^- + 6H^+ = 3I2 + 3H2O", tags=["卤素", "滴定"]),
    R(["KBrO3", "KBr", "H2SO4"], ["Br2", "K2SO4", "H2O"], ion_mode="manual",
      net_override="BrO3^- + 5Br^- + 6H^+ = 3Br2 + 3H2O", tags=["卤素", "归中"]),
    R(["KClO3", "KCl", "H2SO4"], ["Cl2", "K2SO4", "H2O"], ion_mode="manual",
      net_override="ClO3^- + 5Cl^- + 6H^+ = 3Cl2↑ + 3H2O", tags=["卤素", "归中"]),
    R(["NaClO", "KI", "H2SO4"], ["NaCl", "I2", "K2SO4", "H2O"], ion_mode="manual",
      net_override="ClO^- + 2I^- + 2H^+ = Cl^- + I2 + H2O", tags=["卤素", "滴定"]),
    R(["NaClO", "FeSO4", "H2SO4"], ["NaCl", "Fe2(SO4)3", "H2O"],
      ion_mode="manual",
      net_override="ClO^- + 2Fe^2+ + 2H^+ = Cl^- + 2Fe^3+ + H2O",
      tags=["卤素", "氧化还原"]),
    R(["ClO2", "NaOH"], ["NaClO2", "NaClO3", "H2O"], ion_mode="none",
      tags=["卤素", "歧化"]),
    R(["I2", "KClO3"], ["KIO3", "Cl2"], conditions="加热", ion_mode="none",
      tags=["卤素"]),
    R(["I2", "HNO3"], ["HIO3", "NO2", "H2O"], conditions="浓", ion_mode="none",
      tags=["卤素", "氧化还原"]),
    R(["HCl", "O2"], ["Cl2", "H2O"], conditions="CuCl2,加热", ion_mode="none",
      tags=["卤素"]),
    R(["NaBr", "Cl2"], ["NaCl", "Br2"], ion_mode="auto", tags=["卤素"]),
    R(["NaI", "Cl2"], ["NaCl", "I2"], ion_mode="auto", tags=["卤素"]),
    R(["NaBr", "Br2"], ["NaBr3"], ion_mode="none", tags=["卤素"]),
    R(["KBr", "Br2"], ["KBr3"], ion_mode="none", tags=["卤素"]),
    R(["NaI", "I2"], ["NaI3"], ion_mode="none", tags=["卤素"]),
]

# --------------------------------------------------------------------------- #
# 八、滴定与铬化学
# --------------------------------------------------------------------------- #
B6_TITRATION = [
    R(["KMnO4", "FeSO4", "H2SO4"], ["MnSO4", "Fe2(SO4)3", "K2SO4", "H2O"],
      ion_mode="manual",
      net_override="MnO4^- + 5Fe^2+ + 8H^+ = Mn^2+ + 5Fe^3+ + 4H2O",
      tags=["滴定", "氧化还原"]),
    R(["KMnO4", "Na2SO3", "H2O"], ["MnO2", "Na2SO4", "KOH"], ion_mode="manual",
      net_override="2MnO4^- + 3SO3^2- + H2O = 2MnO2↓ + 3SO4^2- + 2OH^-",
      tags=["滴定", "氧化还原"]),
    R(["K2Cr2O7", "KI", "H2SO4"], ["Cr2(SO4)3", "I2", "K2SO4", "H2O"],
      ion_mode="manual",
      net_override="Cr2O7^2- + 6I^- + 14H^+ = 2Cr^3+ + 3I2 + 7H2O",
      tags=["滴定", "铬"]),
    R(["K2CrO4", "H2SO4"], ["K2Cr2O7", "K2SO4", "H2O"], ion_mode="manual",
      net_override="2CrO4^2- + 2H^+ ⇌ Cr2O7^2- + H2O", tags=["铬"]),
    R(["K2Cr2O7", "KOH"], ["K2CrO4", "H2O"], ion_mode="manual",
      net_override="Cr2O7^2- + 2OH^- ⇌ 2CrO4^2- + H2O", tags=["铬"]),
    R(["Na2S2O8", "KI"], ["I2", "K2SO4", "Na2SO4"], ion_mode="manual",
      net_override="S2O8^2- + 2I^- = 2SO4^2- + I2", tags=["氧化还原", "滴定"]),
    R(["Na2SO3", "I2", "H2O"], ["Na2SO4", "HI"], ion_mode="manual",
      net_override="SO3^2- + I2 + H2O = SO4^2- + 2I^- + 2H^+",
      tags=["滴定", "氧化还原"]),
    R(["Na2SO3", "Br2", "H2O"], ["Na2SO4", "HBr"], ion_mode="manual",
      net_override="SO3^2- + Br2 + H2O = SO4^2- + 2Br^- + 2H^+",
      tags=["滴定", "氧化还原"]),
    R(["Na2SO3", "Cl2", "H2O"], ["Na2SO4", "HCl"], ion_mode="manual",
      net_override="SO3^2- + Cl2 + H2O = SO4^2- + 2Cl^- + 2H^+",
      tags=["滴定", "氧化还原"]),
    R(["H2S", "I2"], ["S", "HI"], ion_mode="manual",
      net_override="H2S + I2 = S↓ + 2H^+ + 2I^-", tags=["氧化还原"]),
    R(["O3", "KI", "H2O"], ["I2", "KOH", "O2"], ion_mode="manual",
      net_override="O3 + 2I^- + H2O = I2 + O2↑ + 2OH^-",
      tags=["氧化还原", "臭氧"]),
    R(["H2O2", "H2S"], ["S", "H2O"], ion_mode="manual",
      net_override="H2O2 + H2S = S↓ + 2H2O", tags=["氧化还原"]),
    R(["H2O2", "SO2"], ["H2SO4"], ion_mode="manual",
      net_override="H2O2 + SO2 = SO4^2- + 2H^+", tags=["氧化还原"]),
]

# --------------------------------------------------------------------------- #
# 九、电化学
# --------------------------------------------------------------------------- #
B6_ELECTRO = [
    R(["H2O"], ["H2", "O2"], conditions="电解", ion_mode="manual",
      net_override="2H2O = 2H2↑ + O2↑", tags=["电解"]),
    R(["CuSO4", "H2O"], ["Cu", "H2SO4", "O2"], conditions="电解", ion_mode="manual",
      net_override="2Cu^2+ + 2H2O = 2Cu + O2↑ + 4H^+", tags=["电解"]),
    R(["AgNO3", "H2O"], ["Ag", "HNO3", "O2"], conditions="电解", ion_mode="manual",
      net_override="4Ag^+ + 2H2O = 4Ag + O2↑ + 4H^+", tags=["电解"]),
    R(["MgCl2", "H2O"], ["Mg(OH)2", "H2", "Cl2"], conditions="电解", ion_mode="manual",
      net_override="Mg^2+ + 2Cl^- + 2H2O = Mg(OH)2↓ + H2↑ + Cl2↑",
      tags=["电解"]),
    R(["H2", "O2"], ["H2O"], conditions="燃料电池", ion_mode="manual",
      net_override="2H2 + O2 = 2H2O", tags=["原电池"]),
    R(["CH4", "O2"], ["CO2", "H2O"], conditions="燃料电池", ion_mode="none",
      tags=["原电池"]),
    R(["CH3OH", "O2"], ["CO2", "H2O"], conditions="燃料电池", ion_mode="none",
      tags=["原电池"]),
    R(["Pb", "PbO2", "H2SO4"], ["PbSO4", "H2O"], conditions="放电", ion_mode="manual",
      net_override="Pb + PbO2 + 4H^+ + 2SO4^2- = 2PbSO4↓ + 2H2O",
      tags=["原电池", "铅蓄电池"]),
    R(["PbSO4", "H2O"], ["Pb", "PbO2", "H2SO4"], conditions="充电",
      ion_mode="manual",
      net_override="2PbSO4 + 2H2O = Pb + PbO2 + 4H^+ + 2SO4^2-",
      tags=["电解", "铅蓄电池"]),
    R(["Zn", "CuSO4"], ["ZnSO4", "Cu"], conditions="原电池", ion_mode="manual",
      net_override="Zn + Cu^2+ = Zn^2+ + Cu", tags=["原电池"]),
    R(["Fe", "CuSO4"], ["FeSO4", "Cu"], conditions="原电池", ion_mode="manual",
      net_override="Fe + Cu^2+ = Fe^2+ + Cu", tags=["原电池"]),
    R(["NaCl"], ["Na", "Cl2"], conditions="电解,熔融", ion_mode="none",
      tags=["电解"]),
    R(["MgCl2"], ["Mg", "Cl2"], conditions="电解,熔融", ion_mode="none",
      tags=["电解"]),
    R(["CaCl2"], ["Ca", "Cl2"], conditions="电解,熔融", ion_mode="none",
      tags=["电解"]),
    R(["AlCl3"], ["Al", "Cl2"], conditions="电解,熔融", ion_mode="none",
      tags=["电解"]),
]

# --------------------------------------------------------------------------- #
# 十、元素化合物特性
# --------------------------------------------------------------------------- #
B6_ELEMENT = [
    R(["Fe", "I2"], ["FeI2"], conditions="加热", ion_mode="none", tags=["铁", "卤素"]),
    R(["Fe", "Br2"], ["FeBr3"], conditions="加热", ion_mode="none", tags=["铁", "卤素"]),
    R(["Fe", "H2O"], ["Fe3O4", "H2"], conditions="高温", ion_mode="none",
      tags=["铁"]),
    R(["Fe", "S"], ["FeS"], conditions="加热", ion_mode="none", tags=["铁"]),
    R(["Cu", "O2"], ["CuO"], conditions="加热", ion_mode="none", tags=["铜"]),
    R(["Cu", "S"], ["Cu2S"], conditions="加热", ion_mode="none", tags=["铜"]),
    R(["Cu", "Cl2"], ["CuCl2"], conditions="点燃", ion_mode="none", tags=["铜"]),
    R(["Ag", "S"], ["Ag2S"], conditions="常温", ion_mode="none", tags=["银"]),
    R(["Ag", "O3"], ["Ag2O", "O2"], ion_mode="none", tags=["银", "臭氧"]),
    R(["Ag", "H2S"], ["Ag2S", "H2"], ion_mode="none", tags=["银"]),
    R(["Hg", "S"], ["HgS"], conditions="研磨", ion_mode="none", tags=["汞"]),
    R(["Zn", "S"], ["ZnS"], conditions="加热", ion_mode="none", tags=["锌"]),
    R(["Na", "S"], ["Na2S"], conditions="研磨", ion_mode="none", tags=["钠"]),
    R(["K", "S"], ["K2S"], conditions="研磨", ion_mode="none", tags=["钾"]),
    R(["Ca", "S"], ["CaS"], conditions="加热", ion_mode="none", tags=["钙"]),
    R(["Mg", "S"], ["MgS"], conditions="加热", ion_mode="none", tags=["镁"]),
    R(["Ba", "O2"], ["BaO2"], conditions="点燃", ion_mode="none", tags=["钡"]),
    R(["Li", "O2"], ["Li2O"], conditions="点燃", ion_mode="none", tags=["锂"]),
    R(["Mg", "H2O"], ["Mg(OH)2", "H2"], conditions="热水", ion_mode="none",
      tags=["镁"]),
    R(["Zn", "H2O"], ["ZnO", "H2"], conditions="高温", ion_mode="none", tags=["锌"]),
    R(["Al", "H2O"], ["Al(OH)3", "H2"], conditions="加热", ion_mode="none",
      tags=["铝"]),
    R(["Na", "NH3"], ["NaNH2", "H2"], conditions="加热", ion_mode="none",
      tags=["钠"]),
    R(["Mg", "N2"], ["Mg3N2"], conditions="点燃", ion_mode="none", tags=["镁"]),
    R(["Ca", "N2"], ["Ca3N2"], conditions="加热", ion_mode="none", tags=["钙"]),
    R(["Al", "N2"], ["AlN"], conditions="高温", ion_mode="none", tags=["铝"]),
    R(["Si", "N2"], ["Si3N4"], conditions="高温", ion_mode="none", tags=["硅"]),
    R(["Si", "C"], ["SiC"], conditions="高温", ion_mode="none", tags=["硅"]),
    R(["B", "O2"], ["B2O3"], conditions="点燃", ion_mode="none", tags=["硼"]),
    R(["B2O3", "H2O"], ["H3BO3"], ion_mode="none", tags=["硼"]),
    R(["H3BO3"], ["B2O3", "H2O"], conditions="加热", ion_mode="none", tags=["硼"]),
]

# --------------------------------------------------------------------------- #
# 十一、选修 / 竞赛补充
# --------------------------------------------------------------------------- #
B6_ADV = [
    R(["NO2"], ["N2O4"], ion_mode="none", tags=["氮", "平衡"]),
    R(["N2O4"], ["NO2"], conditions="加热", ion_mode="none", tags=["氮", "平衡"]),
    R(["O2"], ["O3"], conditions="放电", ion_mode="none", tags=["氧", "同素异形体"]),
    R(["O3"], ["O2"], conditions="催化,加热", ion_mode="none", tags=["氧"]),
    R(["KO2", "H2O"], ["KOH", "H2O2", "O2"], ion_mode="none", tags=["过氧化物"]),
    R(["KO2", "CO2"], ["K2CO3", "O2"], ion_mode="none", tags=["过氧化物"]),
    R(["KO2"], ["K", "O2"], conditions="加热", ion_mode="none", tags=["过氧化物"]),
    R(["CaO2", "H2O"], ["Ca(OH)2", "H2O2"], ion_mode="none", tags=["过氧化物"]),
    R(["CaO2", "HCl"], ["CaCl2", "H2O2"], ion_mode="none", tags=["过氧化物"]),
    R(["BaO2", "H2SO4"], ["BaSO4", "H2O2"], ion_mode="none", tags=["过氧化物"]),
    R(["Na2O2", "H2SO4"], ["Na2SO4", "H2O2"], conditions="稀,冷",
      ion_mode="none", tags=["过氧化物"]),
    R(["H2O2"], ["H2O", "O2"], conditions="MnO2", ion_mode="none",
      tags=["过氧化物"]),
    R(["NH4Cl", "NaNO2"], ["N2", "NaCl", "H2O"], conditions="加热",
      ion_mode="none", tags=["氮", "制备"]),
    R(["NaN3"], ["Na", "N2"], conditions="撞击", ion_mode="none", tags=["氮"]),
    R(["HI"], ["H2", "I2"], conditions="加热", ion_mode="none", tags=["卤素"]),
    R(["HBr"], ["H2", "Br2"], conditions="光照", ion_mode="none", tags=["卤素"]),
    R(["HF"], ["H2", "F2"], conditions="电解", ion_mode="none", tags=["卤素"]),
    R(["NaBr"], ["Na", "Br2"], conditions="电解,熔融", ion_mode="none",
      tags=["卤素", "电解"]),
    R(["NaI"], ["Na", "I2"], conditions="电解,熔融", ion_mode="none",
      tags=["卤素", "电解"]),
    R(["AgCl"], ["Ag", "Cl2"], conditions="光照", ion_mode="none", tags=["卤素"]),
    R(["AgBr"], ["Ag", "Br2"], conditions="光照", ion_mode="none", tags=["卤素"]),
    R(["AgI"], ["Ag", "I2"], conditions="光照", ion_mode="none", tags=["卤素"]),
    R(["CuCl2"], ["CuCl", "Cl2"], conditions="加热", ion_mode="none", tags=["铜"]),
    R(["HgO"], ["Hg", "O2"], conditions="加热", ion_mode="none", tags=["汞"]),
    R(["HgS", "O2"], ["Hg", "SO2"], conditions="加热", ion_mode="none", tags=["汞"]),
    R(["H2O2"], ["H2O", "O2"], conditions="催化", ion_mode="none",
      tags=["过氧化物"]),
    R(["K2MnO4", "CO2"], ["KMnO4", "MnO2", "K2CO3"], ion_mode="none", tags=["锰"]),
    R(["MnO2", "KOH", "O2"], ["K2MnO4", "H2O"], conditions="熔融", ion_mode="none",
      tags=["锰"]),
    R(["KMnO4", "KOH"], ["K2MnO4", "O2", "H2O"], conditions="加热", ion_mode="none",
      tags=["锰"]),
    R(["Na2S", "S"], ["Na2S2"], ion_mode="none", tags=["硫"]),
    R(["Na2S", "S"], ["Na2S3"], conditions="加热", ion_mode="none", tags=["硫"]),
    R(["Na2S", "S"], ["Na2S4"], ion_mode="none", tags=["硫"]),
    R(["Na2SO3", "S"], ["Na2S2O3"], ion_mode="none", tags=["硫"]),
    R(["Na2S2O3", "H2SO4"], ["Na2SO4", "S", "SO2", "H2O"], ion_mode="none",
      tags=["硫"]),
    R(["Na2S", "FeSO4"], ["FeS", "Na2SO4"], ion_mode="auto", tags=["复分解"]),
    R(["CaC2", "N2"], ["CaCN2", "C"], conditions="高温", ion_mode="none",
      tags=["钙", "工业"]),
    R(["CaCN2", "H2O"], ["CaCO3", "NH3"], ion_mode="none", tags=["氮"]),
    R(["Na2SiO3", "CO2", "H2O"], ["H2SiO3", "Na2CO3"], ion_mode="none",
      tags=["硅"]),
    R(["SiO2", "HF"], ["SiF4", "H2O"], ion_mode="none", tags=["硅"]),
    R(["SiF4", "H2O"], ["H2SiO3", "H2SiF6"], ion_mode="none", tags=["硅"]),
    R(["Na2SiF6", "H2SO4"], ["H2SiF6", "Na2SO4"], ion_mode="none", tags=["硅"]),
    R(["Al2O3", "C", "N2"], ["AlN", "CO"], conditions="高温", ion_mode="none",
      tags=["铝"]),
    R(["Al2O3", "S", "C"], ["Al2S3", "CO"], conditions="高温", ion_mode="none",
      tags=["铝"]),
    R(["Al(OH)3"], ["Al2O3", "H2O"], conditions="煅烧", ion_mode="none",
      tags=["铝"]),
    R(["AlCl3", "LiAlH4"], ["AlH3", "LiCl"], ion_mode="none", tags=["铝"]),
    R(["AlH3"], ["Al", "H2"], conditions="加热", ion_mode="none", tags=["铝"]),
    R(["NaH", "H2O"], ["NaOH", "H2"], ion_mode="none", tags=["钠"]),
    R(["KH", "H2O"], ["KOH", "H2"], ion_mode="none", tags=["钾"]),
    R(["NaH", "HCl"], ["NaCl", "H2"], ion_mode="none", tags=["钠"]),
    R(["CaH2", "HCl"], ["CaCl2", "H2"], ion_mode="none", tags=["钙"]),
    R(["LiH", "H2O"], ["LiOH", "H2"], ion_mode="none", tags=["锂"]),
]

# --------------------------------------------------------------------------- #
# 十二、酸性氧化物 / 金属氧化物 / 铵盐 / 盐-酸 / 盐-盐 系统组合
# --------------------------------------------------------------------------- #
_G2 = []

# 酸性氧化物 + 碱
for _ox, _base, _p in [
    ("CO2", "Ba(OH)2", "BaCO3"),
    ("CO2", "KOH", "K2CO3"),
    ("SO2", "Ca(OH)2", "CaSO3"),
    ("SO2", "Ba(OH)2", "BaSO3"),
    ("SO2", "KOH", "K2SO3"),
    ("SO3", "NaOH", "Na2SO4"),
    ("SO3", "Ca(OH)2", "CaSO4"),
    ("P2O5", "NaOH", "Na3PO4"),
    ("P2O5", "Ca(OH)2", "Ca3(PO4)2"),
    ("N2O5", "NaOH", "NaNO3"),
    ("CrO3", "NaOH", "Na2CrO4"),
    ("SiO2", "NaOH", "Na2SiO3"),
]:
    _G2.append(R([_ox, _base], [_p, "H2O"], ion_mode="auto",
                 tags=["酸性氧化物"]))

# 金属氧化物 + 酸
for _ox, _acid, _p in [
    ("CuO", "HNO3", "Cu(NO3)2"),
    ("FeO", "HCl", "FeCl2"),
    ("FeO", "H2SO4", "FeSO4"),
    ("FeO", "HNO3", "Fe(NO3)2"),
    ("Fe2O3", "HNO3", "Fe(NO3)3"),
    ("MgO", "HCl", "MgCl2"),
    ("MgO", "H2SO4", "MgSO4"),
    ("MgO", "HNO3", "Mg(NO3)2"),
    ("ZnO", "HCl", "ZnCl2"),
    ("ZnO", "H2SO4", "ZnSO4"),
    ("ZnO", "HNO3", "Zn(NO3)2"),
    ("Al2O3", "HCl", "AlCl3"),
    ("Al2O3", "H2SO4", "Al2(SO4)3"),
    ("CaO", "H2SO4", "CaSO4"),
    ("BaO", "HCl", "BaCl2"),
    ("BaO", "H2SO4", "BaSO4"),
    ("Na2O", "HCl", "NaCl"),
    ("Na2O", "H2SO4", "Na2SO4"),
    ("Na2O", "HNO3", "NaNO3"),
    ("K2O", "HCl", "KCl"),
    ("K2O", "H2SO4", "K2SO4"),
    ("K2O", "HNO3", "KNO3"),
    ("MnO", "HCl", "MnCl2"),
    ("PbO", "HNO3", "Pb(NO3)2"),
    ("Cu2O", "HCl", "CuCl"),
]:
    _G2.append(R([_ox, _acid], [_p, "H2O"], ion_mode="auto",
                 tags=["金属氧化物"]))

# 铵盐 + 强碱
for _salt, _base, _p1 in [
    ("NH4Cl", "NaOH", "NaCl"),
    ("NH4Cl", "KOH", "KCl"),
    ("NH4Cl", "Ca(OH)2", "CaCl2"),
    ("NH4NO3", "NaOH", "NaNO3"),
    ("NH4NO3", "KOH", "KNO3"),
    ("(NH4)2SO4", "NaOH", "Na2SO4"),
    ("(NH4)2SO4", "KOH", "K2SO4"),
    ("(NH4)2CO3", "NaOH", "Na2CO3"),
]:
    _G2.append(R([_salt, _base], [_p1, "NH3·H2O"], ion_mode="auto",
                 tags=["铵盐"]))

# 盐 + 酸（生成挥发性气体，同时生成水）
for _salt, _acid, _p1, _p2 in [
    ("Na2CO3", "H2SO4", "Na2SO4", "CO2"),
    ("Na2CO3", "HNO3", "NaNO3", "CO2"),
    ("K2CO3", "HCl", "KCl", "CO2"),
    ("K2CO3", "H2SO4", "K2SO4", "CO2"),
    ("K2CO3", "HNO3", "KNO3", "CO2"),
    ("Na2SO3", "H2SO4", "Na2SO4", "SO2"),
    ("Na2SO3", "HNO3", "NaNO3", "SO2"),
]:
    _G2.append(R([_salt, _acid], [_p1, _p2, "H2O"], ion_mode="auto",
                 tags=["复分解"]))

# 盐 + 酸（生成弱酸/弱酸盐，无水生）
for _salt, _acid, _p1, _p2 in [
    ("Na2S", "H2SO4", "Na2SO4", "H2S"),
    ("Ca3(PO4)2", "HCl", "CaCl2", "H3PO4"),
    ("Ca3(PO4)2", "H2SO4", "CaSO4", "H3PO4"),
    ("CaHPO4", "HCl", "CaCl2", "H3PO4"),
    ("K2SiO3", "HCl", "KCl", "H2SiO3"),
    ("K2SiO3", "H2SO4", "K2SO4", "H2SiO3"),
    ("Na2C2O4", "HCl", "NaCl", "H2C2O4"),
    ("Na2C2O4", "H2SO4", "Na2SO4", "H2C2O4"),
    ("Mg3N2", "HCl", "MgCl2", "NH4Cl"),
    ("CaC2", "HCl", "CaCl2", "C2H2"),
]:
    _G2.append(R([_salt, _acid], [_p1, _p2], ion_mode="auto",
                 tags=["复分解"]))

# 盐 + 盐
for _a, _b, _p1, _p2 in [
    ("CaCl2", "Na3PO4", "Ca3(PO4)2", "NaCl"),
    ("MgCl2", "Na3PO4", "Mg3(PO4)2", "NaCl"),
    ("BaCl2", "Na3PO4", "Ba3(PO4)2", "NaCl"),
    ("BaCl2", "K2CrO4", "BaCrO4", "KCl"),
    ("Pb(NO3)2", "K2CrO4", "PbCrO4", "KNO3"),
    ("CaCl2", "K2C2O4", "CaC2O4", "KCl"),
    ("Pb(NO3)2", "Na2SO4", "PbSO4", "NaNO3"),
    ("Pb(NO3)2", "NaCl", "PbCl2", "NaNO3"),
    ("AgNO3", "Na2C2O4", "Ag2C2O4", "NaNO3"),
    ("BaCl2", "Na2SiO3", "BaSiO3", "NaCl"),
    ("CaCl2", "NaF", "CaF2", "NaCl"),
    ("MgCl2", "Na2CO3", "MgCO3", "NaCl"),
    ("ZnCl2", "Na2CO3", "ZnCO3", "NaCl"),
    ("FeCl2", "Na2CO3", "FeCO3", "NaCl"),
    ("Ba(NO3)2", "Na2SO4", "BaSO4", "NaNO3"),
]:
    _G2.append(R([_a, _b], [_p1, _p2], ion_mode="auto", tags=["复分解"]))

# 高温还原 / 铝热
for _ox, _red, _p1, _p2 in [
    ("CuO", "H2", "Cu", "H2O"),
    ("CuO", "C", "Cu", "CO2"),
    ("ZnO", "C", "Zn", "CO"),
    ("Fe2O3", "H2", "Fe", "H2O"),
    ("Fe3O4", "CO", "FeO", "CO2"),
    ("WO3", "H2", "W", "H2O"),
    ("Cr2O3", "Al", "Cr", "Al2O3"),
    ("MnO2", "Al", "Mn", "Al2O3"),
    ("V2O5", "Al", "V", "Al2O3"),
    ("TiO2", "C", "Ti", "CO2"),
]:
    _G2.append(R([_ox, _red], [_p1, _p2], conditions="高温", ion_mode="none",
                 tags=["氧化还原", "还原"]))

# 其他高频复分解与脱硫
_G2 += [
    R(["NH4HCO3", "NaOH"], ["Na2CO3", "NH3·H2O", "H2O"], ion_mode="auto",
      tags=["铵盐"]),
    R(["Ca(OH)2", "Na2CO3"], ["CaCO3", "NaOH"], ion_mode="auto",
      tags=["复分解", "制碱"]),
    R(["Ba(OH)2", "Na2SO4"], ["BaSO4", "NaOH"], ion_mode="auto",
      tags=["复分解"]),
    R(["Ca(OH)2", "MgCl2"], ["Mg(OH)2", "CaCl2"], ion_mode="auto",
      tags=["复分解", "制镁"]),
    R(["CaO", "SO2"], ["CaSO3"], conditions="高温", ion_mode="none",
      tags=["脱硫"]),
    R(["CaO", "SO2", "O2"], ["CaSO4"], conditions="高温", ion_mode="none",
      tags=["脱硫"]),
    R(["CaCO3", "SO2", "O2"], ["CaSO4", "CO2"], conditions="高温",
      ion_mode="none", tags=["脱硫"]),
    R(["MnO2", "H2O2", "H2SO4"], ["MnSO4", "O2", "H2O"], ion_mode="manual",
      net_override="MnO2 + H2O2 + 2H^+ = Mn^2+ + O2↑ + 2H2O",
      tags=["氧化还原", "锰"]),
    R(["Ca(OH)2", "Cl2"], ["Ca(ClO)2", "CaCl2", "H2O"], ion_mode="none",
      tags=["卤素", "漂白粉"]),
    R(["Ca(ClO)2", "H2SO4"], ["CaSO4", "HClO"], conditions="浓硫酸",
      ion_mode="none", tags=["卤素"]),
]

EXTRA_REACTIONS = (_G + _G2 + B6_CRYSTAL + B6_HALOGEN + B6_TITRATION +
                   B6_ELECTRO + B6_ELEMENT + B6_ADV)

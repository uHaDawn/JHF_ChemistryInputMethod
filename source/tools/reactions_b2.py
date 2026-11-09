# -*- coding: utf-8 -*-
"""
初高中化学扩充反应库（批次 2：氧化还原 / 卤素 / 氮硫 / 金属置换 / 锰铬）。

说明：
  - ion_mode="none" 表示无离子式（燃烧、固体、浓酸、分解等）；
  - manual 条目的 net_override/full_override 用 ASCII 书写，生成时自动转上下标；
  - 所有条目先由 chem_balance 配平并通过守恒校验。
"""
from reactions_extra import R

# --------------------------------------------------------------------------- #
# 一、卤素及其化合物
# --------------------------------------------------------------------------- #
B2_HALOGEN = [
    R(["Cl2", "KOH"], ["KCl", "KClO", "H2O"], tags=["卤素", "歧化"]),
    R(["Cl2", "Ca(OH)2"], ["CaCl2", "Ca(ClO)2", "H2O"], tags=["卤素", "歧化"]),
    R(["Br2", "NaOH"], ["NaBr", "NaBrO", "H2O"], tags=["卤素", "歧化"]),
    R(["Br2", "KOH"], ["KBr", "KBrO", "H2O"], tags=["卤素", "歧化"]),
    R(["I2", "NaOH"], ["NaI", "NaIO", "H2O"], tags=["卤素", "歧化"]),

    R(["Cl2", "NaBr"], ["NaCl", "Br2"], tags=["卤素", "置换"]),
    R(["Cl2", "KBr"], ["KCl", "Br2"], tags=["卤素", "置换"]),
    R(["Cl2", "KI"], ["KCl", "I2"], tags=["卤素", "置换"]),
    R(["Cl2", "NaI"], ["NaCl", "I2"], tags=["卤素", "置换"]),
    R(["Br2", "KI"], ["KBr", "I2"], tags=["卤素", "置换"]),
    R(["Br2", "NaI"], ["NaBr", "I2"], tags=["卤素", "置换"]),

    R(["Cl2", "FeCl2"], ["FeCl3"], tags=["卤素", "氧化还原"]),
    R(["Cl2", "FeBr2"], ["FeCl3", "Br2"], tags=["卤素", "氧化还原"]),
    R(["Cl2", "SnCl2"], ["SnCl4"], tags=["卤素", "氧化还原"]),
    R(["Cl2", "H2S"], ["HCl", "S"], tags=["卤素", "氧化还原"]),
    R(["Cl2", "SO2", "H2O"], ["H2SO4", "HCl"], tags=["卤素", "氧化还原"]),
    R(["Cl2", "Na2SO3", "H2O"], ["Na2SO4", "HCl"], tags=["卤素", "氧化还原"]),
    R(["Cl2", "Na2S"], ["NaCl", "S"], tags=["卤素", "氧化还原"]),

    R(["NaClO", "KI", "H2O"], ["NaCl", "I2", "KOH"], tags=["卤素", "氧化还原"]),
    R(["NaClO", "Na2S", "H2O"], ["NaCl", "S", "NaOH"], tags=["卤素", "氧化还原"]),
    R(["NaClO", "H2O2"], ["NaCl", "O2", "H2O"], tags=["卤素", "氧化还原"]),
    R(["Cl2", "NaClO2"], ["ClO2", "NaCl"], tags=["卤素", "氧化还原"]),

    R(["HClO", "HCl"], ["Cl2", "H2O"], ion_mode="manual",
      net_override="HClO + H^+ + Cl^- = Cl2↑ + H2O",
      tags=["卤素", "归中"]),
    R(["KClO3", "HCl"], ["KCl", "Cl2", "H2O"], conditions="浓盐酸", ion_mode="manual",
      net_override="ClO3^- + 5Cl^- + 6H^+ = 3Cl2↑ + 3H2O",
      tags=["卤素", "归中"]),
    R(["KBrO3", "KBr", "H2SO4"], ["K2SO4", "Br2", "H2O"], ion_mode="manual",
      net_override="BrO3^- + 5Br^- + 6H^+ = 3Br2 + 3H2O",
      tags=["卤素", "归中"]),
    R(["KIO3", "KI", "H2SO4"], ["K2SO4", "I2", "H2O"], ion_mode="manual",
      net_override="IO3^- + 5I^- + 6H^+ = 3I2 + 3H2O",
      tags=["卤素", "归中"]),
    R(["NaClO", "NaCl", "H2O", "CO2"], ["NaHCO3", "Cl2"], ion_mode="none",
      tags=["卤素"]),

    # 单质化合 / 置换（无离子式）
    R(["Cl2", "Mg"], ["MgCl2"], ion_mode="none", tags=["卤素", "化合"]),
    R(["Cl2", "Zn"], ["ZnCl2"], ion_mode="none", tags=["卤素", "化合"]),
    R(["Cl2", "Al"], ["AlCl3"], ion_mode="none", tags=["卤素", "化合"]),
    R(["Cl2", "P"], ["PCl3"], ion_mode="none", tags=["卤素", "化合"]),
    R(["Cl2", "Si"], ["SiCl4"], ion_mode="none", tags=["卤素", "化合"]),
    R(["Br2", "H2"], ["HBr"], ion_mode="none", tags=["卤素", "化合"]),
    R(["I2", "H2"], ["HI"], ion_mode="none", tags=["卤素", "化合"]),
    R(["Br2", "Mg"], ["MgBr2"], ion_mode="none", tags=["卤素", "化合"]),
    R(["Br2", "Zn"], ["ZnBr2"], ion_mode="none", tags=["卤素", "化合"]),
    R(["Br2", "Fe"], ["FeBr3"], ion_mode="none", tags=["卤素", "化合"]),
    R(["I2", "Fe"], ["FeI2"], ion_mode="none", tags=["卤素", "化合"]),
    R(["I2", "Zn"], ["ZnI2"], ion_mode="none", tags=["卤素", "化合"]),
    R(["I2", "Al"], ["AlI3"], ion_mode="none", tags=["卤素", "化合"]),
    R(["Cl2", "CH4"], ["CH3Cl", "HCl"], conditions="光照", ion_mode="none",
      tags=["卤素", "取代"]),
    R(["Br2", "C2H4"], ["C2H4Br2"], ion_mode="none", tags=["卤素", "加成"]),
    R(["Br2", "C2H2"], ["C2H2Br2"], ion_mode="none", tags=["卤素", "加成"]),

    # 卤化氢 / 次卤酸不稳定
    R(["HClO"], ["HCl", "O2"], conditions="光照", ion_mode="none", tags=["卤素"]),
    R(["NaClO"], ["NaCl", "NaClO3"], conditions="加热", ion_mode="none",
      tags=["卤素", "歧化"]),
]

# --------------------------------------------------------------------------- #
# 二、氮及其化合物
# --------------------------------------------------------------------------- #
B2_NITROGEN = [
    R(["N2", "O2"], ["NO"], conditions="放电", ion_mode="none", tags=["氮"]),
    R(["NO", "O2"], ["NO2"], ion_mode="none", tags=["氮"]),
    R(["NO2"], ["N2O4"], ion_mode="none", tags=["氮", "可逆"]),
    R(["NO2", "H2O"], ["HNO3", "NO"], ion_mode="manual",
      net_override="3NO2 + H2O = 2H^+ + 2NO3^- + NO↑", tags=["氮"]),
    R(["NO2", "NaOH"], ["NaNO3", "NaNO2", "H2O"], ion_mode="manual",
      net_override="2NO2 + 2OH^- = NO3^- + NO2^- + H2O", tags=["氮"]),
    R(["NO", "NO2", "NaOH"], ["NaNO2", "H2O"], ion_mode="manual",
      net_override="NO + NO2 + 2OH^- = 2NO2^- + H2O", tags=["氮"]),

    R(["NH3", "O2"], ["NO", "H2O"], conditions="催化剂,加热", ion_mode="none",
      tags=["氮", "催化氧化"]),
    R(["NH3", "Cl2"], ["N2", "HCl"], ion_mode="none", tags=["氮"]),
    R(["NH3", "CuO"], ["Cu", "N2", "H2O"], conditions="加热", ion_mode="none",
      tags=["氮"]),
    R(["NH3", "CO2", "H2O"], ["NH4HCO3"], ion_mode="none", tags=["氮"]),
    R(["NH3", "HCl"], ["NH4Cl"], ion_mode="none", tags=["氮"]),
    R(["NH3", "H2SO4"], ["(NH4)2SO4"], ion_mode="none", tags=["氮"]),
    R(["NH3", "CO2"], ["CO(NH2)2", "H2O"], conditions="高温高压", ion_mode="none",
      tags=["氮"]),
    R(["HNO3"], ["NO2", "O2", "H2O"], conditions="光照", ion_mode="none", tags=["氮"]),

    R(["Cu", "HNO3"], ["Cu(NO3)2", "NO2", "H2O"], conditions="浓硝酸",
      ion_mode="manual",
      net_override="Cu + 4H^+ + 2NO3^- = Cu^2+ + 2NO2↑ + 2H2O", tags=["氮", "氧化还原"]),
    R(["Cu", "HNO3"], ["Cu(NO3)2", "NO", "H2O"], conditions="稀硝酸",
      ion_mode="manual",
      net_override="3Cu + 8H^+ + 2NO3^- = 3Cu^2+ + 2NO↑ + 4H2O", tags=["氮", "氧化还原"]),
    R(["Ag", "HNO3"], ["AgNO3", "NO2", "H2O"], conditions="浓硝酸",
      ion_mode="manual",
      net_override="Ag + 2H^+ + NO3^- = Ag^+ + NO2↑ + H2O", tags=["氮", "氧化还原"]),
    R(["Ag", "HNO3"], ["AgNO3", "NO", "H2O"], conditions="稀硝酸",
      ion_mode="manual",
      net_override="3Ag + 4H^+ + NO3^- = 3Ag^+ + NO↑ + 2H2O", tags=["氮", "氧化还原"]),
    R(["Fe", "HNO3"], ["Fe(NO3)3", "NO", "H2O"], conditions="稀硝酸",
      ion_mode="manual",
      net_override="Fe + 4H^+ + NO3^- = Fe^3+ + NO↑ + 2H2O", tags=["氮", "氧化还原"]),
    R(["Zn", "HNO3"], ["Zn(NO3)2", "NO2", "H2O"], conditions="浓硝酸",
      ion_mode="manual",
      net_override="Zn + 4H^+ + 2NO3^- = Zn^2+ + 2NO2↑ + 2H2O", tags=["氮", "氧化还原"]),
    R(["Zn", "HNO3"], ["Zn(NO3)2", "N2O", "H2O"], conditions="稀硝酸",
      ion_mode="manual",
      net_override="4Zn + 10H^+ + 2NO3^- = 4Zn^2+ + N2O↑ + 5H2O", tags=["氮", "氧化还原"]),
    R(["Zn", "HNO3"], ["Zn(NO3)2", "NH4NO3", "H2O"], conditions="极稀硝酸",
      ion_mode="manual",
      net_override="4Zn + 10H^+ + NO3^- = 4Zn^2+ + NH4^+ + 3H2O", tags=["氮", "氧化还原"]),

    R(["C", "HNO3"], ["CO2", "NO2", "H2O"], conditions="浓硝酸,加热",
      ion_mode="none", tags=["氮", "氧化还原"]),
    R(["S", "HNO3"], ["H2SO4", "NO2", "H2O"], conditions="浓硝酸,加热",
      ion_mode="none", tags=["氮", "氧化还原"]),
    R(["P", "HNO3"], ["H3PO4", "NO2", "H2O"], conditions="浓硝酸,加热",
      ion_mode="none", tags=["氮", "氧化还原"]),

    # 硝酸盐热分解
    R(["NaNO3"], ["NaNO2", "O2"], conditions="加热", ion_mode="none", tags=["氮", "分解"]),
    R(["KNO3"], ["KNO2", "O2"], conditions="加热", ion_mode="none", tags=["氮", "分解"]),
    R(["Cu(NO3)2"], ["CuO", "NO2", "O2"], conditions="加热", ion_mode="none",
      tags=["氮", "分解"]),
    R(["AgNO3"], ["Ag", "NO2", "O2"], conditions="加热", ion_mode="none",
      tags=["氮", "分解"]),

    # 铵盐热分解 / 与亚硝酸盐
    R(["NH4NO3"], ["N2O", "H2O"], conditions="加热", ion_mode="none", tags=["氮", "分解"]),
    R(["NH4NO2"], ["N2", "H2O"], conditions="加热", ion_mode="none", tags=["氮", "分解"]),
    R(["NH4Cl"], ["NH3", "HCl"], conditions="加热", ion_mode="none", tags=["氮", "分解"]),
    R(["(NH4)2CO3"], ["NH3", "CO2", "H2O"], conditions="加热", ion_mode="none",
      tags=["氮", "分解"]),
    R(["NH4Cl", "NaNO2"], ["NaCl", "N2", "H2O"], conditions="加热",
      ion_mode="manual", net_override="NH4^+ + NO2^- = N2↑ + 2H2O",
      tags=["氮", "氧化还原"]),

    R(["N2H4", "O2"], ["N2", "H2O"], ion_mode="none", tags=["氮"]),
]

# --------------------------------------------------------------------------- #
# 三、硫及其化合物
# --------------------------------------------------------------------------- #
B2_SULFUR = [
    R(["SO2", "O2"], ["SO3"], conditions="催化剂,加热", ion_mode="none",
      tags=["硫", "可逆"]),
    R(["H2S", "SO2"], ["S", "H2O"], ion_mode="none", tags=["硫", "归中"]),
    R(["H2S", "O2"], ["SO2", "H2O"], conditions="点燃", ion_mode="none", tags=["硫"]),
    R(["H2S", "O2"], ["S", "H2O"], conditions="点燃,少量O2", ion_mode="none", tags=["硫"]),
    R(["H2S", "CuSO4"], ["CuS", "H2SO4"], tags=["硫", "复分解"]),
    R(["H2S", "Pb(NO3)2"], ["PbS", "HNO3"], tags=["硫", "复分解"]),
    R(["H2S", "FeCl3"], ["S", "FeCl2", "HCl"], tags=["硫", "氧化还原"]),

    R(["Na2S2O3", "H2SO4"], ["Na2SO4", "SO2", "S", "H2O"], tags=["硫", "歧化"]),
    R(["Na2S2O3", "I2"], ["Na2S4O6", "NaI"], ion_mode="manual",
      net_override="2S2O3^2- + I2 = S4O6^2- + 2I^-", tags=["硫", "氧化还原"]),
    R(["Na2S2O3", "Cl2", "H2O"], ["NaCl", "H2SO4", "HCl"], ion_mode="manual",
      net_override="S2O3^2- + 4Cl2 + 5H2O = 2SO4^2- + 8Cl^- + 10H^+",
      tags=["硫", "氧化还原"]),
    R(["Na2SO3", "H2O2"], ["Na2SO4", "H2O"], tags=["硫", "氧化还原"]),
    R(["Na2SO3", "O2"], ["Na2SO4"], ion_mode="none", tags=["硫", "氧化还原"]),
    R(["SO2", "Na2O2"], ["Na2SO4"], ion_mode="none", tags=["硫", "氧化还原"]),

    R(["Cu", "H2SO4"], ["CuSO4", "SO2", "H2O"], conditions="浓硫酸,加热",
      ion_mode="none", tags=["硫", "氧化还原"]),
    R(["C", "H2SO4"], ["CO2", "SO2", "H2O"], conditions="浓硫酸,加热",
      ion_mode="none", tags=["硫", "氧化还原"]),
    R(["S", "H2SO4"], ["SO2", "H2O"], conditions="浓硫酸,加热",
      ion_mode="none", tags=["硫", "氧化还原"]),

    # 硫化物煅烧
    R(["FeS", "O2"], ["Fe2O3", "SO2"], conditions="高温", ion_mode="none",
      tags=["硫", "煅烧"]),
    R(["FeS2", "O2"], ["Fe2O3", "SO2"], conditions="高温", ion_mode="none",
      tags=["硫", "煅烧"]),
    R(["Cu2S", "O2"], ["Cu", "SO2"], conditions="高温", ion_mode="none",
      tags=["硫", "煅烧"]),
    R(["ZnS", "O2"], ["ZnO", "SO2"], conditions="高温", ion_mode="none",
      tags=["硫", "煅烧"]),
    R(["CaSO4"], ["CaO", "SO2", "O2"], conditions="高温", ion_mode="none",
      tags=["硫", "分解"]),
]

# --------------------------------------------------------------------------- #
# 四、金属与置换反应
# --------------------------------------------------------------------------- #
B2_METAL = [
    # 铝热 / 高温还原
    R(["Al", "Fe2O3"], ["Al2O3", "Fe"], conditions="高温", ion_mode="none",
      tags=["金属", "铝热"]),
    R(["Al", "Fe3O4"], ["Al2O3", "Fe"], conditions="高温", ion_mode="none",
      tags=["金属", "铝热"]),
    R(["Al", "MnO2"], ["Al2O3", "Mn"], conditions="高温", ion_mode="none",
      tags=["金属", "铝热"]),
    R(["Al", "Cr2O3"], ["Al2O3", "Cr"], conditions="高温", ion_mode="none",
      tags=["金属", "铝热"]),
    R(["Al", "V2O5"], ["Al2O3", "V"], conditions="高温", ion_mode="none",
      tags=["金属", "铝热"]),
    R(["Al", "CuO"], ["Al2O3", "Cu"], conditions="高温", ion_mode="none",
      tags=["金属", "铝热"]),

    # 金属与水 / 碱
    R(["Fe", "H2O"], ["Fe3O4", "H2"], conditions="高温", ion_mode="none",
      tags=["金属"]),
    R(["Mg", "H2O"], ["Mg(OH)2", "H2"], conditions="加热", ion_mode="none",
      tags=["金属"]),
    R(["Al", "NaOH", "H2O"], ["NaAlO2", "H2"], ion_mode="manual",
      net_override="2Al + 2OH^- + 2H2O = 2AlO2^- + 3H2↑", tags=["金属", "两性"]),
    R(["Zn", "NaOH"], ["Na2ZnO2", "H2"], ion_mode="manual",
      net_override="Zn + 2OH^- = ZnO2^2- + H2↑", tags=["金属", "两性"]),
    R(["Si", "NaOH", "H2O"], ["Na2SiO3", "H2"], ion_mode="manual",
      net_override="Si + 2OH^- + H2O = SiO3^2- + 2H2↑", tags=["非金属"]),
    R(["Na", "C2H5OH"], ["C2H5ONa", "H2"], ion_mode="none", tags=["金属"]),
    R(["Na", "CH3COOH"], ["CH3COONa", "H2"], ion_mode="manual",
      net_override="2Na + 2CH3COOH = 2Na^+ + 2CH3COO^- + H2↑", tags=["金属"]),

    # 化合（无离子式）
    R(["Na", "O2"], ["Na2O"], conditions="常温", ion_mode="none", tags=["金属", "化合"]),
    R(["Na", "O2"], ["Na2O2"], conditions="点燃", ion_mode="none", tags=["金属", "化合"]),
    R(["K", "O2"], ["KO2"], conditions="点燃", ion_mode="none", tags=["金属", "化合"]),
    R(["Li", "O2"], ["Li2O"], conditions="点燃", ion_mode="none", tags=["金属", "化合"]),
    R(["Ba", "O2"], ["BaO2"], conditions="点燃", ion_mode="none", tags=["金属", "化合"]),
    R(["Ca", "O2"], ["CaO"], conditions="点燃", ion_mode="none", tags=["金属", "化合"]),
    R(["Ca", "Cl2"], ["CaCl2"], conditions="点燃", ion_mode="none", tags=["金属", "化合"]),
    R(["Mg", "Cl2"], ["MgCl2"], conditions="点燃", ion_mode="none", tags=["金属", "化合"]),
    R(["Mg", "S"], ["MgS"], conditions="点燃", ion_mode="none", tags=["金属", "化合"]),
    R(["Mg", "N2"], ["Mg3N2"], conditions="点燃", ion_mode="none", tags=["金属", "化合"]),
    R(["Mg", "CO2"], ["MgO", "C"], conditions="点燃", ion_mode="none",
      tags=["金属", "氧化还原"]),
    R(["Al", "S"], ["Al2S3"], conditions="加热", ion_mode="none", tags=["金属", "化合"]),
    R(["Fe", "S"], ["FeS"], conditions="加热", ion_mode="none", tags=["金属", "化合"]),
    R(["Zn", "S"], ["ZnS"], conditions="加热", ion_mode="none", tags=["金属", "化合"]),
    R(["Cu", "S"], ["Cu2S"], conditions="加热", ion_mode="none", tags=["金属", "化合"]),
    R(["Na", "S"], ["Na2S"], conditions="加热", ion_mode="none", tags=["金属", "化合"]),
    R(["K", "S"], ["K2S"], conditions="加热", ion_mode="none", tags=["金属", "化合"]),
    R(["Hg", "S"], ["HgS"], conditions="研磨", ion_mode="none", tags=["金属", "化合"]),
    R(["Ag", "S"], ["Ag2S"], conditions="常温", ion_mode="none", tags=["金属", "化合"]),

    # 置换（溶液，自动拆写）
    R(["Zn", "AgNO3"], ["Zn(NO3)2", "Ag"], tags=["金属", "置换"]),
    R(["Fe", "AgNO3"], ["Fe(NO3)2", "Ag"], tags=["金属", "置换"]),
    R(["Fe", "Pb(NO3)2"], ["Fe(NO3)2", "Pb"], tags=["金属", "置换"]),
    R(["Zn", "Pb(NO3)2"], ["Zn(NO3)2", "Pb"], tags=["金属", "置换"]),
    R(["Mg", "CuSO4"], ["MgSO4", "Cu"], tags=["金属", "置换"]),
    R(["Al", "CuSO4"], ["Al2(SO4)3", "Cu"], tags=["金属", "置换"]),
    R(["Zn", "CuCl2"], ["ZnCl2", "Cu"], tags=["金属", "置换"]),
    R(["Cu", "Hg(NO3)2"], ["Cu(NO3)2", "Hg"], tags=["金属", "置换"]),
    R(["Fe", "Hg(NO3)2"], ["Fe(NO3)2", "Hg"], tags=["金属", "置换"]),
    R(["Zn", "Hg(NO3)2"], ["Zn(NO3)2", "Hg"], tags=["金属", "置换"]),

    # 铁盐 / 铜盐的氧化还原
    R(["Cu", "FeCl3"], ["CuCl2", "FeCl2"], ion_mode="manual",
      net_override="Cu + 2Fe^3+ = Cu^2+ + 2Fe^2+", tags=["金属", "氧化还原"]),
    R(["Fe", "FeCl3"], ["FeCl2"], ion_mode="manual",
      net_override="Fe + 2Fe^3+ = 3Fe^2+", tags=["金属", "氧化还原"]),
    R(["Zn", "FeCl3"], ["ZnCl2", "FeCl2"], ion_mode="manual",
      net_override="Zn + 2Fe^3+ = Zn^2+ + 2Fe^2+", tags=["金属", "氧化还原"]),
    R(["Cu", "Fe2(SO4)3"], ["CuSO4", "FeSO4"], ion_mode="manual",
      net_override="Cu + 2Fe^3+ = Cu^2+ + 2Fe^2+", tags=["金属", "氧化还原"]),
    R(["Fe", "H2SO4", "O2"], ["FeSO4", "H2O"], conditions="微热", ion_mode="none",
      tags=["金属", "氧化还原"]),
]

# --------------------------------------------------------------------------- #
# 五、锰 / 铬化合物（氧化还原，手动净离子式）
# --------------------------------------------------------------------------- #
B2_MN_CR = [
    R(["MnO2", "HCl"], ["MnCl2", "Cl2", "H2O"], conditions="浓盐酸,加热",
      ion_mode="manual",
      net_override="MnO2 + 4H^+ + 2Cl^- = Mn^2+ + Cl2↑ + 2H2O",
      tags=["锰", "氧化还原"]),
    R(["MnO2", "H2O2", "H2SO4"], ["MnSO4", "O2", "H2O"], ion_mode="manual",
      net_override="MnO2 + H2O2 + 2H^+ = Mn^2+ + O2↑ + 2H2O",
      tags=["锰", "氧化还原"]),
    R(["MnO2", "Na2SO3", "H2SO4"], ["MnSO4", "Na2SO4", "H2O"], ion_mode="manual",
      net_override="MnO2 + SO3^2- + 2H^+ = Mn^2+ + SO4^2- + H2O",
      tags=["锰", "氧化还原"]),
    R(["KMnO4", "HCl"], ["KCl", "MnCl2", "Cl2", "H2O"], conditions="浓盐酸",
      ion_mode="manual",
      net_override="2MnO4^- + 16H^+ + 10Cl^- = 2Mn^2+ + 5Cl2↑ + 8H2O",
      tags=["锰", "氧化还原"]),
    R(["KMnO4", "H2O2", "H2SO4"], ["K2SO4", "MnSO4", "O2", "H2O"], ion_mode="manual",
      net_override="2MnO4^- + 5H2O2 + 6H^+ = 2Mn^2+ + 5O2↑ + 8H2O",
      tags=["锰", "氧化还原"]),
    R(["KMnO4", "FeSO4", "H2SO4"], ["K2SO4", "MnSO4", "Fe2(SO4)3", "H2O"],
      ion_mode="manual",
      net_override="MnO4^- + 5Fe^2+ + 8H^+ = Mn^2+ + 5Fe^3+ + 4H2O",
      tags=["锰", "氧化还原"]),
    R(["KMnO4", "K2C2O4", "H2SO4"], ["K2SO4", "MnSO4", "CO2", "H2O"],
      ion_mode="manual",
      net_override="2MnO4^- + 5C2O4^2- + 16H^+ = 2Mn^2+ + 10CO2↑ + 8H2O",
      tags=["锰", "氧化还原"]),
    R(["KMnO4", "KI", "H2SO4"], ["K2SO4", "MnSO4", "I2", "H2O"], ion_mode="manual",
      net_override="2MnO4^- + 10I^- + 16H^+ = 2Mn^2+ + 5I2 + 8H2O",
      tags=["锰", "氧化还原"]),
    R(["KMnO4", "Na2SO3", "H2SO4"], ["K2SO4", "MnSO4", "Na2SO4", "H2O"],
      ion_mode="manual",
      net_override="2MnO4^- + 5SO3^2- + 6H^+ = 2Mn^2+ + 5SO4^2- + 3H2O",
      tags=["锰", "氧化还原"]),

    R(["K2Cr2O7", "FeSO4", "H2SO4"], ["K2SO4", "Cr2(SO4)3", "Fe2(SO4)3", "H2O"],
      ion_mode="manual",
      net_override="Cr2O7^2- + 6Fe^2+ + 14H^+ = 2Cr^3+ + 6Fe^3+ + 7H2O",
      tags=["铬", "氧化还原"]),
    R(["K2Cr2O7", "KI", "H2SO4"], ["K2SO4", "Cr2(SO4)3", "I2", "H2O"],
      ion_mode="manual",
      net_override="Cr2O7^2- + 6I^- + 14H^+ = 2Cr^3+ + 3I2 + 7H2O",
      tags=["铬", "氧化还原"]),
    R(["K2Cr2O7", "Na2SO3", "H2SO4"], ["K2SO4", "Cr2(SO4)3", "Na2SO4", "H2O"],
      ion_mode="manual",
      net_override="Cr2O7^2- + 3SO3^2- + 8H^+ = 2Cr^3+ + 3SO4^2- + 4H2O",
      tags=["铬", "氧化还原"]),
    R(["K2Cr2O7", "HCl"], ["KCl", "CrCl3", "Cl2", "H2O"], conditions="浓盐酸",
      ion_mode="manual",
      net_override="Cr2O7^2- + 14H^+ + 6Cl^- = 2Cr^3+ + 3Cl2↑ + 7H2O",
      tags=["铬", "氧化还原"]),
    R(["K2Cr2O7", "KOH"], ["K2CrO4", "H2O"], ion_mode="manual",
      net_override="Cr2O7^2- + 2OH^- = 2CrO4^2- + H2O", tags=["铬"]),
    R(["K2CrO4", "H2SO4"], ["K2Cr2O7", "K2SO4", "H2O"], ion_mode="manual",
      net_override="2CrO4^2- + 2H^+ = Cr2O7^2- + H2O", tags=["铬"]),
    R(["Cr2O3", "Al"], ["Al2O3", "Cr"], conditions="高温", ion_mode="none",
      tags=["铬", "铝热"]),
    R(["Cr(OH)3", "NaOH"], ["NaCrO2", "H2O"], tags=["铬", "两性"]),
    R(["CrCl3", "NaOH"], ["Cr(OH)3", "NaCl"], tags=["铬", "复分解"]),
]


EXTRA_REACTIONS = (
    B2_HALOGEN + B2_NITROGEN + B2_SULFUR + B2_METAL + B2_MN_CR
)

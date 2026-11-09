# -*- coding: utf-8 -*-
"""
初高中化学扩充反应库（批次 3：电化学 / 平衡水解 / 元素化合物性质与制备）。

约定同 reactions_extra.R；manual 条目用 ASCII 写净离子式，生成时自动转上下标。
"""
from reactions_extra import R

# --------------------------------------------------------------------------- #
# 一、电化学
# --------------------------------------------------------------------------- #
B3_ELECTRO = [
    R(["CuCl2"], ["Cu", "Cl2"], conditions="电解", ion_mode="manual",
      net_override="Cu^2+ + 2Cl^- = Cu + Cl2↑", tags=["电化学", "电解"]),
    R(["NaCl", "H2O"], ["NaOH", "H2", "Cl2"], conditions="电解",
      ion_mode="manual",
      net_override="2Cl^- + 2H2O = 2OH^- + H2↑ + Cl2↑", tags=["电化学", "电解"]),
    R(["CuSO4", "H2O"], ["Cu", "O2", "H2SO4"], conditions="电解",
      ion_mode="manual",
      net_override="2Cu^2+ + 2H2O = 2Cu + O2↑ + 4H^+", tags=["电化学", "电解"]),
    R(["AgNO3", "H2O"], ["Ag", "O2", "HNO3"], conditions="电解",
      ion_mode="manual",
      net_override="4Ag^+ + 2H2O = 4Ag + O2↑ + 4H^+", tags=["电化学", "电解"]),
    R(["KI", "H2O"], ["KOH", "H2", "I2"], conditions="电解", ion_mode="manual",
      net_override="2I^- + 2H2O = 2OH^- + H2↑ + I2", tags=["电化学", "电解"]),
    R(["HBr"], ["H2", "Br2"], conditions="电解", ion_mode="manual",
      net_override="2H^+ + 2Br^- = H2↑ + Br2", tags=["电化学", "电解"]),
    R(["H2O"], ["H2", "O2"], conditions="电解", ion_mode="none", tags=["电化学", "电解"]),
    R(["NaCl"], ["Na", "Cl2"], conditions="电解,熔融", ion_mode="none",
      tags=["电化学", "电解"]),
    R(["MgCl2"], ["Mg", "Cl2"], conditions="电解,熔融", ion_mode="none",
      tags=["电化学", "电解"]),
    R(["Al2O3"], ["Al", "O2"], conditions="电解,熔融", ion_mode="none",
      tags=["电化学", "电解"]),

    R(["Pb", "PbO2", "H2SO4"], ["PbSO4", "H2O"], conditions="放电",
      ion_mode="manual",
      net_override="Pb + PbO2 + 4H^+ + 2SO4^2- = 2PbSO4 + 2H2O",
      tags=["电化学", "原电池"]),
    R(["PbSO4", "H2O"], ["Pb", "PbO2", "H2SO4"], conditions="充电",
      ion_mode="none", tags=["电化学", "电解"]),
    R(["H2", "O2"], ["H2O"], conditions="燃料电池", ion_mode="none",
      tags=["电化学", "燃料电池"]),
    R(["CH4", "O2"], ["CO2", "H2O"], conditions="燃料电池", ion_mode="none",
      tags=["电化学", "燃料电池"]),
    R(["CH3OH", "O2"], ["CO2", "H2O"], conditions="燃料电池", ion_mode="none",
      tags=["电化学", "燃料电池"]),
    R(["C2H5OH", "O2"], ["CO2", "H2O"], conditions="燃料电池", ion_mode="none",
      tags=["电化学", "燃料电池"]),
    R(["Zn", "Ag2O", "H2O"], ["Zn(OH)2", "Ag"], ion_mode="none",
      tags=["电化学", "原电池"]),
    R(["Al", "O2", "H2O"], ["Al(OH)3"], ion_mode="none",
      tags=["电化学", "原电池"]),
]

# --------------------------------------------------------------------------- #
# 二、盐类水解与弱电解质电离
# --------------------------------------------------------------------------- #
B3_HYDROLYSIS = [
    R(["Na2CO3", "H2O"], ["NaHCO3", "NaOH"], ion_mode="manual",
      net_override="CO3^2- + H2O ⇌ HCO3^- + OH^-", tags=["水解"]),
    R(["NaHCO3", "H2O"], ["H2CO3", "NaOH"], ion_mode="manual",
      net_override="HCO3^- + H2O ⇌ H2CO3 + OH^-", tags=["水解"]),
    R(["CH3COONa", "H2O"], ["CH3COOH", "NaOH"], ion_mode="manual",
      net_override="CH3COO^- + H2O ⇌ CH3COOH + OH^-", tags=["水解"]),
    R(["NH4Cl", "H2O"], ["NH3·H2O", "HCl"], ion_mode="manual",
      net_override="NH4^+ + H2O ⇌ NH3·H2O + H^+", tags=["水解"]),
    R(["FeCl3", "H2O"], ["Fe(OH)3", "HCl"], ion_mode="manual",
      net_override="Fe^3+ + 3H2O ⇌ Fe(OH)3 + 3H^+", tags=["水解"]),
    R(["AlCl3", "H2O"], ["Al(OH)3", "HCl"], ion_mode="manual",
      net_override="Al^3+ + 3H2O ⇌ Al(OH)3 + 3H^+", tags=["水解"]),
    R(["Na2S", "H2O"], ["NaHS", "NaOH"], ion_mode="manual",
      net_override="S^2- + H2O ⇌ HS^- + OH^-", tags=["水解"]),
    R(["Na2SO3", "H2O"], ["NaHSO3", "NaOH"], ion_mode="manual",
      net_override="SO3^2- + H2O ⇌ HSO3^- + OH^-", tags=["水解"]),
    R(["Na3PO4", "H2O"], ["Na2HPO4", "NaOH"], ion_mode="manual",
      net_override="PO4^3- + H2O ⇌ HPO4^2- + OH^-", tags=["水解"]),
    R(["NaF", "H2O"], ["HF", "NaOH"], ion_mode="manual",
      net_override="F^- + H2O ⇌ HF + OH^-", tags=["水解"]),
    R(["NaClO", "H2O"], ["HClO", "NaOH"], ion_mode="manual",
      net_override="ClO^- + H2O ⇌ HClO + OH^-", tags=["水解"]),
    R(["NaAlO2", "H2O"], ["Al(OH)3", "NaOH"], ion_mode="manual",
      net_override="AlO2^- + 2H2O ⇌ Al(OH)3 + OH^-", tags=["水解"]),
    R(["CH3COONH4", "H2O"], ["NH3·H2O", "CH3COOH"], ion_mode="manual",
      net_override="CH3COO^- + NH4^+ + H2O ⇌ CH3COOH + NH3·H2O", tags=["水解"]),
    R(["AlCl3", "NaAlO2", "H2O"], ["Al(OH)3", "NaCl"], ion_mode="manual",
      net_override="Al^3+ + 3AlO2^- + 6H2O = 4Al(OH)3↓",
      tags=["双水解"]),
    R(["FeCl3", "NaAlO2", "H2O"], ["Fe(OH)3", "Al(OH)3", "NaCl"],
      ion_mode="manual",
      net_override="Fe^3+ + 3AlO2^- + 6H2O = Fe(OH)3↓ + 3Al(OH)3↓",
      tags=["双水解"]),
    R(["NH4Cl", "NaAlO2", "H2O"], ["Al(OH)3", "NH3", "NaCl"], ion_mode="manual",
      net_override="NH4^+ + AlO2^- + H2O = Al(OH)3↓ + NH3↑", tags=["双水解"]),

    # 弱酸酸式盐与强碱
    R(["NaHCO3", "NaOH"], ["Na2CO3", "H2O"], tags=["酸式盐"]),
    R(["NaHSO3", "NaOH"], ["Na2SO3", "H2O"], tags=["酸式盐"]),
    R(["NaH2PO4", "NaOH"], ["Na2HPO4", "H2O"], tags=["酸式盐"]),
    R(["Na2HPO4", "NaOH"], ["Na3PO4", "H2O"], tags=["酸式盐"]),
    R(["NaHS", "NaOH"], ["Na2S", "H2O"], tags=["酸式盐"]),
    R(["NaHSO4", "NaOH"], ["Na2SO4", "H2O"], tags=["酸式盐"]),

    # 水解生成气体 / 沉淀
    R(["Mg3N2", "H2O"], ["Mg(OH)2", "NH3"], ion_mode="manual",
      net_override="Mg3N2 + 6H2O = 3Mg(OH)2↓ + 2NH3↑", tags=["水解"]),
    R(["Al2S3", "H2O"], ["Al(OH)3", "H2S"], ion_mode="manual",
      net_override="Al2S3 + 6H2O = 2Al(OH)3↓ + 3H2S↑", tags=["水解"]),
    R(["CaC2", "H2O"], ["Ca(OH)2", "C2H2"], ion_mode="none", tags=["水解"]),
    R(["NaNH2", "H2O"], ["NaOH", "NH3"], ion_mode="manual",
      net_override="NH2^- + H2O = NH3↑ + OH^-", tags=["水解"]),
    R(["PCl3", "H2O"], ["H3PO3", "HCl"], ion_mode="none", tags=["水解"]),
    R(["PCl5", "H2O"], ["H3PO4", "HCl"], ion_mode="none", tags=["水解"]),
    R(["SiCl4", "H2O"], ["H4SiO4", "HCl"], ion_mode="none", tags=["水解"]),
    R(["SOCl2", "H2O"], ["SO2", "HCl"], ion_mode="none", tags=["水解"]),

    # 沉淀溶解与转化
    R(["AgCl", "NH3·H2O"], ["[Ag(NH3)2]Cl", "H2O"], ion_mode="manual",
      net_override="AgCl + 2NH3·H2O = [Ag(NH3)2]^+ + Cl^- + 2H2O",
      tags=["配位", "沉淀溶解"]),
    R(["AgBr", "Na2S2O3"], ["Na3[Ag(S2O3)2]", "NaBr"], ion_mode="manual",
      net_override="AgBr + 2S2O3^2- = [Ag(S2O3)2]^3- + Br^-",
      tags=["配位", "沉淀溶解"]),
    R(["AgI", "KCN"], ["K[Ag(CN)2]", "KI"], ion_mode="manual",
      net_override="AgI + 2CN^- = [Ag(CN)2]^- + I^-", tags=["配位", "沉淀溶解"]),
    R(["Cu(OH)2", "NH3·H2O"], ["[Cu(NH3)4](OH)2", "H2O"], ion_mode="manual",
      net_override="Cu(OH)2 + 4NH3·H2O = [Cu(NH3)4]^2+ + 2OH^- + 4H2O",
      tags=["配位", "沉淀溶解"]),
    R(["CuSO4", "NH3·H2O"], ["[Cu(NH3)4]SO4", "H2O"], ion_mode="manual",
      net_override="Cu^2+ + 4NH3·H2O = [Cu(NH3)4]^2+ + 4H2O",
      tags=["配位"]),
    R(["AgCl", "KI"], ["AgI", "KCl"], tags=["沉淀转化"]),
    R(["AgBr", "NaI"], ["AgI", "NaBr"], tags=["沉淀转化"]),
    R(["2AgI", "Na2S"], ["Ag2S", "NaI"], tags=["沉淀转化"]),
    R(["BaSO4", "Na2CO3"], ["BaCO3", "Na2SO4"], ion_mode="manual",
      net_override="BaSO4 + CO3^2- ⇌ BaCO3 + SO4^2-", tags=["沉淀转化"]),
    R(["CaSO4", "Na2CO3"], ["CaCO3", "Na2SO4"], ion_mode="manual",
      net_override="CaSO4 + CO3^2- ⇌ CaCO3 + SO4^2-", tags=["沉淀转化"]),
    R(["Mg(OH)2", "NH4Cl"], ["MgCl2", "NH3·H2O"], ion_mode="manual",
      net_override="Mg(OH)2 + 2NH4^+ = Mg^2+ + 2NH3·H2O", tags=["沉淀溶解"]),
    R(["PbCl2", "KI"], ["PbI2", "KCl"], tags=["沉淀转化"]),
    R(["AgCl", "NaBr"], ["AgBr", "NaCl"], tags=["沉淀转化"]),
    R(["MgCO3", "CO2", "H2O"], ["Mg(HCO3)2"], tags=["沉淀溶解"]),
    R(["BaCO3", "CO2", "H2O"], ["Ba(HCO3)2"], tags=["沉淀溶解"]),
]

# --------------------------------------------------------------------------- #
# 三、元素化合物性质与制备
# --------------------------------------------------------------------------- #
B3_ELEMENTS = [
    # 钠
    R(["Na2O", "H2O"], ["NaOH"], tags=["钠"]),
    R(["Na2O", "CO2"], ["Na2CO3"], ion_mode="none", tags=["钠"]),
    R(["NaOH", "CO2"], ["Na2CO3", "H2O"], conditions="少量CO2", tags=["钠"]),
    R(["NaOH", "CO2"], ["NaHCO3"], conditions="过量CO2", tags=["钠"]),
    R(["NaOH", "SO2"], ["Na2SO3", "H2O"], conditions="少量SO2", tags=["钠"]),
    R(["NaOH", "SO2"], ["NaHSO3"], conditions="过量SO2", tags=["钠"]),
    R(["Na2CO3", "HCl"], ["NaHCO3", "NaCl"], conditions="少量盐酸", tags=["钠"]),
    R(["Na2CO3", "Ca(OH)2"], ["CaCO3", "NaOH"], tags=["钠", "复分解"]),
    R(["Na2CO3", "Ba(OH)2"], ["BaCO3", "NaOH"], tags=["钠", "复分解"]),
    R(["Na2CO3", "SiO2"], ["Na2SiO3", "CO2"], conditions="高温", ion_mode="none",
      tags=["钠", "硅酸盐"]),
    R(["Na2SO4", "BaCl2"], ["BaSO4", "NaCl"], tags=["钠", "复分解"]),
    R(["Na2SO4", "Ba(OH)2"], ["BaSO4", "NaOH"], tags=["钠", "复分解"]),
    R(["NaCl", "NH3", "CO2", "H2O"], ["NaHCO3", "NH4Cl"], tags=["钠", "侯氏制碱"]),
    R(["Na2CO3", "CO2", "H2O"], ["NaHCO3"], tags=["钠"]),
    R(["Na2O2", "SO2"], ["Na2SO4"], ion_mode="none", tags=["钠"]),

    # 钾
    R(["K2O", "H2O"], ["KOH"], tags=["钾"]),
    R(["KOH", "CO2"], ["K2CO3", "H2O"], conditions="少量CO2", tags=["钾"]),
    R(["KOH", "CO2"], ["KHCO3"], conditions="过量CO2", tags=["钾"]),
    R(["KOH", "SO2"], ["K2SO3", "H2O"], conditions="少量SO2", tags=["钾"]),
    R(["KOH", "SO2"], ["KHSO3"], conditions="过量SO2", tags=["钾"]),
    R(["K2CO3", "CO2", "H2O"], ["KHCO3"], tags=["钾"]),
    R(["K2CO3", "Ca(OH)2"], ["CaCO3", "KOH"], tags=["钾", "复分解"]),
    R(["K2CO3", "BaCl2"], ["BaCO3", "KCl"], tags=["钾", "复分解"]),

    # 镁
    R(["MgO", "H2O"], ["Mg(OH)2"], ion_mode="none", tags=["镁"]),
    R(["Mg(OH)2"], ["MgO", "H2O"], conditions="加热", ion_mode="none", tags=["镁"]),
    R(["MgCO3"], ["MgO", "CO2"], conditions="高温", ion_mode="none", tags=["镁"]),
    R(["Mg(HCO3)2"], ["MgCO3", "H2O", "CO2"], conditions="加热", ion_mode="none",
      tags=["镁"]),
    R(["MgSO4", "BaCl2"], ["BaSO4", "MgCl2"], tags=["镁", "复分解"]),

    # 铝
    R(["AlCl3", "NH3·H2O"], ["Al(OH)3", "NH4Cl"], tags=["铝", "复分解"]),
    R(["Al2(SO4)3", "NH3·H2O"], ["Al(OH)3", "(NH4)2SO4"], tags=["铝", "复分解"]),
    R(["Al(OH)3"], ["Al2O3", "H2O"], conditions="加热", ion_mode="none", tags=["铝"]),
    R(["AlCl3"], ["Al", "Cl2"], conditions="电解,熔融", ion_mode="none", tags=["铝"]),
    R(["NaAlO2", "AlCl3", "H2O"], ["Al(OH)3", "NaCl"], tags=["铝", "双水解"]),

    # 铁
    R(["Fe(OH)3"], ["Fe2O3", "H2O"], conditions="加热", ion_mode="none", tags=["铁"]),
    R(["Fe(OH)2", "O2", "H2O"], ["Fe(OH)3"], ion_mode="none", tags=["铁", "氧化还原"]),
    R(["Fe", "O2", "H2O"], ["Fe2O3·H2O"], ion_mode="none", tags=["铁", "氧化还原"]),
    R(["Fe2O3", "CO"], ["Fe", "CO2"], conditions="高温", ion_mode="none", tags=["铁"]),
    R(["Fe3O4", "CO"], ["Fe", "CO2"], conditions="高温", ion_mode="none", tags=["铁"]),
    R(["Fe2O3", "H2"], ["Fe", "H2O"], conditions="高温", ion_mode="none", tags=["铁"]),
    R(["Fe", "CuSO4"], ["FeSO4", "Cu"], tags=["铁", "置换"]),
    R(["FeSO4", "Zn"], ["ZnSO4", "Fe"], tags=["铁", "置换"]),

    # 铜
    R(["Cu", "O2"], ["CuO"], conditions="加热", ion_mode="none", tags=["铜"]),
    R(["Cu", "O2", "CO2", "H2O"], ["Cu2(OH)2CO3"], ion_mode="none", tags=["铜"]),
    R(["Cu(OH)2"], ["CuO", "H2O"], conditions="加热", ion_mode="none", tags=["铜"]),
    R(["CuSO4·5H2O"], ["CuSO4", "H2O"], conditions="加热", ion_mode="none", tags=["铜"]),
    R(["Cu2(OH)2CO3", "H2SO4"], ["CuSO4", "CO2", "H2O"], tags=["铜"]),
    R(["Cu2O", "H2SO4"], ["CuSO4", "Cu", "H2O"], tags=["铜", "歧化"]),
    R(["CuO", "NH3"], ["Cu", "N2", "H2O"], conditions="加热", ion_mode="none",
      tags=["铜"]),

    # 钙 / 钡
    R(["CaO", "CO2"], ["CaCO3"], ion_mode="none", tags=["钙"]),
    R(["Ca(OH)2", "CO2"], ["CaCO3", "H2O"], tags=["钙"]),
    R(["Ca(OH)2", "Na2CO3"], ["CaCO3", "NaOH"], tags=["钙", "复分解"]),
    R(["Ca(HCO3)2"], ["CaCO3", "H2O", "CO2"], conditions="加热", ion_mode="none",
      tags=["钙"]),
    R(["Ca(HCO3)2", "NaOH"], ["CaCO3", "NaHCO3", "H2O"], conditions="少量NaOH",
      tags=["钙", "复分解"]),
    R(["Ca(HCO3)2", "NaOH"], ["CaCO3", "Na2CO3", "H2O"], conditions="过量NaOH",
      tags=["钙", "复分解"]),
    R(["BaO", "H2O"], ["Ba(OH)2"], tags=["钡"]),
    R(["Ba(OH)2", "CO2"], ["BaCO3", "H2O"], tags=["钡"]),
    R(["Ba(OH)2", "SO2"], ["BaSO3", "H2O"], tags=["钡"]),
    R(["Ba(OH)2", "Ca(HCO3)2"], ["BaCO3", "CaCO3", "H2O"], tags=["钡"]),

    # 硅
    R(["Si", "O2"], ["SiO2"], conditions="加热", ion_mode="none", tags=["硅"]),
    R(["Si", "Cl2"], ["SiCl4"], conditions="加热", ion_mode="none", tags=["硅"]),
    R(["SiO2", "HF"], ["SiF4", "H2O"], ion_mode="none", tags=["硅"]),
    R(["SiO2", "C"], ["Si", "CO"], conditions="高温", ion_mode="none", tags=["硅"]),
    R(["H2SiO3"], ["SiO2", "H2O"], conditions="加热", ion_mode="none", tags=["硅"]),
    R(["SiCl4", "H2"], ["Si", "HCl"], conditions="高温", ion_mode="none", tags=["硅"]),
    R(["Na2SiO3", "CaCl2"], ["CaSiO3", "NaCl"], tags=["硅", "复分解"]),

    # 热分解与制备
    R(["KMnO4"], ["K2MnO4", "MnO2", "O2"], conditions="加热", ion_mode="none",
      tags=["制备"]),
    R(["H2O2"], ["H2O", "O2"], conditions="MnO2", ion_mode="none", tags=["制备"]),
    R(["KClO3"], ["KCl", "O2"], conditions="MnO2,加热", ion_mode="none",
      tags=["制备"]),
    R(["CaCO3"], ["CaO", "CO2"], conditions="高温", ion_mode="none", tags=["制备"]),
    R(["NH4HCO3"], ["NH3", "CO2", "H2O"], conditions="加热", ion_mode="none",
      tags=["制备"]),
    R(["Na", "H2"], ["NaH"], conditions="高温", ion_mode="none", tags=["钠"]),
]


EXTRA_REACTIONS = B3_ELECTRO + B3_HYDROLYSIS + B3_ELEMENTS

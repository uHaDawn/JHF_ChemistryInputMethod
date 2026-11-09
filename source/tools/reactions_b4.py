# -*- coding: utf-8 -*-
"""
初高中化学扩充反应库（批次 4：有机化学基础 + 选修五）。

约定同 reactions_extra.R；有机反应绝大多数为非水溶液体系，离子式标 none。
"""
from reactions_extra import R

# --------------------------------------------------------------------------- #
# 一、燃烧（完全氧化）
# --------------------------------------------------------------------------- #
B4_BURN = [
    R(["CH4", "O2"], ["CO2", "H2O"], conditions="点燃", ion_mode="none", tags=["有机", "燃烧"]),
    R(["C2H6", "O2"], ["CO2", "H2O"], conditions="点燃", ion_mode="none", tags=["有机", "燃烧"]),
    R(["C3H8", "O2"], ["CO2", "H2O"], conditions="点燃", ion_mode="none", tags=["有机", "燃烧"]),
    R(["C4H10", "O2"], ["CO2", "H2O"], conditions="点燃", ion_mode="none", tags=["有机", "燃烧"]),
    R(["C5H12", "O2"], ["CO2", "H2O"], conditions="点燃", ion_mode="none", tags=["有机", "燃烧"]),
    R(["C6H14", "O2"], ["CO2", "H2O"], conditions="点燃", ion_mode="none", tags=["有机", "燃烧"]),
    R(["C8H18", "O2"], ["CO2", "H2O"], conditions="点燃", ion_mode="none", tags=["有机", "燃烧"]),
    R(["C2H4", "O2"], ["CO2", "H2O"], conditions="点燃", ion_mode="none", tags=["有机", "燃烧"]),
    R(["C3H6", "O2"], ["CO2", "H2O"], conditions="点燃", ion_mode="none", tags=["有机", "燃烧"]),
    R(["C4H8", "O2"], ["CO2", "H2O"], conditions="点燃", ion_mode="none", tags=["有机", "燃烧"]),
    R(["C2H2", "O2"], ["CO2", "H2O"], conditions="点燃", ion_mode="none", tags=["有机", "燃烧"]),
    R(["C6H6", "O2"], ["CO2", "H2O"], conditions="点燃", ion_mode="none", tags=["有机", "燃烧"]),
    R(["C7H8", "O2"], ["CO2", "H2O"], conditions="点燃", ion_mode="none", tags=["有机", "燃烧"]),
    R(["C6H12", "O2"], ["CO2", "H2O"], conditions="点燃", ion_mode="none", tags=["有机", "燃烧"]),
    R(["CH3OH", "O2"], ["CO2", "H2O"], conditions="点燃", ion_mode="none", tags=["有机", "燃烧"]),
    R(["C3H7OH", "O2"], ["CO2", "H2O"], conditions="点燃", ion_mode="none", tags=["有机", "燃烧"]),
    R(["CH3CHO", "O2"], ["CO2", "H2O"], conditions="点燃", ion_mode="none", tags=["有机", "燃烧"]),
    R(["CH3COOH", "O2"], ["CO2", "H2O"], conditions="点燃", ion_mode="none", tags=["有机", "燃烧"]),
    R(["C6H12O6", "O2"], ["CO2", "H2O"], conditions="点燃", ion_mode="none", tags=["有机", "燃烧"]),
    R(["C12H22O11", "O2"], ["CO2", "H2O"], conditions="点燃", ion_mode="none", tags=["有机", "燃烧"]),
]

# --------------------------------------------------------------------------- #
# 二、烷烃 / 芳香烃的取代与裂解
# --------------------------------------------------------------------------- #
B4_SUBSTITUTE = [
    R(["CH4", "Cl2"], ["CH3Cl", "HCl"], conditions="光照", ion_mode="none", tags=["有机", "取代"]),
    R(["CH3Cl", "Cl2"], ["CH2Cl2", "HCl"], conditions="光照", ion_mode="none", tags=["有机", "取代"]),
    R(["CH2Cl2", "Cl2"], ["CHCl3", "HCl"], conditions="光照", ion_mode="none", tags=["有机", "取代"]),
    R(["CHCl3", "Cl2"], ["CCl4", "HCl"], conditions="光照", ion_mode="none", tags=["有机", "取代"]),
    R(["C2H6", "Cl2"], ["C2H5Cl", "HCl"], conditions="光照", ion_mode="none", tags=["有机", "取代"]),
    R(["C6H6", "Br2"], ["C6H5Br", "HBr"], conditions="FeBr3", ion_mode="none", tags=["有机", "取代"]),
    R(["C6H6", "Cl2"], ["C6H5Cl", "HCl"], conditions="光照", ion_mode="none", tags=["有机", "取代"]),
    R(["C6H6", "HNO3"], ["C6H5NO2", "H2O"], conditions="浓硫酸,加热", ion_mode="none",
      tags=["有机", "取代"]),
    R(["C6H6", "H2SO4"], ["C6H5SO3H", "H2O"], conditions="加热", ion_mode="none", tags=["有机", "取代"]),
    R(["C7H8", "HNO3"], ["C7H7NO2", "H2O"], conditions="浓硫酸,加热", ion_mode="none",
      tags=["有机", "取代"]),
    R(["C7H8", "Br2"], ["C7H7Br", "HBr"], conditions="FeBr3", ion_mode="none", tags=["有机", "取代"]),
    R(["C6H6", "H2"], ["C6H12"], conditions="Ni,加热", ion_mode="none", tags=["有机", "加成"]),
    R(["C6H6", "Cl2"], ["C6H6Cl6"], conditions="光照", ion_mode="none", tags=["有机", "加成"]),
    R(["C7H8", "H2"], ["C7H14"], conditions="Ni,加热", ion_mode="none", tags=["有机", "加成"]),
    R(["CH4"], ["C", "H2"], conditions="高温", ion_mode="none", tags=["有机", "裂解"]),
    R(["C4H10"], ["C2H4", "C2H6"], conditions="高温", ion_mode="none", tags=["有机", "裂解"]),
    R(["C4H10"], ["CH4", "C3H6"], conditions="高温", ion_mode="none", tags=["有机", "裂解"]),
]

# --------------------------------------------------------------------------- #
# 三、烯烃 / 炔烃加成
# --------------------------------------------------------------------------- #
B4_ADDITION = [
    R(["C2H4", "Br2"], ["C2H4Br2"], ion_mode="none", tags=["有机", "加成"]),
    R(["C2H4", "H2"], ["C2H6"], conditions="Ni,加热", ion_mode="none", tags=["有机", "加成"]),
    R(["C2H4", "HCl"], ["C2H5Cl"], conditions="催化剂", ion_mode="none", tags=["有机", "加成"]),
    R(["C2H4", "HBr"], ["C2H5Br"], conditions="催化剂", ion_mode="none", tags=["有机", "加成"]),
    R(["C2H4", "H2O"], ["C2H5OH"], conditions="催化剂,加热", ion_mode="none",
      tags=["有机", "加成"]),
    R(["C2H4", "Cl2"], ["C2H4Cl2"], conditions="催化剂", ion_mode="none", tags=["有机", "加成"]),
    R(["C3H6", "Br2"], ["C3H6Br2"], ion_mode="none", tags=["有机", "加成"]),
    R(["C3H6", "H2"], ["C3H8"], conditions="Ni,加热", ion_mode="none", tags=["有机", "加成"]),
    R(["C2H2", "Br2"], ["C2H2Br2"], ion_mode="none", tags=["有机", "加成"]),
    R(["C2H2", "H2"], ["C2H4"], conditions="Ni,加热", ion_mode="none", tags=["有机", "加成"]),
    R(["C2H2", "HCl"], ["C2H3Cl"], conditions="催化剂", ion_mode="none", tags=["有机", "加成"]),
    R(["C2H2", "H2O"], ["CH3CHO"], conditions="HgSO4,加热", ion_mode="none",
      tags=["有机", "加成"]),
    R(["C2H2"], ["C6H6"], conditions="催化剂,加热", ion_mode="none", tags=["有机", "加成"]),
    R(["C2H2", "O2"], ["CO2", "H2O"], conditions="点燃", ion_mode="none", tags=["有机", "燃烧"]),
]

# --------------------------------------------------------------------------- #
# 四、卤代烃水解与消去
# --------------------------------------------------------------------------- #
B4_HALIDE = [
    R(["CH3Cl", "NaOH"], ["CH3OH", "NaCl"], conditions="水,加热", ion_mode="none",
      tags=["有机", "水解"]),
    R(["C2H5Cl", "NaOH"], ["C2H5OH", "NaCl"], conditions="水,加热", ion_mode="none",
      tags=["有机", "水解"]),
    R(["C2H5Br", "NaOH"], ["C2H5OH", "NaBr"], conditions="水,加热", ion_mode="none",
      tags=["有机", "水解"]),
    R(["C2H5Cl", "NaOH"], ["C2H4", "NaCl", "H2O"], conditions="乙醇,加热", ion_mode="none",
      tags=["有机", "消去"]),
    R(["C2H5Br", "NaOH"], ["C2H4", "NaBr", "H2O"], conditions="乙醇,加热", ion_mode="none",
      tags=["有机", "消去"]),
]

# --------------------------------------------------------------------------- #
# 五、醇 / 酚
# --------------------------------------------------------------------------- #
B4_ALCOHOL = [
    R(["C2H5OH", "Na"], ["C2H5ONa", "H2"], ion_mode="none", tags=["有机", "醇"]),
    R(["CH3OH", "Na"], ["CH3ONa", "H2"], ion_mode="none", tags=["有机", "醇"]),
    R(["C3H8O3", "Na"], ["C3H5O3Na3", "H2"], ion_mode="none", tags=["有机", "醇"]),
    R(["C2H6O2", "Na"], ["C2H4O2Na2", "H2"], ion_mode="none", tags=["有机", "醇"]),
    R(["C2H5OH", "HBr"], ["C2H5Br", "H2O"], conditions="加热", ion_mode="none",
      tags=["有机", "醇"]),
    R(["C2H5OH", "O2"], ["CH3CHO", "H2O"], conditions="Cu,加热", ion_mode="none",
      tags=["有机", "氧化"]),
    R(["CH3OH", "O2"], ["HCHO", "H2O"], conditions="Cu,加热", ion_mode="none",
      tags=["有机", "氧化"]),
    R(["CH3CHO", "O2"], ["CH3COOH"], conditions="催化剂,加热", ion_mode="none",
      tags=["有机", "氧化"]),
    R(["HCHO", "O2"], ["HCOOH"], conditions="催化剂,加热", ion_mode="none",
      tags=["有机", "氧化"]),
    R(["C2H5OH"], ["C2H4", "H2O"], conditions="浓硫酸,170℃", ion_mode="none",
      tags=["有机", "消去"]),
    R(["C2H5OH"], ["C2H5OC2H5", "H2O"], conditions="浓硫酸,140℃", ion_mode="none",
      tags=["有机", "取代"]),
    R(["C3H7OH"], ["C3H6", "H2O"], conditions="浓硫酸,170℃", ion_mode="none",
      tags=["有机", "消去"]),
    R(["C2H5OH", "CuO"], ["CH3CHO", "Cu", "H2O"], conditions="加热", ion_mode="none",
      tags=["有机", "氧化"]),
    R(["C2H5ONa", "H2O"], ["C2H5OH", "NaOH"], ion_mode="none", tags=["有机", "醇"]),

    # 酚
    R(["C6H5OH", "Na"], ["C6H5ONa", "H2"], ion_mode="none", tags=["有机", "酚"]),
    R(["C6H5OH", "NaOH"], ["C6H5ONa", "H2O"], ion_mode="none", tags=["有机", "酚"]),
    R(["C6H5OH", "Na2CO3"], ["C6H5ONa", "NaHCO3"], ion_mode="none", tags=["有机", "酚"]),
    R(["C6H5OH", "Br2"], ["C6H2Br3OH", "HBr"], ion_mode="none", tags=["有机", "酚"]),
    R(["C6H5OH", "HNO3"], ["C6H2(OH)(NO2)3", "H2O"], conditions="浓硝酸", ion_mode="none",
      tags=["有机", "酚"]),
    R(["C6H5ONa", "CO2", "H2O"], ["C6H5OH", "NaHCO3"], ion_mode="none",
      tags=["有机", "酚"]),
    R(["C6H5ONa", "HCl"], ["C6H5OH", "NaCl"], ion_mode="none", tags=["有机", "酚"]),
]

# --------------------------------------------------------------------------- #
# 六、醛 / 羧酸 / 酯
# --------------------------------------------------------------------------- #
B4_CARBONYL = [
    # 银镜反应
    R(["CH3CHO", "Ag(NH3)2OH"], ["CH3COONH4", "Ag", "NH3", "H2O"], conditions="水浴加热",
      ion_mode="none", tags=["有机", "银镜"]),
    R(["HCHO", "Ag(NH3)2OH"], ["(NH4)2CO3", "Ag", "NH3", "H2O"], conditions="水浴加热",
      ion_mode="none", tags=["有机", "银镜"]),
    R(["HCOOH", "Ag(NH3)2OH"], ["(NH4)2CO3", "Ag", "NH3", "H2O"], conditions="水浴加热",
      ion_mode="none", tags=["有机", "银镜"]),
    R(["C6H12O6", "Ag(NH3)2OH"], ["C6H12O7", "Ag", "NH3", "H2O"], conditions="水浴加热",
      ion_mode="none", tags=["有机", "银镜"]),
    # 斐林反应
    R(["CH3CHO", "Cu(OH)2"], ["CH3COOH", "Cu2O", "H2O"], conditions="加热",
      ion_mode="none", tags=["有机", "斐林"]),
    R(["C6H12O6", "Cu(OH)2"], ["C6H12O7", "Cu2O", "H2O"], conditions="加热",
      ion_mode="none", tags=["有机", "斐林"]),
    # 加氢还原
    R(["CH3CHO", "H2"], ["C2H5OH"], conditions="Ni,加热", ion_mode="none",
      tags=["有机", "还原"]),
    R(["HCHO", "H2"], ["CH3OH"], conditions="Ni,加热", ion_mode="none",
      tags=["有机", "还原"]),

    # 羧酸与碱 / 盐
    R(["CH3COOH", "NaOH"], ["CH3COONa", "H2O"], ion_mode="auto", tags=["有机", "羧酸"]),
    R(["CH3COOH", "Na2CO3"], ["CH3COONa", "H2O", "CO2"], ion_mode="auto",
      tags=["有机", "羧酸"]),
    R(["CH3COOH", "NaHCO3"], ["CH3COONa", "H2O", "CO2"], ion_mode="auto",
      tags=["有机", "羧酸"]),
    R(["HCOOH", "NaOH"], ["HCOONa", "H2O"], ion_mode="auto", tags=["有机", "羧酸"]),
    R(["C6H5COOH", "NaOH"], ["C6H5COONa", "H2O"], ion_mode="auto",
      tags=["有机", "羧酸"]),
    R(["C6H5COOH", "Na2CO3"], ["C6H5COONa", "H2O", "CO2"], ion_mode="auto",
      tags=["有机", "羧酸"]),
    R(["CH3COONa", "HCl"], ["CH3COOH", "NaCl"], ion_mode="auto", tags=["有机", "羧酸"]),
    R(["C6H5COONa", "HCl"], ["C6H5COOH", "NaCl"], ion_mode="auto",
      tags=["有机", "羧酸"]),
    # 甲酸 / 草酸特性
    R(["HCOOH"], ["CO", "H2O"], conditions="浓硫酸,加热", ion_mode="none",
      tags=["有机", "羧酸"]),
    R(["H2C2O4"], ["CO2", "CO", "H2O"], conditions="加热", ion_mode="none",
      tags=["有机", "羧酸"]),
    R(["H2C2O4", "KMnO4", "H2SO4"], ["CO2", "MnSO4", "K2SO4", "H2O"], conditions="加热",
      ion_mode="auto", tags=["有机", "氧化还原"]),

    # 酯化与水解
    R(["CH3COOH", "C2H5OH"], ["CH3COOC2H5", "H2O"], conditions="浓硫酸,加热",
      ion_mode="none", tags=["有机", "酯化"]),
    R(["HCOOH", "C2H5OH"], ["HCOOC2H5", "H2O"], conditions="浓硫酸,加热",
      ion_mode="none", tags=["有机", "酯化"]),
    R(["C6H5COOH", "C2H5OH"], ["C6H5COOC2H5", "H2O"], conditions="浓硫酸,加热",
      ion_mode="none", tags=["有机", "酯化"]),
    R(["CH3COOC2H5", "H2O"], ["CH3COOH", "C2H5OH"], conditions="稀硫酸,加热",
      ion_mode="none", tags=["有机", "水解"]),
    R(["CH3COOC2H5", "NaOH"], ["CH3COONa", "C2H5OH"], conditions="加热",
      ion_mode="none", tags=["有机", "水解"]),
    R(["HCOOC2H5", "NaOH"], ["HCOONa", "C2H5OH"], conditions="加热",
      ion_mode="none", tags=["有机", "水解"]),
]

# --------------------------------------------------------------------------- #
# 七、糖类 / 油脂
# --------------------------------------------------------------------------- #
B4_SUGAR = [
    R(["C6H12O6"], ["C2H5OH", "CO2"], conditions="酒化酶", ion_mode="none",
      tags=["有机", "糖类"]),
    R(["C12H22O11", "H2O"], ["C6H12O6", "C6H12O6"], conditions="酸或酶,加热",
      ion_mode="none", tags=["有机", "糖类"]),
    R(["C12H22O11", "H2O"], ["C6H12O6"], conditions="麦芽糖酶", ion_mode="none",
      tags=["有机", "糖类"]),
    R(["C6H10O5", "H2O"], ["C6H12O6"], conditions="酸或酶,加热", ion_mode="none",
      tags=["有机", "糖类"]),
    R(["C57H110O6", "NaOH"], ["C18H35O2Na", "C3H8O3"], conditions="加热",
      ion_mode="none", tags=["有机", "油脂"]),
    R(["C57H104O6", "H2"], ["C57H110O6"], conditions="Ni,加热", ion_mode="none",
      tags=["有机", "油脂"]),
]

EXTRA_REACTIONS = (B4_BURN + B4_SUBSTITUTE + B4_ADDITION + B4_HALIDE +
                   B4_ALCOHOL + B4_CARBONYL + B4_SUGAR)

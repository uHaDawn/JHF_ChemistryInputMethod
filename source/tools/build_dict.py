# -*- coding: utf-8 -*-
"""化学词库生成器。

手写基础表（元素 / 离子 / 基团 / 氧化物 / 酸 / 碱 / 有机物 / 水合物 / 俗名）
+ 按电荷配平程序化生成盐类，输出 app/src/main/assets/chem_dict.json。

用法：
    python tools/build_dict.py            # 写入 assets
    python tools/build_dict.py --check    # 只统计不写文件

新增词条：直接往对应列表里加即可，然后重新运行本脚本。
"""
import io
import json
import os
import sys
from math import gcd

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "chem_dict.json")

SUB = str.maketrans("0123456789", "₀₁₂₃₄₅₆₇₈₉")


def sub(n):
    return str(n).translate(SUB)


# ---------------------------------------------------------------- 元素（118）
ELEMENTS = [
    ("氢", "qing", "H"), ("氦", "hai", "He"), ("锂", "li", "Li"), ("铍", "pi", "Be"),
    ("硼", "peng", "B"), ("碳", "tan", "C"), ("氮", "dan", "N"), ("氧", "yang", "O"),
    ("氟", "fu", "F"), ("氖", "nai", "Ne"), ("钠", "na", "Na"), ("镁", "mei", "Mg"),
    ("铝", "lv", "Al"), ("硅", "gui", "Si"), ("磷", "lin", "P"), ("硫", "liu", "S"),
    ("氯", "lv", "Cl"), ("氩", "ya", "Ar"), ("钾", "jia", "K"), ("钙", "gai", "Ca"),
    ("钪", "kang", "Sc"), ("钛", "tai", "Ti"), ("钒", "fan", "V"), ("铬", "ge", "Cr"),
    ("锰", "meng", "Mn"), ("铁", "tie", "Fe"), ("钴", "gu", "Co"), ("镍", "nie", "Ni"),
    ("铜", "tong", "Cu"), ("锌", "xin", "Zn"), ("镓", "jia", "Ga"), ("锗", "zhe", "Ge"),
    ("砷", "shen", "As"), ("硒", "xi", "Se"), ("溴", "xiu", "Br"), ("氪", "ke", "Kr"),
    ("铷", "ru", "Rb"), ("锶", "si", "Sr"), ("钇", "yi", "Y"), ("锆", "gao", "Zr"),
    ("铌", "ni", "Nb"), ("钼", "mu", "Mo"), ("锝", "de", "Tc"), ("钌", "liao", "Ru"),
    ("铑", "lao", "Rh"), ("钯", "ba", "Pd"), ("银", "yin", "Ag"), ("镉", "ge", "Cd"),
    ("铟", "yin", "In"), ("锡", "xi", "Sn"), ("锑", "ti", "Sb"), ("碲", "di", "Te"),
    ("碘", "dian", "I"), ("氙", "xian", "Xe"), ("铯", "se", "Cs"), ("钡", "bei", "Ba"),
    ("镧", "lan", "La"), ("铈", "shi", "Ce"), ("镨", "pu", "Pr"), ("钕", "nv", "Nd"),
    ("钷", "po", "Pm"), ("钐", "shan", "Sm"), ("铕", "you", "Eu"), ("钆", "ga", "Gd"),
    ("铽", "te", "Tb"), ("镝", "di", "Dy"), ("钬", "huo", "Ho"), ("铒", "er", "Er"),
    ("铥", "diu", "Tm"), ("镱", "yi", "Yb"), ("镥", "lu", "Lu"), ("铪", "ha", "Hf"),
    ("钽", "tan", "Ta"), ("钨", "wu", "W"), ("铼", "lai", "Re"), ("锇", "e", "Os"),
    ("铱", "yi", "Ir"), ("铂", "bo", "Pt"), ("金", "jin", "Au"), ("汞", "gong", "Hg"),
    ("铊", "ta", "Tl"), ("铅", "qian", "Pb"), ("铋", "bi", "Bi"), ("钋", "po", "Po"),
    ("砹", "ai", "At"), ("氡", "dong", "Rn"), ("钫", "fang", "Fr"), ("镭", "lei", "Ra"),
    ("锕", "a", "Ac"), ("钍", "tu", "Th"), ("镤", "pu", "Pa"), ("铀", "you", "U"),
    ("镎", "na", "Np"), ("钚", "bu", "Pu"), ("镅", "mei", "Am"), ("锔", "ju", "Cm"),
    ("锫", "pei", "Bk"), ("锎", "kai", "Cf"), ("锿", "ai", "Es"), ("镄", "fei", "Fm"),
    ("钔", "men", "Md"), ("锘", "nuo", "No"), ("铹", "lao", "Lr"), ("𬬻", "lu", "Rf"),
    ("𬭊", "du", "Db"), ("𬭳", "xi", "Sg"), ("𬭛", "bo", "Bh"), ("𬭶", "hei", "Hs"),
    ("鿏", "mai", "Mt"), ("𫟼", "da", "Ds"), ("𬬭", "lun", "Rg"), ("鎶", "ge", "Cn"),
    ("鉨", "ni", "Nh"), ("𫓧", "fu", "Fl"), ("镆", "mo", "Mc"), ("鉝", "li", "Lv"),
    ("鿬", "tian", "Ts"), ("鿫", "ao", "Og"),
]

# ---------------------------------------------------------------- 离子
IONS = [
    ("氢离子", "qing li zi", "H⁺"), ("钠离子", "na li zi", "Na⁺"),
    ("钾离子", "jia li zi", "K⁺"), ("银离子", "yin li zi", "Ag⁺"),
    ("铵根", "an gen", "NH₄⁺"), ("铵根离子", "an gen li zi", "NH₄⁺"),
    ("锂离子", "li li zi", "Li⁺"), ("钙离子", "gai li zi", "Ca²⁺"),
    ("镁离子", "mei li zi", "Mg²⁺"), ("钡离子", "bei li zi", "Ba²⁺"),
    ("锌离子", "xin li zi", "Zn²⁺"), ("铜离子", "tong li zi", "Cu²⁺"),
    ("亚铜离子", "ya tong li zi", "Cu⁺"), ("铁离子", "tie li zi", "Fe³⁺"),
    ("亚铁离子", "ya tie li zi", "Fe²⁺"), ("铝离子", "lv li zi", "Al³⁺"),
    ("铅离子", "qian li zi", "Pb²⁺"), ("汞离子", "gong li zi", "Hg²⁺"),
    ("锰离子", "meng li zi", "Mn²⁺"), ("钴离子", "gu li zi", "Co²⁺"),
    ("镍离子", "nie li zi", "Ni²⁺"), ("镉离子", "ge li zi", "Cd²⁺"),
    ("锡离子", "xi li zi", "Sn²⁺"), ("铬离子", "ge li zi", "Cr³⁺"),
    ("氢氧根", "qing yang gen", "OH⁻"), ("羟根", "qiang gen", "OH⁻"),
    ("氯离子", "lv li zi", "Cl⁻"), ("氟离子", "fu li zi", "F⁻"),
    ("溴离子", "xiu li zi", "Br⁻"), ("碘离子", "dian li zi", "I⁻"),
    ("氧离子", "yang li zi", "O²⁻"), ("硫离子", "liu li zi", "S²⁻"),
    ("硫酸根", "liu suan gen", "SO₄²⁻"), ("亚硫酸根", "ya liu suan gen", "SO₃²⁻"),
    ("硝酸根", "xiao suan gen", "NO₃⁻"), ("亚硝酸根", "ya xiao suan gen", "NO₂⁻"),
    ("碳酸根", "tan suan gen", "CO₃²⁻"), ("碳酸氢根", "tan suan qing gen", "HCO₃⁻"),
    ("磷酸根", "lin suan gen", "PO₄³⁻"), ("磷酸氢根", "lin suan qing gen", "HPO₄²⁻"),
    ("磷酸二氢根", "lin suan er qing gen", "H₂PO₄⁻"),
    ("高锰酸根", "gao meng suan gen", "MnO₄⁻"), ("锰酸根", "meng suan gen", "MnO₄²⁻"),
    ("重铬酸根", "chong ge suan gen", "Cr₂O₇²⁻"), ("铬酸根", "ge suan gen", "CrO₄²⁻"),
    ("高氯酸根", "gao lv suan gen", "ClO₄⁻"), ("氯酸根", "lv suan gen", "ClO₃⁻"),
    ("亚氯酸根", "ya lv suan gen", "ClO₂⁻"), ("次氯酸根", "ci lv suan gen", "ClO⁻"),
    ("高碘酸根", "gao dian suan gen", "IO₄⁻"), ("碘酸根", "dian suan gen", "IO₃⁻"),
    ("溴酸根", "xiu suan gen", "BrO₃⁻"), ("偏铝酸根", "pian lv suan gen", "AlO₂⁻"),
    ("硅酸根", "gui suan gen", "SiO₃²⁻"), ("硫氰根", "liu qing gen", "SCN⁻"),
    ("氰根", "qing gen", "CN⁻"), ("硫代硫酸根", "liu dai liu suan gen", "S₂O₃²⁻"),
    ("草酸根", "cao suan gen", "C₂O₄²⁻"), ("乙酸根", "yi suan gen", "CH₃COO⁻"),
    ("醋酸根", "cu suan gen", "CH₃COO⁻"), ("甲酸根", "jia suan gen", "HCOO⁻"),
    ("苯甲酸根", "ben jia suan gen", "C₆H₅COO⁻"),
    ("硬脂酸根", "ying zhi suan gen", "C₁₇H₃₅COO⁻"),
    ("过氧根", "guo yang gen", "O₂²⁻"), ("硫氢根", "liu qing gen", "HS⁻"),
    ("碳酸氢铵根", "tan suan qing an gen", "NH₄HCO₃"),
]

# ---------------------------------------------------------------- 基团
GROUPS = [
    ("甲基", "jia ji", "-CH₃"), ("亚甲基", "ya jia ji", "-CH₂-"),
    ("乙基", "yi ji", "-C₂H₅"), ("丙基", "bing ji", "-C₃H₇"),
    ("异丙基", "yi bing ji", "-CH(CH₃)₂"), ("丁基", "ding ji", "-C₄H₉"),
    ("苯基", "ben ji", "-C₆H₅"), ("苄基", "bian ji", "-CH₂C₆H₅"),
    ("羟基", "qiang ji", "-OH"), ("甲氧基", "jia yang ji", "-OCH₃"),
    ("乙氧基", "yi yang ji", "-OC₂H₅"), ("氨基", "an ji", "-NH₂"),
    ("亚氨基", "ya an ji", "-NH-"), ("硝基", "xiao ji", "-NO₂"),
    ("亚硝基", "ya xiao ji", "-NO"), ("氰基", "qing ji", "-CN"),
    ("羧基", "suo ji", "-COOH"), ("醛基", "quan ji", "-CHO"),
    ("酮基", "tong ji", "-CO-"), ("羰基", "tang ji", "-CO-"),
    ("酰基", "xian ji", "-CO-"), ("酯基", "zhi ji", "-COO-"),
    ("醚键", "mi jian", "-O-"), ("巯基", "qiu ji", "-SH"),
    ("磺酸基", "huang suan ji", "-SO₃H"), ("磷酸基", "lin suan ji", "-PO₄H₂"),
    ("乙烯基", "yi xi ji", "-CH=CH₂"), ("乙炔基", "yi que ji", "-C≡CH"),
    ("卤代基", "lu dai ji", "-X"), ("酰胺键", "xian an jian", "-CONH-"),
]

# ---------------------------------------------------------------- 氧化物
OXIDES = [
    ("水", "shui", "H₂O"), ("过氧化氢", "guo yang hua qing", "H₂O₂"),
    ("双氧水", "shuang yang shui", "H₂O₂"),
    ("二氧化碳", "er yang hua tan", "CO₂"), ("一氧化碳", "yi yang hua tan", "CO"),
    ("二氧化硫", "er yang hua liu", "SO₂"), ("三氧化硫", "san yang hua liu", "SO₃"),
    ("一氧化氮", "yi yang hua dan", "NO"), ("二氧化氮", "er yang hua dan", "NO₂"),
    ("一氧化二氮", "yi yang hua er dan", "N₂O"), ("笑气", "xiao qi", "N₂O"),
    ("氧化钠", "yang hua na", "Na₂O"), ("过氧化钠", "guo yang hua na", "Na₂O₂"),
    ("氧化钾", "yang hua jia", "K₂O"), ("过氧化钾", "guo yang hua jia", "K₂O₂"),
    ("氧化钙", "yang hua gai", "CaO"), ("氧化镁", "yang hua mei", "MgO"),
    ("氧化铝", "yang hua lv", "Al₂O₃"), ("二氧化硅", "er yang hua gui", "SiO₂"),
    ("氧化铁", "yang hua tie", "Fe₂O₃"), ("三氧化二铁", "san yang hua er tie", "Fe₂O₃"),
    ("四氧化三铁", "si yang hua san tie", "Fe₃O₄"),
    ("氧化亚铁", "yang hua ya tie", "FeO"),
    ("氧化铜", "yang hua tong", "CuO"), ("氧化亚铜", "yang hua ya tong", "Cu₂O"),
    ("氧化锌", "yang hua xin", "ZnO"), ("氧化银", "yang hua yin", "Ag₂O"),
    ("氧化钡", "yang hua bei", "BaO"), ("二氧化锰", "er yang hua meng", "MnO₂"),
    ("七氧化二锰", "qi yang hua er meng", "Mn₂O₇"),
    ("五氧化二磷", "wu yang hua er lin", "P₂O₅"),
    ("三氧化二铝", "san yang hua er lv", "Al₂O₃"),
    ("三氧化二铬", "san yang hua er ge", "Cr₂O₃"),
    ("氧化铅", "yang hua qian", "PbO"), ("二氧化铅", "er yang hua qian", "PbO₂"),
    ("氧化汞", "yang hua gong", "HgO"), ("氧化镍", "yang hua nie", "NiO"),
    ("氧化钴", "yang hua gu", "CoO"), ("氧化锂", "yang hua li", "Li₂O"),
]

# ---------------------------------------------------------------- 酸
ACIDS = [
    ("盐酸", "yan suan", "HCl"), ("氢氯酸", "qing lv suan", "HCl"),
    ("硫酸", "liu suan", "H₂SO₄"), ("硝酸", "xiao suan", "HNO₃"),
    ("磷酸", "lin suan", "H₃PO₄"), ("碳酸", "tan suan", "H₂CO₃"),
    ("亚硫酸", "ya liu suan", "H₂SO₃"), ("氢氟酸", "qing fu suan", "HF"),
    ("氢溴酸", "qing xiu suan", "HBr"), ("氢碘酸", "qing dian suan", "HI"),
    ("硫化氢", "liu hua qing", "H₂S"), ("氢硫酸", "qing liu suan", "H₂S"),
    ("次氯酸", "ci lv suan", "HClO"), ("高氯酸", "gao lv suan", "HClO₄"),
    ("氯酸", "lv suan", "HClO₃"), ("亚氯酸", "ya lv suan", "HClO₂"),
    ("高锰酸", "gao meng suan", "HMnO₄"), ("高碘酸", "gao dian suan", "HIO₄"),
    ("乙酸", "yi suan", "CH₃COOH"), ("醋酸", "cu suan", "CH₃COOH"),
    ("冰醋酸", "bing cu suan", "CH₃COOH"), ("甲酸", "jia suan", "HCOOH"),
    ("蚁酸", "yi suan", "HCOOH"), ("草酸", "cao suan", "H₂C₂O₄"),
    ("乙二酸", "yi er suan", "H₂C₂O₄"), ("硼酸", "peng suan", "H₃BO₃"),
    ("硅酸", "gui suan", "H₂SiO₃"), ("苯甲酸", "ben jia suan", "C₆H₅COOH"),
    ("硬脂酸", "ying zhi suan", "C₁₇H₃₅COOH"),
    ("油酸", "you suan", "C₁₇H₃₃COOH"),
    ("丙烯酸", "bing xi suan", "C₃H₄O₂"), ("乳酸", "ru suan", "C₃H₆O₃"),
    ("柠檬酸", "ning meng suan", "C₆H₈O₇"), ("磷酸二氢钠酸", "lin suan er qing na", "NaH₂PO₄"),
    ("氢氰酸", "qing qing suan", "HCN"), ("硫氰酸", "liu qing suan", "HSCN"),
    ("亚磷酸", "ya lin suan", "H₃PO₃"), ("焦磷酸", "jiao lin suan", "H₄P₂O₇"),
]

# ---------------------------------------------------------------- 碱
BASES = [
    ("氢氧化钠", "qing yang hua na", "NaOH"), ("氢氧化钾", "qing yang hua jia", "KOH"),
    ("氢氧化钙", "qing yang hua gai", "Ca(OH)₂"),
    ("氢氧化钡", "qing yang hua bei", "Ba(OH)₂"),
    ("氢氧化镁", "qing yang hua mei", "Mg(OH)₂"),
    ("氢氧化铝", "qing yang hua lv", "Al(OH)₃"),
    ("氢氧化铁", "qing yang hua tie", "Fe(OH)₃"),
    ("氢氧化亚铁", "qing yang hua ya tie", "Fe(OH)₂"),
    ("氢氧化铜", "qing yang hua tong", "Cu(OH)₂"),
    ("氢氧化锌", "qing yang hua xin", "Zn(OH)₂"),
    ("氢氧化银", "qing yang hua yin", "AgOH"),
    ("氢氧化锂", "qing yang hua li", "LiOH"),
    ("氢氧化铵", "qing yang hua an", "NH₃·H₂O"),
    ("一水合氨", "yi shui he an", "NH₃·H₂O"),
    ("氨水", "an shui", "NH₃·H₂O"), ("氨气", "an qi", "NH₃"),
    ("氢氧化锶", "qing yang hua si", "Sr(OH)₂"),
    ("氢氧化镍", "qing yang hua nie", "Ni(OH)₂"),
    ("氢氧化钴", "qing yang hua gu", "Co(OH)₂"),
]

# ---------------------------------------------------------------- 有机物
ORGANICS = [
    ("甲烷", "jia wan", "CH₄"), ("乙烷", "yi wan", "C₂H₆"),
    ("丙烷", "bing wan", "C₃H₈"), ("丁烷", "ding wan", "C₄H₁₀"),
    ("戊烷", "wu wan", "C₅H₁₂"), ("己烷", "ji wan", "C₆H₁₄"),
    ("庚烷", "geng wan", "C₇H₁₆"), ("辛烷", "xin wan", "C₈H₁₈"),
    ("壬烷", "ren wan", "C₉H₂₀"), ("癸烷", "gui wan", "C₁₀H₂₂"),
    ("异丁烷", "yi ding wan", "(CH₃)₃CH"),
    ("环己烷", "huan ji wan", "C₆H₁₂"), ("环丙烷", "huan bing wan", "C₃H₆"),
    ("乙烯", "yi xi", "C₂H₄"), ("丙烯", "bing xi", "C₃H₆"),
    ("丁烯", "ding xi", "C₄H₈"), ("丁二烯", "ding er xi", "C₄H₆"),
    ("异戊二烯", "yi wu er xi", "C₅H₈"),
    ("乙炔", "yi que", "C₂H₂"), ("丙炔", "bing que", "C₃H₄"),
    ("苯", "ben", "C₆H₆"), ("甲苯", "jia ben", "C₇H₈"),
    ("二甲苯", "er jia ben", "C₈H₁₀"), ("乙苯", "yi ben", "C₈H₁₀"),
    ("苯乙烯", "ben yi xi", "C₈H₈"), ("萘", "nai", "C₁₀H₈"),
    ("蒽", "en", "C₁₄H₁₀"), ("苯酚", "ben fen", "C₆H₅OH"),
    ("石炭酸", "shi tan suan", "C₆H₅OH"),
    ("甲醇", "jia chun", "CH₃OH"), ("乙醇", "yi chun", "C₂H₅OH"),
    ("酒精", "jiu jing", "C₂H₅OH"), ("丙醇", "bing chun", "C₃H₇OH"),
    ("异丙醇", "yi bing chun", "(CH₃)₂CHOH"),
    ("丁醇", "ding chun", "C₄H₉OH"), ("乙二醇", "yi er chun", "(CH₂OH)₂"),
    ("丙三醇", "bing san chun", "C₃H₅(OH)₃"), ("甘油", "gan you", "C₃H₅(OH)₃"),
    ("苯甲醇", "ben jia chun", "C₆H₅CH₂OH"),
    ("甲醛", "jia quan", "HCHO"), ("福尔马林", "fu er ma lin", "HCHO"),
    ("乙醛", "yi quan", "CH₃CHO"), ("丙醛", "bing quan", "C₂H₅CHO"),
    ("苯甲醛", "ben jia quan", "C₆H₅CHO"),
    ("丙酮", "bing tong", "CH₃COCH₃"), ("丁酮", "ding tong", "C₂H₅COCH₃"),
    ("丙酸", "bing suan", "C₂H₅COOH"), ("丁酸", "ding suan", "C₃H₇COOH"),
    ("丙二酸", "bing er suan", "HOOCCH₂COOH"),
    ("己二酸", "ji er suan", "HOOC(CH₂)₄COOH"),
    ("葡萄糖", "pu tao tang", "C₆H₁₂O₆"), ("果糖", "guo tang", "C₆H₁₂O₆"),
    ("蔗糖", "zhe tang", "C₁₂H₂₂O₁₁"), ("麦芽糖", "mai ya tang", "C₁₂H₂₂O₁₁"),
    ("淀粉", "dian fen", "(C₆H₁₀O₅)n"), ("纤维素", "xian wei su", "(C₆H₁₀O₅)n"),
    ("乙酸乙酯", "yi suan yi zhi", "CH₃COOC₂H₅"),
    ("乙酸甲酯", "yi suan jia zhi", "CH₃COOCH₃"),
    ("甲酸乙酯", "jia suan yi zhi", "HCOOC₂H₅"),
    ("甘氨酸", "gan an suan", "NH₂CH₂COOH"),
    ("丙氨酸", "bing an suan", "CH₃CH(NH₂)COOH"),
    ("苯丙氨酸", "ben bing an suan", "C₆H₅CH₂CH(NH₂)COOH"),
    ("谷氨酸", "gu an suan", "C₅H₉NO₄"),
    ("尿素", "niao su", "CO(NH₂)₂"), ("三聚氰胺", "san ju qing an", "C₃H₆N₆"),
    ("硝基苯", "xiao ji ben", "C₆H₅NO₂"),
    ("氯甲烷", "lv jia wan", "CH₃Cl"), ("三氯甲烷", "san lv jia wan", "CHCl₃"),
    ("氯仿", "lv fang", "CHCl₃"), ("四氯化碳", "si lv hua tan", "CCl₄"),
    ("溴乙烷", "xiu yi wan", "C₂H₅Br"), ("溴苯", "xiu ben", "C₆H₅Br"),
    ("维生素C", "wei sheng su", "C₆H₈O₆"),
    ("阿司匹林", "a si pi lin", "C₉H₈O₄"),
    ("柠檬烯", "ning meng xi", "C₁₀H₁₆"),
    ("乙醚", "yi mi", "C₂H₅OC₂H₅"),
    ("甲胺", "jia an", "CH₃NH₂"), ("苯胺", "ben an", "C₆H₅NH₂"),
    ("硝化甘油", "xiao hua gan you", "C₃H₅N₃O₉"),
    ("聚乙烯", "ju yi xi", "(C₂H₄)n"), ("聚氯乙烯", "ju lv yi xi", "(C₂H₃Cl)n"),
    ("聚丙烯", "ju bing xi", "(C₃H₆)n"),
    ("酚酞", "fen tai", "C₂₀H₁₄O₄"),
    ("甲基橙", "jia ji cheng", "C₁₄H₁₄N₃NaO₃S"),
]

# ---------------------------------------------------------------- 水合物 / 复盐
HYDRATES = [
    ("五水硫酸铜", "wu shui liu suan tong", "CuSO₄·5H₂O"),
    ("胆矾", "dan fan", "CuSO₄·5H₂O"), ("蓝矾", "lan fan", "CuSO₄·5H₂O"),
    ("十水碳酸钠", "shi shui tan suan na", "Na₂CO₃·10H₂O"),
    ("七水硫酸亚铁", "qi shui liu suan ya tie", "FeSO₄·7H₂O"),
    ("绿矾", "lv fan", "FeSO₄·7H₂O"),
    ("七水硫酸镁", "qi shui liu suan mei", "MgSO₄·7H₂O"),
    ("泻盐", "xie yan", "MgSO₄·7H₂O"),
    ("二水硫酸钙", "er shui liu suan gai", "CaSO₄·2H₂O"),
    ("石膏", "shi gao", "CaSO₄·2H₂O"),
    ("熟石膏", "shu shi gao", "2CaSO₄·H₂O"),
    ("六水氯化镁", "liu shui lv hua mei", "MgCl₂·6H₂O"),
    ("六水氯化钙", "liu shui lv hua gai", "CaCl₂·6H₂O"),
    ("二水氯化钙", "er shui lv hua gai", "CaCl₂·2H₂O"),
    ("二水氯化铜", "er shui lv hua tong", "CuCl₂·2H₂O"),
    ("六水硝酸钴", "liu shui xiao suan gu", "Co(NO₃)₂·6H₂O"),
    ("十二水硫酸铝钾", "shi er shui liu suan lv jia", "KAl(SO₄)₂·12H₂O"),
    ("明矾", "ming fan", "KAl(SO₄)₂·12H₂O"),
    ("三水碳酸镁", "san shui tan suan mei", "MgCO₃·3H₂O"),
    ("一水硫酸锂", "yi shui liu suan li", "Li₂SO₄·H₂O"),
    ("五水硫代硫酸钠", "wu shui liu dai liu suan na", "Na₂S₂O₃·5H₂O"),
    ("大苏打", "da su da", "Na₂S₂O₃·5H₂O"),
    ("海波", "hai bo", "Na₂S₂O₃·5H₂O"),
    ("十水硫酸钠", "shi shui liu suan na", "Na₂SO₄·10H₂O"),
    ("芒硝", "mang xiao", "Na₂SO₄·10H₂O"),
    ("硼砂", "peng sha", "Na₂B₄O₇·10H₂O"),
    ("七水硫酸锌", "qi shui liu suan xin", "ZnSO₄·7H₂O"),
    ("皓矾", "hao fan", "ZnSO₄·7H₂O"),
    ("六水硫酸镍", "liu shui liu suan nie", "NiSO₄·6H₂O"),
    ("二水氯化钡", "er shui lv hua bei", "BaCl₂·2H₂O"),
    ("一水硫酸铜", "yi shui liu suan tong", "CuSO₄·H₂O"),
]

# ---------------------------------------------------------------- 俗名 / 常见名
TRIVIAL = [
    ("纯碱", "chun jian", "Na₂CO₃"), ("苏打", "su da", "Na₂CO₃"),
    ("小苏打", "xiao su da", "NaHCO₃"),
    ("烧碱", "shao jian", "NaOH"), ("火碱", "huo jian", "NaOH"),
    ("苛性钠", "ke xing na", "NaOH"), ("苛性钾", "ke xing jia", "KOH"),
    ("熟石灰", "shu shi hui", "Ca(OH)₂"), ("消石灰", "xiao shi hui", "Ca(OH)₂"),
    ("生石灰", "sheng shi hui", "CaO"),
    ("石灰石", "shi hui shi", "CaCO₃"), ("大理石", "da li shi", "CaCO₃"),
    ("方解石", "fang jie shi", "CaCO₃"),
    ("食盐", "shi yan", "NaCl"), ("干冰", "gan bing", "CO₂"),
    ("石英", "shi ying", "SiO₂"), ("水晶", "shui jing", "SiO₂"),
    ("金刚石", "jin gang shi", "C"), ("石墨", "shi mo", "C"),
    ("碳化硅", "tan hua gui", "SiC"), ("金刚砂", "jin gang sha", "SiC"),
    ("铁锈", "tie xiu", "Fe₂O₃·xH₂O"),
    ("磁铁矿", "ci tie kuang", "Fe₃O₄"), ("磁铁", "ci tie", "Fe₃O₄"),
    ("赤铁矿", "chi tie kuang", "Fe₂O₃"),
    ("沼气", "zhao qi", "CH₄"), ("天然气", "tian ran qi", "CH₄"),
    ("水银", "shui yin", "Hg"),
    ("硫铵", "liu an", "(NH₄)₂SO₄"), ("硝铵", "xiao an", "NH₄NO₃"),
    ("碳铵", "tan an", "NH₄HCO₃"),
    ("冰晶石", "bing jing shi", "Na₃AlF₆"),
    ("重晶石", "chong jing shi", "BaSO₄"),
    ("萤石", "ying shi", "CaF₂"), ("氟石", "fu shi", "CaF₂"),
    ("光气", "guang qi", "COCl₂"),
    ("电石", "dian shi", "CaC₂"),
    ("钠汞齐", "na gong qi", "NaHg"),
    ("漂白粉", "piao bai fen", "Ca(ClO)₂"),
    ("漂白液", "piao bai ye", "NaClO"),
]

# ---------------------------------------------------------------- 盐类生成器
# (名称, 拼音, 符号, 电荷, 是否多原子)
CATIONS = [
    ("钠", "na", "Na", 1, False), ("钾", "jia", "K", 1, False),
    ("锂", "li", "Li", 1, False), ("银", "yin", "Ag", 1, False),
    ("铵", "an", "NH₄", 1, True),
    ("钙", "gai", "Ca", 2, False), ("镁", "mei", "Mg", 2, False),
    ("钡", "bei", "Ba", 2, False), ("锶", "si", "Sr", 2, False),
    ("锌", "xin", "Zn", 2, False), ("铜", "tong", "Cu", 2, False),
    ("亚铜", "ya tong", "Cu", 1, False), ("亚铁", "ya tie", "Fe", 2, False),
    ("铁", "tie", "Fe", 3, False), ("铅", "qian", "Pb", 2, False),
    ("锰", "meng", "Mn", 2, False), ("镍", "nie", "Ni", 2, False),
    ("钴", "gu", "Co", 2, False), ("镉", "ge", "Cd", 2, False),
    ("锡", "xi", "Sn", 2, False), ("钙", "gai", "Ca", 2, False),
    ("铝", "lv", "Al", 3, False), ("铬", "ge", "Cr", 3, False),
    ("汞", "gong", "Hg", 2, False),
]

ANIONS = [
    ("氯化", "lv hua", "Cl", 1, False), ("氟化", "fu hua", "F", 1, False),
    ("溴化", "xiu hua", "Br", 1, False), ("碘化", "dian hua", "I", 1, False),
    ("硫化", "liu hua", "S", 2, False), ("氧化", "yang hua", "O", 2, False),
    ("氢氧化", "qing yang hua", "OH", 1, True),
    ("硝酸", "xiao suan", "NO₃", 1, True),
    ("亚硝酸", "ya xiao suan", "NO₂", 1, True),
    ("硫酸", "liu suan", "SO₄", 2, True),
    ("亚硫酸", "ya liu suan", "SO₃", 2, True),
    ("碳酸", "tan suan", "CO₃", 2, True),
    ("碳酸氢", "tan suan qing", "HCO₃", 1, True),
    ("磷酸", "lin suan", "PO₄", 3, True),
    ("磷酸氢", "lin suan qing", "HPO₄", 2, True),
    ("磷酸二氢", "lin suan er qing", "H₂PO₄", 1, True),
    ("高氯酸", "gao lv suan", "ClO₄", 1, True),
    ("氯酸", "lv suan", "ClO₃", 1, True),
    ("次氯酸", "ci lv suan", "ClO", 1, True),
    ("高锰酸", "gao meng suan", "MnO₄", 1, True),
    ("铬酸", "ge suan", "CrO₄", 2, True),
    ("重铬酸", "chong ge suan", "Cr₂O₇", 2, True),
    ("氰化", "qing hua", "CN", 1, True),
    ("硫氰化", "liu qing hua", "SCN", 1, True),
    ("硅酸", "gui suan", "SiO₃", 2, True),
    ("偏铝酸", "pian lv suan", "AlO₂", 1, True),
    ("草酸", "cao suan", "C₂O₄", 2, True),
    ("乙酸", "yi suan", "CH₃COO", 1, True),
    ("硫酸氢", "liu suan qing", "HSO₄", 1, True),
    ("亚硫酸氢", "ya liu suan qing", "HSO₃", 1, True),
    ("硫氢化", "liu qing hua", "HS", 1, True),
    ("磷酸一氢", "lin suan yi qing", "HPO₄", 2, True),
    ("高碘酸", "gao dian suan", "IO₄", 1, True),
    ("碘酸", "dian suan", "IO₃", 1, True),
    ("溴酸", "xiu suan", "BrO₃", 1, True),
    ("硫代硫酸", "liu dai liu suan", "S₂O₃", 2, True),
    ("过氧化", "guo yang hua", "O₂", 2, True),
    ("苯甲酸", "ben jia suan", "C₆H₅COO", 1, True),
    ("甲酸", "jia suan", "HCOO", 1, True),
    ("丙酸", "bing suan", "C₂H₅COO", 1, True),
    ("丁酸", "ding suan", "C₃H₇COO", 1, True),
    ("乳酸", "ru suan", "CH₃CH(OH)COO", 1, True),
    ("柠檬酸", "ning meng suan", "C₆H₅O₇", 3, True),
]


def wrap(sym, n, poly):
    if n <= 1:
        return sym
    return ("(" + sym + ")" if poly else sym) + sub(n)


def make_formula(cat, an):
    cchg, achg = cat[3], an[3]
    g = gcd(cchg, achg)
    left = wrap(cat[2], achg // g, cat[4])
    right = wrap(an[2], cchg // g, an[4])
    # 羧酸根习惯把金属写在后面：CH₃COONa / (CH₃COO)₂Ca
    if an[2].endswith("COO"):
        return right + left
    return left + right


def build_salts():
    out = []
    seen = set()
    for cat in CATIONS:
        for an in ANIONS:
            name = an[0] + cat[0]
            if name in seen:
                continue
            seen.add(name)
            out.append((name, an[1] + " " + cat[1], make_formula(cat, an)))
    return out


def main():
    check_only = "--check" in sys.argv
    entries = []
    for name, py, f in ELEMENTS:
        entries.append({"n": name, "p": py, "f": [f]})
    for table in (IONS, GROUPS, OXIDES, ACIDS, BASES, ORGANICS, HYDRATES, TRIVIAL):
        for name, py, f in table:
            entries.append({"n": name, "p": py, "f": [f]})
    for name, py, f in build_salts():
        entries.append({"n": name, "p": py, "f": [f]})

    # 按名称去重（保留先出现的，手写表优先于程序生成）
    uniq, seen = [], set()
    for e in entries:
        if e["n"] in seen:
            continue
        seen.add(e["n"])
        uniq.append(e)

    print(f"元素 {len(ELEMENTS)} | 离子 {len(IONS)} | 基团 {len(GROUPS)} | "
          f"氧化物 {len(OXIDES)} | 酸 {len(ACIDS)} | 碱 {len(BASES)} | "
          f"有机物 {len(ORGANICS)} | 水合物 {len(HYDRATES)} | "
          f"生成盐 {len(build_salts())}")
    print(f"去重后总计：{len(uniq)} 条")

    bad = [e["n"] for e in uniq
           for s in e["p"].split()
           if not s.isascii() or not s.isalpha() or len(s) > 6]
    print("非法音节：" + (", ".join(bad) if bad else "无"))
    noname = [e["n"] for e in uniq if not e["f"][0]]
    print("空化学式：" + (", ".join(noname) if noname else "无"))

    if check_only:
        return
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with io.open(OUT, "w", encoding="utf-8", newline="\n") as fp:
        fp.write("[\n")
        fp.write(",\n".join(
            json.dumps(e, ensure_ascii=False, separators=(",", ":")) for e in uniq))
        fp.write("\n]\n")
    print(f"已写入 {OUT}")


if __name__ == "__main__":
    main()

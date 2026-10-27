# -*- coding: utf-8 -*-
"""化学词库生成器。

手写基础表（元素 / 离子 / 基团 / 氧化物 / 酸 / 碱 / 有机物 / 水合物 / 俗名 / 其他）
+ 按电荷配平程序化生成盐类，输出 app/src/main/assets/chem_dict.json。

单词条可带多个读音：p 字段用 "|" 分隔，如 "chong ge suan jia|zhong ge suan jia"。
多音字/常见异读由 ALIAS_RULES 自动派生，无需逐条手写。

用法：
    python tools/build_dict.py            # 写入 assets
    python tools/build_dict.py --check    # 只统计不写文件

新增词条：直接往对应列表里加即可，然后重新运行本脚本。

修改日志（之后每次改动都在这里追加一条：日期 + 改动内容）：
    2026-10-25 新增有机化学符号 61 条：GROUPS 补炔基/烯基/芳基/R 基/酯键/肽键/糖苷键/苯环/杂环/
               手性碳/顺式/反式/伯碳/仲碳/叔碳/季碳共 16 条；ORGANICS 补对苯二甲酸、TNT、苦味酸、
               尼龙66、ATP、卟啉等 45 条。去重后总量 3897 条，多读音 479 条。
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


# ---------------------------------------------------------------- 异读规则
# (名称包含的字, 词库标准音节, 用户常用异读音节)
# 名称命中时自动追加一整组第二种拼音，解决“会念但打不出”的问题。
ALIAS_RULES = [
    ("重铬", "chong", "zhong"),
    ("重晶石", "chong", "zhong"),
    ("铬", "ge", "luo"),
    ("铍", "pi", "bei"),
    ("溴", "xiu", "chou"),
]


def with_aliases(name, py):
    """按 ALIAS_RULES 为名称派生异读拼音，返回 '读音1|读音2' 形式。"""
    variants = [py]
    for ch, src, dst in ALIAS_RULES:
        if ch not in name:
            continue
        for v in list(variants):
            syls = v.split()
            if src in syls:
                alt = " ".join(dst if s == src else s for s in syls)
                if alt not in variants:
                    variants.append(alt)
    return "|".join(variants)


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
    ("油酸根", "you suan gen", "C₁₇H₃₃COO⁻"),
    ("过氧根", "guo yang gen", "O₂²⁻"), ("硫氢根", "liu qing gen", "HS⁻"),
    ("超氧根", "chao yang gen", "O₂⁻"),
    ("水合氢离子", "shui he qing li zi", "H₃O⁺"),
    ("亚锡离子", "ya xi li zi", "Sn²⁺"),
    ("亚汞离子", "ya gong li zi", "Hg₂²⁺"),
    ("金离子", "jin li zi", "Au³⁺"),
    ("铂离子", "bo li zi", "Pt²⁺"),
    ("高铁酸根", "gao tie suan gen", "FeO₄²⁻"),
    ("高溴酸根", "gao xiu suan gen", "BrO₄⁻"),
    ("次溴酸根", "ci xiu suan gen", "BrO⁻"),
    ("次碘酸根", "ci dian suan gen", "IO⁻"),
    ("硒酸根", "xi suan gen", "SeO₄²⁻"),
    ("亚硒酸根", "ya xi suan gen", "SeO₃²⁻"),
    ("砷酸根", "shen suan gen", "AsO₄³⁻"),
    ("亚砷酸根", "ya shen suan gen", "AsO₃³⁻"),
    ("硼酸根", "peng suan gen", "BO₃³⁻"),
    ("偏硼酸根", "pian peng suan gen", "BO₂⁻"),
    ("四硼酸根", "si peng suan gen", "B₄O₇²⁻"),
    ("偏磷酸根", "pian lin suan gen", "PO₃⁻"),
    ("焦磷酸根", "jiao lin suan gen", "P₂O₇⁴⁻"),
    ("焦硫酸根", "jiao liu suan gen", "S₂O₇²⁻"),
    ("过硫酸根", "guo liu suan gen", "S₂O₈²⁻"),
    ("连四硫酸根", "lian si liu suan gen", "S₄O₆²⁻"),
    ("亚磷酸根", "ya lin suan gen", "PO₃³⁻"),
    ("亚磷酸氢根", "ya lin suan qing gen", "HPO₃²⁻"),
    ("草酸氢根", "cao suan qing gen", "HC₂O₄⁻"),
    ("柠檬酸根", "ning meng suan gen", "C₆H₅O₇³⁻"),
    ("酒石酸根", "jiu shi suan gen", "C₄H₄O₆²⁻"),
    ("水杨酸根", "shui yang suan gen", "C₇H₅O₃⁻"),
    ("苯磺酸根", "ben huang suan gen", "C₆H₅SO₃⁻"),
    ("山梨酸根", "shan li suan gen", "C₆H₇O₂⁻"),
    ("丙烯酸根", "bing xi suan gen", "C₃H₃O₂⁻"),
    ("锌酸根", "xin suan gen", "ZnO₂²⁻"),
    ("锡酸根", "xi suan gen", "SnO₃²⁻"),
    ("钨酸根", "wu suan gen", "WO₄²⁻"),
    ("钼酸根", "mu suan gen", "MoO₄²⁻"),
    ("钒酸根", "fan suan gen", "VO₃⁻"),
    ("氟硅酸根", "fu gui suan gen", "SiF₆²⁻"),
    ("氟硼酸根", "fu peng suan gen", "BF₄⁻"),
    ("苯酚根", "ben fen gen", "C₆H₅O⁻"),
    ("银氨络离子", "yin an luo li zi", "[Ag(NH₃)₂]⁺"),
    ("铜氨络离子", "tong an luo li zi", "[Cu(NH₃)₄]²⁺"),
    ("四羟基合铝酸根", "si qiang ji he lv suan gen", "[Al(OH)₄]⁻"),
    ("铁氰根", "tie qing gen", "[Fe(CN)₆]³⁻"),
    ("亚铁氰根", "ya tie qing gen", "[Fe(CN)₆]⁴⁻"),
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
    ("烯丙基", "xi bing ji", "-CH₂CH=CH₂"),
    ("仲丁基", "zhong ding ji", "-CH(CH₃)C₂H₅"),
    ("叔丁基", "shu ding ji", "-C(CH₃)₃"),
    ("乙酰基", "yi xian ji", "-COCH₃"),
    ("苯甲酰基", "ben jia xian ji", "-COC₆H₅"),
    ("苯氧基", "ben yang ji", "-OC₆H₅"),
    ("过氧基", "guo yang ji", "-O-O-"),
    ("偶氮基", "ou dan ji", "-N=N-"),
    ("肼基", "jing ji", "-NHNH₂"),
    ("磺酰基", "huang xian ji", "-SO₂-"),
    ("羟甲基", "qiang jia ji", "-CH₂OH"),
    ("次甲基", "ci jia ji", "-CH="),
    # ---- 2026-10-25 新增：有机化学常用结构符号 / 命名前缀 ----
    ("炔基", "que ji", "-C≡C-"),
    ("烯基", "xi ji", "-CH=CH-"),
    ("芳基", "fang ji", "-Ar"),
    ("R基", "r ji", "-R"),
    ("酯键", "zhi jian", "-COO-"),
    ("肽键", "tai jian", "-CONH-"),
    ("糖苷键", "tang gan jian", "-O-"),
    ("苯环", "ben huan", "C₆H₆"),
    ("杂环", "za huan", "—"),
    ("手性碳", "shou xing tan", "C*"),
    ("顺式", "shun shi", "cis-"),
    ("反式", "fan shi", "trans-"),
    ("伯碳", "bo tan", "1°C"),
    ("仲碳", "zhong tan", "2°C"),
    ("叔碳", "shu tan", "3°C"),
    ("季碳", "ji tan", "4°C"),
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
    ("二氧化氯", "er yang hua lv", "ClO₂"),
    ("一氧化二氯", "yi yang hua er lv", "Cl₂O"),
    ("七氧化二氯", "qi yang hua er lv", "Cl₂O₇"),
    ("三氧化铬", "san yang hua ge", "CrO₃"),
    ("氧化铬", "yang hua ge", "Cr₂O₃"),
    ("氧化亚铬", "yang hua ya ge", "CrO"),
    ("二氧化钛", "er yang hua tai", "TiO₂"), ("氧化钛", "yang hua tai", "TiO₂"),
    ("三氧化钨", "san yang hua wu", "WO₃"), ("氧化钨", "yang hua wu", "WO₃"),
    ("三氧化钼", "san yang hua mu", "MoO₃"), ("氧化钼", "yang hua mu", "MoO₃"),
    ("五氧化二钒", "wu yang hua er fan", "V₂O₅"),
    ("三氧化二钒", "san yang hua er fan", "V₂O₃"),
    ("氧化硼", "yang hua peng", "B₂O₃"),
    ("三氧化二硼", "san yang hua er peng", "B₂O₃"),
    ("氧化锰", "yang hua meng", "MnO"),
    ("氧化亚锰", "yang hua ya meng", "MnO"),
    ("三氧化二锰", "san yang hua er meng", "Mn₂O₃"),
    ("二氧化锡", "er yang hua xi", "SnO₂"),
    ("氧化亚锡", "yang hua ya xi", "SnO"),
    ("氧化镉", "yang hua ge", "CdO"),
    ("氧化锶", "yang hua si", "SrO"),
    ("氧化铍", "yang hua pi", "BeO"),
    ("二氧化锆", "er yang hua gao", "ZrO₂"), ("氧化锆", "yang hua gao", "ZrO₂"),
    ("二氧化铈", "er yang hua shi", "CeO₂"),
    ("二氧化铀", "er yang hua you", "UO₂"),
    ("二氧化钍", "er yang hua tu", "ThO₂"),
    ("四氧化锇", "si yang hua e", "OsO₄"),
    ("四氧化三铅", "si yang hua san qian", "Pb₃O₄"),
    ("三氧化二氮", "san yang hua er dan", "N₂O₃"),
    ("四氧化二氮", "si yang hua er dan", "N₂O₄"),
    ("五氧化二氮", "wu yang hua er dan", "N₂O₅"),
    ("氧化亚氮", "yang hua ya dan", "N₂O"),
    ("三氧化二磷", "san yang hua er lin", "P₂O₃"),
    ("一氧化硅", "yi yang hua gui", "SiO"),
    ("二氧化硒", "er yang hua xi", "SeO₂"),
    ("氧化金", "yang hua jin", "Au₂O₃"),
    ("氧化铂", "yang hua bo", "PtO₂"),
    ("三氧化二锑", "san yang hua er ti", "Sb₂O₃"),
    ("五氧化二锑", "wu yang hua er ti", "Sb₂O₅"),
    ("氧化镓", "yang hua jia", "Ga₂O₃"),
    ("氧化铟", "yang hua yin", "In₂O₃"),
    ("氧化镧", "yang hua lan", "La₂O₃"),
    ("氧化钇", "yang hua yi", "Y₂O₃"),
    ("三氧化二砷", "san yang hua er shen", "As₂O₃"),
    ("五氧化二砷", "wu yang hua er shen", "As₂O₅"),
    ("二氧化铪", "er yang hua ha", "HfO₂"),
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
    ("柠檬酸", "ning meng suan", "C₆H₈O₇"),
    ("氢氰酸", "qing qing suan", "HCN"), ("硫氰酸", "liu qing suan", "HSCN"),
    ("亚磷酸", "ya lin suan", "H₃PO₃"), ("焦磷酸", "jiao lin suan", "H₄P₂O₇"),
    ("亚硝酸", "ya xiao suan", "HNO₂"),
    ("高溴酸", "gao xiu suan", "HBrO₄"), ("溴酸", "xiu suan", "HBrO₃"),
    ("次溴酸", "ci xiu suan", "HBrO"),
    ("碘酸", "dian suan", "HIO₃"), ("次碘酸", "ci dian suan", "HIO"),
    ("次磷酸", "ci lin suan", "H₃PO₂"),
    ("偏磷酸", "pian lin suan", "HPO₃"),
    ("焦硫酸", "jiao liu suan", "H₂S₂O₇"),
    ("过硫酸", "guo liu suan", "H₂S₂O₈"),
    ("硫代硫酸", "liu dai liu suan", "H₂S₂O₃"),
    ("硒酸", "xi suan", "H₂SeO₄"), ("亚硒酸", "ya xi suan", "H₂SeO₃"),
    ("砷酸", "shen suan", "H₃AsO₄"), ("亚砷酸", "ya shen suan", "H₃AsO₃"),
    ("偏硼酸", "pian peng suan", "HBO₂"), ("四硼酸", "si peng suan", "H₂B₄O₇"),
    ("铬酸", "ge suan", "H₂CrO₄"), ("锰酸", "meng suan", "H₂MnO₄"),
    ("偏硅酸", "pian gui suan", "H₂SiO₃"),
    ("氟硅酸", "fu gui suan", "H₂SiF₆"), ("氟硼酸", "fu peng suan", "HBF₄"),
    ("钨酸", "wu suan", "H₂WO₄"), ("钼酸", "mu suan", "H₂MoO₄"),
    ("钒酸", "fan suan", "HVO₃"), ("高铼酸", "gao lai suan", "HReO₄"),
    ("氯铂酸", "lv bo suan", "H₂PtCl₆"),
    ("水杨酸", "shui yang suan", "C₇H₆O₃"),
    ("酒石酸", "jiu shi suan", "C₄H₆O₆"),
    ("苹果酸", "ping guo suan", "C₄H₆O₅"),
    ("琥珀酸", "hu po suan", "C₄H₆O₄"), ("丁二酸", "ding er suan", "C₄H₆O₄"),
    ("苯磺酸", "ben huang suan", "C₆H₅SO₃H"),
    ("山梨酸", "shan li suan", "C₆H₈O₂"),
    ("乙二胺四乙酸", "yi er an si yi suan", "C₁₀H₁₆N₂O₈"),
    ("甲基丙烯酸", "jia ji bing xi suan", "C₄H₆O₂"),
    ("叠氮酸", "die dan suan", "HN₃"),
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
    ("氢氧化铍", "qing yang hua pi", "Be(OH)₂"),
    ("氢氧化镉", "qing yang hua ge", "Cd(OH)₂"),
    ("氢氧化铬", "qing yang hua ge", "Cr(OH)₃"),
    ("氢氧化锰", "qing yang hua meng", "Mn(OH)₂"),
    ("氢氧化亚锰", "qing yang hua ya meng", "Mn(OH)₂"),
    ("氢氧化亚铜", "qing yang hua ya tong", "CuOH"),
    ("氢氧化金", "qing yang hua jin", "Au(OH)₃"),
    ("氢氧化铅", "qing yang hua qian", "Pb(OH)₂"),
    ("氢氧化锡", "qing yang hua xi", "Sn(OH)₂"),
    ("氢氧化亚锡", "qing yang hua ya xi", "Sn(OH)₂"),
    ("氢氧化锆", "qing yang hua gao", "Zr(OH)₄"),
    ("氢氧化铈", "qing yang hua shi", "Ce(OH)₄"),
    ("氢氧化镓", "qing yang hua jia", "Ga(OH)₃"),
    ("氢氧化铟", "qing yang hua yin", "In(OH)₃"),
    ("氢氧化铋", "qing yang hua bi", "Bi(OH)₃"),
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
    ("甲基红", "jia ji hong", "C₁₅H₁₅N₃O₂"),
    ("十一烷", "shi yi wan", "C₁₁H₂₄"), ("十二烷", "shi er wan", "C₁₂H₂₆"),
    ("十三烷", "shi san wan", "C₁₃H₂₈"), ("十四烷", "shi si wan", "C₁₄H₃₀"),
    ("十五烷", "shi wu wan", "C₁₅H₃₂"), ("十六烷", "shi liu wan", "C₁₆H₃₄"),
    ("十七烷", "shi qi wan", "C₁₇H₃₆"), ("十八烷", "shi ba wan", "C₁₈H₃₈"),
    ("十九烷", "shi jiu wan", "C₁₉H₄₀"), ("二十烷", "er shi wan", "C₂₀H₄₂"),
    ("环丁烷", "huan ding wan", "C₄H₈"), ("环戊烷", "huan wu wan", "C₅H₁₀"),
    ("环庚烷", "huan geng wan", "C₇H₁₄"), ("环辛烷", "huan xin wan", "C₈H₁₆"),
    ("新戊烷", "xin wu wan", "C₅H₁₂"), ("异戊烷", "yi wu wan", "C₅H₁₂"),
    ("环己烯", "huan ji xi", "C₆H₁₀"), ("环戊二烯", "huan wu er xi", "C₅H₆"),
    ("异丁烯", "yi ding xi", "C₄H₈"), ("丁炔", "ding que", "C₄H₆"),
    ("戊炔", "wu que", "C₅H₈"),
    ("邻二甲苯", "lin er jia ben", "C₈H₁₀"),
    ("间二甲苯", "jian er jia ben", "C₈H₁₀"),
    ("对二甲苯", "dui er jia ben", "C₈H₁₀"),
    ("丙苯", "bing ben", "C₉H₁₂"), ("异丙苯", "yi bing ben", "C₉H₁₂"),
    ("联苯", "lian ben", "C₁₂H₁₀"),
    ("氯苯", "lv ben", "C₆H₅Cl"),
    ("邻二氯苯", "lin er lv ben", "C₆H₄Cl₂"),
    ("对二氯苯", "dui er lv ben", "C₆H₄Cl₂"),
    ("萘酚", "nai fen", "C₁₀H₈O"), ("蒽醌", "en kun", "C₁₄H₈O₂"),
    ("戊醇", "wu chun", "C₅H₁₂O"), ("己醇", "ji chun", "C₆H₁₄O"),
    ("环己醇", "huan ji chun", "C₆H₁₂O"),
    ("烯丙醇", "xi bing chun", "C₃H₆O"),
    ("丙二醇", "bing er chun", "C₃H₈O₂"),
    ("甲醚", "jia mi", "C₂H₆O"), ("甲乙醚", "jia yi mi", "C₃H₈O"),
    ("环氧乙烷", "huan yang yi wan", "C₂H₄O"),
    ("四氢呋喃", "si qing fu nan", "C₄H₈O"),
    ("丁醛", "ding quan", "C₄H₈O"), ("异丁醛", "yi ding quan", "C₄H₈O"),
    ("戊醛", "wu quan", "C₅H₁₀O"), ("苯乙醛", "ben yi quan", "C₈H₈O"),
    ("糠醛", "kang quan", "C₅H₄O₂"), ("丙烯醛", "bing xi quan", "C₃H₄O"),
    ("戊酮", "wu tong", "C₅H₁₀O"),
    ("环己酮", "huan ji tong", "C₆H₁₀O"),
    ("苯乙酮", "ben yi tong", "C₈H₈O"),
    ("戊酸", "wu suan", "C₅H₁₀O₂"), ("己酸", "ji suan", "C₆H₁₂O₂"),
    ("苯乙酸", "ben yi suan", "C₈H₈O₂"),
    ("乙酸丙酯", "yi suan bing zhi", "C₅H₁₀O₂"),
    ("乙酸丁酯", "yi suan ding zhi", "C₆H₁₂O₂"),
    ("苯甲酸甲酯", "ben jia suan jia zhi", "C₈H₈O₂"),
    ("苯甲酸乙酯", "ben jia suan yi zhi", "C₉H₁₀O₂"),
    ("乳糖", "ru tang", "C₁₂H₂₂O₁₁"), ("糖原", "tang yuan", "(C₆H₁₀O₅)n"),
    ("核糖", "he tang", "C₅H₁₀O₅"),
    ("脱氧核糖", "tuo yang he tang", "C₅H₁₀O₄"),
    ("亮氨酸", "liang an suan", "C₆H₁₃NO₂"),
    ("异亮氨酸", "yi liang an suan", "C₆H₁₃NO₂"),
    ("缬氨酸", "xie an suan", "C₅H₁₁NO₂"),
    ("丝氨酸", "si an suan", "C₃H₇NO₃"),
    ("苏氨酸", "su an suan", "C₄H₉NO₃"),
    ("半胱氨酸", "ban guang an suan", "C₃H₇NO₂S"),
    ("甲硫氨酸", "jia liu an suan", "C₅H₁₁NO₂S"),
    ("蛋氨酸", "dan an suan", "C₅H₁₁NO₂S"),
    ("色氨酸", "se an suan", "C₁₁H₁₂N₂O₂"),
    ("酪氨酸", "lao an suan", "C₉H₁₁NO₃"),
    ("赖氨酸", "lai an suan", "C₆H₁₄N₂O₂"),
    ("精氨酸", "jing an suan", "C₆H₁₄N₄O₂"),
    ("组氨酸", "zu an suan", "C₆H₉N₃O₂"),
    ("天冬氨酸", "tian dong an suan", "C₄H₇NO₄"),
    ("门冬氨酸", "men dong an suan", "C₄H₇NO₄"),
    ("脯氨酸", "fu an suan", "C₅H₉NO₂"),
    ("谷氨酰胺", "gu an xian an", "C₅H₁₀N₂O₃"),
    ("聚苯乙烯", "ju ben yi xi", "(C₈H₈)n"),
    ("聚四氟乙烯", "ju si fu yi xi", "(C₂F₄)n"),
    ("聚丙烯腈", "ju bing xi jing", "(C₃H₃N)n"),
    ("有机玻璃", "you ji bo li", "(C₅H₈O₂)n"),
    ("聚甲基丙烯酸甲酯", "ju jia ji bing xi suan jia zhi", "(C₅H₈O₂)n"),
    ("乙二胺", "yi er an", "C₂H₈N₂"),
    ("硫脲", "liu niao", "CH₄N₂S"),
    ("吡啶", "bi ding", "C₅H₅N"),
    ("呋喃", "fu nan", "C₄H₄O"),
    ("噻吩", "sai fen", "C₄H₄S"),
    ("吡咯", "bi luo", "C₄H₅N"),
    ("咪唑", "mi zuo", "C₃H₄N₂"),
    ("吲哚", "yin duo", "C₈H₇N"),
    ("二甲基亚砜", "er jia ji ya feng", "C₂H₆OS"),
    ("胆固醇", "dan gu chun", "C₂₇H₄₆O"),
    ("咖啡因", "ka fei yin", "C₈H₁₀N₄O₂"),
    ("尼古丁", "ni gu ding", "C₁₀H₁₄N₂"),
    ("布洛芬", "bu luo fen", "C₁₃H₁₈O₂"),
    ("对乙酰氨基酚", "dui yi xian an ji fen", "C₈H₉NO₂"),
    ("维生素E", "wei sheng su e", "C₂₉H₅₀O₂"),
    ("山梨酸钾", "shan li suan jia", "C₆H₇KO₂"),
    ("谷氨酸钠", "gu an suan na", "C₅H₈NNaO₄"),
    ("味精", "wei jing", "C₅H₈NNaO₄"),
    ("硬脂酸钠", "ying zhi suan na", "C₁₇H₃₅COONa"),
    ("油酸钠", "you suan na", "C₁₇H₃₃COONa"),
    # ---- 2026-10-25 新增：有机化学常见化合物 / 高分子 / 天然产物 ----
    ("对苯二甲酸", "dui ben er jia suan", "C₆H₄(COOH)₂"),
    ("苯甲酸钠", "ben jia suan na", "C₆H₅COONa"),
    ("乙酸钠", "yi suan na", "CH₃COONa"),
    ("三硝基甲苯", "san xiao ji jia ben", "C₆H₂CH₃(NO₂)₃"),
    ("TNT", "tnt", "C₆H₂CH₃(NO₂)₃"),
    ("苦味酸", "ku wei suan", "C₆H₂OH(NO₂)₃"),
    ("十二烷基苯磺酸钠", "shi er wan ji ben huang suan na", "C₁₂H₂₅C₆H₄SO₃Na"),
    ("聚乙烯醇", "ju yi xi chun", "(C₂H₄O)n"),
    ("聚碳酸酯", "ju tan suan zhi", "(C₁₆H₁₄O₃)n"),
    ("尼龙66", "ni long|ni long liu liu", "[NH(CH₂)₆NHCO(CH₂)₄CO]n"),
    ("涤纶", "di lun", "(C₁₀H₈O₄)n"),
    ("天然橡胶", "tian ran xiang jiao", "(C₅H₈)n"),
    ("甘油三酯", "gan you san zhi", "C₃H₅(OOCR)₃"),
    ("肾上腺素", "shen shang xian su", "C₉H₁₃NO₃"),
    ("青霉素", "qing mei su", "C₁₆H₁₈N₂O₄S"),
    ("叶绿素", "ye lv su", "C₅₅H₇₂MgN₄O₅"),
    ("血红素", "xue hong su", "C₃₄H₃₂FeN₄O₄"),
    ("三磷酸腺苷", "san lin suan xian gan", "C₁₀H₁₆N₅O₁₃P₃"),
    ("ATP", "atp", "C₁₀H₁₆N₅O₁₃P₃"),
    ("多巴胺", "duo ba an", "C₈H₁₁NO₂"),
    ("薄荷醇", "bo he chun", "C₁₀H₂₀O"),
    ("樟脑", "zhang nao", "C₁₀H₁₆O"),
    ("香兰素", "xiang lan su", "C₈H₈O₃"),
    ("糖精", "tang jing", "C₇H₅NO₃S"),
    ("阿斯巴甜", "a si ba tian", "C₁₄H₁₈N₂O₅"),
    ("丙烯酰胺", "bing xi xian an", "C₃H₅NO"),
    ("己内酰胺", "ji nei xian an", "C₆H₁₁NO"),
    ("对苯醌", "dui ben kun", "C₆H₄O₂"),
    ("苯甲醚", "ben jia mi", "C₇H₈O"),
    ("二苯醚", "er ben mi", "C₁₂H₁₀O"),
    ("苯肼", "ben jing", "C₆H₈N₂"),
    ("苯甲腈", "ben jia jing", "C₇H₅N"),
    ("丙烯腈", "bing xi jing", "C₃H₃N"),
    ("乙酸酐", "yi suan gan", "C₄H₆O₃"),
    ("邻苯二甲酸酐", "lin ben er jia suan gan", "C₈H₄O₃"),
    ("环氧氯丙烷", "huan yang lv bing wan", "C₃H₅ClO"),
    ("双酚A", "shuang fen a", "C₁₅H₁₆O₂"),
    ("对苯二酚", "dui ben er fen", "C₆H₆O₂"),
    ("间苯二酚", "jian ben er fen", "C₆H₆O₂"),
    ("吡喃", "bi nan", "C₅H₆O"),
    ("嘌呤", "piao ling", "C₅H₄N₄"),
    ("嘧啶", "mi ding", "C₄H₄N₂"),
    ("喹啉", "kui lin", "C₉H₇N"),
    ("咔唑", "ka zuo", "C₁₂H₉N"),
    ("卟啉", "bu lin", "C₂₀H₁₄N₄"),
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
    ("三水硝酸铜", "san shui xiao suan tong", "Cu(NO₃)₂·3H₂O"),
    ("六水硝酸锌", "liu shui xiao suan xin", "Zn(NO₃)₂·6H₂O"),
    ("六水硝酸镁", "liu shui xiao suan mei", "Mg(NO₃)₂·6H₂O"),
    ("六水氯化钴", "liu shui lv hua gu", "CoCl₂·6H₂O"),
    ("六水氯化镍", "liu shui lv hua nie", "NiCl₂·6H₂O"),
    ("七水硫酸钴", "qi shui liu suan gu", "CoSO₄·7H₂O"),
    ("七水硫酸镍", "qi shui liu suan nie", "NiSO₄·7H₂O"),
    ("二水草酸", "er shui cao suan", "H₂C₂O₄·2H₂O"),
    ("三水乙酸钠", "san shui yi suan na", "CH₃COONa·3H₂O"),
    ("一水乙酸铜", "yi shui yi suan tong", "Cu(CH₃COO)₂·H₂O"),
    ("十二水磷酸钠", "shi er shui lin suan na", "Na₃PO₄·12H₂O"),
    ("一水硫酸锰", "yi shui liu suan meng", "MnSO₄·H₂O"),
    ("一水氢氧化钡", "yi shui qing yang hua bei", "Ba(OH)₂·H₂O"),
    ("八水氢氧化钡", "ba shui qing yang hua bei", "Ba(OH)₂·8H₂O"),
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
    ("84消毒液", "ba si xiao du ye", "NaClO"),
    ("砒霜", "pi shuang", "As₂O₃"),
    ("雄黄", "xiong huang", "As₄S₄"),
    ("雌黄", "ci huang", "As₂S₃"),
    ("辰砂", "chen sha", "HgS"), ("朱砂", "zhu sha", "HgS"),
    ("黄铁矿", "huang tie kuang", "FeS₂"), ("硫铁矿", "liu tie kuang", "FeS₂"),
    ("黄铜矿", "huang tong kuang", "CuFeS₂"),
    ("赤铜矿", "chi tong kuang", "Cu₂O"),
    ("软锰矿", "ruan meng kuang", "MnO₂"),
    ("铝土矿", "lv tu kuang", "Al₂O₃"),
    ("白云石", "bai yun shi", "CaMg(CO₃)₂"),
    ("菱镁矿", "ling mei kuang", "MgCO₃"),
    ("菱铁矿", "ling tie kuang", "FeCO₃"),
    ("磷灰石", "lin hui shi", "Ca₅(PO₄)₃F"),
    ("天青石", "tian qing shi", "SrSO₄"),
    ("孔雀石", "kong que shi", "Cu₂(OH)₂CO₃"),
    ("铜绿", "tong lv", "Cu₂(OH)₂CO₃"),
    ("元明粉", "yuan ming fen", "Na₂SO₄"),
    ("硝石", "xiao shi", "KNO₃"), ("火硝", "huo xiao", "KNO₃"),
    ("智利硝石", "zhi li xiao shi", "NaNO₃"),
    ("钾碱", "jia jian", "K₂CO₃"),
    ("水玻璃", "shui bo li", "Na₂SiO₃"),
    ("硅胶", "gui jiao", "SiO₂·nH₂O"),
    ("食醋", "shi cu", "CH₃COOH"),
    ("活性炭", "huo xing tan", "C"),
    ("焦炭", "jiao tan", "C"), ("木炭", "mu tan", "C"), ("炭黑", "tan hei", "C"),
    ("白磷", "bai lin", "P₄"), ("红磷", "hong lin", "P₄"),
    ("硫磺", "liu huang", "S"), ("硫黄", "liu huang", "S"),
    ("臭氧", "chou yang", "O₃"),
    ("二硫化碳", "er liu hua tan", "CS₂"),
    ("碳化钙", "tan hua gai", "CaC₂"),
    ("氮化铝", "dan hua lv", "AlN"),
    ("氢化钠", "qing hua na", "NaH"),
    ("氢化钙", "qing hua gai", "CaH₂"),
    ("氢化铝锂", "qing hua lv li", "LiAlH₄"),
    ("铁氰化钾", "tie qing hua jia", "K₃[Fe(CN)₆]"),
    ("亚铁氰化钾", "ya tie qing hua jia", "K₄[Fe(CN)₆]"),
    ("普鲁士蓝", "pu lu shi lan", "Fe₄[Fe(CN)₆]₃"),
    ("滕氏蓝", "teng shi lan", "Fe₄[Fe(CN)₆]₃"),
    ("三氯化铁", "san lv hua tie", "FeCl₃"),
    ("三氯化铝", "san lv hua lv", "AlCl₃"),
    ("三氯化磷", "san lv hua lin", "PCl₃"),
    ("五氯化磷", "wu lv hua lin", "PCl₅"),
    ("四氯化硅", "si lv hua gui", "SiCl₄"),
    ("四氯化钛", "si lv hua tai", "TiCl₄"),
    ("三氟化硼", "san fu hua peng", "BF₃"),
    ("六氟化硫", "liu fu hua liu", "SF₆"),
    ("二氯甲烷", "er lv hua tan", "CH₂Cl₂"),
    ("二氯乙烷", "er lv yi wan", "C₂H₄Cl₂"),
    ("氯乙烯", "lv yi xi", "C₂H₃Cl"),
    ("三氯甲烷", "san lv hua tan", "CHCl₃"),
    ("硼氢化钠", "peng qing hua na", "NaBH₄"),
    ("碳化硅", "tan hua gui", "SiC"),
    ("氮化硅", "dan hua gui", "Si₃N₄"),
    ("氟化氢", "fu hua qing", "HF"),
    ("氯化氢", "lv hua qing", "HCl"),
    ("溴化氢", "xiu hua qing", "HBr"),
    ("碘化氢", "dian hua qing", "HI"),
    ("氰化氢", "qing hua qing", "HCN"),
    ("氨气", "an qi", "NH₃"),
    ("液氨", "ye an", "NH₃"),
    ("笑气", "xiao qi", "N₂O"),
    ("重水", "zhong shui", "D₂O"),
    ("过磷酸钙", "guo lin suan gai", "Ca(H₂PO₄)₂·CaSO₄"),
    ("重过磷酸钙", "chong guo lin suan gai", "Ca(H₂PO₄)₂"),
    ("磷酸氢二钠", "lin suan qing er na", "Na₂HPO₄"),
    ("磷酸三钠", "lin suan san na", "Na₃PO₄"),
    ("磷酸氢二铵", "lin suan qing er an", "(NH₄)₂HPO₄"),
    ("磷酸氢二钾", "lin suan qing er jia", "K₂HPO₄"),
    ("酒石酸钾钠", "jiu shi suan jia na", "KNaC₄H₄O₆"),
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
    ("锡", "xi", "Sn", 2, False),
    ("铝", "lv", "Al", 3, False), ("铬", "ge", "Cr", 3, False),
    ("汞", "gong", "Hg", 2, False),
    ("铍", "pi", "Be", 2, False), ("亚锡", "ya xi", "Sn", 2, False),
    ("亚汞", "ya gong", "Hg₂", 2, True), ("金", "jin", "Au", 3, False),
    ("铂", "bo", "Pt", 2, False), ("亚铬", "ya ge", "Cr", 2, False),
    ("镓", "jia", "Ga", 3, False), ("铟", "yin", "In", 3, False),
    ("铋", "bi", "Bi", 3, False), ("锆", "gao", "Zr", 4, False),
    ("钇", "yi", "Y", 3, False), ("镧", "lan", "La", 3, False),
    ("铈", "shi", "Ce", 3, False),
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
    ("高溴酸", "gao xiu suan", "BrO₄", 1, True),
    ("次溴酸", "ci xiu suan", "BrO", 1, True),
    ("次碘酸", "ci dian suan", "IO", 1, True),
    ("硒酸", "xi suan", "SeO₄", 2, True),
    ("亚硒酸", "ya xi suan", "SeO₃", 2, True),
    ("砷酸", "shen suan", "AsO₄", 3, True),
    ("亚砷酸", "ya shen suan", "AsO₃", 3, True),
    ("硼酸", "peng suan", "BO₃", 3, True),
    ("偏硼酸", "pian peng suan", "BO₂", 1, True),
    ("四硼酸", "si peng suan", "B₄O₇", 2, True),
    ("偏磷酸", "pian lin suan", "PO₃", 1, True),
    ("次磷酸", "ci lin suan", "H₂PO₂", 1, True),
    ("焦磷酸", "jiao lin suan", "P₂O₇", 4, True),
    ("焦硫酸", "jiao liu suan", "S₂O₇", 2, True),
    ("过硫酸", "guo liu suan", "S₂O₈", 2, True),
    ("连四硫酸", "lian si liu suan", "S₄O₆", 2, True),
    ("亚磷酸", "ya lin suan", "PO₃", 3, True),
    ("亚磷酸氢", "ya lin suan qing", "HPO₃", 2, True),
    ("锰酸", "meng suan", "MnO₄", 2, True),
    ("高铁酸", "gao tie suan", "FeO₄", 2, True),
    ("锌酸", "xin suan", "ZnO₂", 2, True),
    ("锡酸", "xi suan", "SnO₃", 2, True),
    ("偏钛酸", "pian tai suan", "TiO₃", 2, True),
    ("钨酸", "wu suan", "WO₄", 2, True),
    ("钼酸", "mu suan", "MoO₄", 2, True),
    ("钒酸", "fan suan", "VO₃", 1, True),
    ("高铼酸", "gao lai suan", "ReO₄", 1, True),
    ("氟硅酸", "fu gui suan", "SiF₆", 2, True),
    ("氟硼酸", "fu peng suan", "BF₄", 1, True),
    ("草酸氢", "cao suan qing", "HC₂O₄", 1, True),
    ("酒石酸", "jiu shi suan", "C₄H₄O₆", 2, True),
    ("水杨酸", "shui yang suan", "C₇H₅O₃", 1, True),
    ("苯磺酸", "ben huang suan", "C₆H₅SO₃", 1, True),
    ("山梨酸", "shan li suan", "C₆H₇O₂", 1, True),
    ("丙烯酸", "bing xi suan", "C₃H₃O₂", 1, True),
    ("硬脂酸", "ying zhi suan", "C₁₇H₃₅COO", 1, True),
    ("油酸", "you suan", "C₁₇H₃₃COO", 1, True),
    ("谷氨酸", "gu an suan", "C₅H₈NO₄", 1, True),
    ("甘氨酸", "gan an suan", "NH₂CH₂COO", 1, True),
    ("氢化", "qing hua", "H", 1, False),
    ("氮化", "dan hua", "N", 3, False),
    ("磷化", "lin hua", "P", 3, False),
    ("硅化", "gui hua", "Si", 4, False),
    ("超氧化", "chao yang hua", "O₂", 1, True),
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

    # 按名称去重（保留先出现的，手写表优先于程序生成）；同名词条合并异读拼音
    uniq, by_name = [], {}
    for e in entries:
        old = by_name.get(e["n"])
        if old is not None:
            variants = old["p"].split("|")
            for alt in e["p"].split("|"):
                if alt not in variants:
                    variants.append(alt)
            old["p"] = "|".join(variants)
            continue
        by_name[e["n"]] = e
        uniq.append(e)

    # 按 ALIAS_RULES 派生多音字/常见异读
    for e in uniq:
        e["p"] = with_aliases(e["n"], e["p"])

    print(f"元素 {len(ELEMENTS)} | 离子 {len(IONS)} | 基团 {len(GROUPS)} | "
          f"氧化物 {len(OXIDES)} | 酸 {len(ACIDS)} | 碱 {len(BASES)} | "
          f"有机物 {len(ORGANICS)} | 水合物 {len(HYDRATES)} | "
          f"生成盐 {len(build_salts())}")
    print(f"去重后总计：{len(uniq)} 条")
    print(f"多读音词条：{sum(1 for e in uniq if '|' in e['p'])} 条")

    bad = [(e["n"], s) for e in uniq
           for alt in e["p"].split("|")
           for s in alt.split()
           if not s.isascii() or not s.isalpha() or len(s) > 6]
    print("非法音节：" + (", ".join(f"{n}:{s}" for n, s in bad) if bad else "无"))
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

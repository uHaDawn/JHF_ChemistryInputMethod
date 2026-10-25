# -*- coding: utf-8 -*-
"""交互回归：输入法弹出、按键上屏、候选、手势、引导等端到端用例。

覆盖《设计文档》§6.4 的 TC-03 ~ TC-12、TC-15、TC-16
（TC-01/02 在 run_ui_tests.ps1 的 L1，TC-13/14 在 verify_theme，TC-09 在 verify_tabs）。

用法：
    python tools/interaction_tests.py [输出目录，默认 tests/current]
坐标基于 Medium_Phone (1080x2400 @420dpi) 实测校准。
"""
import io
import os
import subprocess
import sys
import time

import numpy as np
from PIL import Image

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

ADB = r"D:\development\Android\Sdk\platform-tools\adb.exe"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PKG = "com.jhf.jiehuafa"
IME_ID = "com.jhf.jiehuafa/.ime.JhfImeService"
CHROME = "com.android.chrome/com.google.android.apps.chrome.Main"

URL_BAR = (440, 191)
# 字母页键中心（kb_probe 实测；底部 ⇌ 条收窄到 34dp 后整块键盘下移 24dp，y 相应 +62）
LETTER = {"Q": (64, 1605), "W": (169, 1605), "T": (487, 1605), "O": (910, 1605),
          # A 行：A(69) S(187) D(305) F(422) G(540) H(657) J(775) K(892) L(1010)
          "G": (540, 1756), "H": (657, 1756), "J": (775, 1756), "K": (892, 1756),
          # Z 行：⇧(69) Z(187) X(305) C(422) V(540) B(657) N(775) M(892) ⌫(1010)
          "N": (775, 1907), "C": (422, 1907)}
BACK_KEY = (1010, 1907)
SPACE_KEY = (644, 2058)
# 两页现在都是 5 行（3 内容 + 功能行 + ⇌ 条），键盘等高 → 候选栏位置相同
PAGE_CHIP_LETTER = (119, 1458)      # 「化｜符」
PAGE_CHIP_SYMBOL = (119, 1458)      # 「化｜符」（符号页同高）
CAND_FIRST = (420, 1457)            # 首个候选
IME_BAR = (540, 2150)               # 底部 ⇌ 细条 = 切换输入法（客户第三轮明确）
CAND_COLLAPSE = (1005, 1462)        # 候选栏右侧 ⌄ 芯片 = 收起键盘（与化｜符同一条 y）
SYM_TAB_X, SYM_TAB_Y = 176, [1602, 1717, 1832, 1947, 2062]
# 主题选择弹窗（竖排列表，靠右）：点「当前主题」条弹出，点行即选中并自动关闭
THEME_BAR = (539, 650)
PICKER_X = 829
PICKER_ROW_Y = {"fe2o3": 770, "cucl2": 890, "kmno4": 1010, "agbr": 1131, "auto": 1252}
# T 键长按浮层：6 个候选，第 3 个是「数字下角标 ₅」
POPUP_OPT3 = (423, 1462)

ABOUT_BTN = (924, 162)
MODE_BTN = (924, 804)
HAGO_LOGO = (790, 1046)             # 关于页 HAGO logo 的 G 字位置

OUT = os.path.join(ROOT, "tests", "current")

results = []


def adb(*args):
    r = subprocess.run([ADB] + [str(a) for a in args],
                       capture_output=True, encoding="utf-8", errors="replace")
    return (r.stdout or "").strip()


def tap(x, y, wait=0.6):
    adb("shell", "input", "tap", x, y)
    time.sleep(wait)


def swipe(x1, y1, x2, y2, ms=400, wait=0.8):
    adb("shell", "input", "swipe", x1, y1, x2, y2, ms)
    time.sleep(wait)


def longpress(x, y, ms=700, wait=1.0):
    # 必须用 motionevent DOWN/UP：`input swipe x y x y ms` 这种零位移滑动
    # 在部分系统上不会触发长按计时（实测浮层根本不出现）。
    adb("shell", "input", "motionevent", "DOWN", x, y)
    time.sleep(ms / 1000.0)
    adb("shell", "input", "motionevent", "UP", x, y)
    time.sleep(wait)


def hold_down(x, y):
    adb("shell", "input", "motionevent", "DOWN", x, y)


def move_to(x, y):
    adb("shell", "input", "motionevent", "MOVE", x, y)


def release(x, y):
    adb("shell", "input", "motionevent", "UP", x, y)


def shot(name):
    # 保持整屏原图：用例里的像素断言（如 TC-08/TC-09 的分类栏取样）都按绝对 y 计算，
    # 裁剪会让这些坐标失效。宿主 App 的噪声由 visual_diff.KB_IGNORE_TOP 统一处理，
    # 且 it_* 已不作为视觉基线（只作报告附件）。
    adb("shell", "screencap", "-p", "/sdcard/_it.png")
    dst = os.path.join(OUT, name + ".png")
    adb("pull", "/sdcard/_it.png", dst)
    return dst


def ui():
    adb("shell", "uiautomator", "dump", "/sdcard/_it.xml")
    return adb("shell", "cat", "/sdcard/_it.xml")


NODE_RE = __import__("re").compile(r"<node[^>]*>")


def edit_text():
    """当前输入框文本（Chrome 地址栏）。

    必须逐节点解析：属性里 text 排在 class 之前，用"往前找 text="的取法
    会抓到上一个节点（候选栏芯片）的文本，得到 氰化钾→KCN 这种假结果。
    """
    xml = ui()
    for raw in NODE_RE.findall(xml):
        if 'class="android.widget.EditText"' in raw:
            k = raw.find('text="')
            t = raw[k + 6:raw.find('"', k + 6)] if k >= 0 else ""
            # 地址栏为空时会显示占位文案，要当作空串
            return "" if t in ("Search or type URL", "about:blank") else t
    return None


def ime_vis():
    out = adb("shell", "dumpsys", "input_method")
    for line in out.splitlines():
        s = line.strip()
        if s.startswith("mImeWindowVis="):
            return int(s.split("=")[1])
    return -1


def ime_switcher_shown():
    """系统输入法切换器是否弹出（窗口名 = IME Switcher Menu）。

    比像素比对稳：⇌ 键的功能就是弹它，直接断言窗口存在。
    """
    return "IME Switcher Menu" in adb("shell", "dumpsys", "window", "windows")


def ime_input_shown():
    """输入法是否处于「已显示」状态（mInputShown）。

    切换器弹窗会把键盘遮住，这时像素判据 keyboard_drawn() 会误判成「键盘没了」，
    所以「点 ⇌ 不该收起键盘」这条只能用系统状态判断。
    """
    for line in adb("shell", "dumpsys", "input_method").splitlines():
        s = line.strip()
        if s.startswith("mInputShown="):
            return s.split("=")[1].strip() == "true"
    return False


def focus():
    out = adb("shell", "dumpsys", "window")
    for line in out.splitlines():
        if "mCurrentFocus=" in line:
            return line.split("mCurrentFocus=")[1].strip()
    return ""


def check(name, cond, detail=""):
    results.append((name, bool(cond), detail))
    print(f"{'OK  ' if cond else 'FAIL'} {name}" + (f"   [{detail}]" if detail else ""))
    if not cond:
        # 失败自动留现场，便于定位（键盘是否在、落在哪一页）
        try:
            adb("shell", "screencap", "-p", "/sdcard/_fail.png")
            adb("pull", "/sdcard/_fail.png",
                os.path.join(OUT, "_fail_" + name.split()[0] + ".png"))
        except Exception:
            pass


def px(img_path, y, x):
    return tuple(int(v) for v in np.asarray(Image.open(img_path).convert("RGB"))[y, x])


def keyboard_drawn():
    """从截图判断键盘是否真的画出来。

    mImeWindowVis 在多次启停后会残留旧值；只看"大面积白色"又会把
    Chrome 的纯白页面误判成键盘。所以要求「白键 + 灰间隙」同时存在。
    """
    adb("shell", "screencap", "-p", "/sdcard/_probe.png")
    p = os.path.join(OUT, "_probe.png")
    adb("pull", "/sdcard/_probe.png", p)
    a = np.asarray(Image.open(p).convert("RGB")).astype(int)
    band = a[1450:2250, :, :]
    white = (band >= 248).all(axis=2).mean()
    gray = (np.abs(band - 234).max(axis=2) <= 6).mean()
    return bool(0.25 < white < 0.95 and gray > 0.03)


def url_focused():
    """地址栏是否真的拿到了输入焦点（键盘可见 ≠ 焦点在输入框上）。"""
    xml = ui()
    for raw in NODE_RE.findall(xml):
        if 'class="android.widget.EditText"' in raw and 'focused="true"' in raw:
            return True
    return False


def reset_chrome():
    adb("shell", "am", "force-stop", "com.android.chrome")
    time.sleep(1)
    adb("shell", "am", "start", "-n", CHROME, "-d", "about:blank")
    time.sleep(7)
    # 冷启动后地址栏偶发需要点两次才聚焦；流水线里机器更忙，留足重试。
    # 必须同时满足「键盘画出来了」和「输入框拿到焦点」，否则按键不会有反应。
    for i in range(4):
        tap(*URL_BAR, wait=3)
        kb, uf = keyboard_drawn(), url_focused()
        if kb and uf:
            return
        print(f"   (第 {i + 1} 次聚焦未成功：键盘={kb} 焦点={uf}，重试)")
    print("   WARN 地址栏聚焦失败，键盘未弹出")


EMPTY_HINTS = ("", None, "Search or type URL", "about:blank")


def clear_input():
    """已废弃：清空输入框的各种做法都有副作用，见 tc04 的注释。

    保留空实现只是为了不破坏调用点语义（当下没有任何用例需要清空）。
    """
    return


def reset_theme_and_mode():
    """把主题复位为 CuCl₂、深浅色复位为浅色。

    键盘的像素断言（TC-08/09/11）都按默认主题色 #05BEC1 取样，上一轮用例
    （尤其 TC-15 会把主题切到 Fe₂O₃）残留下来会让它们全部失效。
    """
    adb("shell", "am", "start", "--activity-clear-top", "-n", f"{PKG}/.MainActivity")
    time.sleep(1.8)
    dismiss_guide()
    tap(*THEME_BAR, wait=0.9)
    tap(PICKER_X, PICKER_ROW_Y["cucl2"], wait=1.6)
    for _ in range(4):
        xml = ui()
        if ("浅色模式" in xml) and ("跟随系统" not in xml):
            break
        tap(*MODE_BTN, wait=1.3)


def prepare_ime():
    # 重置键盘页状态（字母页/符号页常驻在 IME 服务里，会串到下一轮用例）。
    # force-stop 后必须再 start 一次：被 force-stop 的包处于 stopped 状态，
    # 系统不会自动拉起它，IME 窗口会存在但没有内容。
    adb("shell", "am", "force-stop", PKG)
    time.sleep(1.2)
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    time.sleep(2.5)
    adb("shell", "settings", "put", "secure", "show_ime_with_hard_keyboard", "1")
    adb("shell", "ime", "enable", IME_ID)
    adb("shell", "ime", "set", IME_ID)
    time.sleep(1.5)


# ------------------------------------------------------------------ 用例
def tc03_keyboard_shows():
    vis = ime_vis()
    check("TC-03 输入法在第三方输入框上方弹出", vis == 3, f"mImeWindowVis={vis}")


def tc04_letter_commit():
    """用"相对断言"替代清空输入框。

    清空走过的弯路：点键盘 ⌫ 依赖坐标会点错键；硬件 DEL 被组词吞掉；
    ESC 能取消组词却会把键盘一起收起；每个用例重启 Chrome 又把模拟器压垮。
    改成记录输入前文本、断言增量，一举解决。
    """
    before = edit_text() or ""
    for k in ("Q", "H", "J"):
        tap(*LETTER[k], wait=0.45)
    time.sleep(0.5)
    t = edit_text() or ""
    check("TC-04 点按字母上屏", t == before + "qhj",
          f"输入框={t!r} 期望 {before + 'qhj'!r}")


def tc05_06_pinyin_candidate():
    before = edit_text() or ""
    for k in ("T", "O", "N", "G"):     # tong
        tap(*LETTER[k], wait=0.45)
    time.sleep(0.6)
    t1 = edit_text() or ""
    check("TC-05 拼音入组词区", t1 == before + "tong", f"输入框={t1!r}")
    shot("it_pinyin_candidates")
    tap(*CAND_FIRST, wait=1.0)
    t2 = edit_text() or ""
    check("TC-06 点候选上屏化学式", t2 == before + "Cu",
          f"输入框={t2!r} 期望 {before + 'Cu'!r}")


def tc07_space_commit():
    before = edit_text() or ""
    for k in ("Q", "H", "K"):          # qhk 无候选
        tap(*LETTER[k], wait=0.45)
    time.sleep(0.4)
    tap(*SPACE_KEY, wait=0.8)
    t = edit_text() or ""
    check("TC-07 无候选时空格直出组词", t == before + "qhk",
          f"输入框={t!r} 期望 {before + 'qhk'!r}")


def tab_accent_ratio(png, y):
    """分类栏区域的主题色占比（判断符号页是否已展开）。"""
    a = np.asarray(Image.open(png).convert("RGB")).astype(int)
    band = a[max(0, y - 10):y + 10, 30:320]
    return float((np.abs(band - np.array([5, 190, 193])).sum(axis=2) < 40).mean())


def tc08_page_toggle():
    # 两页现在等高（符号页也补上了 ⇌ 条），不能再靠「键盘高度变化」判断，
    # 改为断言符号页左侧 5 分类栏的出现/消失。
    a = shot("it_letter_page")
    r_letter = tab_accent_ratio(a, SYM_TAB_Y[0])
    tap(*PAGE_CHIP_LETTER, wait=1.2)
    b = shot("it_symbol_page")
    r_symbol = tab_accent_ratio(b, SYM_TAB_Y[0])
    check("TC-08 化|符 切换符号页（分类栏出现）",
          r_letter < 0.1 and r_symbol > 0.5,
          f"字母页分类位主题色占比={r_letter:.2f} 符号页={r_symbol:.2f}")
    tap(*PAGE_CHIP_SYMBOL, wait=1.0)


def tc10_swipe():
    qx, qy = LETTER["Q"]
    ok, detail = False, ""
    for _ in range(3):                      # 上滑偶发被识别成点按，重试
        before = edit_text() or ""
        swipe(qx, qy, qx, qy - 220, 500, wait=0.9)
        t = edit_text() or ""
        if t == before + "₁":
            ok = True
            break
        detail = f"输入框={t!r} 期望 {before + '₁'!r}"
    check("TC-10 上滑输出化学符号（Q → ₁）", ok, detail)


def tc11_longpress():
    qx, qy = LETTER["T"]
    txt0 = edit_text() or ""
    before = shot("it_before_longpress")
    hold_down(qx, qy)
    time.sleep(1.1)
    after = shot("it_longpress_popup")
    diff = image_diff_ratio(before, after)
    check("TC-11 长按弹出形态浮层", diff > 0.5, f"长按前后画面差异 {diff:.2f}%")

    # 按住不松手、滑到第 3 个候选再放开：应直接输出该项（客户意见 3）
    move_to(*POPUP_OPT3)
    time.sleep(0.6)
    release(*POPUP_OPT3)
    time.sleep(1.0)
    t = edit_text() or ""
    check("TC-11b 长按后滑动选择输出", t == txt0 + "₅",
          f"输入框={t!r} 期望 {txt0 + '₅'!r}")


def tc12_ime_switcher():
    """底部 ⇌ = 切换输入法；⌄ = 收起键盘（客户第三轮明确，此前把两者做反了）。

    断言：① 点 ⇌ 后弹出系统「IME Switcher Menu」窗口；② 键盘没有被收起（上一版就是这个 bug）；
    ③ 点候选栏 ⌄ 才收起键盘。
    """
    if not keyboard_drawn():               # 前置用例可能让键盘收起，先确保它在
        tap(*URL_BAR, wait=3)
    shot("it_ime_picker_before")
    tap(*IME_BAR, wait=2.0)
    shot("it_ime_picker_after")
    check("TC-12 底部 ⇌ 唤起输入法切换器", ime_switcher_shown(),
          "未出现 IME Switcher Menu 窗口")
    check("TC-12b 切输入法不收起键盘", ime_input_shown(), "点 ⇌ 后输入法应仍是显示态")

    adb("shell", "input", "keyevent", "4")   # 关掉切换器，恢复现场
    time.sleep(1.2)
    tap(*URL_BAR, wait=2.5)
    if keyboard_drawn():
        tap(*CAND_COLLAPSE, wait=1.8)
        hidden = not keyboard_drawn()
        check("TC-12c 候选栏 ⌄ 收起键盘", hidden, f"点击后键盘是否仍可见={not hidden}")
        if hidden:
            tap(*URL_BAR, wait=3)              # 恢复键盘，后续用例还要用
    else:
        check("TC-12c 候选栏 ⌄ 收起键盘", False, "前置：键盘没弹出来")


def tc15_hago_logo():
    """HAGO logo 在任何主题下都必须固定为品牌青色。"""
    adb("shell", "am", "start", "--activity-clear-top", "-n", f"{PKG}/.MainActivity")
    time.sleep(2)
    dismiss_guide()
    seen = {}
    accent = {"agbr": (231, 196, 0), "kmno4": (143, 45, 185),
              "cucl2": (5, 190, 193), "fe2o3": (185, 45, 45)}
    for theme, color in accent.items():
        # 主题选择弹窗（竖排列表）：点「当前主题」条弹出 → 点对应行，自动关闭
        tap(*THEME_BAR, wait=0.8)
        tap(PICKER_X, PICKER_ROW_Y[theme], wait=2.2)
        tap(*ABOUT_BTN, wait=2.0)
        p = shot(f"it_hago_{theme}")
        # 在 HAGO logo 区域内找是否出现该主题色（不应出现）
        arr = np.asarray(Image.open(p).convert("RGB")).astype(int)
        region = arr[1029:1069, 772:998]
        # 阈值收紧到 15：CuCl₂ 主题色 (5,190,193) 与品牌青 (0,196,210) 太接近，
        # 用宽阈值会把品牌色本身误判成"跟随了主题"
        hit = (np.abs(region - np.array(color)).sum(axis=2) < 15).sum()
        teal = (np.abs(region - np.array([0, 196, 210])).sum(axis=2) < 60).sum()
        seen[theme] = (int(hit), int(teal))
        tap(159, 162, wait=1.5)     # 返回
    bad = {k: v for k, v in seen.items() if v[0] > 20}
    check("TC-15 HAGO logo 不随主题变色", not bad,
          f"各主题下主题色像素命中 {seen}（期望 hits≈0，teal>0）")
    # 复位为 CuCl₂ 浅色（点行即自动关闭弹窗）
    adb("shell", "am", "start", "--activity-clear-top", "-n", f"{PKG}/.MainActivity")
    time.sleep(1.5)
    tap(*THEME_BAR, wait=0.8)
    tap(PICKER_X, PICKER_ROW_Y["cucl2"], wait=1.5)


def tc16_guide():
    adb("shell", "ime", "disable", IME_ID)
    adb("shell", "ime", "set",
        "com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME")
    time.sleep(2)
    adb("shell", "am", "force-stop", PKG)
    time.sleep(1.5)
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    time.sleep(3)
    xml1 = ui()
    shot("it_guide_enable")
    check("TC-16a 未启用时自动弹出操作引导",
          ("操作引导" in xml1) and ("前往" in xml1) and ("已完成" not in xml1),
          "引导面板 + 前往按钮存在")
    adb("shell", "ime", "enable", IME_ID)
    time.sleep(2)
    adb("shell", "am", "start", "--activity-clear-top", "-n", f"{PKG}/.MainActivity")
    time.sleep(2.5)
    xml2 = ui()
    shot("it_guide_done")
    check("TC-16b 启用后引导项变已完成", "已完成" in xml2, "第一行按钮文案")
    # 复位：设为默认输入法并关闭引导
    adb("shell", "ime", "set", IME_ID)
    time.sleep(1.5)
    dismiss_guide()


# ------------------------------------------------------------------ 辅助
def keyboard_top(png):
    """从截图找键盘第一行白色键的顶部 y（用于判断键盘高度）。"""
    a = np.asarray(Image.open(png).convert("RGB"))
    mask = (a[:, :, 0] >= 248) & (a[:, :, 1] >= 248) & (a[:, :, 2] >= 248)
    rowsum = mask.sum(axis=1)
    h, w = rowsum.shape[0], a.shape[1]
    for y in range(h // 2, h):
        if rowsum[y] > w * 0.35:
            return y
    return None


def image_diff_ratio(p1, p2):
    A = np.asarray(Image.open(p1).convert("RGB")).astype(int)
    B = np.asarray(Image.open(p2).convert("RGB")).astype(int)
    if A.shape != B.shape:
        return 100.0
    return float((np.abs(A - B).max(axis=2) > 12).mean()) * 100


def dismiss_guide():
    xml = ui()
    if "前往" in xml or "操作引导" in xml:
        tap(540, 1800, wait=1.0)


def tc09_symbol_tabs():
    """符号页 5 分类逐一切换并校验高亮。"""
    if not keyboard_drawn():             # 前置用例可能让键盘收起
        tap(*URL_BAR, wait=3)
    tap(*PAGE_CHIP_LETTER, wait=1.2)     # 进符号页
    time.sleep(1.0)                      # 等键盘按符号页高度重排完再点 tab
    accent = np.array([5, 190, 193])
    ok_all, detail = True, []
    for i, y in enumerate(SYM_TAB_Y):
        tap(SYM_TAB_X, y, wait=0.9)
        p = shot(f"it_symtab_{i}")
        a = np.asarray(Image.open(p).convert("RGB")).astype(int)
        band = a[max(0, y - 10):y + 10, 30:320]
        hit = float((np.abs(band - accent).sum(axis=2) < 40).mean())
        detail.append(round(hit, 2))
        if hit < 0.5:
            ok_all = False
    check("TC-09 符号页 5 分类 tab 高亮", ok_all, f"各档主题色占比 {detail}")
    tap(*PAGE_CHIP_SYMBOL, wait=1.0)


def main():
    global OUT
    if len(sys.argv) > 1:
        OUT = os.path.abspath(sys.argv[1])
    os.makedirs(OUT, exist_ok=True)

    print("========== 交互回归 ==========")
    reset_theme_and_mode()
    prepare_ime()
    reset_chrome()

    tc03_keyboard_shows()
    # 候选用例先跑：地址栏为空时组词区短，候选芯片位置稳定；
    # 若排在后面，前面的输入会把候选整体右推，点击坐标就失效了。
    tc05_06_pinyin_candidate()
    tc04_letter_commit()
    tc07_space_commit()
    tc08_page_toggle()
    tc09_symbol_tabs()
    tc10_swipe()
    tc11_longpress()
    tc12_ime_switcher()
    tc15_hago_logo()
    tc16_guide()

    failed = [r for r in results if not r[1]]
    print(f"\n========== 结果：{len(results) - len(failed)}/{len(results)} 通过 ==========")
    for name, _, detail in failed:
        print(f"  FAIL {name}  {detail}")
    sys.exit(0 if not failed else 1)


if __name__ == "__main__":
    main()

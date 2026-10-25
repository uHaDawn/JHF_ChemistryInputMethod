# -*- coding: utf-8 -*-
"""采集界面基线截图（App 页面 + 键盘），输出到 tests/golden/。

用法：
    python tools/capture_baseline.py            # 全量
    python tools/capture_baseline.py --app      # 只采 App 页面（主界面 / 关于页）
    python tools/capture_baseline.py --kbd      # 只采键盘（字母页 / 符号页 5 分类）

坐标基于 Medium_Phone (1080x2400 @420dpi, density 2.625) 实测校准，
换设备需同步更新 tools/figma_spec.json 与这里的坐标常量。
"""
import argparse
import io
import os
import subprocess
import sys
import time

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

ADB = r"D:\development\Android\Sdk\platform-tools\adb.exe"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GOLDEN = os.path.join(ROOT, "tests", "golden")

PKG = "com.jhf.jiehuafa"
IME_ID = "com.jhf.jiehuafa/.ime.JhfImeService"
CHROME = "com.android.chrome/com.google.android.apps.chrome.Main"

# ---- 主界面坐标（实测） ----
ABOUT_BTN = (924, 162)
BACK_BTN = (159, 162)
THEME_BAR = (539, 650)
# 主题选择已改成弹窗式竖排列表（设计稿 8a653cd5）：靠右、纵向对齐「当前主题」条
PICKER_X = 829
PICKER_ROW_Y = {"fe2o3": 770, "cucl2": 890, "kmno4": 1010, "agbr": 1131, "auto": 1252}
MODE_BTN_COLLAPSED = (924, 804)
MODE_BTN_EXPANDED = (924, 943)
THEMES = ["agbr", "kmno4", "cucl2", "fe2o3"]

# ---- 键盘坐标（实测；底部 ⇌ 条收窄后整块键盘下移，两页等高） ----
URL_BAR = (440, 191)
PAGE_CHIP_LETTER = (119, 1458)      # 「化｜符」
PAGE_CHIP_SYMBOL = (119, 1458)      # 「化｜符」（符号页同高，位置一致）
SYM_TAB_X = 176
SYM_TAB_Y = [1593, 1720, 1847, 1974, 2100]
SYM_CATS = ["duliang", "wuzhi", "fanying", "jiaobiao", "qita"]


def adb(*args, quiet=True):
    # 必须显式 utf-8：adb 输出里的中文（控件文本）用默认 GBK 解码会直接抛异常
    r = subprocess.run([ADB] + [str(a) for a in args],
                       capture_output=True, encoding="utf-8", errors="replace")
    if not quiet:
        print("   adb", " ".join(str(a) for a in args))
    return (r.stdout or "").strip()


def tap(x, y, wait=0.7):
    adb("shell", "input", "tap", x, y)
    time.sleep(wait)


def shot(name):
    # 统一等一拍：切换操作会弹 Toast（约 2s），截进图里会被视觉回归判成差异
    time.sleep(1.4)
    adb("shell", "screencap", "-p", "/sdcard/_base.png")
    dst = os.path.join(GOLDEN, name + ".png")
    adb("pull", "/sdcard/_base.png", dst)
    # 键盘截图保留整屏：宿主 App 的上半屏由 visual_diff.KB_IGNORE_TOP 统一裁掉，
    # 在这里裁剪会让基线尺寸与 current 不一致，反而比不了。
    print(f"   saved {name}.png")


def safe(name, fn):
    """单张采集失败不中断整轮。"""
    try:
        fn()
    except Exception as e:
        print(f"   WARN {name}: {e}")


def ime_vis():
    out = adb("shell", "dumpsys", "input_method")
    for line in out.splitlines():
        s = line.strip()
        if s.startswith("mImeWindowVis="):
            return int(s.split("=")[1])
    return -1


def keyboard_visible():
    """从截图判断键盘是否真的画出来了。

    dumpsys 的 mImeWindowVis 在多次启停后会残留旧值，不能作为依据；
    键盘区域有一大片纯白按键，用它当判据最可靠。
    """
    adb("shell", "screencap", "-p", "/sdcard/_probe.png")
    p = os.path.join(GOLDEN, "_probe.png")
    adb("pull", "/sdcard/_probe.png", p)
    return probe_has_keyboard(p)


def probe_has_keyboard(png):
    """键盘的判据 = 白键 + 灰间隙共存。

    只看"大面积白色"会把 about:blank 这种纯白页面误判成键盘，
    所以必须同时要求存在键与键之间的灰色底（#EAEAEA）。
    """
    from PIL import Image
    import numpy as np
    a = np.asarray(Image.open(png).convert("RGB")).astype(int)
    band = a[1450:2250, :, :]
    white = (band >= 248).all(axis=2).mean()
    gray = (np.abs(band - 234).max(axis=2) <= 6).mean()
    return bool(0.25 < white < 0.95 and gray > 0.03)


def focus_url_bar():
    """聚焦 Chrome 地址栏，直到键盘真的显示出来。"""
    for i in range(4):
        tap(*URL_BAR, wait=3)
        if keyboard_visible():
            return True
        print(f"   (第 {i + 1} 次聚焦后键盘仍未显示，重试)")
    raise RuntimeError("地址栏聚焦失败，键盘未弹出")


def dismiss_guide_if_any():
    """若操作引导浮层正在显示，点击遮罩空白处收起（否则它会吃掉后续所有点击）。"""
    adb("shell", "uiautomator", "dump", "/sdcard/_chk.xml")
    out = adb("shell", "cat", "/sdcard/_chk.xml")
    if "前往" in out or "操作引导" in out:
        print("   (检测到操作引导浮层，收起)")
        tap(540, 1800, wait=1.0)


def open_main():
    # CLEAR_TOP：若栈上还压着 关于页，需要把它清掉再回到主界面，
    # 否则 `am start` 只会把整个 task 带到前台，栈顶仍是 AboutActivity。
    adb("shell", "am", "start", "--activity-clear-top", "-n", f"{PKG}/.MainActivity")
    time.sleep(2.5)


def reset_app():
    """彻底重启 App，保证从已知状态开始采集。"""
    adb("shell", "am", "force-stop", PKG)
    time.sleep(1.5)


def open_about():
    tap(*ABOUT_BTN, wait=2)


def prepare_ime():
    # 重置键盘页状态：字母页/符号页常驻在 IME 服务里，上一轮用例停在符号页
    # 会污染这一轮的键盘基线。但 am force-stop 会把包标记为 stopped，
    # 系统此后不会再自动拉起该应用（IME 也一样），必须再 start 一次解除。
    adb("shell", "am", "force-stop", PKG)
    time.sleep(1.2)
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    time.sleep(2.5)
    adb("shell", "settings", "put", "secure", "show_ime_with_hard_keyboard", "1")
    adb("shell", "ime", "enable", IME_ID)
    adb("shell", "ime", "set", IME_ID)
    time.sleep(1.5)


def ui_text():
    adb("shell", "uiautomator", "dump", "/sdcard/_chk.xml")
    return adb("shell", "cat", "/sdcard/_chk.xml")


def read_dark():
    """读当前是否深色模式（靠界面文案判断，不依赖内部状态）。"""
    return "深色模式" in ui_text()


def on_main_page():
    """主界面才有「浅色模式 / 深色模式」这行文案；停在关于页时必须先退回去。"""
    out = ui_text()
    return ("浅色模式" in out) or ("深色模式" in out)


def set_mode(dark, tries=6):
    """把深浅色切到指定状态，**且必须落在「非跟随系统」**。

    深浅色现在是三态循环（浅色 → 深色 → 跟随系统）：点了两次就会停在「跟随系统」，
    此时文案是「浅色模式 ｜ 跟随系统」，视觉回归基线会随系统深浅色漂移，
    几何校验里按精确文案找「浅色模式」也会失败。所以这里要额外判一次「跟随系统」。
    """
    for _ in range(tries):
        out = ui_text()
        if ("浅色模式" not in out) and ("深色模式" not in out):
            adb("shell", "input", "keyevent", "4")      # 还停在关于页 → 退回
            time.sleep(1.3)
            continue
        if ("深色模式" in out) == dark and ("跟随系统" not in out):
            return
        dismiss_guide_if_any()
        tap(*MODE_BTN_COLLAPSED, wait=1.5)
    raise RuntimeError("无法把界面切到 " + ("深色" if dark else "浅色"))


def chips_collapsed():
    """主题芯片行是否已收起（靠芯片文案判断，不依赖内部状态）。"""
    adb("shell", "uiautomator", "dump", "/sdcard/_chk.xml")
    return "AgBr" not in adb("shell", "cat", "/sdcard/_chk.xml")


def ensure_chips_collapsed():
    for _ in range(3):
        if chips_collapsed():
            return
        tap(*THEME_BAR, wait=0.9)
    print("   WARN 主题芯片行未能收起")


def ensure_no_follow():
    """深浅色换成三态循环（浅色 → 深色 → 跟随系统）后，基线必须在「非跟随」状态下采，
    否则系统一改深色基线就整体失效。"""
    for _ in range(4):
        if "跟随系统" not in ui_text():
            return
        tap(*MODE_BTN_COLLAPSED, wait=1.4)
    print("   WARN 未能退出「跟随系统」模式")


def capture_app():
    """主界面 4 主题 x 深浅色 + 关于页 2 张。

    主题选择已改为弹窗（点击「当前主题」条弹出、点芯片后自动关闭），
    所以每换一个主题都要重新点开一次弹窗。
    """
    print("== 采集 App 页面 ==")
    prepare_ime()          # 先让输入法处于「已启用 + 已选中」，引导浮层才不会弹出
    reset_app()
    open_main()
    dismiss_guide_if_any()
    ensure_no_follow()
    for dark in (False, True):
        set_mode(dark)
        for theme in THEMES:
            tap(*THEME_BAR, wait=0.8)               # 弹出主题选择浮层
            # 切主题会弹 Toast「主题：xxx」，约 2s；不等它消失会被视觉回归判成差异
            tap(PICKER_X, PICKER_ROW_Y[theme], wait=2.4)   # 点行后弹窗自动关闭
            shot(f"main_{'dark' if dark else 'light'}_{theme}")
    # 关于页（浅色 / 深色）
    open_main()
    for dark in (False, True):
        set_mode(dark)
        open_about()
        shot(f"about_{'dark' if dark else 'light'}")
        tap(*BACK_BTN, wait=2.0)
    set_mode(False)


def reset_theme():
    """把主题复位为默认 CuCl₂（键盘基线依赖默认主题色 #05BEC1 做校验）。"""
    open_main()
    dismiss_guide_if_any()
    tap(*THEME_BAR, wait=0.8)       # 弹出主题选择浮层
    tap(PICKER_X, PICKER_ROW_Y["cucl2"], wait=1.5)   # CuCl₂，选完自动关闭


def capture_kbd():
    """键盘：字母页 + 符号页 5 分类（默认主题 + 浅色）。"""
    print("== 采集键盘 ==")
    reset_theme()
    prepare_ime()
    adb("shell", "am", "force-stop", "com.android.chrome")
    time.sleep(1)
    adb("shell", "am", "start", "-n", CHROME, "-d", "about:blank")
    time.sleep(5)
    focus_url_bar()          # 内部会在键盘始终不出现时抛异常，避免采到空画面
    shot("kbd_letter")
    tap(*PAGE_CHIP_LETTER, wait=1.0)             # 切符号页
    for i, cat in enumerate(SYM_CATS):
        tap(SYM_TAB_X, SYM_TAB_Y[i], wait=0.9)
        shot(f"kbd_sym_{i}_{cat}")
    tap(*PAGE_CHIP_SYMBOL, wait=1.0)             # 回字母页


def main():
    global GOLDEN
    ap = argparse.ArgumentParser()
    ap.add_argument("--app", action="store_true")
    ap.add_argument("--kbd", action="store_true")
    ap.add_argument("--out", default=GOLDEN, help="输出目录，默认 tests/golden；回归时传 tests/current")
    args = ap.parse_args()
    GOLDEN = os.path.abspath(args.out)
    os.makedirs(GOLDEN, exist_ok=True)
    do_app = args.app or not (args.app or args.kbd)
    do_kbd = args.kbd or not (args.app or args.kbd)
    if do_app:
        capture_app()
    if do_kbd:
        capture_kbd()
    print(f"== 完成，输出目录：{GOLDEN}")


if __name__ == "__main__":
    main()

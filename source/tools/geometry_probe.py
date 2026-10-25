# -*- coding: utf-8 -*-
"""真机 dump 主界面 / 关于页的控件树并跑几何校验（设计还原度）。

用法：
    python tools/geometry_probe.py [输出目录，默认 tests/current]
"""
import io
import os
import subprocess
import sys
import time

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

ADB = r"D:\development\Android\Sdk\platform-tools\adb.exe"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PKG = "com.jhf.jiehuafa"
ABOUT_BTN = (924, 162)
BACK_BTN = (159, 162)


def adb(*args):
    r = subprocess.run([ADB] + [str(a) for a in args],
                       capture_output=True, encoding="utf-8", errors="replace")
    return (r.stdout or "").strip()


def dump_to(path):
    adb("shell", "uiautomator", "dump", "/sdcard/_geo.xml")
    adb("pull", "/sdcard/_geo.xml", path)


def run_check(page, xml):
    script = os.path.join(ROOT, "tools", "geometry_check.py")
    r = subprocess.run([sys.executable, script, "--page", page, xml],
                       capture_output=True, encoding="utf-8", errors="replace")
    print(r.stdout, end="")
    if r.stderr:
        print(r.stderr, end="")
    return r.returncode == 0


def dismiss_guide():
    adb("shell", "uiautomator", "dump", "/sdcard/_chk.xml")
    if "前往" in adb("shell", "cat", "/sdcard/_chk.xml"):
        print("   (收起操作引导浮层)")
        adb("shell", "input", "tap", 540, 1800)
        time.sleep(1)


def main():
    out_dir = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "tests", "current")
    os.makedirs(out_dir, exist_ok=True)
    adb("shell", "am", "force-stop", PKG)
    time.sleep(1.5)
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    time.sleep(3)
    dismiss_guide()

    main_xml = os.path.join(out_dir, "geometry_main.xml")
    dump_to(main_xml)
    ok1 = run_check("main", main_xml)

    adb("shell", "input", "tap", *ABOUT_BTN)
    time.sleep(2)
    about_xml = os.path.join(out_dir, "geometry_about.xml")
    dump_to(about_xml)
    ok2 = run_check("about", about_xml)

    adb("shell", "input", "tap", *BACK_BTN)
    print("==> " + ("ALL OK" if (ok1 and ok2) else "HAS FAIL"))
    sys.exit(0 if (ok1 and ok2) else 1)


if __name__ == "__main__":
    main()

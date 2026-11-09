/*
 * 接化发 · 化学输入法
 * Copyright (C) 2026 哈潚
 *
 * 本程序是自由软件：你可以依据自由软件基金会发布的 GNU 通用公共许可证
 * （第 3 版，或你选择的任何更高版本）的条款重新分发和/或修改它。
 */
package com.jhf.jiehuafa;

/**
 * SheetNav 的单元测试（纯 Java，无 Android 依赖）。
 *
 * 运行：source\test.cmd（编译 SheetNav + 本测试后直接执行 main）。
 * 覆盖：主页/非主页 × 半屏/全屏、图标形态、圆钮与返回键动作、
 *      多层级嵌套返回、栈底边界、快速交替切换。
 */
public final class SheetNavTest {

    private static int checks;

    public static void main(String[] args) {
        homeHalfScreen();
        homeFullScreen();
        docHalfScreen();
        docFromFullScreen();
        docCollapsedFromFullScreen();
        nestedStack();
        rapidToggle();
        popBoundaryAtHome();
        resetAfterClose();
        expandCollapseGlyph();

        System.out.println("ALL PASSED: " + checks + " checks");
    }

    /** 主页 + 半屏：✕，点击关闭面板，返回键关闭面板。 */
    private static void homeHalfScreen() {
        SheetNav n = new SheetNav();
        eq(1, n.depth(), "初始深度");
        check(n.isHome(), "初始停在主页");
        check(!n.closeGlyphIsArrow(), "主页半屏图标为叉号");
        eq(SheetNav.ACT_DISMISS, n.closeButtonAction(), "主页半屏圆钮关闭面板");
        eq(SheetNav.ACT_DISMISS, n.backAction(), "主页半屏返回键关闭面板");
    }

    /** 主页 + 全屏：箭头（收回半屏），但返回键仍直接关闭面板。 */
    private static void homeFullScreen() {
        SheetNav n = new SheetNav();
        n.setExpanded(true);
        check(n.isHome(), "全屏仍在主页");
        check(n.closeGlyphIsArrow(), "主页全屏图标为箭头");
        eq(SheetNav.ACT_COLLAPSE, n.closeButtonAction(), "主页全屏圆钮收回半屏");
        eq(SheetNav.ACT_DISMISS, n.backAction(), "主页全屏返回键关闭面板");
    }

    /** 非主页 + 半屏：箭头，圆钮与返回键都只退一层。 */
    private static void docHalfScreen() {
        SheetNav n = new SheetNav();
        n.push();
        eq(2, n.depth(), "打开全文页后深度");
        check(!n.isHome(), "全文页不在主页");
        check(n.closeGlyphIsArrow(), "全文页半屏图标为箭头");
        eq(SheetNav.ACT_POP, n.closeButtonAction(), "全文页半屏圆钮退页");
        eq(SheetNav.ACT_POP, n.backAction(), "全文页半屏返回键退页");
        check(n.pop(), "退页成功");
        eq(1, n.depth(), "退页后回到主页");
        check(!n.closeGlyphIsArrow(), "回到主页后图标切回叉号");
        eq(SheetNav.ACT_DISMISS, n.closeButtonAction(), "回到主页后圆钮为关闭");
    }

    /** 非主页 + 全屏：箭头，圆钮退页而不是收回半屏。 */
    private static void docFromFullScreen() {
        SheetNav n = new SheetNav();
        n.setExpanded(true);
        n.push();
        check(n.closeGlyphIsArrow(), "全屏文档页图标为箭头");
        eq(SheetNav.ACT_POP, n.closeButtonAction(), "全屏文档页圆钮退页");
        eq(SheetNav.ACT_POP, n.backAction(), "全屏文档页返回键退页");
        n.pop();
        check(n.isExpanded(), "退页不影响全屏状态");
        check(n.closeGlyphIsArrow(), "全屏主页图标仍为箭头");
        eq(SheetNav.ACT_COLLAPSE, n.closeButtonAction(), "全屏主页圆钮收回半屏");
    }

    /** 全屏文档页收回半屏后，因仍在非主页层级，图标必须保持箭头。 */
    private static void docCollapsedFromFullScreen() {
        SheetNav n = new SheetNav();
        n.setExpanded(true);
        n.push();
        n.setExpanded(false);
        check(!n.isExpanded(), "已收回半屏");
        check(n.closeGlyphIsArrow(), "非主页半屏仍为箭头");
        eq(SheetNav.ACT_POP, n.closeButtonAction(), "非主页半屏圆钮退页");
    }

    /** 多层级嵌套：逐层退页直到主页，栈底不可再退。 */
    private static void nestedStack() {
        SheetNav n = new SheetNav();
        n.push();
        n.push();
        n.push();
        eq(4, n.depth(), "三层嵌套后的深度");
        check(n.closeGlyphIsArrow(), "多层嵌套图标为箭头");
        for (int expected = 3; expected >= 1; expected--) {
            eq(SheetNav.ACT_POP, n.backAction(), "返回键逐层退页");
            check(n.pop(), "逐层退页成功");
            eq(expected, n.depth(), "退页后深度");
        }
        check(!n.closeGlyphIsArrow(), "全部退出后图标切回叉号");
        eq(SheetNav.ACT_DISMISS, n.backAction(), "全部退出后返回键关闭面板");
    }

    /** 快速交替 push/pop 与展开/收回：每次状态都应与当前层级、展开态一致。 */
    private static void rapidToggle() {
        SheetNav n = new SheetNav();
        for (int i = 0; i < 20; i++) {
            n.push();
            eq(2, n.depth(), "快速入栈深度");
            check(n.closeGlyphIsArrow(), "快速入栈后图标为箭头");
            eq(SheetNav.ACT_POP, n.closeButtonAction(), "快速入栈后圆钮退页");
            n.setExpanded(i % 2 == 0);
            eq(SheetNav.ACT_POP, n.closeButtonAction(), "展开态不影响非主页圆钮动作");
            check(n.pop(), "快速退页成功");
            eq(1, n.depth(), "快速退页后深度");
            eq(n.isExpanded() ? SheetNav.ACT_COLLAPSE : SheetNav.ACT_DISMISS,
                    n.closeButtonAction(), "回主页后圆钮动作跟随展开态");
            check(n.closeGlyphIsArrow() == n.isExpanded(), "回主页后图标跟随展开态");
            n.setExpanded(false);
        }
        check(!n.closeGlyphIsArrow(), "快速切换结束后回到叉号");
    }

    /** 边界：主页调用 pop 不改变深度并返回 false。 */
    private static void popBoundaryAtHome() {
        SheetNav n = new SheetNav();
        check(!n.pop(), "主页退页应失败");
        eq(1, n.depth(), "主页退页后深度不变");
        n.push();
        check(n.pop(), "一层退页成功");
        check(!n.pop(), "重复退页到底部应失败");
        eq(1, n.depth(), "重复退页后深度仍为 1");
    }

    /** 关闭面板后 reset：层级与展开态都回到初始。 */
    private static void resetAfterClose() {
        SheetNav n = new SheetNav();
        n.setExpanded(true);
        n.push();
        n.push();
        n.reset();
        eq(1, n.depth(), "reset 后深度");
        check(!n.isExpanded(), "reset 后不再是全屏");
        check(!n.closeGlyphIsArrow(), "reset 后图标为叉号");
        eq(SheetNav.ACT_DISMISS, n.closeButtonAction(), "reset 后圆钮关闭面板");
    }

    /** 主页展开 → 收回：图标在箭头与叉号之间往返，且不回退层级。 */
    private static void expandCollapseGlyph() {
        SheetNav n = new SheetNav();
        n.setExpanded(true);
        check(n.closeGlyphIsArrow(), "展开后图标为箭头");
        n.setExpanded(false);
        check(!n.closeGlyphIsArrow(), "收回后图标为叉号");
        eq(1, n.depth(), "展开/收回不改变层级");
    }

    // ------------------------------------------------------------ 断言

    private static void check(boolean cond, String message) {
        checks++;
        if (!cond) fail(message);
    }

    private static void eq(int expected, int actual, String message) {
        checks++;
        if (expected != actual) fail(message + "：期望 " + expected + "，实际 " + actual);
    }

    private static void fail(String message) {
        throw new AssertionError(message);
    }
}

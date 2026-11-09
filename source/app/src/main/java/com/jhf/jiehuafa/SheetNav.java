/*
 * 接化发 · 化学输入法
 * Copyright (C) 2026 哈潚
 *
 * 本程序是自由软件：你可以依据自由软件基金会发布的 GNU 通用公共许可证
 * （第 3 版，或你选择的任何更高版本）的条款重新分发和/或修改它。
 */
package com.jhf.jiehuafa;

/**
 * 底部半屏面板的导航状态机（2026-11-08）。
 *
 * 只负责「页面栈深度 + 是否吸附成全屏」两项状态，以及由它们推导出的
 * 关闭键图标形态与点击动作，不依赖任何 Android 视图，便于单元测试。
 *
 * 页面栈约定：栈底恒为列表主页（depth = 1），每打开一层全文页 depth + 1。
 */
final class SheetNav {

    /** 关闭键 / 返回键动作：面板内返回上一层。 */
    static final int ACT_POP = 0;
    /** 关闭键动作：完整页面收回半屏。 */
    static final int ACT_COLLAPSE = 1;
    /** 关闭键 / 返回键动作：关闭整个面板。 */
    static final int ACT_DISMISS = 2;

    private int depth = 1;
    private boolean expanded;

    int depth() {
        return depth;
    }

    /** 是否停在列表主页（栈底）。 */
    boolean isHome() {
        return depth <= 1;
    }

    boolean isExpanded() {
        return expanded;
    }

    void setExpanded(boolean value) {
        expanded = value;
    }

    /** 打开一层新页面。 */
    void push() {
        depth++;
    }

    /** 返回上一层；已在主页时不动并返回 false。 */
    boolean pop() {
        if (depth <= 1) return false;
        depth--;
        return true;
    }

    /** 回到初始状态（面板关闭后重置）。 */
    void reset() {
        depth = 1;
        expanded = false;
    }

    /**
     * 关闭键应使用左指箭头吗：
     * 非主页层级一律用箭头（点击即返回上一层）；主页只在吸附成全屏时用箭头（点击收回半屏）。
     */
    boolean closeGlyphIsArrow() {
        return depth > 1 || expanded;
    }

    /** 关闭键点击动作。 */
    int closeButtonAction() {
        if (depth > 1) return ACT_POP;
        return expanded ? ACT_COLLAPSE : ACT_DISMISS;
    }

    /** 系统返回键动作：非主页先退页，主页直接关闭整块面板。 */
    int backAction() {
        return depth > 1 ? ACT_POP : ACT_DISMISS;
    }
}

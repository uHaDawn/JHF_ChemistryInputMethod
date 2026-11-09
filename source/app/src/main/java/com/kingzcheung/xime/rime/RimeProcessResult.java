/*
 * 接化发 · 化学输入法
 * Copyright (C) 2026 哈潚
 *
 * 本文件衍生自 CyIME（GPLv3，https://github.com/Cyletix/CyIME）的 RimeEngine.kt，
 * 是最小 Java 版本：只保留一次按键/查询返回的状态快照结构。
 *
 * 本程序是自由软件：你可以依据自由软件基金会发布的 GNU 通用公共许可证
 * （第 3 版，或你选择的任何更高版本）的条款重新分发和/或修改它。
 *
 * 本程序分发时希望它有用，但不提供任何担保，甚至不含适销性或特定用途
 * 适用性的默示担保。详见 GNU 通用公共许可证。
 *
 * 你应已随本程序收到一份 GNU 通用公共许可证副本；如果没有，请见
 * <https://www.gnu.org/licenses/>。
 */
package com.kingzcheung.xime.rime;

/** 一次按键/查询返回的完整状态快照。 */
public final class RimeProcessResult {
    public final boolean processed;
    public final String committedText;
    public final String inputText;
    public final String preeditText;
    public final RimeCandidate[] candidates;
    public final boolean isAsciiMode;
    public final boolean hasNextPage;
    public final boolean hasPrevPage;
    public final String t9PanelState;
    public final String t9SyllableOptions;

    public RimeProcessResult(boolean processed, String committedText, String inputText,
                             String preeditText, RimeCandidate[] candidates,
                             boolean isAsciiMode, boolean hasNextPage, boolean hasPrevPage,
                             String t9PanelState, String t9SyllableOptions) {
        this.processed = processed;
        this.committedText = committedText == null ? "" : committedText;
        this.inputText = inputText == null ? "" : inputText;
        this.preeditText = preeditText == null ? "" : preeditText;
        this.candidates = candidates == null ? new RimeCandidate[0] : candidates;
        this.isAsciiMode = isAsciiMode;
        this.hasNextPage = hasNextPage;
        this.hasPrevPage = hasPrevPage;
        this.t9PanelState = t9PanelState == null ? "" : t9PanelState;
        this.t9SyllableOptions = t9SyllableOptions == null ? "" : t9SyllableOptions;
    }
}

/*
 * 接化发 · 化学输入法
 * Copyright (C) 2026 哈潚
 *
 * 本文件衍生自 CyIME（GPLv3，https://github.com/Cyletix/CyIME）的 RimeEngine.kt，
 * 是最小 Java 版本：只保留 JNI 回调构造 composition 快照所需的结构。
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

/** 当前 composition 快照（输入串 / 预编辑 / 上屏 / 候选 / 翻页 / 英文模式）。 */
public final class RimeComposition {
    public final String input;
    public final String preedit;
    public final String committedText;
    public final RimeCandidate[] candidates;
    public final boolean hasNextPage;
    public final boolean hasPrevPage;
    public final boolean isAsciiMode;

    public RimeComposition(String input, String preedit, String committedText,
                           RimeCandidate[] candidates, boolean hasNextPage,
                           boolean hasPrevPage, boolean isAsciiMode) {
        this.input = input == null ? "" : input;
        this.preedit = preedit == null ? "" : preedit;
        this.committedText = committedText == null ? "" : committedText;
        this.candidates = candidates == null ? new RimeCandidate[0] : candidates;
        this.hasNextPage = hasNextPage;
        this.hasPrevPage = hasPrevPage;
        this.isAsciiMode = isAsciiMode;
    }
}

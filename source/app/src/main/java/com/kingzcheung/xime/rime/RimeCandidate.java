/*
 * 接化发 · 化学输入法
 * Copyright (C) 2026 哈潚
 *
 * 本文件衍生自 CyIME（GPLv3，https://github.com/Cyletix/CyIME）的 RimeEngine.kt，
 * 是最小 Java 版本：只保留 JNI 回调构造候选对象所需的结构，包名与 .so 中导出的
 * 符号（com_kingzcheung_xime_rime_*）对齐，不能改包名，否则 native 侧 FindClass 会失败。
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

/** 单个候选：文本 + 注释（拼音/编码）+ 是否用户词 + 纠错标记。 */
public final class RimeCandidate {
    public final String text;
    public final String comment;
    public final boolean learned;
    public final String correction;

    public RimeCandidate(String text, String comment, boolean learned, String correction) {
        this.text = text == null ? "" : text;
        this.comment = comment == null ? "" : comment;
        this.learned = learned;
        this.correction = correction == null ? "" : correction;
    }
}

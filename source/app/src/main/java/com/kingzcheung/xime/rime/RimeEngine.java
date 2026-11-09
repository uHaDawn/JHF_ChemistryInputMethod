/*
 * 接化发 · 化学输入法
 * Copyright (C) 2026 哈潚
 *
 * 本文件衍生自 CyIME（GPLv3，https://github.com/Cyletix/CyIME）的 RimeEngine.kt，
 * 是最小 Java 移植版：只声明接化发实际调用的方法，签名与 .so 内导出符号一一对应。
 * 包名/类名固定为 com.kingzcheung.xime.rime.RimeEngine，不能改（native 侧写死）。
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

import android.util.Log;

/**
 * Rime 引擎单例封装。
 *
 * 所有 native 调用都在 {@link #RIME_LOCK} 内串行执行：librime 的 session/context
 * 不是线程安全的，而部署（全量编译词库）会长时间占用引擎，因此：
 * 管理型操作（初始化/部署/切方案）阻塞等待锁，输入型操作同样串行（简单优先）。
 */
public final class RimeEngine {

    private static final String TAG = "JhfRime";

    /** lua/octagram/predict/t9 等模块在 .so 内静态注册，载入即生效。 */
    static {
        System.loadLibrary("rime_jni");
    }

    private static volatile RimeEngine instance;

    private static final Object INSTANCE_LOCK = new Object();
    private static final Object RIME_LOCK = new Object();

    private volatile boolean initialized;

    private RimeEngine() {
    }

    /** 载入失败（无 arm64 .so / ABI 不支持）时抛出 UnsatisfiedLinkError，由调用方回退。 */
    public static RimeEngine get() {
        RimeEngine e = instance;
        if (e != null) return e;
        synchronized (INSTANCE_LOCK) {
            if (instance == null) instance = new RimeEngine();
            return instance;
        }
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void initialize(String userDataDir, String sharedDataDir) {
        synchronized (RIME_LOCK) {
            if (initialized) return;
            nativeInstallSignalHandler(userDataDir);
            nativeInitialize(userDataDir, sharedDataDir);
            initialized = true;
        }
    }

    /** 全量部署（编译词库/方案），阻塞直到维护线程结束；完成后自动建会话。 */
    public boolean deploy() {
        synchronized (RIME_LOCK) {
            if (!initialized) return false;
            return nativeDeploy();
        }
    }

    public boolean hasSession() {
        synchronized (RIME_LOCK) {
            return initialized && nativeHasSession();
        }
    }

    public boolean createSession() {
        synchronized (RIME_LOCK) {
            return initialized && nativeCreateSession();
        }
    }

    public String getCurrentSchema() {
        synchronized (RIME_LOCK) {
            return initialized ? nativeGetCurrentSchema() : "";
        }
    }

    /** 切换输入方案（jhf_pinyin 全拼 / jhf_flypy 小鹤双拼）。 */
    public boolean switchSchema(String schemaId) {
        synchronized (RIME_LOCK) {
            return initialized && nativeSwitchSchema(schemaId);
        }
    }

    public RimeProcessResult processKey(int keycode, int mask) {
        synchronized (RIME_LOCK) {
            if (!initialized) return null;
            if (!nativeHasSession() && !nativeCreateSession()) return null;
            return nativeProcessKeyAndGetResult(keycode, mask);
        }
    }

    /**
     * 九键按键（2026-11-02）：交给 t9_processor 处理数字/分隔/退格，再执行异步 flush，
     * 最后重新读取整份快照。三步缺一不可——processKey 只把待发送内容标记为 pending，
     * 不 flush 就读不到新候选。
     */
    public RimeProcessResult processT9Key(int keycode) {
        synchronized (RIME_LOCK) {
            if (!initialized) return null;
            if (!nativeHasSession() && !nativeCreateSession()) return null;
            nativeProcessKey(keycode, 0);
            nativeT9FlushRimeInput();
            return nativeGetProcessResult(true);
        }
    }

    /** 九键左栏首音节选项（拼音 + 数字长度）→ 选中该拼音，flush 后返回新快照。 */
    public RimeProcessResult t9SelectPinyin(String pinyin, int digitLength) {
        synchronized (RIME_LOCK) {
            if (!initialized) return null;
            if (!nativeHasSession() && !nativeCreateSession()) return null;
            boolean processed = nativeT9SelectPinyinDirect(pinyin, digitLength);
            nativeT9FlushRimeInput();
            return nativeGetProcessResult(processed);
        }
    }

    /**
     * 九键候选上屏：按候选注释拼音 + 文本 + 字数精确消费数字串（不按索引，翻页后不会错位）。
     * 返回 null 表示 T9 处理器不可用，调用方回退普通 selectCandidate + commit。
     */
    public RimeProcessResult t9SelectCandidate(String pinyin, String text, int textLength) {
        synchronized (RIME_LOCK) {
            if (!initialized) return null;
            if (!nativeHasSession() && !nativeCreateSession()) return null;
            boolean full = nativeT9SelectCandidate(pinyin, text, textLength);
            nativeT9FlushRimeInput();
            RimeProcessResult r = nativeGetProcessResult(true);
            return full ? r : null;
        }
    }

    /** 清空九键状态：mode=0 仅清组合区，mode=1 连本地数字缓冲一起重置。 */
    public void t9ClearComposition(int mode) {
        synchronized (RIME_LOCK) {
            if (initialized && nativeHasSession()) nativeT9ClearComposition(mode);
        }
    }

    public RimeComposition getComposition() {
        synchronized (RIME_LOCK) {
            if (!initialized || !nativeHasSession()) return null;
            return nativeGetComposition();
        }
    }

    public boolean selectCandidate(int index) {
        synchronized (RIME_LOCK) {
            return initialized && nativeHasSession() && nativeSelectCandidate(index);
        }
    }

    public String commit() {
        synchronized (RIME_LOCK) {
            if (!initialized || !nativeHasSession()) return "";
            String text = nativeCommit();
            return text == null ? "" : text;
        }
    }

    public void clearComposition() {
        synchronized (RIME_LOCK) {
            if (initialized && nativeHasSession()) nativeClearComposition();
        }
    }

    public boolean pageDown() {
        synchronized (RIME_LOCK) {
            return initialized && nativeHasSession() && nativePageDown();
        }
    }

    public boolean pageUp() {
        synchronized (RIME_LOCK) {
            return initialized && nativeHasSession() && nativePageUp();
        }
    }

    public String getInput() {
        synchronized (RIME_LOCK) {
            if (!initialized || !nativeHasSession()) return "";
            String s = nativeGetInput();
            return s == null ? "" : s;
        }
    }

    public boolean isAsciiMode() {
        synchronized (RIME_LOCK) {
            return initialized && nativeHasSession() && nativeIsAsciiMode();
        }
    }

    public void setOption(String option, boolean value) {
        synchronized (RIME_LOCK) {
            if (initialized && nativeHasSession()) nativeSetOption(option, value);
        }
    }

    public boolean getOption(String option) {
        synchronized (RIME_LOCK) {
            return initialized && nativeHasSession() && nativeGetOption(option);
        }
    }

    public String[] getAvailableSchemas() {
        synchronized (RIME_LOCK) {
            if (!initialized) return new String[0];
            String[] list = nativeGetAvailableSchemas();
            return list == null ? new String[0] : list;
        }
    }

    public void destroy() {
        synchronized (RIME_LOCK) {
            if (!initialized) return;
            nativeDestroy();
            initialized = false;
        }
    }

    public static void log(String msg) {
        Log.i(TAG, msg);
    }

    // ---- JNI（签名必须与 librime_jni.so 导出符号一致） ----

    private native void nativeInitialize(String userDataDir, String sharedDataDir);

    private native void nativeInstallSignalHandler(String userDataDir);

    private native boolean nativeCreateSession();

    private native boolean nativeHasSession();

    private native String nativeGetCurrentSchema();

    private native RimeProcessResult nativeProcessKeyAndGetResult(int keycode, int mask);

    private native boolean nativeProcessKey(int keycode, int mask);

    private native RimeProcessResult nativeGetProcessResult(boolean processed);

    private native void nativeT9FlushRimeInput();

    private native void nativeT9ClearComposition(int mode);

    private native boolean nativeT9SelectPinyinDirect(String pinyin, int digitLength);

    private native boolean nativeT9SelectCandidate(String pinyin, String text, int textLength);

    private native RimeComposition nativeGetComposition();

    private native boolean nativeSelectCandidate(int index);

    private native String nativeCommit();

    private native void nativeClearComposition();

    private native boolean nativePageDown();

    private native boolean nativePageUp();

    private native String nativeGetInput();

    private native boolean nativeIsAsciiMode();

    private native void nativeSetOption(String option, boolean value);

    private native boolean nativeGetOption(String option);

    private native boolean nativeSwitchSchema(String schemaId);

    private native boolean nativeDeploy();

    private native String[] nativeGetAvailableSchemas();

    private native void nativeDestroy();
}

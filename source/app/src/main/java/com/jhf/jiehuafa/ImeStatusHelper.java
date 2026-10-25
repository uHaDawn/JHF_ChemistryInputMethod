package com.jhf.jiehuafa;

import android.content.Context;
import android.provider.Settings;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import java.util.List;

/**
 * 输入法启用 / 选中状态检测（操作引导用）。
 * 两个状态互相独立：已启用 ≠ 已选中。
 */
public final class ImeStatusHelper {

    /** 完整 IME ID，必须与 AndroidManifest.xml 中 service 的声明一致。 */
    public static final String IME_ID = "com.jhf.jiehuafa/.ime.JhfImeService";

    private ImeStatusHelper() {
    }

    /**
     * 用户是否已在系统设置中启用本输入法。
     * 两条通道任一命中即算启用：列表接口在部分 ROM / 刚安装后可能返回不全，
     * 单看它会误判成「未启用」而反复弹出引导，所以设置读取必须并行兜底。
     */
    public static boolean isEnabled(Context ctx) {
        InputMethodManager imm = imm(ctx);
        if (imm != null) {
            try {
                List<InputMethodInfo> list = imm.getEnabledInputMethodList();
                if (list != null) {
                    for (InputMethodInfo info : list) {
                        if (IME_ID.equals(info.getId())) return true;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            String enabled = Settings.Secure.getString(ctx.getContentResolver(),
                    Settings.Secure.ENABLED_INPUT_METHODS);
            return enabled != null && enabled.contains(IME_ID);
        } catch (Throwable e) {
            return false;
        }
    }

    /** 本输入法是否为当前正在使用的输入法。 */
    public static boolean isCurrent(Context ctx) {
        try {
            String cur = Settings.Secure.getString(ctx.getContentResolver(),
                    Settings.Secure.DEFAULT_INPUT_METHOD);
            return IME_ID.equals(cur);
        } catch (Throwable e) {
            return false;
        }
    }

    /** 启用 + 选中 都已完成。 */
    public static boolean isReady(Context ctx) {
        return isEnabled(ctx) && isCurrent(ctx);
    }

    private static InputMethodManager imm(Context ctx) {
        return (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
    }
}

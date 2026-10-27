/*
 * 接化发 · 化学输入法
 * Copyright (C) 2026 哈潚
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
package com.jhf.jiehuafa;

import android.app.Application;
import android.os.Process;

/**
 * 全局崩溃捕获：任何未捕获异常 → 跳转 ErrorActivity（红底白字堆栈）。
 * 用于桌面点击闪退且无 logcat 时收集根因。
 */
public class JhfApp extends Application {

    private static boolean crashing = false;

    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler def = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            if (crashing) { // ErrorActivity 自身再崩则交还系统，防止死循环
                if (def != null) def.uncaughtException(t, e);
                return;
            }
            crashing = true;
            ErrorActivity.launch(this, e);
            // startActivity 已通过 binder 发往 system_server，此处结束崩溃进程，
            // ErrorActivity 将在系统侧拉起新进程实例并显示堆栈。
            Process.killProcess(Process.myPid());
            System.exit(10);
        });
    }
}

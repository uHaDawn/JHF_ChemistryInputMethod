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
import android.content.SharedPreferences;
import android.content.res.Configuration;
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
        // 进程启动时按已保存的深浅色同步桌面图标（2026-11-07）：
        // 覆盖重装/升级后别名状态回到清单默认值的场景；状态已正确时不会重复刷新桌面。
        try {
            SharedPreferences p = getSharedPreferences("jhf_settings", MODE_PRIVATE);
            boolean follow = p.getBoolean("follow", false);
            boolean dark = follow
                    ? (getResources().getConfiguration().uiMode
                        & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                    : p.getBoolean("dark", false);
            AppIcon.syncLauncher(this, dark);
        } catch (Throwable ignored) {
            // 图标同步失败不影响应用启动
        }
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

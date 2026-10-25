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

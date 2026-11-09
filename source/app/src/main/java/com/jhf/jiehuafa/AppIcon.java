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

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

/**
 * 应用图标随深浅色主题切换（2026-11-07）。
 *
 * 界面内 logo：深色用 drawable-nodpi/ic_app_logo_dark.png（图标-1.png），浅色用
 * drawable-nodpi/ic_app_logo.png（原有默认图标）。
 * 桌面图标：Android 不允许运行时直接改 android:icon，改为两个 activity-alias
 * （LightAlias / DarkAlias）互斥启用，由 PackageManager 切换启动入口的图标；
 * 两个别名都指向 MainActivity，切换时用 DONT_KILL_APP 避免进程被杀。
 *
 * 异常兜底：深色图标资源缺失/无法解码时，一律回退浅色默认图标，绝不抛出异常。
 *
 * 界面内图标的圆角为 G2 连续曲率（2026-11-07）：图标 PNG 的 alpha 遮罩由
 * {@link SmoothCorners} 的算法离线生成，运行时描边同样用 {@link SmoothDrawable}，
 * 保证描边与图标轮廓完全贴合；描边色随深浅色变化。
 */
public final class AppIcon {

    /** 浅色（默认）启动入口（AndroidManifest 中 enabled=true）。 */
    public static final String ALIAS_LIGHT = "com.jhf.jiehuafa.LightAlias";
    /** 深色启动入口（AndroidManifest 中 enabled=false，深色模式时启用）。 */
    public static final String ALIAS_DARK = "com.jhf.jiehuafa.DarkAlias";

    /** 深色桌面图标是否可用（只探测一次，之后缓存）。 */
    private static Boolean darkLauncherUsable;

    private AppIcon() {
    }

    /**
     * 界面内显示的应用图标：深色取 ic_app_logo_dark，取不到就回退默认图标。
     */
    public static Drawable logo(Context c, boolean dark) {
        if (dark) {
            try {
                Drawable d = c.getDrawable(R.drawable.ic_app_logo_dark);
                if (d != null) return d;
            } catch (Throwable ignored) {
                // 资源缺失或解码失败：走下面的默认图标
            }
        }
        return c.getDrawable(R.drawable.ic_app_logo);
    }

    /**
     * 界面内图标的细描边（2026-11-07）：1dp 线绘制在图标之上（前景层），
     * 圆角用 {@link SmoothDrawable} 的 G2 连续曲率，半径取图标本身的 26%
     * （图标 400px 半径 104，ImageView 等比铺满），与图标轮廓一致。
     * 描边色随深浅色：浅色 #E7E7E7、深色 #242424。
     * 不改变 ImageView 尺寸、不产生内边距。
     */
    public static void stroke(ImageView v, int sizeDp, boolean dark) {
        if (v == null) return;
        try {
            float density = v.getResources().getDisplayMetrics().density;
            SmoothDrawable g = new SmoothDrawable();
            g.setColor(Color.TRANSPARENT);
            g.setCornerRadius(sizeDp * density * 0.26f);
            g.setStroke(Math.max(1, Math.round(density)),
                    dark ? 0xFF242424 : 0xFFE7E7E7);
            v.setForeground(g);
        } catch (Throwable ignored) {
            // 描边只是视觉装饰，失败时保持无描边即可
        }
    }

    /**
     * 让桌面图标与当前深浅色一致；状态已正确时不重复设置，避免多余的桌面刷新。
     */
    public static synchronized void syncLauncher(Context c, boolean dark) {
        try {
            boolean wantDark = dark && darkLauncherIconUsable(c);
            ComponentName light = new ComponentName(c, ALIAS_LIGHT);
            ComponentName darkAlias = new ComponentName(c, ALIAS_DARK);
            PackageManager pm = c.getPackageManager();
            int lightState = pm.getComponentEnabledSetting(light);
            int darkState = pm.getComponentEnabledSetting(darkAlias);
            // 浅色别名在清单里是 enabled=true，所以 DEFAULT 也算启用
            boolean lightOn = lightState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    || lightState == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
            boolean darkOn = darkState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;

            if (wantDark) {
                if (!darkOn) {
                    // 先点亮新入口再关旧入口，中间始终有图标可点
                    pm.setComponentEnabledSetting(darkAlias,
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                            PackageManager.DONT_KILL_APP);
                    if (lightOn) {
                        pm.setComponentEnabledSetting(light,
                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                PackageManager.DONT_KILL_APP);
                    }
                }
            } else {
                if (!lightOn) {
                    pm.setComponentEnabledSetting(light,
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                            PackageManager.DONT_KILL_APP);
                    if (darkOn) {
                        pm.setComponentEnabledSetting(darkAlias,
                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                PackageManager.DONT_KILL_APP);
                    }
                }
            }
        } catch (Throwable ignored) {
            // 切图标失败不影响应用本身（例如 ROM 限制），界面继续用当前主题
        }
    }

    /** 深色图标资源是否真的能读出来；读不出就当作不可用，回退默认图标。 */
    private static boolean darkLauncherIconUsable(Context c) {
        if (darkLauncherUsable != null) return darkLauncherUsable;
        boolean usable = false;
        try {
            Drawable d = c.getDrawable(R.mipmap.ic_launcher_dark);
            usable = d != null;
        } catch (Throwable ignored) {
            usable = false;
        }
        darkLauncherUsable = usable;
        return usable;
    }
}

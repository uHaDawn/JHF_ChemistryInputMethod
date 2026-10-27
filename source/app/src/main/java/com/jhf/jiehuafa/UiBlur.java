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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import android.widget.ImageView;

/**
 * 浮层背景模糊。
 *
 * 把目标 View 的当前内容截成位图，压暗之后交给 {@link ImageView}，并在 API 31+ 上挂
 * {@link RenderEffect} 做高斯模糊（旧系统只保留压暗，效果退化为普通遮罩）。
 */
final class UiBlur {

    private UiBlur() {
    }

    /**
     * @param source    要作为背景的视图（一般是主内容 ScrollView，必须已完成布局）
     * @param dimColor  位图上叠加的压暗色（ARGB）
     * @param fallback  截图失败时的纯色兜底
     */
    static void apply(ImageView iv, View source, int dimColor, int fallback) {
        iv.setScaleType(ImageView.ScaleType.FIT_XY);
        int w = source == null ? 0 : source.getWidth();
        int h = source == null ? 0 : source.getHeight();
        if (w <= 0 || h <= 0) {
            iv.setBackgroundColor(fallback);
            return;
        }
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        source.draw(c);
        c.drawColor(dimColor);
        iv.setImageBitmap(bmp);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            iv.setRenderEffect(RenderEffect.createBlurEffect(26f, 26f, Shader.TileMode.CLAMP));
        }
    }
}

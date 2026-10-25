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

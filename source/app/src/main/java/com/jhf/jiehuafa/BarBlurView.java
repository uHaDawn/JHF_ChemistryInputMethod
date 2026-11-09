/*
 * 接化发 · 化学输入法
 * Copyright (C) 2026 哈潚
 *
 * 本程序是自由软件：你可以依据自由软件基金会发布的 GNU 通用公共许可证
 * （第 3 版，或你选择的任何更高版本）的条款重新分发和/或修改它。
 */
package com.jhf.jiehuafa;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;

/**
 * 吸顶栏的「渐变毛玻璃」层（2026-11-01，Figma 212-72 / 212-160 的 backdrop-filter: blur(9px)）。
 *
 * 旧做法是把内容层层缩小再放大（1/8、1/16、1/26、1/38），放大后是一块块色块，观感很「像素」。
 * 现在改为 GPU 真高斯：
 *   1. 内容只在首次布局后按 1/2 拍一张，绘制时放大 2 倍；
 *   2. 整层交给 View 的 RenderEffect(createBlurEffect) 做一次真正的高斯模糊；
 *   3. 同一层内再叠一条 alpha 渐变（顶部实心 → 底部透明），
 *      得到「上糊下清」的渐变模糊，过渡连续，不会在某条线上突然切换。
 *
 * 滚动时只改绘制矩阵，不重新拍图。API < 31 没有 RenderEffect，退化为 1/8 缩略图放大（兜底）。
 */
final class BarBlurView extends View {

    /** 快照缩小倍数：1/2 拍图、绘制时放大 2 倍（配合 GPU 高斯，看不出放大痕迹）。 */
    static final float K = 2f;

    /** 高斯半径（dp）：对齐设计稿 backdrop-filter: blur(9px)（Android 的 radius 比 CSS sigma 小，取下限补偿）。 */
    private static final float BLUR_DP = 15f;

    /** 模糊保持实心的位置（栏高比例），之后线性过渡到底部完全清晰。 */
    private static final float SOLID = 0.32f;

    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint mask = new Paint();
    private final Matrix matrix = new Matrix();
    private Bitmap src;
    private float scale = K;
    private float contentW;
    private int originPx;
    private int scrollPx;
    private LinearGradient fade;

    BarBlurView(Context c) {
        super(c);
        mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        setWillNotDraw(false);
        if (Build.VERSION.SDK_INT >= 31) {
            float r = BLUR_DP * getResources().getDisplayMetrics().density;
            setRenderEffect(RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP));
        }
    }

    /** 模糊源：内容按 scale 倍缩小后的位图；origin 为内容 0 点在栏坐标里的偏移。 */
    void setSource(Bitmap base, float scale, int origin) {
        if (base == null) return;
        if (Build.VERSION.SDK_INT < 31) {
            // 没有 GPU 高斯时再缩一次，靠双线性放大把色块糊开（仅兜底）。
            float k = 4f;
            src = Bitmap.createScaledBitmap(base,
                    Math.max(1, (int) (base.getWidth() / k)),
                    Math.max(1, (int) (base.getHeight() / k)), true);
            this.scale = scale * k;
        } else {
            src = base;
            this.scale = scale;
        }
        contentW = src.getWidth() * this.scale;
        originPx = origin;
        invalidate();
    }

    void setScroll(int scrollY) {
        if (scrollPx != scrollY) {
            scrollPx = scrollY;
            invalidate();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        fade = new LinearGradient(0f, 0f, 0f, (float) h,
                new int[]{0xFFFFFFFF, 0xFFFFFFFF, 0x00000000},
                new float[]{0f, SOLID, 1f}, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (src == null || src.isRecycled()
                || getWidth() <= 0 || getHeight() <= 0 || contentW <= 0f || fade == null) return;
        int w = getWidth(), h = getHeight();
        float s = contentW / src.getWidth();
        // 内容与 alpha 渐变必须在同一层里，DST_IN 才能把渐变乘到内容上
        // （兄弟 View 各自是独立的 RenderNode，跨 View 的 xfermode 不生效）。
        int layer = c.saveLayer(0f, 0f, w, h, null);
        matrix.setScale(s, s);
        matrix.postTranslate(0f, originPx - scrollPx);
        c.drawBitmap(src, matrix, paint);
        mask.setShader(fade);
        c.drawRect(0f, 0f, w, h, mask);
        c.restoreToCount(layer);
    }
}

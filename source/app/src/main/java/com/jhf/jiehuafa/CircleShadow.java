/*
 * 接化发 · 化学输入法
 * Copyright (C) 2026 哈潚
 *
 * 本程序是自由软件：你可以依据自由软件基金会发布的 GNU 通用公共许可证
 * （第 3 版，或你选择的任何更高版本）的条款重新分发和/或修改它。
 */
package com.jhf.jiehuafa;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/**
 * 圆形柔和投影（2026-11-08，按客户 SVG 212-116）。
 *
 * 阴影规格：白色圆（r=22）四周均匀扩散，黑色 19% 不透明度、高斯模糊 stdDeviation 23.35、
 * 无偏移。系统 elevation 阴影有方向性、且不跟随父布局 alpha，因此这里改为自绘：
 * 用径向渐变从圆心 19% 黑渐变到圆边 10% 再淡出到 0，整个 View 作为阴影层垫在圆钮下面。
 *
 * 因为阴影是普通子 View 绘制，会跟随父容器 alpha / visibility 一起渐显渐隐，
 * 吸顶栏淡入时不会出现「按钮阴影突然满强度出现」的硬切。
 */
final class CircleShadow extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float circleR;
    private final float strength;
    private Shader shader;

    /** 按 SVG 原始强度（19%）绘制。 */
    CircleShadow(Context c, float circleRadiusDp, float blurDp) {
        this(c, circleRadiusDp, blurDp, 1f);
    }

    /**
     * @param circleRadiusDp 圆钮半径（dp），如 48dp 圆钮传 24。
     * @param blurDp         高斯模糊 stdDeviation（dp），SVG 为 23.35。
     * @param strength       阴影强度系数（1 = SVG 原值，越小越淡），用于压暗很重的场景。
     */
    CircleShadow(Context c, float circleRadiusDp, float blurDp, float strength) {
        super(c);
        float d = c.getResources().getDisplayMetrics().density;
        circleR = circleRadiusDp * d;
        this.strength = Math.max(0f, Math.min(1f, strength));
        // 盒子边长 = 2 × (圆半径 + 模糊半径)，模糊在这个范围内衰减到完全透明。
        int side = (int) ((circleRadiusDp + blurDp) * 2f * d + 0.5f);
        setLayoutParams(new android.view.ViewGroup.LayoutParams(side, side));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float half = Math.min(w, h) / 2f;
        if (half <= 0f) return;
        int shadow = ((int) (0x30 * strength)) << 24;
        int shadowEdge = ((int) (0x1A * strength)) << 24;
        shader = new RadialGradient(w / 2f, h / 2f, half,
                new int[]{shadow, shadowEdge, 0x00000000},
                new float[]{0f, Math.min(0.98f, circleR / half), 1f},
                Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (shader == null) return;
        paint.setShader(shader);
        canvas.drawCircle(getWidth() / 2f, getHeight() / 2f,
                Math.min(getWidth(), getHeight()) / 2f, paint);
    }
}

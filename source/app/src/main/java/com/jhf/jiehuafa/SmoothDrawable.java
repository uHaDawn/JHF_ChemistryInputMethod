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

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * 用 G2 连续圆角绘制的背景 Drawable（2026-11-03）。
 *
 * 替代原先的 {@code GradientDrawable}：接口保留 setColor / setStroke /
 * setCornerRadius / setCornerRadii / setGradient，绘制时统一走
 * {@link SmoothCorners}（移植自 Capsule，Kyant0，Apache License 2.0）。
 *
 * 描边与 GradientDrawable 一致画在边界内侧：路径按 strokeWidth/2 内缩，
 * 再用 strokeWidth 居中描边，外缘正好落在 View 边界上。
 * 圆角为 0 时仍然画普通矩形（不额外开销）。
 */
public class SmoothDrawable extends Drawable {

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    /** 上左、上右、下右、下左（像素）。 */
    private final float[] radii = new float[4];

    private int color = Color.TRANSPARENT;
    private int gradientFrom = 0;
    private int gradientTo = 0;
    private boolean gradient = false;
    private LinearGradient shader;
    private float shaderLeft, shaderRight;

    private int strokeWidth = 0;
    private int strokeColor = Color.TRANSPARENT;
    private int alpha = 255;

    public SmoothDrawable() {
    }

    public SmoothDrawable(int color, float radius) {
        setColor(color);
        setCornerRadius(radius);
    }

    public SmoothDrawable setColor(int color) {
        this.color = color;
        this.gradient = false;
        invalidateSelf();
        return this;
    }

    /** 横向线性渐变（相当于 GradientDrawable.Orientation.LEFT_RIGHT）。 */
    public SmoothDrawable setGradient(int fromColor, int toColor) {
        this.gradientFrom = fromColor;
        this.gradientTo = toColor;
        this.gradient = true;
        this.shader = null;
        invalidateSelf();
        return this;
    }

    public SmoothDrawable setStroke(int width, int color) {
        this.strokeWidth = width;
        this.strokeColor = color;
        invalidateSelf();
        return this;
    }

    public SmoothDrawable setCornerRadius(float radius) {
        for (int i = 0; i < 4; i++) radii[i] = radius;
        invalidateSelf();
        return this;
    }

    /** 支持 Android 的 8 值格式（tl,tl,tr,tr,br,br,bl,bl）或简化的 4 值格式。 */
    public SmoothDrawable setCornerRadii(float[] r) {
        if (r == null) {
            for (int i = 0; i < 4; i++) radii[i] = 0f;
        } else if (r.length >= 8) {
            radii[0] = r[0];
            radii[1] = r[2];
            radii[2] = r[4];
            radii[3] = r[6];
        } else {
            for (int i = 0; i < 4; i++) radii[i] = i < r.length ? r[i] : 0f;
        }
        invalidateSelf();
        return this;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        shader = null;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        if (color == 0 && !gradient && strokeWidth <= 0) return;

        float inset = strokeWidth > 0 ? strokeWidth * 0.5f : 0f;
        rect.set(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset);
        SmoothCorners.build(path, rect, radii[0], radii[1], radii[2], radii[3]);

        if (color != 0 || gradient) {
            if (gradient) {
                if (shader == null || shaderLeft != rect.left || shaderRight != rect.right) {
                    shader = new LinearGradient(rect.left, 0f, rect.right, 0f,
                            gradientFrom, gradientTo, Shader.TileMode.CLAMP);
                    shaderLeft = rect.left;
                    shaderRight = rect.right;
                }
                fillPaint.setShader(shader);
                fillPaint.setAlpha(alpha);
            } else {
                fillPaint.setShader(null);
                fillPaint.setColor(color);
                fillPaint.setAlpha(fillPaint.getAlpha() * alpha / 255);
            }
            canvas.drawPath(path, fillPaint);
        }

        if (strokeWidth > 0) {
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(strokeWidth);
            strokePaint.setColor(strokeColor);
            strokePaint.setAlpha(strokePaint.getAlpha() * alpha / 255);
            canvas.drawPath(path, strokePaint);
        }
    }

    @Override
    public void getOutline(Outline outline) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        rect.set(b);
        SmoothCorners.build(path, rect, radii[0], radii[1], radii[2], radii[3]);
        try {
            outline.setConvexPath(path);
        } catch (Throwable t) {
            // 极端尺寸下路径可能被判非凸，退回普通圆角保证阴影不丢
            float r = Math.max(radii[0], Math.max(radii[1], Math.max(radii[2], radii[3])));
            outline.setRoundRect(b, r);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        fillPaint.setColorFilter(colorFilter);
        strokePaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}

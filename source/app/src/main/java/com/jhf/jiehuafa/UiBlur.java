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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.ImageView;

/**
 * 浮层背景模糊。
 *
 * 把目标 View 的当前内容截成位图，压暗之后交给 {@link ImageView}，并在 API 31+ 上挂
 * {@link RenderEffect} 做高斯模糊（旧系统只保留压暗，效果退化为普通遮罩）。
 *
 * 2026-10-29 新增显隐过渡：模糊半径从 0 平滑升到 {@link #TARGET_BLUR}（非线性，缓出曲线），
 * 与遮罩透明度、气泡面板的位移/缩放同周期播放，避免浮层"啪"地一下出现或消失。
 * 2026-10-31 新增 cancel()：取消未播完的模糊动画并清掉接管标记，供浮层在收起过程中重新显示时使用。
 * 2026-11-05 修复 cancel() 在 Android 12 以下失效：旧实现只取消 ValueAnimator，而低版本走的是
 *              postDelayed 收尾，回调没被移除——收起动画没播完就重新显示时，旧回调仍会把刚显示的
 *              浮层置为 GONE。现在动画与延后回调统一装进接管对象 Fx，新动画启动/取消时一并撤销。
 */
final class UiBlur {

    /** 过渡时长（200~350ms 区间取中值）。 */
    static final long DUR = 280;
    /** 目标模糊半径（px）。 */
    static final float TARGET_BLUR = 26f;
    /** 近似 iOS 的缓出曲线：起步快、收尾慢。 */
    static final Interpolator EASE_OUT = new PathInterpolator(0.16f, 0.84f, 0.22f, 1f);

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
            iv.setRenderEffect(RenderEffect.createBlurEffect(TARGET_BLUR, TARGET_BLUR, Shader.TileMode.CLAMP));
        }
    }

    /** 模糊半径 0 → 目标值（"从无模糊到目标模糊"的非线性渐变）。 */
    static void blurIn(ImageView iv) {
        blur(iv, 0f, TARGET_BLUR, null);
    }

    /** 模糊半径目标值 → 0，结束后回调（用于把浮层收起）。 */
    static void blurOut(ImageView iv, Runnable onEnd) {
        blur(iv, TARGET_BLUR, 0f, onEnd);
    }

    /**
     * 取消未播完的模糊动画（2026-10-31）。
     * 先清接管标记再撤销，动画 cancel 同步触发的 onAnimationEnd 与低版本的延后回调
     * 都会被守卫挡掉，不会执行旧收尾；供浮层在收起过程中重新显示时调用，
     * 避免旧 blurOut 回调把浮层置为 GONE（2026-11-05 补齐 Android 12 以下的延后回调）。
     */
    static void cancel(ImageView iv) {
        stop(iv);
    }

    /** 撤销当前接管：清标记 → 取消动画 / 移除延后回调 → 摘掉模糊特效。 */
    private static void stop(ImageView iv) {
        Object prev = iv.getTag();
        iv.setTag(null);
        if (prev instanceof Fx) {
            ((Fx) prev).cancel(iv);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) iv.setRenderEffect(null);
    }

    private static void blur(ImageView iv, float from, float to, final Runnable onEnd) {
        // 同一张遮罩上只允许一个过渡在跑：新过渡启动前先撤销旧的（含低版本的延后收尾）
        stop(iv);
        final Fx fx = new Fx();
        iv.setTag(fx);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            // 没有 RenderEffect：仍按同一时长延后收尾，保证淡出与旧系统的透明度过渡同步结束
            if (onEnd != null) {
                fx.pending = () -> {
                    if (iv.getTag() != fx) return;   // 已被新过渡接管，不重复收尾
                    iv.setTag(null);
                    onEnd.run();
                };
                iv.postDelayed(fx.pending, DUR);
            }
            return;
        }
        ValueAnimator va = ValueAnimator.ofFloat(from, to);
        fx.anim = va;
        va.setDuration(DUR);
        va.setInterpolator(EASE_OUT);
        va.addUpdateListener(a -> {
            float r = (float) a.getAnimatedValue();
            // 半径接近 0 时直接摘掉特效，省一次 blur 绘制
            iv.setRenderEffect(r < 0.05f ? null
                    : RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP));
        });
        if (onEnd != null) {
            va.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (iv.getTag() != fx) return;   // 已被新过渡接管，不重复收尾
                    iv.setTag(null);
                    onEnd.run();
                }
            });
        }
        va.start();
    }

    /** 一次过渡的接管对象：API 31+ 用模糊动画，低版本用延后回调，两者都能被撤销。 */
    private static final class Fx {
        ValueAnimator anim;
        Runnable pending;

        void cancel(ImageView iv) {
            if (anim != null) anim.cancel();
            if (pending != null) iv.removeCallbacks(pending);
        }
    }
}

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
package com.jhf.jiehuafa.ime;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

import java.util.List;

import com.jhf.jiehuafa.SmoothCorners;

/**
 * 长按浮层（客户意见 3/6）。
 *
 * 为什么单独做一个 View：键盘自身画不出自己上方的内容（会被父容器裁掉），
 * 所以由服务把这一层叠在 IME 根布局上，键盘只负责算位置 + 跟踪手指。
 *
 * ⚠ 这个 View **不能**用 MATCH_PARENT：它会把 WRAP_CONTENT 的宿主 FrameLayout
 * 撑到整屏高度，IME 窗口跟着变满屏（表现就是键盘被顶到屏幕顶部、下面一大片空白）。
 * 所以每次显示时按浮层实际所需区域设置宽高与边距。
 *
 * 样式对齐设计稿 177-751 / 224-12（浅色·圆润）与 177-1655（深色·圆润）：
 * 56 高圆角气泡（radius 12）+ 11px 外阴影，白底（深色 #484848），无描边；
 * 三项依次为「符号」·「蓝格高亮的主字母」·「变体串」，字 24sp；
 * 手指滑动到的候选换成蓝格高亮（#2E73F3）+ 白字。
 *
 * 修改日志：
 *   2026-11-02 按 177-751 / 177-1655 / 224-12 一比一重做气泡（客户意见）：
 *              删除直角描边框、灰底方格与竖向连线；改 56 高、圆角 12、外阴影 11px；
 *              内边距 5/14/5/15（带四个声调时右 16），整宽 111（三等项：符号 11 +
 *              蓝格 34 + 变体 11，间隙 13），带声调时 233（变体区 135 = 5 字 × 27）；
 *              蓝格 radius 7、#2E73F3；字 24sp 垂直居中，深色底 #484848。
 *   2026-11-02 选中态统一（客户意见）：高亮块固定 34×46（不随字符宽度变），
 *              圆角恒 7；高亮块按字符中心定位，并限制在气泡内 5dp 边距内，
 *              首/尾字符选中时左右间距与上下间距（5）完全一致。
 *   2026-10-29 显隐加过渡（280ms 缓出）：出现时以被长按按键为轴心缩放淡入，
 *              收起时先淡出再 GONE（保留上一次候选内容，见 update/fadeTo），
 *              不再手指一抬就瞬间消失。
 *   2026-10-31 审查修复：fadeTo 取消旧动画前先清空 animator 字段，旧 onAnimationEnd 的守卫必然失效，
 *              避免取消瞬间误收起浮层。
 */
public class KeyPopupView extends View {

    /* 2026-11-02 气泡几何（单位 dp，全部取自设计稿 CSS）：
     *   224-12：233×56、radius 12、padding 5/16/5/15、阴影 0 0 11 #00000036；
     *   177-751 / 177-1655：111×56、padding 5/14/5/15、阴影 0 0 11 #00000036 / #000000b0。
     * 三项宽度：符号 11、蓝格 34（12+11+11 内边距）、变体串（单字 11 / 带声调时 5×27）。 */
    public static final float POPUP_H = 56f;
    public static final float SYM_W = 11f;
    public static final float BLUE_W = 34f;
    public static final float SLOT_W = 27f;
    public static final float CHAR_W = 11f;
    public static final float GAP = 13f;
    public static final float GAP_WIDE = 11f;
    public static final float PAD_L = 15f;
    public static final float PAD_R = 14f;
    public static final float PAD_R_WIDE = 16f;
    public static final float PAD_V = 5f;
    /** 选中高亮块的统一宽度（= 设计稿蓝格 34×46），不随字符宽度变化。 */
    public static final float HILITE_W = 34f;
    /** 高亮块与气泡边缘的最小间距：与上下内边距同为 5，首/尾字符选中时正好贴这个数值。 */
    public static final float HILITE_EDGE = 5f;

    /** 变体串是否按「一个字一个 27dp 槽位」排版（带四个声调时为真）。 */
    public static boolean wide(int n) {
        return n > 3;
    }

    /** 第 item 项（0 符号 / 1 蓝格 / 2 变体串）的宽度，dp。 */
    public static float itemWidth(int item, int n) {
        if (item == 0) return SYM_W;
        if (item == 1) return BLUE_W;
        return wide(n) ? SLOT_W * Math.max(0, n - 2) : CHAR_W;
    }

    /** 第 item 项左缘相对气泡左缘的距离，dp。 */
    public static float itemLeft(int item, int n) {
        float x = PAD_L;
        float g = wide(n) ? GAP_WIDE : GAP;
        for (int i = 0; i < item; i++) x += itemWidth(i, n) + g;
        return x;
    }

    /** 整个气泡的宽度（dp）：两项 87、三项 111、带四声调 233。 */
    public static float width(int n) {
        if (n <= 0) return 0;
        int items = n >= 3 ? 3 : 2;
        float x = PAD_L + (wide(n) ? PAD_R_WIDE : PAD_R)
                + (wide(n) ? GAP_WIDE : GAP) * (items - 1);
        for (int i = 0; i < items; i++) x += itemWidth(i, n);
        return x;
    }

    /* 2026-10-29 与全局动效一致：280ms、缓出曲线 */
    private static final long DUR = 280;
    private static final Interpolator EASE_OUT = new PathInterpolator(0.16f, 0.84f, 0.22f, 1f);

    private final float d;
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    /** G2 连续圆角（Capsule）路径，气泡与高亮块复用。 */
    private final Path smoothPath = new Path();
    private final RectF tmp = new RectF();

    private List<String> opts;
    private int hover = -1;
    private float anchorX, anchorY;
    private float offX, offY;      // 本 View 在根布局里的左/上（绘制时反向平移）
    private boolean dark;
    private int accent = 0xFF05BEC1;
    /* 2026-10-29 显隐动画进度（0 隐藏 → 1 完全显示） */
    private float anim = 1f;
    private boolean opening;
    private ValueAnimator animator;

    public KeyPopupView(Context ctx) {
        super(ctx);
        d = getResources().getDisplayMetrics().density;
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        // 外阴影用 Paint.setShadowLayer 画，需要软件图层
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        // 必须不可点击：FrameLayout 先派发给它，返回 false 才会把触摸交给下面的键盘
        setClickable(false);
        setFocusable(false);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(0, 0);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        setLayoutParams(lp);
        applyColors();
        setVisibility(GONE);
    }

    public void setTheme(boolean dark, int accent) {
        this.dark = dark;
        this.accent = accent;
        applyColors();
        invalidate();
    }

    private void applyColors() {
        // 2026-11-09 新配色：气泡键帽跟随字符键（浅 #FFFFFF / 深 #606060）+ 11px 外阴影，无描边
        bgPaint.setColor(dark ? 0xFF606060 : Color.WHITE);
        bgPaint.setShadowLayer(11 * d, 0, 0, dark ? 0xB0000000 : 0x36000000);
    }

    /**
     * @param opts null 或空 = 收起浮层。所有坐标都是 IME 根布局坐标系。
     *
     * 2026-10-29 收起不再直接 GONE：保留上一次的候选内容做淡出，动画播完才真正隐藏，
     * 避免手指抬起瞬间浮层"啪"地消失。
     */
    public void update(List<String> opts, RectF b, int hover, float ax, float ay) {
        this.hover = hover;
        if (b != null) box.set(b);
        this.anchorX = ax;
        this.anchorY = ay;

        boolean show = opts != null && !opts.isEmpty();
        if (!show) {
            if (getVisibility() == VISIBLE) fadeTo(0f, true);
            return;
        }
        this.opts = opts;
        // 只占浮层真正需要的区域，避免把宿主撑高；四周留出 11px 外阴影的余量
        float shadow = 12 * d;
        float left = Math.max(0, box.left - shadow);
        float top = Math.max(0, box.top - shadow);
        float right = box.right + shadow;
        float bottom = box.bottom + shadow;
        offX = left;
        offY = top;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        lp.width = Math.max(1, (int) Math.ceil(right - left));
        lp.height = Math.max(1, (int) Math.ceil(bottom - top));
        lp.leftMargin = (int) left;
        lp.topMargin = (int) top;
        setLayoutParams(lp);
        // 从被长按的按键处"长"出来
        setPivotX(anchorX - offX);
        setPivotY(anchorY - offY);
        if (getVisibility() != VISIBLE || (!opening && anim < 0.99f)) {
            if (getVisibility() != VISIBLE) {
                anim = 0f;
                setVisibility(VISIBLE);
            }
            fadeTo(1f, false);
        }
        invalidate();
    }

    /** 透明度/缩放/位移一起渐变；收起结束后才 GONE 并清空候选。 */
    private void fadeTo(float target, boolean hideWhenDone) {
        // 2026-10-31 审查修复：先清空字段再取消，旧动画的 onAnimationEnd 会看到 a != animator 直接返回，
        // 否则取消瞬间旧回调仍通过守卫，可能把浮层误置 GONE 并清空候选
        ValueAnimator prev = animator;
        animator = null;
        if (prev != null) prev.cancel();
        opening = !hideWhenDone;
        final boolean hideAtEnd = hideWhenDone;
        animator = ValueAnimator.ofFloat(anim, target);
        animator.setDuration(DUR);
        animator.setInterpolator(EASE_OUT);
        animator.addUpdateListener(a -> {
            anim = (float) a.getAnimatedValue();
            setAlpha(anim);
            float s = 0.92f + 0.08f * anim;
            setScaleX(s);
            setScaleY(s);
            setTranslationY((1f - anim) * 4 * d);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                if (a != animator) return;   // 已被新动画接管
                opening = false;
                if (hideAtEnd && anim <= 0.02f) {
                    setVisibility(GONE);
                    opts = null;
                }
            }
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (opts == null || opts.isEmpty()) return;
        int save = c.save();
        c.translate(-offX, -offY);      // 回到根布局坐标系

        // 气泡本体：圆角 12 + 外阴影，无描边（2026-11-02 按 224-12 / 177-1655）
        float r = 12 * d;
        SmoothCorners.build(smoothPath, box, r);
        c.drawPath(smoothPath, bgPaint);

        int n = opts.size();
        textPaint.setTextSize(24 * d);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float top = box.top + PAD_V * d;
        float bottom = box.bottom - PAD_V * d;
        float baseY = (top + bottom) / 2 - (fm.ascent + fm.descent) / 2;

        float symL = box.left + itemLeft(0, n) * d;
        float blueL = box.left + itemLeft(1, n) * d;
        float runL = box.left + itemLeft(2, n) * d;
        float slotW = SLOT_W * d;
        float blueR = 7 * d;
        // 2026-11-02 客户要求：选中高亮块统一 34×46、圆角 7，宽度不随字符变化；
        // 首/尾字符选中时与气泡左右边缘的间距固定 5（与上下间距 PAD_V 完全一致）。
        float hlW = HILITE_W * d;
        float edge = HILITE_EDGE * d;
        for (int i = 0; i < n; i++) {
            float l, w;
            if (i == 0) {
                l = symL;
                w = SYM_W * d;
            } else if (i == 1) {
                l = blueL;
                w = BLUE_W * d;
            } else {
                l = runL + (i - 2) * slotW;
                w = slotW;
            }
            // 设计稿的主字母格常态就是蓝底高亮；手指滑到别的候选时高亮跟着走
            boolean on = (hover < 0) ? (i == 1) : (i == hover);
            if (on) {
                float hl = l + w / 2 - hlW / 2;     // 高亮块对准字符中心
                hl = Math.max(box.left + edge,
                        Math.min(hl, box.right - edge - hlW));
                cellPaint.setColor(accent);
                tmp.set(hl, top, hl + hlW, bottom);
                SmoothCorners.build(smoothPath, tmp, blueR);
                c.drawPath(smoothPath, cellPaint);
            }
            textPaint.setColor(on ? onAccent() : (dark ? Color.WHITE : Color.BLACK));
            c.drawText(opts.get(i), l + w / 2, baseY, textPaint);
        }
        c.restoreToCount(save);
    }

    private int onAccent() {
        double l = 0.299 * Color.red(accent) + 0.587 * Color.green(accent) + 0.114 * Color.blue(accent);
        return l < 160 ? 0xFFFFFFFF : 0xE6101418;
    }
}

package com.jhf.jiehuafa.ime;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import java.util.List;

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
 * 样式对齐设计稿 4488aa83：全直角、细深色描边、候选块浅灰底；
 * 手指滑动到的候选为主题色填充 + 对比字色；并用一条竖线连到被长按的按键。
 */
public class KeyPopupView extends View {

    private static final float CELL = 44f;   // dp
    private static final float GAP = 2.5f;   // dp
    private static final float PAD = 6f;     // dp

    private final float d;
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();

    private List<String> opts;
    private int hover = -1;
    private float anchorX, anchorY;
    private float offX, offY;      // 本 View 在根布局里的左/上（绘制时反向平移）
    private boolean dark;
    private int accent = 0xFF05BEC1;

    public KeyPopupView(Context ctx) {
        super(ctx);
        d = getResources().getDisplayMetrics().density;
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(1.5f * d);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(1.5f * d);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
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
        bgPaint.setColor(dark ? 0xFF1E1E1E : Color.WHITE);
        borderPaint.setColor(dark ? 0xB3FFFFFF : 0xE6080C10);
        linePaint.setColor(dark ? 0xB3FFFFFF : 0xE6080C10);
    }

    /** @param opts null 或空 = 收起浮层。所有坐标都是 IME 根布局坐标系。 */
    public void update(List<String> opts, RectF b, int hover, float ax, float ay) {
        this.opts = opts;
        this.hover = hover;
        if (b != null) box.set(b);
        this.anchorX = ax;
        this.anchorY = ay;

        boolean show = opts != null && !opts.isEmpty();
        if (!show) {
            if (getVisibility() != GONE) setVisibility(GONE);
            return;
        }
        // 只占浮层（含连线）真正需要的区域，避免把宿主撑高
        float left = Math.min(box.left, anchorX) - 2 * d;
        float top = box.top;
        float right = Math.max(box.right, anchorX) + 2 * d;
        float bottom = Math.max(box.bottom, anchorY) + 2 * d;
        offX = left;
        offY = top;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        lp.width = Math.max(1, (int) Math.ceil(right - left));
        lp.height = Math.max(1, (int) Math.ceil(bottom - top));
        lp.leftMargin = (int) left;
        lp.topMargin = (int) top;
        setLayoutParams(lp);
        if (getVisibility() != VISIBLE) setVisibility(VISIBLE);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (opts == null || opts.isEmpty()) return;
        int save = c.save();
        c.translate(-offX, -offY);      // 回到根布局坐标系

        // 浮层下缘 → 按键上缘的连线
        if (anchorY > box.bottom) c.drawLine(anchorX, box.bottom, anchorX, anchorY, linePaint);

        c.drawRoundRect(box, 0, 0, bgPaint);
        c.drawRoundRect(box, 0, 0, borderPaint);

        textPaint.setTextSize(19 * d);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float cellW = (CELL + GAP * 2) * d;
        float t = box.top + PAD * d, b = box.bottom - PAD * d;
        for (int i = 0; i < opts.size(); i++) {
            float l = box.left + PAD * d + i * cellW + GAP * d;
            float r = l + CELL * d;
            boolean on = (i == hover);
            cellPaint.setColor(on ? accent : (dark ? 0xFF2E2E2E : 0xFFF4F4F4));
            c.drawRoundRect(new RectF(l, t, r, b), 0, 0, cellPaint);
            textPaint.setColor(on ? onAccent() : (dark ? 0xFFFFFFFF : 0xFF000000));
            c.drawText(opts.get(i), (l + r) / 2,
                    (t + b) / 2 - (fm.ascent + fm.descent) / 2, textPaint);
        }
        c.restoreToCount(save);
    }

    private int onAccent() {
        double l = 0.299 * Color.red(accent) + 0.587 * Color.green(accent) + 0.114 * Color.blue(accent);
        return l < 160 ? 0xFFFFFFFF : 0xE6101418;
    }
}

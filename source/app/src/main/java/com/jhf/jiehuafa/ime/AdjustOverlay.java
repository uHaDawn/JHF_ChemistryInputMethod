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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

import com.jhf.jiehuafa.R;

/**
 * 键盘调节编辑态（2026-11-04，Figma 249-204 浅色 / 249-394 深色，一比一）：
 * 调节框就出现在键盘原位上——整卡压暗 #00000073，框边即键盘边界，不弹窗、不位移。
 * 拖动时键盘实时重排（键高/间距跟手，输入法底边固定、顶部随高度伸缩），保存才持久化。
 *
 * 框：四角白色角标 40×40、四边白色拖手 60×8 圆角 8；
 * 三钮：76 白圆，纵向位置按设计稿 115px（相对卡顶），下挂 7px、20px 白字标签；
 * 重置（环形箭头 28×34 #EB7777）/ 取消（X 26×26 黑）/ 保存（对勾 34×21 #2E73F3）。
 *
 * 2026-11-05 拖动卡顿修复：MOVE 的实时下发改为 postOnAnimation 按帧合并（同一帧多个触摸采样点
 *              只重排一次），抬手/取消时 flush 最后一帧几何；配合服务侧去掉浮层多余的
 *              requestLayout，避免每帧多走一趟布局造成抽搐。
 */
public class AdjustOverlay extends View {

    public interface Callback {
        /**
         * 拖动中实时把新几何应用到键盘：left / width 为内容区左边界与宽度占全宽的比例，
         * h 为纵向比例；gapPx 为键盘底与卡片底之间的间隙（底部拖手用）。
         */
        void onPreview(float left, float width, float h, float gapPx);

        /** 保存：保留当前几何并持久化（键盘保持已预览的尺寸，不二次跳动）。 */
        void onSave(float left, float width, float h, float gapPx);

        /** 取消：丢弃改动，键盘恢复为已保存的尺寸。 */
        void onCancel();
    }

    /** 由服务侧回填当前键盘框（相对本浮层坐标）、卡片内容高度与底部间隙。 */
    public interface FrameSource {
        void fillFrame(RectF out);

        /**
         * 卡片内容真实高度（= 抓手 + 候选栏 + 分割线 + 键盘 + 底部间隙，不含本浮层自身）。
         * 浮层按这个高度测量，绝不反向撑高卡片，拖动时键盘顶部才能实时跟随。
         */
        int contentHeight();

        /** 键盘底与卡片底之间的间隙（底部拖手拖出来的空白，px）。 */
        float bottomGap();
    }

    private static final float MIN_W = 0.55f;               // 宽度占比下限（左右可自由扩展/收窄）
    private static final float MIN_H = 0.65f, MAX_H = 1.35f;
    /** 拖手命中范围（dp）：比白色拖手本身宽得多，手指不用瞄准细条也能拖。 */
    private static final float EDGE_TOL_DP = 44f;

    private final Callback callback;
    private final FrameSource frameSource;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF frame = new RectF();   // 键盘原始矩形（框的基准）
    private final RectF box = new RectF();     // 当前调节框（拖动后）
    private final Path path = new Path();
    private Drawable iconReset, iconCancel, iconSave;

    /** 当前几何（相对键盘全宽 baseW0 与标准高 baseH0 的比例）。 */
    private float tempLeft = 0f, tempWidth = 1f, tempHeight = 1f, tempGapPx = 0f;
    /** 按下瞬间的绝对几何（left/right/top 边、键盘高、底部间隙），拖动全部基于它换算。 */
    private float startLeftPx = 0f, startRightPx = 0f, startKbH = 0f, startGapPx = 0f;
    /** baseW0 = 键盘视图全宽、baseH0 = 键盘标准高（不含用户比例）、viewLeft0 = 视图左缘。 */
    private float baseW0 = 1f, baseH0 = 1f, viewLeft0 = 0f;
    private float downX, downY;   // 按下时的屏幕坐标（raw），拖动位移不受浮层自身移动影响
    private int dragEdge = 0;      // 0 无；1 左 2 右 4 上 8 下（角标为组合值）
    private int pressedBtn = -1;   // 0 重置 1 取消 2 保存
    /** 拖动中的实时下发按帧合并：一帧最多重排一次，避免每个触摸采样点都触发整窗重排。 */
    private boolean previewPending;
    private final Runnable previewTask;

    public AdjustOverlay(Context ctx, Callback cb, FrameSource fs) {
        super(ctx);
        callback = cb;
        frameSource = fs;
        // 按帧合并的实时下发任务（回调引用只能在构造里拿到，不能放字段初始化）
        previewTask = () -> {
            previewPending = false;
            callback.onPreview(tempLeft, tempWidth, tempHeight, tempGapPx);
        };
        stroke.setColor(Color.WHITE);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        text.setColor(Color.WHITE);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        iconReset = ctx.getDrawable(R.drawable.ic_adj_reset);
        iconCancel = ctx.getDrawable(R.drawable.ic_adj_cancel);
        iconSave = ctx.getDrawable(R.drawable.ic_adj_save);
    }

    /**
     * 测量：宽 = 卡片宽（MATCH_PARENT 规格），高 = 卡片内容真实高度（frameSource 现算）。
     * 绝不能取 AT_MOST 规格里的整屏高度，也不能沿用上一帧的旧高度——否则键盘变矮时
     * 卡片被浮层反向撑住、键盘顶部会锁死不动（2026-11-04 修复）。
     */
    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int h = frameSource != null ? frameSource.contentHeight() : 0;
        if (h <= 0) h = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(w, h);
    }

    /**
     * 进入编辑态：按已保存的左边界 / 宽度 / 高度落框。
     * 反推键盘视图全宽与标准高，拖动时用绝对像素换算，左右两侧各自独立、
     * 不再把单边位移翻倍成对称收窄（2026-11-04 修复左右联动收窄问题）。
     */
    public void begin(float left, float width, float height) {
        frameSource.fillFrame(frame);
        float w = Math.max(0.3f, Math.min(1f, width));
        float l = Math.max(0f, Math.min(1f - w, left));
        float h = height <= 0 ? 1f : Math.max(MIN_H, Math.min(MAX_H, height));
        baseW0 = Math.max(1f, frame.width() / w);
        baseH0 = Math.max(1f, frame.height() / h);
        viewLeft0 = frame.left - l * baseW0;
        tempLeft = l;
        tempWidth = w;
        tempHeight = h;
        tempGapPx = startGapPx = Math.max(0f, frameSource.bottomGap());
        dragEdge = 0;
        pressedBtn = -1;
        requestLayout();
        invalidate();
    }

    private float u() {
        return getWidth() / 402f;
    }

    /**
     * 按 tempLeft / tempWidth / tempHeight 推出当前框。
     * 左缘 = 键盘视图左缘 + 左边界占比；上缘固定为键盘视图顶（键盘在卡片内按列排布，
     * 高度变化时顶不动、底伸缩），底 = 顶 + 标准高 × 高度比例。
     */
    private void computeBox() {
        float left = viewLeft0 + tempLeft * baseW0;
        box.set(left, frame.top,
                left + tempWidth * baseW0, frame.top + baseH0 * tempHeight);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float u = u();
        computeBox();

        // 整卡压暗（设计稿 #00000073，blur 用纯色压暗近似）
        c.drawColor(0x73000000);

        // 四角白色角标：两臂沿框边各 36u
        stroke.setStrokeWidth(4 * u);
        float arm = 36 * u;
        drawCorner(c, box.left, box.top, arm, arm);
        drawCorner(c, box.right, box.top, -arm, arm);
        drawCorner(c, box.left, box.bottom, arm, -arm);
        drawCorner(c, box.right, box.bottom, -arm, -arm);

        // 四边白色拖手 60×8 圆角 8，骑在框边中点
        fill.setColor(Color.WHITE);
        float hw = 60 * u, hh = 8 * u;
        drawHandle(c, box.centerX() - hw / 2, box.top - hh / 2, hw, hh, u);
        drawHandle(c, box.centerX() - hw / 2, box.bottom - hh / 2, hw, hh, u);
        drawHandle(c, box.left - hh / 2, box.centerY() - hw / 2, hh, hw, u);
        drawHandle(c, box.right - hh / 2, box.centerY() - hw / 2, hh, hw, u);

        // 三颗 76 圆钮：设计稿 x 65 / 164 / 263（中心 103 / 202 / 301）、y 115
        float cy = btnCy();
        float r = 38 * u();
        float[] cx = {103 * u(), 202 * u(), 301 * u()};
        String[] labels = {"重置", "取消", "保存"};
        for (int i = 0; i < 3; i++) {
            fill.setColor(pressedBtn == i ? 0xFFD9D9D9 : Color.WHITE);
            c.drawCircle(cx[i], cy, r, fill);
        }
        drawIcon(c, iconReset, cx[0], cy, 28 * u, 34 * u);
        drawIcon(c, iconCancel, cx[1], cy, 26 * u, 26 * u);
        drawIcon(c, iconSave, cx[2], cy, 34 * u, 21 * u);

        // 标签 20px、圆钮下 7px（设计稿文字盒 80×30）
        text.setTextSize(20 * u);
        Paint.FontMetrics fm = text.getFontMetrics();
        float labelCy = cy + r + 7 * u + 15 * u;
        for (int i = 0; i < 3; i++) {
            c.drawText(labels[i], cx[i], labelCy - (fm.ascent + fm.descent) / 2f, text);
        }
    }

    private void drawCorner(Canvas c, float x, float y, float ax, float ay) {
        path.reset();
        path.moveTo(x + ax, y);
        path.lineTo(x, y);
        path.lineTo(x, y + ay);
        c.drawPath(path, stroke);
    }

    private void drawHandle(Canvas c, float l, float t, float w, float h, float u) {
        c.drawRoundRect(new RectF(l, t, l + w, t + h), 8 * u, 8 * u, fill);
    }

    private void drawIcon(Canvas c, Drawable icon, float cx, float cy, float w, float h) {
        if (icon == null) return;
        icon.setBounds((int) (cx - w / 2), (int) (cy - h / 2), (int) (cx + w / 2), (int) (cy + h / 2));
        icon.draw(c);
    }

    private float btnCx(int i) {
        return new float[]{103, 202, 301}[i] * u();
    }

    private float btnCy() {
        // 设计稿圆钮中心 y = 153（相对 402×331 的卡顶）；键盘缩得很矮时压到卡片中部避免溢出
        float y = 153 * u();
        float h = getHeight();
        if (h > 0 && y > h * 0.5f) y = h * 0.5f;
        return y;
    }

    private int btnAt(float x, float y) {
        float r = 44 * u();   // 38 圆 + 6 触控余量
        for (int i = 0; i < 3; i++) {
            float dx = x - btnCx(i), dy = y - btnCy();
            if (dx * dx + dy * dy <= r * r) return i;
        }
        return -1;
    }

    /**
     * 落点命中的框边（1 左 2 右 4 上 8 下；角标为组合值）。
     * 判距放宽到 44dp 并取最近的一条边：白色拖手只有 8px 宽，手指不用瞄准细条，
     * 落在框边附近 ±44dp 都能拖（2026-11-04 扩大可交互区域）。
     */
    private int edgeAt(float x, float y) {
        float tol = EDGE_TOL_DP * getResources().getDisplayMetrics().density;
        if (x < box.left - tol || x > box.right + tol
                || y < box.top - tol || y > box.bottom + tol) return 0;
        int e = 0;
        float dl = Math.abs(x - box.left), dr = Math.abs(x - box.right);
        float dt = Math.abs(y - box.top), db = Math.abs(y - box.bottom);
        if (dl <= tol && dl <= dr) e |= 1;
        else if (dr <= tol) e |= 2;
        if (dt <= tol && dt <= db) e |= 4;
        else if (db <= tol) e |= 8;
        return e;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                computeBox();
                pressedBtn = btnAt(ev.getX(), ev.getY());
                dragEdge = pressedBtn >= 0 ? 0 : edgeAt(ev.getX(), ev.getY());
                // ⚠ 位移必须用屏幕坐标：实时重排时卡片顶边（含浮层本身）会随手指上下移动，
                // 若取本地坐标，手指与拖手的相对位移会被窗口位移抵消，拖动会「锁死」在原地
                downX = ev.getRawX();
                downY = ev.getRawY();
                startLeftPx = box.left;
                startRightPx = box.right;
                startKbH = box.height();
                startGapPx = tempGapPx;
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragEdge != 0) {
                    float dx = ev.getRawX() - downX, dy = ev.getRawY() - downY;
                    // 四条边各自独立换算：左边只改左边界、右边只改右边界、
                    // 上边只改键盘高、下边只改底部间隙，互不牵连（可向任意方向扩展或收窄）
                    float left = startLeftPx, right = startRightPx;
                    float h = startKbH, gap = startGapPx;
                    if ((dragEdge & 1) != 0) left = startLeftPx + dx;
                    if ((dragEdge & 2) != 0) right = startRightPx + dx;
                    if ((dragEdge & 4) != 0) h = startKbH - dy;   // 上边跟手（上移变大）
                    if ((dragEdge & 8) != 0) gap = startGapPx - dy; // 下边跟手（下移贴近屏幕底）

                    // 左右夹紧：不出键盘视图，最小宽度 MIN_W
                    left = Math.max(0f, Math.min(left, baseW0));
                    right = Math.max(0f, Math.min(right, baseW0));
                    float minW = MIN_W * baseW0;
                    if (right - left < minW) {
                        if ((dragEdge & 1) != 0 && (dragEdge & 2) == 0) {
                            left = Math.max(0f, right - minW);   // 只拖左边：右边固定
                        } else {
                            right = Math.min(baseW0, left + minW); // 只拖右边：左边固定
                        }
                    }
                    h = clamp(h, MIN_H * baseH0, MAX_H * baseH0);

                    tempLeft = left / baseW0;
                    tempWidth = (right - left) / baseW0;
                    tempHeight = h / baseH0;
                    tempGapPx = clamp(gap, 0f, maxGapPx());
                    schedulePreview();   // 按帧合并下发，键盘不再被每个触摸采样点重排多次
                    invalidate();
                } else if (pressedBtn >= 0) {
                    int b = btnAt(ev.getX(), ev.getY());
                    if (b != pressedBtn) {
                        pressedBtn = -1;   // 滑出圆钮：本次不再触发
                        invalidate();
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                flushPreview();   // 抬手前把最后一帧几何立刻落地，避免停在上一帧尺寸上
                if (pressedBtn >= 0 && btnAt(ev.getX(), ev.getY()) == pressedBtn) {
                    int b = pressedBtn;
                    pressedBtn = -1;
                    if (b == 0) {
                        // 重置：左右铺满、标准高、无底部间隙，继续编辑
                        tempLeft = 0f;
                        tempWidth = 1f;
                        tempHeight = 1f;
                        tempGapPx = 0f;
                        callback.onPreview(0f, 1f, 1f, 0f);
                        invalidate();
                    } else if (b == 1) {
                        callback.onCancel();
                    } else {
                        callback.onSave(tempLeft, tempWidth, tempHeight, tempGapPx);
                    }
                    return true;
                }
                pressedBtn = -1;
                dragEdge = 0;
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                flushPreview();
                pressedBtn = -1;
                dragEdge = 0;
                invalidate();
                return true;
            default:
                return true;
        }
    }

    /** 登记一次「下一帧下发」：同一帧内的多个触摸采样点只保留一次重排。 */
    private void schedulePreview() {
        if (previewPending) return;
        previewPending = true;
        postOnAnimation(previewTask);
    }

    /** 立刻下发当前几何（抬手/取消时调用），保证最终尺寸与手指位置一致。 */
    private void flushPreview() {
        removeCallbacks(previewTask);
        if (!previewPending) return;
        previewPending = false;
        callback.onPreview(tempLeft, tempWidth, tempHeight, tempGapPx);
    }

    /** 底部间隙上限：给键盘上方留出至少约 10% 屏幕高，避免整卡顶出屏幕。 */
    private float maxGapPx() {
        float screenH = getResources().getDisplayMetrics().heightPixels;
        return Math.max(0f, screenH * 0.9f - frame.top - baseH0 * tempHeight);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}

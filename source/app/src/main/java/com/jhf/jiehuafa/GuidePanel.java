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

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 操作引导浮层（设计稿 86449181）：
 * 半透明遮罩 + 居中面板（主题色标题栏「▶ 操作引导」+ 两行引导）。
 * 完成项整行浅主题色底、按钮变「已完成」；点击遮罩空白处收起。
 *
 * 修改日志：
 *   2026-10-29 show()/hide() 加入显隐过渡：遮罩透明度与模糊半径（0↔26）同步渐变，
 *              面板上移淡入/淡出，统一 280ms 缓出曲线，结束后才真正 GONE。
 *              补强：收起过程中 refresh() 不再重建 UI（否则 removeAllViews 打断淡出）；
 *              窗口无焦点时推迟 200ms 再收（从系统设置切回来的场景），最多重试 5 次。
 *   2026-10-31 新增圆润风格版面（Figma 129-2342）：遮罩 #00000080 + 模糊，面板贴底
 *              （左右 20/21、底 39）、圆角 32 白卡（暗色 #1C1C1E）、内边距 16/25/28/33，
 *              标题「操作引导」20sp Medium 居中 31 高，两行 33 高（上距 19 / 27），
 *              右侧「前往」为 #2E94F3 蓝字（完成时改为灰色「已完成」），不再有标题栏/
 *              分割线/描边按钮。
 *   2026-10-31 审查修复：收起动画未播完时再次 show() 会取消未完成的模糊/遮罩动画并重新淡入，
 *              避免旧 blurOut 回调把刚显示的引导置为 GONE。
 *   2026-10-31 按新版 Figma 129-2342 逐像素校正圆润版面：面板 361×172（内边距 16/27/21/27、
 *              底距 22），标题与两行文案统一 16sp Medium，两行上距均为 19，动作「前往」右缘
 *              距面板右 33；强调色随 UiStyle.ACCENT 改为 #2E73F3。
 *   2026-10-31 按设计稿「待启用」状态：未启用输入法时第二行也显示蓝色「前往」（此前显示灰色
 *              「待启用」并置灰不可点），点击时直接跳系统输入法设置，避免打开选择器却选不到本输入法。
 *   2026-10-31 深色圆润（Figma 177-1341）：贴底面板 #252525、白字，遮罩 #00000080 + blur 5。
 *   2026-10-31 自适应修复：窄屏（900px 实测）行文案与右侧「前往/已完成」贴字，
 *              行文案改为单行超长省略（END）并留 8dp 间距。
 *   2026-11-07 深色面板底色 #252525 → #242424（配合页面纯黑 #000000，客户要求）。
 */
public class GuidePanel extends FrameLayout {

    /** 遮罩压暗色（同时作为截图失败时的兜底底）。 */
    private final float d;
    private boolean dark = false;
    private String builtKey = null;

    private int cCard = 0xFFFFFFFF;
    private int cText = 0xFF000000;

    private LinearLayout panel;
    private LinearLayout rowEnable, rowSwitch;
    private TextView btnEnable, btnSwitch;

    private View backdropSource;
    private ImageView backdrop;
    /* 2026-10-29 收起动画进行中：期间拒绝重建 UI，否则 removeAllViews 会把淡出打断 */
    private boolean closing;
    /** 等窗口拿到焦点后再播放收起动画（最多重试 5 次，避免后台空转）。 */
    private boolean hideQueued;
    private int hideRetry;

    public GuidePanel(Context ctx) {
        super(ctx);
        d = getResources().getDisplayMetrics().density;
        setBackgroundColor(Color.TRANSPARENT); // 压暗由模糊背景图承担
        setClickable(true);
        setOnClickListener(v -> hide());
        setVisibility(GONE);
    }

    /** 背景要模糊谁的画面（主界面内容视图）。 */
    public void setBackdropSource(View source) {
        this.backdropSource = source;
    }

    /** 按主题重建外观（主题未变则只刷新状态），并刷新两个引导项的完成状态。 */
    public void refresh(String themeId, boolean dark) {
        if (closing) return;   // 正在淡出，等收完再重建
        String key = themeId + "/" + dark;
        if (!key.equals(builtKey)) {
            this.dark = dark;
            buildUi();
            builtKey = key;
        }
        refreshStatus();
    }

    public void show() {
        boolean wasClosing = closing;
        closing = false;
        hideRetry = 0;
        // 2026-10-31 审查修复：收起动画没播完就再次显示时，先取消旧模糊/遮罩动画并重播淡入，
        // 否则旧 blurOut 的回调仍会在 280ms 后把刚显示的浮层置为 GONE
        if (wasClosing && backdrop != null) UiBlur.cancel(backdrop);
        refreshBackdrop();
        if (getVisibility() == VISIBLE && !wasClosing) return;
        // 2026-10-29 显隐过渡：遮罩淡入 + 模糊 0→26（缓出），面板上移淡入
        setVisibility(VISIBLE);
        notifyHost(true);
        animateContentsIn();
    }

    /** 引导浮层显隐时同步主界面的系统栏压暗（2026-11-03）。 */
    private void notifyHost(boolean visible) {
        if (getContext() instanceof MainActivity) {
            ((MainActivity) getContext()).onModalOverlayVisibility(visible);
        }
    }

    /** 每次显示都重新截一次背景（主界面可能已经变过主题/滚动位置）。 */
    private void refreshBackdrop() {
        if (backdrop == null) return;
        UiBlur.apply(backdrop, backdropSource, 0x80000000, 0xCC000000);
    }

    public void hide() {
        if (getVisibility() != VISIBLE || closing) return;
        if (!isShown() || !hasWindowFocus()) {
            // 刚从系统设置切回来时窗口还没焦点、未开始绘制，此时播放动画看不到，先等一会
            if (!hasWindowFocus() && isShown() && hideRetry < 5) {
                if (!hideQueued) {
                    hideQueued = true;
                    postDelayed(() -> {
                        hideQueued = false;
                        hideRetry++;
                        hide();
                    }, 200);
                }
                return;
            }
            setVisibility(GONE);
            notifyHost(false);
            return;
        }
        hideRetry = 0;
        closing = true;
        animateContentsOut();
    }

    /** 遮罩透明度与模糊度同步渐变，面板轻微上移淡入。 */
    private void animateContentsIn() {
        if (backdrop != null) {
            backdrop.animate().cancel();
            backdrop.setAlpha(0f);
            UiBlur.blurIn(backdrop);
            backdrop.animate().alpha(1f).setDuration(UiBlur.DUR)
                    .setInterpolator(UiBlur.EASE_OUT).start();
        }
        if (panel != null) {
            panel.animate().cancel();
            panel.setAlpha(0f);
            panel.setTranslationY(12f * d);
            panel.setScaleX(0.97f);
            panel.setScaleY(0.97f);
            panel.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                    .setDuration(UiBlur.DUR).setInterpolator(UiBlur.EASE_OUT).start();
        }
    }

    private void animateContentsOut() {
        if (backdrop != null) {
            backdrop.animate().cancel();
            backdrop.animate().alpha(0f).setDuration(UiBlur.DUR)
                    .setInterpolator(UiBlur.EASE_OUT).start();
            UiBlur.blurOut(backdrop, () -> {
                closing = false;
                setVisibility(GONE);
                notifyHost(false);
            });
        } else {
            closing = false;
            setVisibility(GONE);
            notifyHost(false);
        }
        if (panel != null) {
            panel.animate().cancel();
            panel.animate().alpha(0f).translationY(10f * d).scaleX(0.98f).scaleY(0.98f)
                    .setDuration(UiBlur.DUR).setInterpolator(UiBlur.EASE_OUT).start();
        }
    }

    // ---------------- 构建 ----------------

    private void buildUi() {
        removeAllViews();
        buildRoundUi();
    }

    /**
     * 贴底圆角大卡（Figma 129-2342）：402 画板下贴底白卡 361×172、圆角 32，
     * 内边距 16/27/21/27，标题 16sp 居中 31 高，两行 33 高（上距各 19）。
     */
    private void buildRoundUi() {
        // 深色（2026-11-07 客户要求）：面板 #242424、圆角 32、白字
        cCard = dark ? 0xFF242424 : 0xFFFFFFFF;
        cText = dark ? 0xFFFFFFFF : 0xFF000000;

        backdrop = new ImageView(getContext());
        backdrop.setScaleType(ImageView.ScaleType.FIT_XY);
        addView(backdrop, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        panel = new LinearLayout(getContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClickable(true);   // 消费点击，避免穿透到遮罩把自己关掉
        SmoothDrawable pg = new SmoothDrawable();
        pg.setColor(cCard);
        pg.setCornerRadius(dp(32));
        panel.setBackground(pg);
        // 设计稿 16px 105px 21px 27px；右侧 105 是给「前往」留的位置，
        // 这里用 paddingRight 27 + 行内动作 rightMargin 6，动作右缘同样落在面板右 33
        panel.setPadding(dp(27), dp(16), dp(27), dp(21));

        TextView title = new TextView(getContext());
        title.setText("操作引导");
        title.setTextSize(16);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setTextColor(cText);
        title.setGravity(Gravity.CENTER);
        panel.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(31)));

        rowEnable = roundRow();
        btnEnable = (TextView) rowEnable.getChildAt(1);
        btnEnable.setOnClickListener(v -> {
            try {
                getContext().startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Throwable ignored) {
            }
        });
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(33));
        lp1.topMargin = dp(19);
        panel.addView(rowEnable, lp1);

        rowSwitch = roundRow();
        btnSwitch = (TextView) rowSwitch.getChildAt(1);
        btnSwitch.setOnClickListener(v -> showPickerOrSettings());
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(33));
        lp2.topMargin = dp(19);
        panel.addView(rowSwitch, lp2);

        // 自适应：402 宽屏（设计稿）下正好 361dp；窄屏左右各收一半余量，宽屏封顶 420dp
        int panelW = Math.min(getResources().getDisplayMetrics().widthPixels - dp(41), dp(420));
        LayoutParams lp = new LayoutParams(panelW, LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = dp(22);
        addView(panel, lp);
    }

    /** 圆润行：16sp Medium 文案（weight 1）+ 右侧 16sp Medium 动作字。 */
    private LinearLayout roundRow() {
        LinearLayout r = new LinearLayout(getContext());
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);

        TextView label = new TextView(getContext());
        label.setTextSize(16);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setSingleLine();
        // 窄屏（<402dp）时文案让位给右侧动作字：超长省略，并留 8dp 间距避免贴字
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setPadding(0, 0, dp(8), 0);
        label.setGravity(Gravity.CENTER_VERTICAL);
        r.addView(label, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        TextView act = new TextView(getContext());
        act.setTextSize(16);
        act.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        act.setSingleLine();
        act.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        LinearLayout.LayoutParams actLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT);
        // 设计稿动作右缘距面板右 33：内容右缘(334) - 6 = 328
        actLp.rightMargin = dp(6);
        r.addView(act, actLp);
        return r;
    }

    // ---------------- 状态 ----------------

    private void refreshStatus() {
        if (rowEnable == null) return;
        Context ctx = getContext();
        boolean enabled = ImeStatusHelper.isEnabled(ctx);
        boolean current = ImeStatusHelper.isCurrent(ctx);
        styleRoundRow(rowEnable, "启用 接化发输入法", "前往", enabled);
        styleRoundRow(rowSwitch, "切换到 接化发输入法", "前往", current);
        // 2026-10-31 按设计稿 129-2342「待启用」页：未启用时两行动作同样是「前往」，
        // 不再置灰成「待启用」；点击未启用时的第二行改为直接跳输入法设置。
        rowSwitch.setAlpha(1f);
        rowSwitch.setClickable(true);
        btnSwitch.setClickable(true);
    }

    /** 行状态：无底色无描边，动作字为蓝色「前往」；完成变灰「已完成」。 */
    private void styleRoundRow(LinearLayout r, String label, String todoText, boolean done) {
        TextView text = (TextView) r.getChildAt(0);
        TextView act = (TextView) r.getChildAt(1);
        text.setText(label);
        text.setTextColor(cText);
        if (done) {
            act.setText("已完成");
            act.setTextColor(dark ? 0x4DFFFFFF : 0x33000000);
        } else {
            act.setText(todoText);
            act.setTextColor("前往".equals(todoText)
                    ? UiStyle.ACCENT : (dark ? 0x4DFFFFFF : 0x33000000));
        }
        r.setBackground(null);
        act.setBackground(null);
    }

    private void showPickerOrSettings() {
        // 还没启用时选择器里根本没有接化发，直接去系统输入法设置更合理
        if (!ImeStatusHelper.isEnabled(getContext())) {
            try {
                getContext().startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (Throwable ignored) {
            }
        }
        try {
            InputMethodManager imm = (InputMethodManager)
                    getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm == null) throw new IllegalStateException();
            imm.showInputMethodPicker();
        } catch (Throwable e) {
            try {
                getContext().startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Throwable ignored) {
            }
        }
    }

    // ---------------- 工具 ----------------

    private int dp(float v) {
        return (int) (v * d);
    }
}

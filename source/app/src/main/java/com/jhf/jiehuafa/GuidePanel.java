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
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.jhf.jiehuafa.ime.ChemKeyboardView;

/**
 * 操作引导浮层（设计稿 86449181）：
 * 半透明遮罩 + 居中面板（主题色标题栏「▶ 操作引导」+ 两行引导）。
 * 完成项整行浅主题色底、按钮变「已完成」；点击遮罩空白处收起。
 */
public class GuidePanel extends FrameLayout {

    /** 遮罩压暗色（同时作为截图失败时的兜底底）。 */
    private static final int MASK = 0x99000000;

    private final float d;
    private String themeId = "cucl2";
    private boolean dark = false;
    private String builtKey = null;

    private int cCard = 0xFFFFFFFF;
    private int cText = 0xFF000000;

    private LinearLayout panel;
    private LinearLayout rowEnable, rowSwitch;
    private TextView btnEnable, btnSwitch;

    private View backdropSource;
    private ImageView backdrop;

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
        String key = themeId + "/" + dark;
        if (!key.equals(builtKey)) {
            this.themeId = themeId;
            this.dark = dark;
            buildUi();
            builtKey = key;
        }
        refreshStatus();
    }

    public void show() {
        refreshBackdrop();
        if (getVisibility() != VISIBLE) setVisibility(VISIBLE);
    }

    /** 每次显示都重新截一次背景（主界面可能已经变过主题/滚动位置）。 */
    private void refreshBackdrop() {
        if (backdrop == null) return;
        UiBlur.apply(backdrop, backdropSource, MASK, 0xCC000000);
    }

    public void hide() {
        if (getVisibility() != GONE) setVisibility(GONE);
    }

    // ---------------- 构建 ----------------

    private void buildUi() {
        removeAllViews();
        int accent = ChemKeyboardView.themeColor(themeId);
        cCard = dark ? 0xFF000000 : 0xFFFFFFFF;
        cText = dark ? 0xFFFFFFFF : 0xFF000000;

        // 模糊背景（客户意见 5）：压暗 + 高斯模糊，替代原来的纯半透明黑遮罩
        backdrop = new ImageView(getContext());
        backdrop.setScaleType(ImageView.ScaleType.FIT_XY);
        addView(backdrop, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        panel = new LinearLayout(getContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClickable(true);   // 消费点击，避免穿透到遮罩把自己关掉
        GradientDrawable pg = new GradientDrawable();
        pg.setColor(cCard);
        pg.setCornerRadius(0);
        panel.setBackground(pg);

        // ---- 标题栏：主题色填充 + ▶ 操作引导 ----
        LinearLayout titleBar = new LinearLayout(getContext());
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setPadding(dp(16), dp(10), dp(16), dp(10));
        GradientDrawable tg = new GradientDrawable();
        tg.setColor(accent);
        tg.setCornerRadius(0);
        titleBar.setBackground(tg);
        TextView title = new TextView(getContext());
        title.setText("▶ 操作引导");
        title.setTextSize(20);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        title.setTextColor(onAccent(accent));
        titleBar.addView(title);
        panel.addView(titleBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // ---- 行 1：启用 接化发输入法 ----
        rowEnable = guideRow();
        btnEnable = (TextView) rowEnable.getChildAt(1);
        btnEnable.setOnClickListener(v -> {
            try {
                getContext().startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Throwable ignored) {
            }
        });
        panel.addView(rowEnable, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(57)));

        View div = new View(getContext());
        div.setBackgroundColor(dark ? 0x1AFFFFFF : 0x1A000000);
        panel.addView(div, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));

        // ---- 行 2：切换到 接化发输入法 ----
        rowSwitch = guideRow();
        btnSwitch = (TextView) rowSwitch.getChildAt(1);
        btnSwitch.setOnClickListener(v -> showPickerOrSettings());
        panel.addView(rowSwitch, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(57)));

        LayoutParams lp = new LayoutParams(dp(381), LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        addView(panel, lp);
    }

    /** 一行引导：左文案（weight 1）+ 右按钮 78×33。 */
    private LinearLayout guideRow() {
        LinearLayout r = new LinearLayout(getContext());
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(24), 0, dp(10), 0);

        // 文案必须显式垂直居中：TextView 默认 TOP 对齐，而这里为了配合右侧按钮
        // 把文案撑满了 57dp 行高，文字行盒只占约 20dp，不设 gravity 会整体贴顶，
        // 实测比右侧按钮高出约 36px（文案中心 1155 vs 按钮中心 1190）。
        TextView label = new TextView(getContext());
        label.setTextSize(20);
        label.setSingleLine();
        label.setGravity(Gravity.CENTER_VERTICAL);
        r.addView(label, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        TextView btn = new TextView(getContext());
        btn.setTextSize(20);
        btn.setGravity(Gravity.CENTER);
        btn.setMaxLines(1);
        r.addView(btn, new LinearLayout.LayoutParams(dp(78), dp(33)));
        return r;
    }

    // ---------------- 状态 ----------------

    private void refreshStatus() {
        if (rowEnable == null) return;
        Context ctx = getContext();
        boolean enabled = ImeStatusHelper.isEnabled(ctx);
        boolean current = ImeStatusHelper.isCurrent(ctx);
        styleRow(rowEnable, "启用 接化发输入法", "前往", enabled);
        styleRow(rowSwitch, "切换到 接化发输入法", enabled ? "前往" : "待启用", current);
        rowSwitch.setAlpha(enabled ? 1f : 0.45f);
        rowSwitch.setClickable(enabled);
        btnSwitch.setClickable(enabled);
    }

    private void styleRow(LinearLayout r, String label, String todoText, boolean done) {
        int accent = ChemKeyboardView.themeColor(themeId);
        TextView text = (TextView) r.getChildAt(0);
        TextView btn = (TextView) r.getChildAt(1);
        text.setText(label);
        text.setTextColor(done ? accent : cText);

        GradientDrawable rowBg = new GradientDrawable();
        rowBg.setColor(done ? withAlpha(accent, 0x33) : Color.TRANSPARENT);
        rowBg.setCornerRadius(0);
        r.setBackground(rowBg);

        GradientDrawable g = new GradientDrawable();
        g.setColor(done ? withAlpha(accent, 0x33) : cCard);
        g.setCornerRadius(0);
        g.setStroke(dp(1.3f), accent);
        btn.setBackground(g);
        btn.setTextColor(accent);
        btn.setText(done ? "已完成" : todoText);
    }

    private void showPickerOrSettings() {
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

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private static int onAccent(int accent) {
        double l = 0.299 * Color.red(accent) + 0.587 * Color.green(accent) + 0.114 * Color.blue(accent);
        return l < 160 ? 0xFFFFFFFF : 0xE6101418;
    }

    private int dp(float v) {
        return (int) (v * d);
    }
}

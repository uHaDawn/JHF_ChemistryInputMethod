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

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.jhf.jiehuafa.R;
import com.jhf.jiehuafa.SmoothDrawable;
import com.jhf.jiehuafa.UiStyle;

import java.util.ArrayList;
import java.util.List;

/**
 * 普通键盘「候选字界面」（Figma 216-519，键盘／普通输入／浅色·圆润）。
 *
 * 点击候选栏右侧的「→」箭头（不是收起键盘的 ⌄）后展开：整块面板铺满键区，
 * 顶栏是「返回 / 词频·单字 / ⌄」，主体左侧「首字母」筛选列 + 5×5 候选网格。
 * 2026-11-04 起底部「选择键盘模式 / 工具栏 / 切换输入法」芯片行删除（功能已移到候选栏快捷按键）。
 * 2026-11-05 顶栏「词频 / 单字」切换改为滑块动画：选中态从「给 TextView 换底色」改为
 *              一块独立白色滑块，在 260ms 内按 iOS 非线性曲线（0.32, 0.72, 0, 1）平移过去。
 *              滑块用 SmoothDrawable 绘制并随宽度按比例定位，任意屏宽/字号都落在两格正中。
 *
 * 本类只负责画界面与收点击，候选数据（含跨页收集与上屏）由 JhfImeService 提供。
 */
public class NormalCandPanel extends LinearLayout {

    public static final int TAB_FREQ = 0;     // 词频（默认，按引擎顺序）
    public static final int TAB_SINGLE = 1;   // 单字（只留单字候选）

    private static final int ROWS = 5;
    private static final int COLS = 5;
    private static final int MAX_INITIALS = 8;
    /** 词频 / 单字 滑块平移时长与曲线（iOS 标准非线性：先快后缓，收尾无回弹）。 */
    private static final long TAB_DUR = 260;
    private static final Interpolator TAB_EASE = new PathInterpolator(0.32f, 0.72f, 0f, 1f);

    /** 一个候选格子：文本 / 注释 / 在 Rime 中的页偏移与页内下标（上屏要用）。 */
    public static class Item {
        public final String text;
        public final String comment;
        public final int pageOffset;
        public final int indexInPage;

        public Item(String text, String comment, int pageOffset, int indexInPage) {
            this.text = text == null ? "" : text;
            this.comment = comment == null ? "" : comment;
            this.pageOffset = pageOffset;
            this.indexInPage = indexInPage;
        }
    }

    public interface Listener {
        /** 返回键盘（顶栏「返回」）。 */
        void onPanelBack();
        /** 收起键盘（顶栏 ⌄，与候选栏那颗同义）。 */
        void onPanelHide();
        /** 点候选格子。 */
        void onPanelSelect(Item item);
        /** 网格右下角翻页箭头。 */
        void onPanelPage(boolean next);
        /** 底部「选择键盘模式」。 */
        void onPanelMode();
        /** 底部「切换输入法」。 */
        void onPanelIme();
        /** 词频 / 单字 切页。 */
        void onPanelTab(int tab);
        /** 底部「工具栏」：回到键盘的工具栏页。 */
        void onPanelToolbar();
    }

    private final Listener listener;
    private boolean dark = false;
    private int accent = 0xFF2E73F3;

    private LinearLayout topRow, navBox, navLetters;
    /** 词频 / 单字 的分段容器（顶层是 FrameLayout，选中滑块垫在文字下面）。 */
    private FrameLayout segGroup;
    private SegThumb segThumb;
    private LinearLayout pagerBox;      // 第 25 格：两枚翻页箭头（设计稿右下角 union 图标）
    private TextView pagerPrev, pagerNext;
    private FrameLayout gridFrame;
    private TextView backChip, tabFreq, tabSingle, emptyHint, navTitle;
    /** 顶栏收起键盘键：与主键盘候选栏那顆 ⌄ 用同一个 ImageView 图标芯片（2026-11-02）。 */
    private ImageView hideChip;
    private TextView[] cells = new TextView[ROWS * COLS];
    private final List<View> dividers = new ArrayList<>();

    private final List<Item> items = new ArrayList<>();
    private final List<String> initials = new ArrayList<>();
    private int tab = TAB_FREQ;
    private String initialFilter = "";

    public NormalCandPanel(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        float d = context.getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        setPadding((int) (7 * d), (int) (8 * d), (int) (7 * d), (int) (9 * d));

        // ---- 顶栏：返回 · 词频/单字 · ⌄ ----
        topRow = new LinearLayout(context);
        topRow.setOrientation(HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        backChip = pillText("返回", 17, d);
        backChip.setOnClickListener(v -> listener.onPanelBack());
        topRow.addView(backChip, lp((int) (75 * d), (int) (35 * d), 0));

        segGroup = new FrameLayout(context);
        // 底层滑块：铺满分段容器，自己按当前格位画白色胶囊（切换时平移，不再换 TextView 底色）
        segThumb = new SegThumb(context);
        FrameLayout.LayoutParams thumbLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, (int) (31 * d));
        thumbLp.topMargin = (int) (2 * d);
        segGroup.addView(segThumb, thumbLp);

        LinearLayout segTabs = new LinearLayout(context);
        segTabs.setOrientation(HORIZONTAL);
        segTabs.setGravity(Gravity.CENTER_VERTICAL);
        segTabs.setPadding((int) (2 * d), (int) (2 * d), (int) (2 * d), (int) (2 * d));
        tabFreq = segTab("词频", d);
        tabSingle = segTab("单字", d);
        tabFreq.setOnClickListener(v -> switchTab(TAB_FREQ));
        tabSingle.setOnClickListener(v -> switchTab(TAB_SINGLE));
        segTabs.addView(tabFreq, new LinearLayout.LayoutParams(0, (int) (31 * d), 1f));
        segTabs.addView(tabSingle, new LinearLayout.LayoutParams(0, (int) (31 * d), 1f));
        segGroup.addView(segTabs, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams segLp = new LinearLayout.LayoutParams(
                0, (int) (35 * d), 1f);
        segLp.setMargins((int) (8 * d), 0, (int) (8 * d), 0);
        topRow.addView(segGroup, segLp);

        hideChip = new ImageView(context);
        hideChip.setScaleType(ImageView.ScaleType.CENTER);
        hideChip.setImageResource(R.drawable.ic_chevron_down_small);
        hideChip.setOnClickListener(v -> listener.onPanelHide());
        topRow.addView(hideChip, lp((int) (35 * d), (int) (35 * d), 0));
        addView(topRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (35 * d)));

        // ---- 主体：首字母列 + 5×5 候选网格 ----
        LinearLayout bodyRow = new LinearLayout(context);
        bodyRow.setOrientation(HORIZONTAL);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        bodyLp.setMargins(0, (int) (7 * d), 0, 0);
        bodyRow.setLayoutParams(bodyLp);

        navBox = new LinearLayout(context);
        navBox.setOrientation(VERTICAL);
        navBox.setGravity(Gravity.CENTER_HORIZONTAL);
        navBox.setPadding((int) (5 * d), (int) (10 * d), (int) (5 * d), (int) (15 * d));
        navTitle = new TextView(context);
        navTitle.setText("首字母");
        navTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        navTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        navTitle.setGravity(Gravity.CENTER);
        navTitle.setIncludeFontPadding(false);
        navBox.addView(navTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (22 * d)));
        navLetters = new LinearLayout(context);
        navLetters.setOrientation(VERTICAL);
        navBox.addView(navLetters, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        bodyRow.addView(navBox, new LinearLayout.LayoutParams((int) (65 * d),
                LinearLayout.LayoutParams.MATCH_PARENT));

        gridFrame = new FrameLayout(context);
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        gridLp.setMargins((int) (9 * d), 0, 0, 0);
        bodyRow.addView(gridFrame, gridLp);
        LinearLayout grid = new LinearLayout(context);
        grid.setOrientation(VERTICAL);
        gridFrame.addView(grid, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        for (int r = 0; r < ROWS; r++) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(HORIZONTAL);
            for (int c = 0; c < COLS; c++) {
                LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
                if (r == ROWS - 1 && c == COLS - 1) {
                    // 设计稿最后一格不是候选，而是灰底圆角的「‹ ›」翻页键（maskGroup / rectangle546）
                    pagerBox = new LinearLayout(context);
                    pagerBox.setOrientation(HORIZONTAL);
                    pagerBox.setGravity(Gravity.CENTER);
                    pagerPrev = pagerArrow("‹", d);
                    pagerNext = pagerArrow("›", d);
                    pagerPrev.setOnClickListener(v -> listener.onPanelPage(false));
                    pagerNext.setOnClickListener(v -> listener.onPanelPage(true));
                    pagerBox.addView(pagerPrev, new LinearLayout.LayoutParams(0,
                            LinearLayout.LayoutParams.MATCH_PARENT, 1f));
                    pagerBox.addView(pagerNext, new LinearLayout.LayoutParams(0,
                            LinearLayout.LayoutParams.MATCH_PARENT, 1f));
                    row.addView(pagerBox, cellLp);
                    continue;
                }
                TextView cell = new TextView(context);
                cell.setGravity(Gravity.CENTER);
                cell.setSingleLine();
                cell.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
                cell.setIncludeFontPadding(false);
                cell.setPadding((int) (2 * d), 0, (int) (2 * d), 0);
                final int index = r * COLS + c;
                cell.setOnClickListener(v -> onCellClick(index));
                cells[index] = cell;
                row.addView(cell, cellLp);
            }
            grid.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
            if (r < ROWS - 1) {
                View line = new View(context);
                dividers.add(line);
                grid.addView(line, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, (int) d));
            }
        }

        emptyHint = new TextView(context);
        emptyHint.setText("继续输入拼音，候选会出现在这里");
        emptyHint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        emptyHint.setGravity(Gravity.CENTER);
        emptyHint.setVisibility(GONE);
        gridFrame.addView(emptyHint, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        addView(bodyRow, bodyLp);

        style();
    }

    // ---------------- 外部接口 ----------------

    /** 设置候选数据与可用于筛选的首字母。 */
    public void setData(List<Item> list, List<String> initialList) {
        items.clear();
        if (list != null) items.addAll(list);
        initials.clear();
        if (initialList != null) initials.addAll(initialList);
        if (!initialFilter.isEmpty() && !initials.contains(initialFilter)) initialFilter = "";
        rebuildNav();
        renderGrid();
    }

    public void setDark(boolean dark) {
        this.dark = dark;
        style();
    }

    public void setAccent(int color) {
        this.accent = color;
        style();
        renderGrid();
    }

    public void setTab(int newTab) {
        if (tab != newTab) {
            tab = newTab;
            renderGrid();
        }
        styleTabs();
    }

    public int getTab() {
        return tab;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    // ---------------- 内部绘制 ----------------

    private float dp() {
        return getResources().getDisplayMetrics().density;
    }

    private LinearLayout.LayoutParams lp(int w, int h, float marginLeftDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins((int) (marginLeftDp * dp()), 0, 0, 0);
        return p;
    }

    private TextView pillText(String text, float sizeDp, float d) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizeDp);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine();
        tv.setIncludeFontPadding(false);
        tv.setPadding((int) (8 * d), 0, (int) (8 * d), 0);
        return tv;
    }

    private TextView segTab(String text, float d) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine();
        tv.setIncludeFontPadding(false);
        return tv;
    }

    private TextView pagerArrow(String text, float d) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine();
        tv.setIncludeFontPadding(false);
        return tv;
    }

    private SmoothDrawable round(int color, float radiusDp) {
        SmoothDrawable g = new SmoothDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusDp * dp());
        return g;
    }

    private void style() {
        // 2026-11-09 新配色：与键盘底板一致（浅 #E0E0E0 / 深 #252525）
        setBackgroundColor(dark ? 0xFF252525 : 0xFFE0E0E0);
        // 顶栏
        backChip.setTextColor(accent);
        backChip.setBackground(round(dark ? 0xFF484848 : Color.WHITE, 25));
        // 收起键：与主键盘候选栏 ⌄ 完全同款（JhfImeService.restyleIconChip，2026-11-02）
        hideChip.setImageResource(R.drawable.ic_chevron_down_small);
        SmoothDrawable hideBg = new SmoothDrawable();
        hideBg.setColor(dark ? 0xFF484848 : Color.WHITE);
        hideBg.setCornerRadius(23 * dp());
        hideChip.setColorFilter(dark ? 0xFFFFFFFF : 0xFF000000);
        hideChip.setBackground(hideBg);
        // 分段底板：纯色胶囊，不再叠 1dp 深色下层（2026-11-11 去掉滑块背景胶囊的阴影）
        segGroup.setBackground(round(dark ? 0xFF414141 : 0xFFC9C9C9, 25));
        styleTabs();
        // 主体卡片
        navBox.setBackground(round(dark ? 0xFF313131 : Color.WHITE, 10));
        navTitle.setTextColor(dark ? 0xFF8A959B : 0xFFADADAD);
        for (View v : dividers) v.setBackgroundColor(dark ? 0x22FFFFFF : 0x12000000);
        emptyHint.setTextColor(dark ? 0xFF8A959B : 0xFF9AA6AC);
        // 翻页格（第 25 格）：灰底圆角 6，两枚箭头用强调色
        if (pagerBox != null) {
            pagerBox.setBackground(round(dark ? 0xFF4A4A4A : 0xFFD9D9D9, 6));
            pagerPrev.setTextColor(dark ? 0xFFFFFFFF : 0xFF000000);
            pagerNext.setTextColor(dark ? 0xFFFFFFFF : 0xFF000000);
            boolean has = !items.isEmpty();
            pagerPrev.setVisibility(has ? VISIBLE : INVISIBLE);
            pagerNext.setVisibility(has ? VISIBLE : INVISIBLE);
        }
        if (gridFrame != null) {
            gridFrame.setBackground(round(dark ? 0xFF313131 : Color.WHITE, 10));
        }
        renderGrid();
    }

    private void styleTabs() {
        // 选中态由滑块承担（底色/字色只决定滑块与文字，不再给 TextView 挂背景）
        segThumb.setColor(dark ? 0xFF4A4A4A : Color.WHITE);
        TextView[] tabs = {tabFreq, tabSingle};
        for (int i = 0; i < 2; i++) {
            boolean selected = tab == i;
            tabs[i].setBackground(null);
            tabs[i].setTextColor(selected
                    ? (dark ? 0xFFE9EDF0 : 0xFF000000)
                    : (dark ? 0xFF9AA6AC : 0xFF000000));
            tabs[i].setTypeface(Typeface.create(
                    selected ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        }
        segThumb.setTarget(tab == TAB_SINGLE ? 1f : 0f);
    }

    /**
     * 顶栏分段滑块：整条容器里画一块圆角胶囊，frac = 0 时落在「词频」格、1 时落在「单字」格。
     * 切换只改 target，由 ValueAnimator + iOS 非线性曲线平移过去；首次布局前（宽度还是 0）
     * 直接落位，避免进面板时滑块从左边滑进来。
     */
    private class SegThumb extends View {
        private final SmoothDrawable pill = new SmoothDrawable();
        private final float d;
        private float frac = 0f;
        private float target = 0f;
        private ValueAnimator anim;

        SegThumb(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            pill.setCornerRadius(17 * d);
        }

        void setColor(int color) {
            pill.setColor(color);
            invalidate();
        }

        void setTarget(float t) {
            if (t == target) return;   // 目标没变：要么已就位，要么动画正朝它跑
            target = t;
            if (getWidth() == 0) {     // 还没量出宽度，直接落位
                frac = t;
                invalidate();
                return;
            }
            if (anim != null) anim.cancel();
            ValueAnimator a = ValueAnimator.ofFloat(frac, t);
            a.setDuration(TAB_DUR);
            a.setInterpolator(TAB_EASE);
            a.addUpdateListener(an -> {
                frac = (float) an.getAnimatedValue();
                invalidate();
            });
            anim = a;
            a.start();
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            float pad = 2 * d;                          // 与 segTabs 内边距一致
            float slot = (getWidth() - pad * 2) / 2f;   // 单格宽：两格等分
            if (slot <= 0) return;
            float left = pad + frac * slot;
            pill.setBounds((int) left, 0, (int) (left + slot), getHeight());
            pill.draw(c);
        }
    }

    /** 首字母筛选列：只列当前候选里出现过的首字母。 */
    private void rebuildNav() {
        navLetters.removeAllViews();
        float d = dp();
        List<String> shown = new ArrayList<>();
        for (String s : initials) {
            if (s == null || s.isEmpty() || shown.contains(s)) continue;
            shown.add(s);
            if (shown.size() >= MAX_INITIALS) break;
        }
        for (String s : shown) {
            TextView tv = new TextView(getContext());
            tv.setText(s);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
            tv.setGravity(Gravity.CENTER);
            tv.setSingleLine();
            tv.setIncludeFontPadding(false);
            tv.setBackground(initialFilter.equals(s)
                    ? round(dark ? 0xFF4A4A4A : 0xFFD9D9D9, 17) : null);
            tv.setTextColor(dark ? 0xFFE9EDF0 : 0xFF000000);
            tv.setOnClickListener(v -> {
                initialFilter = initialFilter.equals(s) ? "" : s;
                rebuildNav();
                renderGrid();
            });
            navLetters.addView(tv, lp((int) (65 * d), (int) (26 * d), 0));
        }
        if (shown.isEmpty()) initialFilter = "";
    }

    private List<Item> filtered() {
        List<Item> out = new ArrayList<>();
        for (Item it : items) {
            if (tab == TAB_SINGLE && it.text.length() > 1) continue;
            if (!initialFilter.isEmpty()) {
                String ini = NormalPinyinMatcher.initialOf(it.text);
                if (ini == null || ini.isEmpty() || ini.charAt(0) != initialFilter.charAt(0)) continue;
            }
            out.add(it);
        }
        return out;
    }

    private void renderGrid() {
        List<Item> list = filtered();
        int cellCount = ROWS * COLS - 1;   // 最后一格留给翻页键
        emptyHint.setVisibility(list.isEmpty() ? VISIBLE : GONE);
        for (int i = 0; i < cellCount; i++) {
            TextView cell = cells[i];
            if (cell == null) continue;
            if (i < list.size()) {
                Item it = list.get(i);
                cell.setText(it.text);
                cell.setVisibility(VISIBLE);
                if (i == 0) {
                    // 首个候选灰底高亮（设计稿 rectangle546）
                    cell.setBackground(round(dark ? 0xFF4A4A4A : 0xFFD9D9D9, 6));
                    cell.setTextColor(dark ? 0xFFFFFFFF : 0xFF000000);
                } else {
                    cell.setBackground(null);
                    cell.setTextColor(dark ? 0xFFE9EDF0 : 0xFF000000);
                }
            } else {
                cell.setText("");
                cell.setVisibility(INVISIBLE);
            }
        }
        if (pagerBox != null) {
            boolean has = !items.isEmpty();
            pagerPrev.setVisibility(has ? VISIBLE : INVISIBLE);
            pagerNext.setVisibility(has ? VISIBLE : INVISIBLE);
        }
    }

    private void onCellClick(int index) {
        List<Item> list = filtered();
        if (index >= 0 && index < list.size()) listener.onPanelSelect(list.get(index));
    }

    private void switchTab(int newTab) {
        if (tab == newTab) return;
        tab = newTab;
        initialFilter = "";
        rebuildNav();
        styleTabs();
        renderGrid();
        listener.onPanelTab(newTab);
    }
}

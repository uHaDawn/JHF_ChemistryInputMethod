package com.jhf.jiehuafa.ime;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.app.WallpaperManager;
import android.app.WallpaperColors;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 接化发键盘（Figma 设计稿样式）：全直角白键 + 底影 + 主题强调色。
 *
 * 字母页：QWERTY 三排 + 功能行「拼｜素 · ， · 空格 · ←」+ 底部细条 ⇌（切换系统输入法）。
 * 符号页：左侧 5 分类竖向 tab（度量 / 物质 / 反应 / 角标 / 其他）+ 右侧符号网格 + 功能行。
 * 收起键盘的入口在候选栏右侧的 ⌄ 芯片（服务侧 JhfImeService）。
 *
 * 手势：
 *   点按 = 主字符；上滑 = 化学符号（带预览气泡）；长按 = 形态三选（数字/符号 · 大写 · 小写）。
 */
public class ChemKeyboardView extends View {

    public interface Listener {
        void onChar(String s);
        void onAction(int action);

        /**
         * 长按浮层状态变化（opts == null 表示收起）。
         * 坐标已在 IME 根布局坐标系里，交给叠在根布局上的 overlay 绘制。
         *
         * @param hover 当前手指选中的候选下标，-1 表示未选中
         * @param ax/ay 被长按按键的上边中点（浮层与按键之间的连线用）
         */
        default void onLongPressPopup(List<String> opts, RectF box, int hover, float ax, float ay) {
        }
    }

    public static final int ACT_SHIFT = 1;
    public static final int ACT_MODE = 2;   // 拼/直
    public static final int ACT_CASE = 3;   // 化：元素自动大小写
    public static final int ACT_SPACE = 4;
    public static final int ACT_ENTER = 5;
    public static final int ACT_BACK = 6;
    public static final int ACT_PAGE = 7;   // 符号页/字母页
    /**
     * 底部 ⇌ 条 = 唤起系统输入法切换器（设计稿 §3.5.4）。
     * 客户第三轮验收明确：底部 ⇌ 就是切换输入法；当初按「客户 2026-10-11」改成的收起键盘是改错了键位。
     */
    public static final int ACT_IME = 8;
    /** 收起键盘（候选栏右侧的 ⌄ 芯片 = 客户第三轮指定的收起键盘入口）。 */
    public static final int ACT_HIDE = 9;

    /** 符号页分类名（顺序即 tab 顺序）。 */
    public static final String[] SYM_CAT_NAMES = {"度量", "物质", "反应", "角标", "其他"};

    private static final int PAGE_LETTER = 0, PAGE_SYMBOL = 1;
    private static final int ROW_TAB = -2;   // pressedRow 标记：命中的是分类 tab

    private static final int STYLE_NORMAL = 0, STYLE_SPECIAL = 1, STYLE_ACCENT = 2, STYLE_PLAIN = 3;

    private static class Key {
        String label, swipe, chem, alt;
        int action = 0;
        float weight = 1f;
        int style = STYLE_NORMAL;
        /** 角标与主字同轴上下堆叠（设计稿的「，/。」键），而非贴右下角。 */
        boolean stackChem = false;
    }

    private static Key mk(String label, String swipe, String chem) {
        return mk(label, swipe, chem, null);
    }

    /** alt = 长按首选项（数字/符号的普通形态，设计稿：T → 5 / T / t）。 */
    private static Key mk(String label, String swipe, String chem, String alt) {
        Key key = new Key();
        key.label = label;
        key.swipe = swipe;
        key.chem = chem;
        key.alt = alt;
        return key;
    }

    private static Key sp(String label, int action) {
        Key key = new Key();
        key.label = label;
        key.action = action;
        key.style = STYLE_SPECIAL;
        return key;
    }

    private static Key sp(String label, int action, float weight) {
        Key key = sp(label, action);
        key.weight = weight;
        return key;
    }

    private static Key accentKey(String label, int action) {
        return accentKey(label, action, 1f);
    }

    private static Key accentKey(String label, int action, float weight) {
        Key key = sp(label, action, weight);
        key.style = STYLE_ACCENT;
        return key;
    }

    /** 底部 ⇌ 条：无背景细条，点击唤起系统输入法切换器（字母页与符号页都有）。 */
    private static List<Key> imeRow() {
        Key ime = new Key();
        ime.label = "⇌";
        ime.action = ACT_IME;
        ime.style = STYLE_PLAIN;
        return row(ime);
    }

    private static List<Key> row(Key... keys) {
        return new ArrayList<>(Arrays.asList(keys));
    }

    // ---------------- 页 / 分类状态 ----------------
    private int page = PAGE_LETTER;
    private int symbolCat = 0;

    private List<List<Key>> letterRows;
    private final List<List<List<Key>>> symbolCats = new ArrayList<>();

    private boolean shiftOn = false;
    private boolean pinyinMode = true;
    private boolean autoCase = true;
    private boolean darkMode = false;

    // 主题色
    private int accent = 0xFF05BEC1;
    private int accentDark = 0xFF05696B;
    private int kbBg = 0xFFEAEAEA, keyBgC = 0xFFFFFFFF, keySpecialC = 0xFFF4F4F4;
    private int labelColor = 0xFF000000, hintColor = 0x66000000;
    private int shadowColor = 0x26000000;
    private int offColor = 0x24000000;

    private final Listener listener;
    private final float d;
    private final float padH, padBottom, gap, vGap, keyH, imeH, radius, tabW, tabGap;

    private final Paint bgNormal = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgSpecial = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgAccent = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPressed = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint letterShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowAccent = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chemPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tabPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);   // 分类卡内分隔线
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);      // 回车 / 收起键盘的手绘图标
    private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubbleBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubbleText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF rect2 = new RectF();

    private int pressedRow = -1, pressedCol = -1;
    private float downX, downY, curX, curY;
    private boolean swipeFired = false, longPressDone = false;
    private String bubbleChar = null;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable longPressRunnable = this::fireLongPress;

    /** 长按浮层状态（自绘 + 滑动选择，客户意见 3/6）。opts 为 null 表示未显示。 */
    private List<String> popupOpts;
    private int popupIndex = -1;
    private final RectF popupBox = new RectF();
    private float popupAnchorX, popupAnchorY;

    public ChemKeyboardView(Context ctx, Listener l) {
        super(ctx);
        listener = l;
        d = getResources().getDisplayMetrics().density;
        padH = 8 * d;
        padBottom = 2 * d;       // 底部留白收窄（原与顶部同为 8dp，客户反馈太空）
        gap = 7.5f * d;
        vGap = 11.5f * d;
        keyH = 46 * d;
        imeH = 34 * d;           // 底部 ⇌ 条高度（原 52dp 过高）
        radius = 0;              // 设计稿全部直角矩形
        tabW = 118 * d;          // 符号页左侧分类栏宽度
        tabGap = 11.5f * d;
        bubbleBorder.setStyle(Paint.Style.STROKE);
        bubbleBorder.setStrokeWidth(d);
        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeCap(Paint.Cap.ROUND);
        iconPaint.setStrokeJoin(Paint.Join.ROUND);
        dividerPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        hintPaint.setTextAlign(Paint.Align.RIGHT);
        chemPaint.setTextAlign(Paint.Align.RIGHT);
        tabPaint.setTextAlign(Paint.Align.CENTER);
        tabPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        bubbleText.setTextAlign(Paint.Align.CENTER);
        bubbleText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        setLongClickable(false);
        buildPages();
        applyThemeColors();
    }

    /** 「自动」主题：跟随系统壁纸取色（客户 2026-10-11 意见 11）。 */
    public static final String THEME_AUTO = "auto";
    private static int autoAccent = 0xFF5C6BC0;
    private static int autoAccentDark = 0xFF3949AB;

    /**
     * 解析「自动」主题的颜色并缓存到静态字段。
     *
     * Android 12+ 直接取系统 Monet 调色板（system_accent1_*，由壁纸生成）；
     * 低版本退回「壁纸主导色」。必须在 Activity / IME 启动时调用一次。
     */
    public static void resolveAutoColors(Context ctx) {
        int a = 0, d = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                a = ctx.getResources().getColor(android.R.color.system_accent1_500, ctx.getTheme());
                d = ctx.getResources().getColor(android.R.color.system_accent1_700, ctx.getTheme());
            } catch (Throwable ignored) {
            }
        }
        if (a == 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            try {
                WallpaperManager wm = WallpaperManager.getInstance(ctx);
                WallpaperColors wc = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
                if (wc != null && wc.getPrimaryColor() != null) {
                    a = wc.getPrimaryColor().toArgb();
                    d = darken(a);
                }
            } catch (Throwable ignored) {
            }
        }
        if (a != 0) {
            autoAccent = a;
            autoAccentDark = (d != 0) ? d : darken(a);
        }
    }

    private static int darken(int c) {
        float f = 0.62f;
        return Color.rgb((int) (Color.red(c) * f), (int) (Color.green(c) * f), (int) (Color.blue(c) * f));
    }

    /** Figma 设计稿四主题 + 「自动」（壁纸取色）：AgBr 金 / KMnO₄ 紫 / CuCl₂ 青 / Fe₂O₃ 红。 */
    public static int themeColor(String id) {
        if (THEME_AUTO.equals(id)) return autoAccent;
        switch (id) {
            case "agbr": return 0xFFE7C400;
            case "kmno4": return 0xFF8F2DB9;
            case "fe2o3": return 0xFFB92D2D;
            case "cucl2":
            default: return 0xFF05BEC1;
        }
    }

    /** 主题深色（键底影 / 按压 / 状态栏）。 */
    public static int themeColorDark(String id) {
        if (THEME_AUTO.equals(id)) return autoAccentDark;
        switch (id) {
            case "agbr": return 0xFF8F7A00;
            case "kmno4": return 0xFF55196F;
            case "fe2o3": return 0xFF6E1A1A;
            case "cucl2":
            default: return 0xFF05696B;
        }
    }

    public void setThemeColors(String themeId, boolean dark) {
        accent = themeColor(themeId);
        accentDark = themeColorDark(themeId);
        darkMode = dark;
        applyThemeColors();
        invalidate();
    }

    /** 主题色偏暗时用白字，偏亮时用深字，保证对比度。 */
    private int onAccentColor() {
        return isDarkColor(accent) ? 0xFFFFFFFF : 0xE6101418;
    }

    private static boolean isDarkColor(int c) {
        return (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) < 160;
    }

    private void applyThemeColors() {
        if (darkMode) {
            kbBg = 0xFF313131;
            keyBgC = 0xFF000000;
            keySpecialC = keyBgC;
            labelColor = 0xFFFFFFFF;
            hintColor = 0xFFFFFFFF;
            shadowColor = 0x26FFFFFF;
            offColor = 0x6EFFFFFF;
        } else {
            kbBg = 0xFFEAEAEA;
            keyBgC = 0xFFFFFFFF;
            keySpecialC = keyBgC;
            labelColor = 0xFF000000;
            hintColor = 0xFF000000;
            shadowColor = 0x26000000;
            offColor = 0x24000000;
        }
        bgNormal.setColor(keyBgC);
        bgSpecial.setColor(keySpecialC);
        bgAccent.setColor(accent);
        bgPressed.setColor(darkMode ? 0xFF2E2E2E : 0xFFE0E0E0);
        shadowPaint.setColor(shadowColor);
        letterShadow.setColor(accent);
        shadowAccent.setColor(accentDark);
        textPaint.setColor(labelColor);
        hintPaint.setColor(labelColor);
        chemPaint.setColor(accent);
        bubblePaint.setColor(darkMode ? 0xFF1E1E1E : 0xFFFFFFFF);
        bubbleBorder.setColor(accent);
        bubbleText.setColor(labelColor);
        dividerPaint.setColor(darkMode ? 0x1AFFFFFF : 0x14000000);
        setBackgroundColor(kbBg);
    }

    // ---------------- 页数据 ----------------

    private void buildPages() {
        buildLetterRows();
        buildSymbolCats();
    }

    /**
     * 字母页：QWERTY 三排 + 功能行 + 底部 ⇌ 细条。
     * r1 右上=上标数字、右下=下标数字，长按 alt=普通数字（设计稿 T → 5 / T / t）。
     * r2/r3 右上=上滑化学符号、右下=另一化学符号。
     */
    private void buildLetterRows() {
        letterRows = new ArrayList<>();

        letterRows.add(row(
                mk("Q", "¹", "₁", "1"), mk("W", "²", "₂", "2"),
                mk("E", "³", "₃", "3"), mk("R", "⁴", "₄", "4"),
                mk("T", "⁵", "₅", "5"), mk("Y", "⁶", "₆", "6"),
                mk("U", "⁷", "₇", "7"), mk("I", "⁸", "₈", "8"),
                mk("O", "⁹", "₉", "9"), mk("P", "⁰", "₀", "0")));

        letterRows.add(row(
                mk("A", "α", "Å"), mk("S", "σ", "Σ"),
                mk("D", "[", "]"), mk("F", "{", "}"),
                mk("G", "(", ")"), mk("H", "←", "→"),
                mk("J", "↑", "↓"), mk("K", "⇌", "↔"),
                mk("L", "·", "×")));

        letterRows.add(row(
                sp("⇧", ACT_SHIFT),
                mk("Z", "Δ", "°"), mk("X", "℃", "±"),
                mk("C", "ν", "λ"), mk("V", "σ", "π"),
                mk("B", "=", "≡"), mk("N", "+", "−"),
                mk("M", "%", "‰"),
                sp("⌫", ACT_BACK)));

        // 功能行权重对齐设计稿（量自 52218da0 底部行）：拼｜素 2.2 / ，1 / 空格 4.6 / ← 2.2
        // 逗号键宽 = 上面 Z 行的字母键宽（该行 9 键，单位宽 36.35dp @402dp 画板）。
        Key comma = sp("，", 0);
        comma.chem = "。";
        comma.stackChem = true;
        letterRows.add(row(
                sp("拼｜素", ACT_MODE, 2.2f),
                comma,
                sp("空格", ACT_SPACE, 4.6f),
                accentKey("←", ACT_ENTER, 2.2f)));

        letterRows.add(imeRow());
    }

    /**
     * 符号页 5 分类，每分类 3 行内容 + 1 行功能键（空格 / ⌫ / ←）。
     * 「度量 / 物质 / 反应 / 其他」取自设计稿；「角标」及「其他」第三行为拟定内容。
     */
    private void buildSymbolCats() {
        symbolCats.clear();

        symbolCats.add(cat(          // 0 度量
                row(mk("°F", null, null), mk("°C", null, null), mk("μ", null, null)),
                row(mk("mol", null, null), mk("ε", null, null), mk("ρ", null, null)),
                row(mk("/", null, null), mk("Δ", null, null), mk("-", null, null))));

        symbolCats.add(cat(          // 1 物质
                row(sym("H°"), sym("G°"), sym("α"), sym("β"), sym("γ"), sym("δ")),
                row(sym("ε"), sym("ζ"), sym("θ"), sym("μ"), sym("ν"), sym("π")),
                row(sym("ρ"), sym("σ"), sym("τ"), sym("ψ"), sym("ω"), sym("Ĥ"))));

        symbolCats.add(cat(          // 2 反应
                row(sym("‡"), sym("∵"), sym("∴"), sym("⋮"), sym("≡"), sym("Δ")),
                row(sym("×"), sym("⬡"), sym("⬢"), sym("⇅"), sym("⇌"), sym("↺")),
                row(sym("→"), sym("←"), sym("↔"), sym("⇄"), sym("↑"), sym("↓"))));

        symbolCats.add(cat(          // 3 角标
                row(sym("⁰"), sym("¹"), sym("²"), sym("³"), sym("⁴"),
                        sym("⁵"), sym("⁶"), sym("⁷"), sym("⁸"), sym("⁹")),
                row(sym("₀"), sym("₁"), sym("₂"), sym("₃"), sym("₄"),
                        sym("₅"), sym("₆"), sym("₇"), sym("₈"), sym("₉")),
                row(sym("⁺"), sym("⁻"), sym("⁼"), sym("⁽"), sym("⁾"),
                        sym("₍"), sym("₎"), sym("ⁿ"), sym("·"), sym("⇌"))));

        symbolCats.add(cat(          // 4 其他
                row(sym("a"), sym("β"), sym("⊕"), sym("⊖"), sym("λ"), sym("%")),
                row(sym("-"), sym("="), sym("≡"), sym("⊓")),
                row(sym("("), sym(")"), sym("["), sym("]"), sym("{"), sym("}"))));

        // 每个分类统一「3 行内容 + 功能行 + ⇌ 条」，保证切分类高度一致，
        // 同时让符号页也有切换系统输入法的入口（客户意见 6）。
        for (List<List<Key>> c : symbolCats) {
            c.add(symFuncRow());
            c.add(imeRow());
        }
    }

    @SafeVarargs
    private static List<List<Key>> cat(List<Key>... rows) {
        return new ArrayList<>(Arrays.asList(rows));
    }

    /** 符号页纯符号键：无上滑/角标，长按不弹窗。 */
    private static Key sym(String label) {
        Key key = new Key();
        key.label = label;
        return key;
    }

    private static List<Key> symFuncRow() {
        return row(
                sp("空格", ACT_SPACE, 2.2f),
                sp("⌫", ACT_BACK),
                accentKey("←", ACT_ENTER));
    }

    // ---------------- 对外控制 ----------------

    public boolean isSymbolPage() {
        return page == PAGE_SYMBOL;
    }

    public int getSymbolCategory() {
        return symbolCat;
    }

    public void setSymbolCategory(int index) {
        if (index < 0 || index >= symbolCats.size() || index == symbolCat) return;
        symbolCat = index;
        invalidate();
    }

    public void togglePage() {
        page = (page == PAGE_LETTER) ? PAGE_SYMBOL : PAGE_LETTER;
        requestLayout();
        invalidate();
    }

    public void setShiftOn(boolean on) {
        shiftOn = on;
        invalidate();
    }

    public void setAutoCase(boolean on) {
        autoCase = on;
        invalidate();
    }

    public void setPinyinMode(boolean on) {
        pinyinMode = on;
        invalidate();
    }

    // ---------------- 布局计算 ----------------

    private int rowCount() {
        return page == PAGE_LETTER ? letterRows.size() : symbolCats.get(symbolCat).size();
    }

    private List<Key> rowKeys(int row) {
        return page == PAGE_LETTER ? letterRows.get(row) : symbolCats.get(symbolCat).get(row);
    }

    private float rowH(int row) {
        return rowKeys(row).get(0).style == STYLE_PLAIN ? imeH : keyH;
    }

    /** 符号页右侧网格左边界（跳过左侧分类栏）。 */
    private float symGridLeft() {
        return padH + tabW + gap;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        float h = padH + padBottom;
        for (int r = 0; r < rowCount(); r++) h += rowH(r) + vGap;
        setMeasuredDimension(w, (int) h);
    }

    private float[] keyRect(int row, int col, int w) {
        List<Key> r = rowKeys(row);
        float sumW = 0;
        for (Key key : r) sumW += key.weight;
        boolean sym = (page == PAGE_SYMBOL);
        float left = sym ? symGridLeft() : padH;
        float avail = (sym ? (w - padH - symGridLeft()) : (w - 2 * padH)) - (r.size() - 1) * gap;
        float x = left;
        for (int i = 0; i < col; i++) x += avail * (r.get(i).weight / sumW) + gap;
        float kw = avail * (r.get(col).weight / sumW);
        float y = padH;
        for (int i = 0; i < row; i++) y += rowH(i) + vGap;
        return new float[]{x, y, x + kw, y + rowH(row)};
    }

    /**
     * 分类栏内容区高度 = 3 行内容 + 功能行的高度 + 行间距（不含最后一行之后的间距，
     * 也不含底部 ⇌ 条）。底边与右侧功能行底边严格对齐。
     */
    private float tabContentH() {
        int rows = rowCount();
        if (rows > 0 && rowKeys(rows - 1).get(0).style == STYLE_PLAIN) rows--; // 排除 ⇌ 条
        float h = 0;
        for (int r = 0; r < rows; r++) h += rowH(r);
        return h + Math.max(0, rows - 1) * vGap;
    }

    /**
     * 分类 tab 矩形。整列是**一张卡片**，5 项在卡内均分、无间隙（设计稿 39ed7978），
     * 底边与右侧功能行底边对齐。
     *
     * 之前用「Σ(行高 + 行间距)」当总高，多算了一个行间距，导致「其他」比功能行低 11.5dp
     * —— 这就是客户说的「底部没有对齐」。
     */
    private float[] tabRect(int i) {
        float h = tabContentH() / SYM_CAT_NAMES.length;
        float y = padH + i * h;
        return new float[]{padH, y, padH + tabW, y + h};
    }

    // ---------------- 绘制 ----------------

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w = getWidth();
        if (page == PAGE_SYMBOL) drawCategoryTabs(c);
        int rows = rowCount();
        for (int r = 0; r < rows; r++) {
            List<Key> keys = rowKeys(r);
            for (int i = 0; i < keys.size(); i++) {
                Key key = keys.get(i);
                float[] f = keyRect(r, i, w);
                rect.set(f[0], f[1], f[2], f[3]);
                boolean pressed = (r == pressedRow && i == pressedCol);
                if (key.style != STYLE_PLAIN) {
                    Paint bg = key.style == STYLE_ACCENT ? bgAccent
                            : key.style == STYLE_SPECIAL ? bgSpecial : bgNormal;
                    // 键底影 0 2dp 0：字母键=主题色本色，功能键=黑/白15%，accent 键=主题深色
                    if (!pressed) {
                        rect2.set(f[0], f[1] + 2 * d, f[2], f[3] + 2 * d);
                        c.drawRoundRect(rect2, radius, radius,
                                key.style == STYLE_ACCENT ? shadowAccent
                                        : key.style == STYLE_SPECIAL ? shadowPaint : letterShadow);
                    }
                    c.drawRoundRect(rect, radius, radius, pressed ? bgPressed : bg);
                }

                float cx = (f[0] + f[2]) / 2, cy = (f[1] + f[3]) / 2;
                boolean letterKey = key.action == 0 && key.chem != null;
                // 字号整体上调（客户意见 7：设计稿里键字与功能字明显更大）
                textPaint.setTextSize((key.style == STYLE_NORMAL ? 23 : 18) * d);
                int tc = labelColor;
                boolean stateOn = false;
                if (key.action == ACT_SHIFT && shiftOn) stateOn = true;
                if (key.action == ACT_CASE) stateOn = autoCase;
                if (stateOn) tc = accent;
                if (key.style == STYLE_ACCENT) tc = Color.WHITE;
                textPaint.setColor(tc);
                Paint.FontMetrics fm = textPaint.getFontMetrics();

                if (key.action == ACT_ENTER) {
                    // 回车键：手绘矢量图标（客户意见 7，替代 Unicode「←」）
                    drawEnterIcon(c, cx, cy, Color.WHITE);
                } else if (key.action == ACT_IME) {
                    // 底部 ⇌ 条：手绘矢量「可逆箭头」，与设计稿的 ⇌ 字形对齐
                    drawSwitchImeIcon(c, cx, cy, labelColor);
                } else if (key.action == ACT_MODE && key.label.contains("｜")) {
                    // 拼｜素 双色：当前模式主题色、另一模式弱化。
                    // ⚠ textPaint 的对齐是 CENTER，这里必须临时切到 LEFT 再按左边界逐字绘制，
                    // 否则每个字都会以传入的 x 为中心再左移半个字宽，整串看起来明显偏左。
                    textPaint.setTextAlign(Paint.Align.LEFT);
                    textPaint.setTextSize(18 * d);
                    fm = textPaint.getFontMetrics();
                    float base = cy - (fm.ascent + fm.descent) / 2;
                    float w0 = textPaint.measureText("拼");
                    float wBar = textPaint.measureText("｜");
                    float w1 = textPaint.measureText("素");
                    float x0 = cx - (w0 + wBar + w1) / 2;
                    textPaint.setColor(pinyinMode ? accent : offColor);
                    c.drawText("拼", x0, base, textPaint);
                    textPaint.setColor(offColor);
                    c.drawText("｜", x0 + w0, base, textPaint);
                    textPaint.setColor(pinyinMode ? offColor : accent);
                    c.drawText("素", x0 + w0 + wBar, base, textPaint);
                    textPaint.setTextAlign(Paint.Align.CENTER);
                } else {
                    drawKeyLabel(c, key, cx, cy, fm);
                }

                if (key.stackChem) {
                    // 「，/。」键：句号与逗号同一纵轴、居其下方（设计稿 52218da0 底部功能行）
                    if (key.chem != null && !key.chem.isEmpty()) {
                        chemPaint.setTextSize(12 * d);
                        chemPaint.setTextAlign(Paint.Align.LEFT);
                        Paint.FontMetrics cfm = chemPaint.getFontMetrics();
                        c.drawText(key.chem, cx - punctInkCx(chemPaint, key.chem),
                                cy + 9 * d - (cfm.ascent + cfm.descent) / 2, chemPaint);
                        chemPaint.setTextAlign(Paint.Align.RIGHT);
                    }
                } else if (letterKey) {
                    // 右上=长按可得的形态（alt 优先）；右下=上滑输出的化学符号（主题色）
                    hintPaint.setTextSize(12 * d);
                    String hint = (key.alt != null && !key.alt.isEmpty()) ? key.alt : key.swipe;
                    if (hint != null && !hint.isEmpty()) {
                        c.drawText(hint, f[2] - 3 * d, f[1] + 14 * d, hintPaint);
                    }
                    chemPaint.setTextSize(12 * d);
                    if (key.chem != null && !key.chem.isEmpty()) {
                        c.drawText(key.chem, f[2] - 4 * d, f[3] - 5 * d, chemPaint);
                    }
                }
            }
        }
        drawSwipeBubble(c, w);
    }

    // ---------------- 文字与图标绘制 ----------------

    /** 半角/全角标点：字形在字框左下角，按字框居中会明显偏左（客户意见 8）。 */
    private static final String PUNCT_CHARS = "，。、；：,.";

    private static boolean isPunct(String s) {
        return s != null && s.length() == 1 && PUNCT_CHARS.indexOf(s.charAt(0)) >= 0;
    }

    /** 标点墨迹中心相对「文本原点（Advance 左缘）」的水平偏移。 */
    private static float punctInkCx(Paint p, String s) {
        if (!isPunct(s)) return 0f;
        android.graphics.Rect b = new android.graphics.Rect();
        p.getTextBounds(s, 0, 1, b);
        return b.exactCenterX();
    }

    /**
     * 绘制键主字符：标点按「墨迹水平居中」，其它字符按字框居中。
     * 垂直方向一律用 baseline 居中（设计稿里逗号的墨迹本来就略偏上，这与字形有关）。
     *
     * ⚠ 标点必须切到 Align.LEFT 再按「墨迹左缘」定位：
     * Align.CENTER 居中的是**步进宽度**，不是墨迹，直接减墨迹偏移会少算半个步进
     * （实测「，」会再偏左 20px ≈ 7.6dp）。
     */
    private void drawKeyLabel(Canvas c, Key key, float cx, float cy, Paint.FontMetrics fm) {
        String s = key.label;
        if (s == null) return;
        float base = cy - (fm.ascent + fm.descent) / 2;
        if (isPunct(s)) {
            textPaint.setTextAlign(Paint.Align.LEFT);
            c.drawText(s, cx - punctInkCx(textPaint, s), base, textPaint);
            textPaint.setTextAlign(Paint.Align.CENTER);
        } else {
            c.drawText(s, cx, base, textPaint);
        }
    }

    /** 回车键图标：手绘矢量（替换原来的 Unicode「←」，客户意见 7）。 */
    private void drawEnterIcon(Canvas c, float cx, float cy, int color) {
        iconPaint.setColor(color);
        iconPaint.setStrokeWidth(2.2f * d);
        float top = cy - 9 * d, bottom = cy + 6 * d, left = cx - 9 * d, right = cx + 10 * d;
        c.drawLine(right, bottom, right, top, iconPaint);                      // 右端竖杆
        c.drawLine(right, bottom, left, bottom, iconPaint);                    // 横杆
        c.drawLine(left, bottom, left + 6 * d, bottom - 6 * d, iconPaint);     // 左端箭头
        c.drawLine(left, bottom, left + 6 * d, bottom + 6 * d, iconPaint);
    }

    /** 底部 ⇌ 条图标：手绘矢量「可逆箭头」（替换原来的 Unicode「⇌」，客户意见 7）。 */
    private void drawSwitchImeIcon(Canvas c, float cx, float cy, int color) {
        iconPaint.setColor(color);
        iconPaint.setStrokeWidth(2.2f * d);
        float l = cx - 13 * d, r = cx + 13 * d, t = cy - 5 * d, b = cy + 5 * d;
        c.drawLine(l, t, r - 3 * d, t, iconPaint);
        c.drawLine(r - 3 * d, t, r - 8 * d, t - 4.5f * d, iconPaint);
        c.drawLine(r - 3 * d, t, r - 8 * d, t + 4.5f * d, iconPaint);
        c.drawLine(r, b, l + 3 * d, b, iconPaint);
        c.drawLine(l + 3 * d, b, l + 8 * d, b - 4.5f * d, iconPaint);
        c.drawLine(l + 3 * d, b, l + 8 * d, b + 4.5f * d, iconPaint);
    }

    /**
     * 符号页左侧分类栏（设计稿 39ed7978）：
     * 整列是一张卡片，5 项在卡内均分、用细线分隔；选中项 = 主题色填充 + 对比字色。
     */
    private void drawCategoryTabs(Canvas c) {
        tabPaint.setTextSize(18 * d);
        Paint.FontMetrics fm = tabPaint.getFontMetrics();
        float h = tabContentH() / SYM_CAT_NAMES.length;
        // 卡片底 + 底影
        rect.set(padH, padH, padH + tabW, padH + h * SYM_CAT_NAMES.length);
        rect2.set(rect.left, rect.top + 2 * d, rect.right, rect.bottom + 2 * d);
        c.drawRoundRect(rect2, radius, radius, shadowPaint);
        c.drawRoundRect(rect, radius, radius, bgNormal);

        for (int i = 0; i < SYM_CAT_NAMES.length; i++) {
            float[] f = tabRect(i);
            boolean on = (i == symbolCat);
            if (on) {
                rect.set(f[0], f[1], f[2], f[3]);
                c.drawRoundRect(rect, radius, radius, bgAccent);
            }
            if (i > 0) {   // 卡内分隔线（压在卡片底上，选中项之上不画）
                c.drawRect(f[0], f[1], f[2], f[1] + Math.max(1f, d * 0.6f), dividerPaint);
            }
            tabPaint.setColor(on ? onAccentColor() : labelColor);
            float cy = (f[1] + f[3]) / 2;
            c.drawText(SYM_CAT_NAMES[i], (f[0] + f[2]) / 2,
                    cy - (fm.ascent + fm.descent) / 2, tabPaint);
        }
    }

    /** 上滑预览气泡（52×56dp，圆角 10dp，主题色描边）。 */
    private void drawSwipeBubble(Canvas c, int w) {
        if (!swipeFired || bubbleChar == null) return;
        float bw = 52 * d, bh = 56 * d;
        float bx = Math.max(padH, Math.min(curX - bw / 2, w - bw - padH));
        float by = curY - keyH * 0.9f - bh;
        rect.set(bx, by, bx + bw, by + bh);
        c.drawRoundRect(rect, 10 * d, 10 * d, bubblePaint);
        c.drawRoundRect(rect, 10 * d, 10 * d, bubbleBorder);
        bubbleText.setTextSize(30 * d);
        Paint.FontMetrics fm = bubbleText.getFontMetrics();
        c.drawText(bubbleChar, bx + bw / 2, by + bh / 2 - (fm.ascent + fm.descent) / 2, bubbleText);
    }

    // ---------------- 命中测试 ----------------

    /** 返回 {row, col}；命中分类 tab 时 row = ROW_TAB(-2)，col = 分类下标。 */
    private int[] hitKey(float x, float y) {
        int w = getWidth();
        List<Key> keys;
        if (y < padH) return null;
        if (page == PAGE_SYMBOL) {
            for (int i = 0; i < SYM_CAT_NAMES.length; i++) {
                float[] f = tabRect(i);
                if (x >= f[0] && x <= f[2] && y >= f[1] && y <= f[3]) return new int[]{ROW_TAB, i};
            }
        }
        float y0 = padH;
        int row = -1;
        for (int r = 0; r < rowCount(); r++) {
            float y1 = y0 + rowH(r);
            if (y >= y0 && y <= y1 + vGap / 2) {
                row = r;
                break;
            }
            y0 = y1 + vGap;
        }
        if (row < 0) return null;
        keys = rowKeys(row);
        for (int i = 0; i < keys.size(); i++) {
            float[] f = keyRect(row, i, w);
            if (x >= f[0] && x <= f[2]) return new int[]{row, i};
        }
        return null;
    }

    // ---------------- 触摸 ----------------

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                dismissPopup();
                int[] hit = hitKey(e.getX(), e.getY());
                if (hit == null) return false;
                pressedRow = hit[0];
                pressedCol = hit[1];
                downX = curX = e.getX();
                downY = curY = e.getY();
                swipeFired = false;
                longPressDone = false;
                bubbleChar = null;
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                if (pressedRow != ROW_TAB) handler.postDelayed(longPressRunnable, 420);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (pressedRow == ROW_TAB) {
                    curX = e.getX();
                    curY = e.getY();
                    return true;
                }
                if (pressedRow < 0) return false;
                curX = e.getX();
                curY = e.getY();
                if (popupShowing()) {
                    // 长按后不松手、直接滑到目标候选上，松手即输出（客户意见 3）
                    int idx = popupHit(curX, getTop() + curY);
                    if (idx != popupIndex) {
                        popupIndex = idx;
                        notifyPopup();
                    }
                    return true;
                }
                if (Math.abs(curX - downX) > 18 * d || Math.abs(curY - downY) > 18 * d) {
                    handler.removeCallbacks(longPressRunnable);
                }
                Key key = rowKeys(pressedRow).get(pressedCol);
                String up = (key.chem != null && !key.chem.isEmpty()) ? key.chem : key.swipe;
                if (!swipeFired && up != null && downY - curY > keyH * 0.55f) {
                    swipeFired = true;
                    bubbleChar = up;
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP: {
                handler.removeCallbacks(longPressRunnable);
                if (pressedRow == ROW_TAB) {
                    int[] hit = hitKey(e.getX(), e.getY());
                    if (hit != null && hit[0] == ROW_TAB) setSymbolCategory(hit[1]);
                    pressedRow = -1;
                    invalidate();
                    return true;
                }
                if (pressedRow < 0) return false;
                Key key = rowKeys(pressedRow).get(pressedCol);
                if (popupShowing()) {
                    String val = (popupIndex >= 0 && popupIndex < popupOpts.size())
                            ? popupOpts.get(popupIndex) : null;
                    dismissPopup();
                    if (val != null) listener.onChar(val);
                    pressedRow = -1;
                    invalidate();
                    return true;
                }
                if (swipeFired) {
                    listener.onChar(bubbleChar);
                } else if (!longPressDone) {
                    if (key.action != 0) listener.onAction(key.action);
                    else listener.onChar(key.label);
                }
                swipeFired = false;
                bubbleChar = null;
                pressedRow = -1;
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                handler.removeCallbacks(longPressRunnable);
                dismissPopup();
                swipeFired = false;
                bubbleChar = null;
                pressedRow = -1;
                invalidate();
                return true;
            }
        }
        return super.onTouchEvent(e);
    }

    // ---------------- 长按多选 ----------------

    private void fireLongPress() {
        if (pressedRow < 0 || pressedCol < 0 || swipeFired) return;
        Key key = rowKeys(pressedRow).get(pressedCol);
        if (key.action == ACT_MODE) {
            // 长按「拼｜素」= 开关元素自动大小写（设计稿 §3.6.2）
            longPressDone = true;
            listener.onAction(ACT_CASE);
            return;
        }
        if (key.action != 0) return;
        List<String> opts = longPressOptions(key);
        if (opts.size() < 2) return;
        longPressDone = true;
        showPopup(opts);
    }

    /**
     * 字母的「上角标」形态（Unicode 缺字的不收录：Q/X/Y/Z 无上角标）。
     * 每段格式固定为「大写字母 + 上角标字符」，上角标都是 BMP 单字符。
     */
    private static final String SUPER_LETTERS =
            "Aᴬ|Bᴮ|Cᶜ|Dᴰ|Eᴱ|Fᶠ|Gᴳ|Hᴴ|Iᴵ|Jᴶ|Kᴷ|Lᴸ|Mᴹ|Nᴺ|Oᴼ|Pᴾ|"
            + "Rᴿ|Sˢ|Tᵀ|Uᵁ|Vⱽ|Wᵂ";

    private static String superscriptOf(String label) {
        if (label == null) return null;
        for (String seg : SUPER_LETTERS.split("\\|")) {
            if (seg.length() == 2 && seg.charAt(0) == label.charAt(0)) {
                return String.valueOf(seg.charAt(1));
            }
        }
        return null;
    }

    /**
     * 长按候选项（对齐设计稿 4488aa83 与客户意见 10）：
     * 数字形态 · 数字上角标 · 数字下角标 · 字母上角标 · 大写 · 小写。
     * 例：T → 5 / ⁵ / ₅ / ᵀ / T / t。
     */
    private List<String> longPressOptions(Key key) {
        List<String> opts = new ArrayList<>();
        addOpt(opts, key.alt);     // 数字/符号的常规形态
        addOpt(opts, key.swipe);   // 上角标形态
        addOpt(opts, key.chem);    // 下角标形态
        String label = key.label;
        if (label != null && label.length() == 1 && Character.isLetter(label.charAt(0))) {
            addOpt(opts, superscriptOf(label));           // 字母上角标
            addOpt(opts, label.toUpperCase());            // 大写
            addOpt(opts, label.toLowerCase());            // 小写
        } else {
            addOpt(opts, label);
        }
        return opts;
    }

    private static void addOpt(List<String> opts, String v) {
        if (v != null && !v.isEmpty() && !opts.contains(v)) opts.add(v);
    }

    /** 浮层是否正在显示。 */
    private boolean popupShowing() {
        return popupOpts != null;
    }

    private static final float POPUP_CELL = 44f;    // dp，候选块尺寸
    private static final float POPUP_GAP = 2.5f;    // dp，候选块左右外边距
    private static final float POPUP_PAD = 6f;      // dp，容器内边距

    /**
     * 显示长按浮层。
     *
     * 坐标换算到 **IME 根布局坐标系**：键盘 View 画不出自己上方的内容（会被父容器裁掉），
     * 所以真正的绘制交给服务侧叠在根布局上的 overlay，键盘只负责算位置和跟踪手指。
     */
    private void showPopup(List<String> opts) {
        popupOpts = opts;
        popupIndex = -1;
        float[] f = keyRect(pressedRow, pressedCol, getWidth());
        float bw = opts.size() * (POPUP_CELL + POPUP_GAP * 2) * d + POPUP_PAD * 2 * d;
        float bh = (POPUP_CELL + POPUP_PAD * 2) * d;
        float bx = (f[0] + f[2]) / 2 - bw / 2;
        bx = Math.max(padH, Math.min(bx, getWidth() - bw - padH));
        float by = getTop() + f[1] - bh - 4 * d;
        if (by < 0) by = 0;                        // 上方空间不足就贴顶（可盖住候选栏）
        popupBox.set(bx, by, bx + bw, by + bh);
        popupAnchorX = (f[0] + f[2]) / 2;
        popupAnchorY = getTop() + f[1];
        notifyPopup();
        invalidate();
    }

    private void dismissPopup() {
        if (popupOpts == null) return;
        popupOpts = null;
        popupIndex = -1;
        notifyPopup();
        invalidate();
    }

    private void notifyPopup() {
        if (listener != null) {
            listener.onLongPressPopup(popupOpts, popupBox, popupIndex, popupAnchorX, popupAnchorY);
        }
    }

    /** 手指落在浮层第几个候选上（-1 = 不在浮层内）。入参是 IME 根布局坐标。 */
    private int popupHit(float rootX, float rootY) {
        if (popupOpts == null || !popupBox.contains(rootX, rootY)) return -1;
        float cell = (POPUP_CELL + POPUP_GAP * 2) * d;
        int idx = (int) ((rootX - popupBox.left - POPUP_PAD * d) / cell);
        return (idx >= 0 && idx < popupOpts.size()) ? idx : -1;
    }
}

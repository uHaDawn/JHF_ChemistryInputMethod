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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.app.WallpaperManager;
import android.app.WallpaperColors;
import android.graphics.drawable.Drawable;
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

import com.jhf.jiehuafa.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 接化发键盘（Figma 设计稿样式）：全直角白键 + 底影 + 主题强调色。
 *
 * 字母页：QWERTY 三排 + 功能行「拼｜素 · ， · 空格 · ←」+ 底部细条 ⇌（切换系统输入法）。
 * 符号页：左侧 4 分类竖向 tab（度量 / 物质 / 反应 / 其他）+ 右侧符号网格 + 功能行。
 * 收起键盘的入口在候选栏右侧的 ⌄ 芯片（服务侧 JhfImeService）。
 *
 * 手势：
 *   点按 = 主字符；上滑 = 化学符号（带预览气泡）；长按 = 形态三选（数字/符号 · 大写 · 小写）。
 *
 * 修改日志（之后每次改动都在这里追加一条：日期 + 改动内容）：
 *   2026-10-25 删除键/回车键改用客户提供的 SVG（ic_backspace 21×13、ic_enter 21×11），
 *              统一走 drawBitmapIcon() 按主题色着色，删除旧的手绘回车图标；
 *              新增长按删除连续退格（420ms 触发后每 70ms 一发，松手/取消立即停止）；
 *              新增 Shift 两种大写行为：点按=只大写一次；长按后另一指连续点字母=持续大写、松手恢复；
 *              副指按键闪烁高亮（flashRow/flashCol），Listener 增加 onShiftHoldChanged()；
 *              自动主题改为莫奈壁纸取色（WallpaperColors → 壁纸位图量化 → system_accent 兜底）。
 *   2026-10-26 按键震动改为受 setHapticEnabled() 控制（主界面「通用」里的「触感反馈」开关），
 *              三处震动调用统一收口到 tapFeedback()；
 *              震动实现改走 performHapticFeedback(..., FLAG_IGNORE_GLOBAL_SETTING)：
 *              系统「触摸振动」关闭时会过滤普通触感（IGNORED_FOR_SETTINGS），
 *              加该 flag 后只受本应用开关控制，保证开关打开就一定有反馈。
 *   2026-10-27 符号页带「+ / −」气泡的 N 键，长按候选里补充上标电荷符号 ⁺ / ⁻。
 *              符号页删除「角标」分类，仅保留度量 / 物质 / 反应 / 其他 4 个分页；
 *              命中测试外扩半格：行上下各探半格行距、列取最近按键中心并左右扩半格，
 *              点在按键缝隙里也会命中相邻按键（原来缝隙完全无触控事件）。
 *   2026-10-27 新增三种平板布局（setLayoutMode）：1 限宽居中（宽度上限 720dp，两侧留白）、
 *              2 横屏分体（字母三排与功能行中间断开 28dp 握持区，留白区不响应触摸）、
 *              3 悬浮小键盘（宽 66% / 上限 560dp 的直角卡片，顶部 20dp 拖动条可横向拖动，
 *              卡片外背景透明透出应用）；键区坐标统一走 contentLeft/contentW，
 *              大屏（>560dp）按键按 1.12 倍放大避免键面矮胖。
 *   2026-10-28 按参考图重构为三种平板布局：铺满 / 左右断开 / 浮窗（删除「限宽居中」）。
 *              左右断开按图逐行给出左右两套重复按键（Q..Y｜T..P、⇧A..H｜F..L、Z..B｜V..⌫、
 *              功能行 拼｜素·，·空格｜空格·←），中间 28dp 握持留白，底部 ⇌ 不分体、居中；
 *              浮窗不再由键盘自绘卡片与横向拖动，改为服务侧一张卡片（抓手 + 候选栏 + 键盘）
 *              整体上下左右拖动，候选栏随卡片一起移动，键区始终按卡片宽度铺满。
 *   2026-10-28 左右断开按 Figma 稿重新排版：两簇各占可用宽度 34.5%（原来各占半屏、键面被拉宽），
 *              中间留白约 31% 不响应触摸；右簇第三行 V B N M ⌫ 中 ⌫ 按图加宽到 2.2 倍键宽
 *              （复制一份键改权重，不影响铺满布局）。
 *   2026-10-28 三种布局显示名改为 平铺 / 分离 / 悬浮（常量值 LAYOUT_FULL=0、LAYOUT_SPLIT=1、
 *              LAYOUT_FLOAT=2 保持不变，避免旧偏好错位）。
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

        /**
         * 长按 Shift 的「按住大写」状态（2026-10-25）。
         * held=true：按住期间字母一律大写；held=false：松手恢复默认小写。
         */
        default void onShiftHoldChanged(boolean held) {
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
    public static final String[] SYM_CAT_NAMES = {"度量", "物质", "反应", "其他"};

    // ---- 平板布局模式（2026-10-28 按参考图收敛为三种）----
    // 0 平铺：按可用宽度铺满（手机/平板默认）
    // 1 分离：字母行与功能行左右各一套重复按键，中间留握持区
    // 2 悬浮：整张卡片（抓手 + 候选栏 + 键盘）由服务侧上下左右拖动
    public static final int LAYOUT_FULL = 0, LAYOUT_SPLIT = 1, LAYOUT_FLOAT = 2;
    /** 显示名按索引对应上面三个常量（浮层里的显示顺序见 MainActivity.KB_ORDER）。 */
    public static final String[] LAYOUT_NAMES = {"平铺", "分离", "悬浮"};

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
        /** 占位键：只占宽度、不绘制也不响应触摸（左右断开的行首缩进用）。 */
        boolean spacer = false;
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
    /** 按键触感反馈开关（主界面「通用 → 触感反馈」，2026-10-26）。 */
    private boolean hapticEnabled = true;

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
    /** 底部切换输入法图标（客户提供的 SVG，已转为 vector drawable）。 */
    private Drawable imeSwitchIcon;
    /** 删除键 / 回车键图标（客户 2026-10-25 提供的 SVG，随主题色/白字着色）。 */
    private Drawable backspaceIcon, enterIcon;
    private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubbleBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubbleText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF rect2 = new RectF();

    private int pressedRow = -1, pressedCol = -1;
    private float downX, downY, curX, curY;

    // ---- 平板布局（2026-10-28）----
    /** 当前布局模式，见 LAYOUT_* 常量。 */
    private int layoutMode = LAYOUT_FULL;
    /** 键区宽度与左边界：各模式都按自身可用宽度铺满（浮窗的宽度由服务侧卡片决定）。 */
    private float contentW = 0f, contentLeft = 0f;
    /** 大屏按键放大系数（1 表示不放大）。 */
    private float keyScale = 1f;
    /** 左右断开：左半 / 右半各行的按键（可复用同一 Key 对象，故允许重复键）与拼接后的整行。 */
    private final List<List<Key>> splitLeftRows = new ArrayList<>();
    private final List<List<Key>> splitRightRows = new ArrayList<>();
    private final List<List<Key>> splitRows = new ArrayList<>();
    /**
     * 左右断开时每一簇占「可用宽度」的比例（2026-10-28 按 Figma 稿量取：
     * 左簇 11→354 / 996、右簇 637→985 / 996，两簇各约 34.5%，中间留白约 31%）。
     */
    private static final float SPLIT_RATIO = 0.345f;
    private boolean swipeFired = false, longPressDone = false;
    private String bubbleChar = null;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable longPressRunnable = this::fireLongPress;

    // ---- 2026-10-25 新增：长按删除连发 / 按住 Shift 双指大写 ----
    /** 副指点按字母时的短暂高亮（主指正按住 Shift，不能用 pressedRow 抢状态）。 */
    private int flashRow = -1, flashCol = -1;
    /** 按住 Shift 的那根手指的 pointerId；-1 表示当前没有按住。 */
    private int shiftPointerId = -1;
    private boolean shiftHeld = false;
    private final Runnable clearFlash = new Runnable() {
        @Override
        public void run() {
            flashRow = -1;
            flashCol = -1;
            invalidate();
        }
    };
    /** 每 70ms 删一个字符，直到 ACTION_UP / CANCEL 移除回调。 */
    private final Runnable backspaceRepeat = new Runnable() {
        @Override
        public void run() {
            if (pressedRow < 0 || pressedCol < 0) return;
            List<Key> keys = rowKeys(pressedRow);
            if (pressedCol >= keys.size() || keys.get(pressedCol).action != ACT_BACK) return;
            listener.onAction(ACT_BACK);
            handler.postDelayed(this, 70);
        }
    };

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
        imeSwitchIcon = getContext().getDrawable(R.drawable.ic_ime_switch);
        backspaceIcon = getContext().getDrawable(R.drawable.ic_backspace);
        enterIcon = getContext().getDrawable(R.drawable.ic_enter);
        bubbleBorder.setStyle(Paint.Style.STROKE);
        bubbleBorder.setStrokeWidth(d);
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
    /** 取色结果缓存：壁纸没变就不必反复解码位图。收到壁纸变更广播时置脏。 */
    private static boolean autoResolved = false;

    /** 壁纸换了：丢弃缓存，下次 resolveAutoColors 重新取色。 */
    public static void invalidateAutoColors() {
        autoResolved = false;
    }

    /**
     * 解析「自动」主题的颜色并缓存到静态字段。
     *
     * 取色顺序（由准到稳）：
     * 1. Android 12+ 的 {@link WallpaperColors} —— 系统 Monet 调色板的种子色，最贴合壁纸；
     * 2. 壁纸位图量化 —— 部分 OEM（实测 HyperOS）不做系统级取色
     *    （dumpsys wallpaper 里 isColorExtracted=false），此时 WallpaperColors 为 null，
     *    只能自己降采样统计主色调；
     * 3. 系统 accent1_500 资源 —— 最后兜底。
     *
     * 取到的种子色统一做饱和度/明度归一化，保证主题色够鲜艳、且白字对比度足够。
     */
    public static void resolveAutoColors(Context ctx) {
        if (autoResolved) return;
        int seed = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            seed = wallpaperColorsSeed(ctx);
        }
        if (seed == 0) {
            seed = wallpaperBitmapSeed(ctx);
        }
        if (seed == 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                seed = ctx.getResources().getColor(android.R.color.system_accent1_500, ctx.getTheme());
            } catch (Throwable ignored) {
            }
        }
        int a = normalizeAccent(seed);
        if (a != 0) {
            autoAccent = a;
            autoAccentDark = darkerAccent(a);
        }
        autoResolved = true;
    }

    /** Monet 种子：主/次/三级色里挑最鲜艳、明度适中的那个。 */
    private static int wallpaperColorsSeed(Context ctx) {
        try {
            WallpaperManager wm = WallpaperManager.getInstance(ctx);
            WallpaperColors wc = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
            if (wc == null) return 0;
            Color[] colors = {wc.getPrimaryColor(), wc.getSecondaryColor(), wc.getTertiaryColor()};
            int best = 0;
            float bestScore = 0f;
            float[] hsv = new float[3];
            for (Color c : colors) {
                if (c == null) continue;
                int rgb = c.toArgb();
                Color.RGBToHSV(Color.red(rgb), Color.green(rgb), Color.blue(rgb), hsv);
                if (hsv[1] < 0.12f || hsv[2] < 0.10f || hsv[2] > 0.97f) continue;
                float score = hsv[1] * (1f - Math.abs(hsv[2] - 0.55f));
                if (score > bestScore) {
                    bestScore = score;
                    best = rgb;
                }
            }
            return best;
        } catch (Throwable t) {
            return 0;
        }
    }

    /** 壁纸位图降采样后按色相分桶，取占比最高的鲜艳色。 */
    private static int wallpaperBitmapSeed(Context ctx) {
        Bitmap small = null;
        try {
            Drawable d = WallpaperManager.getInstance(ctx).getDrawable();
            if (d == null) return 0;
            small = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(small);
            d.setBounds(0, 0, 64, 64);
            d.draw(c);

            final int bins = 24; // 每桶 15°
            int[] weight = new int[bins];
            long[] r = new long[bins], g = new long[bins], b = new long[bins];
            float[] hsv = new float[3];
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    int p = small.getPixel(x, y);
                    if (Color.alpha(p) < 128) continue;
                    Color.RGBToHSV(Color.red(p), Color.green(p), Color.blue(p), hsv);
                    // 跳过灰、过暗、过曝的像素（都不适合当主题色）
                    if (hsv[1] < 0.18f || hsv[2] < 0.12f || hsv[2] > 0.97f) continue;
                    int idx = (int) (hsv[0] / (360f / bins)) % bins;
                    int w = (int) (hsv[1] * 100);
                    weight[idx] += w;
                    r[idx] += (long) Color.red(p) * w;
                    g[idx] += (long) Color.green(p) * w;
                    b[idx] += (long) Color.blue(p) * w;
                }
            }
            int best = -1, bw = 0;
            for (int i = 0; i < bins; i++) {
                // 相邻桶合并，避免主色刚好跨桶时被拆散
                int sum = weight[i] + weight[(i + 1) % bins];
                if (sum > bw) {
                    bw = sum;
                    best = i;
                }
            }
            // 鲜艳像素太少（几乎纯灰壁纸）就不硬造颜色
            if (best < 0 || bw < 1200) return 0;
            int i2 = (best + 1) % bins;
            long tw = weight[best] + weight[i2];
            return Color.rgb((int) ((r[best] + r[i2]) / tw),
                    (int) ((g[best] + g[i2]) / tw),
                    (int) ((b[best] + b[i2]) / tw));
        } catch (Throwable t) {
            return 0;
        } finally {
            if (small != null) small.recycle();
        }
    }

    /** 把任意种子色调成「适合当强调色」的鲜艳中间调；灰色返回 0。 */
    private static int normalizeAccent(int seed) {
        if (seed == 0) return 0;
        float[] hsv = new float[3];
        Color.RGBToHSV(Color.red(seed), Color.green(seed), Color.blue(seed), hsv);
        if (hsv[1] < 0.12f) return 0;
        hsv[1] = Math.min(0.92f, Math.max(0.55f, hsv[1] * 1.15f + 0.10f));
        hsv[2] = 0.78f;
        int c = Color.HSVToColor(hsv);
        // 主题条上是白字，明度压到白字可读为止（与 AgBr 黄的处理一致）
        while (luminance(c) > 158 && hsv[2] > 0.40f) {
            hsv[2] -= 0.04f;
            c = Color.HSVToColor(hsv);
        }
        return c;
    }

    /** 同色相/饱和度，压暗得到深色变体（键底影、按压、状态栏）。 */
    private static int darkerAccent(int accent) {
        float[] hsv = new float[3];
        Color.RGBToHSV(Color.red(accent), Color.green(accent), Color.blue(accent), hsv);
        hsv[2] = Math.max(0.28f, hsv[2] * 0.58f);
        return Color.HSVToColor(hsv);
    }

    private static float luminance(int c) {
        return 0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c);
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
        // 悬浮模式的卡片外区域要透出下方应用，其余模式铺满键盘底色
        setBackgroundColor(layoutMode == LAYOUT_FLOAT ? Color.TRANSPARENT : kbBg);
    }

    // ---------------- 页数据 ----------------

    private void buildPages() {
        buildLetterRows();
        buildSplitRows();
        buildSymbolCats();
    }

    /**
     * 左右断开用的重复键行（2026-10-28，对齐参考图）：
     * 复用原键对象，因此同一个键会同时出现在左右两半；行首占位键只占宽度不绘制。
     */
    private void buildSplitRows() {
        splitLeftRows.clear();
        splitRightRows.clear();
        splitRows.clear();
        if (letterRows == null || letterRows.size() < 4) return;
        List<Key> r0 = letterRows.get(0), r1 = letterRows.get(1),
                r2 = letterRows.get(2), rf = letterRows.get(3);

        splitLeftRows.add(row(keyOf(r0, "Q"), keyOf(r0, "W"), keyOf(r0, "E"),
                keyOf(r0, "R"), keyOf(r0, "T"), keyOf(r0, "Y")));
        splitRightRows.add(row(keyOf(r0, "T"), keyOf(r0, "Y"), keyOf(r0, "U"),
                keyOf(r0, "I"), keyOf(r0, "O"), keyOf(r0, "P")));

        splitLeftRows.add(row(keyOf(r1, "A"), keyOf(r1, "S"), keyOf(r1, "D"),
                keyOf(r1, "F"), keyOf(r1, "G"), keyOf(r1, "H")));
        splitRightRows.add(row(keyOf(r1, "F"), keyOf(r1, "G"), keyOf(r1, "H"),
                keyOf(r1, "J"), keyOf(r1, "K"), keyOf(r1, "L")));

        splitLeftRows.add(row(keyOf(r2, "⇧"), keyOf(r2, "Z"), keyOf(r2, "X"),
                keyOf(r2, "C"), keyOf(r2, "V"), keyOf(r2, "B")));
        // 右簇只有 5 键，参考图里 ⌫ 约为普通键 2.2 倍宽；复制一份改权重，避免影响铺满布局
        splitRightRows.add(row(keyOf(r2, "V"), keyOf(r2, "B"), keyOf(r2, "N"),
                keyOf(r2, "M"), withWeight(keyOf(r2, "⌫"), 2.2f)));

        Key space = keyOf(rf, "空格");
        splitLeftRows.add(row(keyOfAction(rf, ACT_MODE), keyOf(rf, "，"), space));
        splitRightRows.add(row(space, keyOfAction(rf, ACT_ENTER)));

        for (int i = 0; i < splitLeftRows.size(); i++) {
            List<Key> both = new ArrayList<>(splitLeftRows.get(i));
            both.addAll(splitRightRows.get(i));
            splitRows.add(both);
        }
    }

    private static Key keyOf(List<Key> keys, String label) {
        for (Key k : keys) if (label.equals(k.label)) return k;
        return null;
    }

    private static Key keyOfAction(List<Key> keys, int action) {
        for (Key k : keys) if (k.action == action) return k;
        return null;
    }

    /** 复制一个键并改权重（左右断开里同一键在两簇中的宽度可能不同）。 */
    private static Key withWeight(Key src, float weight) {
        if (src == null) return null;
        Key key = mk(src.label, src.swipe, src.chem, src.alt);
        key.action = src.action;
        key.style = src.style;
        key.stackChem = src.stackChem;
        key.weight = weight;
        return key;
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
     * 符号页 4 分类，每分类 3 行内容 + 1 行功能键（空格 / ⌫ / ←）。
     * 「度量 / 物质 / 反应 / 其他」取自设计稿；「其他」第三行为拟定内容。
     * 2026-10-27 客户要求删除「角标」分类（上/下标可由字母键长按浮层与符号页其他分页补齐）。
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

        symbolCats.add(cat(          // 3 其他
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

    /** 打开/关闭按键震动反馈（2026-10-26「通用 → 触感反馈」开关）。 */
    public void setHapticEnabled(boolean on) {
        hapticEnabled = on;
    }

    /**
     * 统一的按键触感：开关关闭时什么都不做。
     * 2026-10-26 修正：设备上系统「触摸振动」是关闭的（haptic_feedback_enabled=0），
     * 普通 performHapticFeedback 会被系统过滤（日志状态 IGNORED_FOR_SETTINGS），导致完全没手感。
     * 这里加 FLAG_IGNORE_GLOBAL_SETTING，只受本应用「通用 → 触感反馈」开关控制。
     */
    private void tapFeedback() {
        if (!hapticEnabled) return;
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
    }

    public void setPinyinMode(boolean on) {
        pinyinMode = on;
        invalidate();
    }

    // ---------------- 布局计算 ----------------

    private int rowCount() {
        return page == PAGE_LETTER ? letterRows.size() : symbolCats.get(symbolCat).size();
    }

    /**
     * 某行当前要绘制的按键序列。
     * 左右断开时返回「左半 + 右半」拼接后的整行（含重复键与行首占位键），
     * 因此按下坐标、绘制、长按逻辑都用同一套下标。
     */
    private List<Key> rowKeys(int row) {
        if (page == PAGE_LETTER) {
            if (splitActive() && row < splitRows.size()) return splitRows.get(row);
            return letterRows.get(row);
        }
        return symbolCats.get(symbolCat).get(row);
    }

    private float rowH(int row) {
        // 底部 ⇌ 条保持原高；其余行在大屏布局下按 keyScale 放大
        return rowKeys(row).get(0).style == STYLE_PLAIN ? imeH : keyH * keyScale;
    }

    /** 符号页右侧网格左边界（跳过左侧分类栏）。 */
    private float symGridLeft() {
        return padH + tabW + gap;
    }

    /** 设置平板布局模式（见 LAYOUT_*）。 */
    public void setLayoutMode(int mode) {
        if (mode < LAYOUT_FULL || mode > LAYOUT_FLOAT) mode = LAYOUT_FULL;
        layoutMode = mode;
        setBackgroundColor(mode == LAYOUT_FLOAT ? Color.TRANSPARENT : kbBg);
        requestLayout();
        invalidate();
    }

    /** 分体布局是否生效：仅字母页、且键区足够宽。 */
    private boolean splitActive() {
        return layoutMode == LAYOUT_SPLIT && page == PAGE_LETTER && contentW >= 560 * d;
    }

    /** 该行是否要在中间断开（功能行跟随断开，底部 ⇌ 条不断）。 */
    private boolean isSplitRow(int row) {
        return splitActive() && row < splitRows.size();
    }

    /**
     * 算出键区宽度与左边界。
     * 三种模式都按自身可用宽度铺满：铺满/断开用整屏，浮窗用服务侧卡片的宽度。
     */
    private void computeContent(int w) {
        contentW = w;
        contentLeft = 0f;
        // 大屏按键略微放大，避免键面「矮胖」
        keyScale = (layoutMode != LAYOUT_FULL && w > 560 * d) ? 1.12f : 1f;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        computeContent(w);
        float h = padH + padBottom;
        for (int r = 0; r < rowCount(); r++) h += rowH(r) + vGap;
        setMeasuredDimension(w, (int) h);
    }

    /** 在给定起点与可用宽度里，按权重排第 idx 个键。 */
    private float[] keyRectIn(List<Key> group, int idx, float start, float avail, float y, float rowHeight) {
        float sumW = 0;
        for (Key key : group) sumW += key.weight;
        float x = start;
        for (int i = 0; i < idx; i++) x += avail * (group.get(i).weight / sumW) + gap;
        float kw = avail * (group.get(idx).weight / sumW);
        return new float[]{x, y, x + kw, y + rowHeight};
    }

    private float[] keyRect(int row, int col, int w) {
        List<Key> r = rowKeys(row);
        boolean sym = (page == PAGE_SYMBOL);
        float y = padH;
        for (int i = 0; i < row; i++) y += rowH(i) + vGap;
        float rh = rowH(row);

        // 左右断开：本行按参考图分左右两套按键，两簇各占 34.5%，中间留握持白
        if (isSplitRow(row)) {
            List<Key> left = splitLeftRows.get(row), right = splitRightRows.get(row);
            float clusterW = (contentW - 2 * padH) * SPLIT_RATIO;
            if (col < left.size()) {
                return keyRectIn(left, col, padH,
                        clusterW - (left.size() - 1) * gap, y, rh);
            }
            return keyRectIn(right, col - left.size(), contentW - padH - clusterW,
                    clusterW - (right.size() - 1) * gap, y, rh);
        }

        float left = sym ? symGridLeft() : padH;
        float avail = (sym ? (contentW - padH - symGridLeft()) : (contentW - 2 * padH))
                - (r.size() - 1) * gap;
        return keyRectIn(r, col, left, avail, y, rh);
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
        float left = padH;
        return new float[]{left, y, left + tabW, y + h};
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
                if (key.spacer) continue;   // 行首占位键：只占宽度，不画也不可点
                float[] f = keyRect(r, i, w);
                rect.set(f[0], f[1], f[2], f[3]);
                boolean pressed = (r == pressedRow && i == pressedCol)
                        || (r == flashRow && i == flashCol);
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

                if (key.action == ACT_BACK) {
                    // 删除键：客户 2026-10-25 提供的 21×13 SVG，按主题文字色着色
                    drawBitmapIcon(c, backspaceIcon, cx, cy, 21 * d, 13 * d, tc);
                } else if (key.action == ACT_ENTER) {
                    // 回车键：客户 2026-10-25 提供的 21×11 SVG，强调色键上用白色
                    drawBitmapIcon(c, enterIcon, cx, cy, 21 * d, 11 * d, Color.WHITE);
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

    /**
     * 矢量图标统一绘制入口（删除键 / 回车键 / 底部切换输入法）。
     * 图标资源本身是黑色，这里用 tint 染成主题文字色或白色。
     */
    private void drawBitmapIcon(Canvas c, Drawable icon, float cx, float cy,
                                float w, float h, int color) {
        if (icon == null) return;
        icon.setTint(color);
        icon.setBounds((int) (cx - w / 2), (int) (cy - h / 2),
                (int) (cx + w / 2), (int) (cy + h / 2));
        icon.draw(c);
    }

    /** 底部 ⇌ 条图标：客户 2026-10-25 提供的 SVG（14×10），按 24dp 宽等比绘制。 */
    private void drawSwitchImeIcon(Canvas c, float cx, float cy, int color) {
        drawBitmapIcon(c, imeSwitchIcon, cx, cy, 24 * d, 24 * d * 10f / 14f, color);
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
        if (y < padH - vGap / 2) return null;   // 顶部允许上探半格，扩大第一排按键的触控范围
        if (page == PAGE_SYMBOL) {
            for (int i = 0; i < SYM_CAT_NAMES.length; i++) {
                float[] f = tabRect(i);
                if (x >= f[0] && x <= f[2] && y >= f[1] && y <= f[3]) return new int[]{ROW_TAB, i};
            }
        }
        // 触控范围外扩（2026-10-27）：行向上探半格、向下覆盖整条行间隙；列取最近的按键中心。
        // 这样点在按键缝隙里也会命中相邻键，不再出现「点在缝里没反应」。
        float y0 = padH - vGap / 2;
        int row = -1;
        for (int r = 0; r < rowCount(); r++) {
            float y1 = y0 + rowH(r);
            if (y >= y0 && y < y1 + vGap / 2) {
                row = r;
                break;
            }
            y0 = y1 + vGap;
        }
        if (row < 0) return null;
        keys = rowKeys(row);
        int best = -1;
        float bestDist = Float.MAX_VALUE;
        float[] bestRect = null;
        for (int i = 0; i < keys.size(); i++) {
            if (keys.get(i).spacer) continue;   // 占位键不可命中
            float[] f = keyRect(row, i, w);
            float dist = Math.abs(x - (f[0] + f[2]) / 2);
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
                bestRect = f;
            }
        }
        if (best < 0 || bestRect == null) return null;
        // 列方向只外扩半个间隙：更远的位置（如符号页左侧分类栏、分体中间留白）不算命中
        if (x < bestRect[0] - gap / 2 || x > bestRect[2] + gap / 2) return null;
        return new int[]{row, best};
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
                tapFeedback();
                handler.removeCallbacks(backspaceRepeat);
                if (pressedRow != ROW_TAB) {
                    handler.postDelayed(longPressRunnable, 420);
                    if (rowKeys(pressedRow).get(pressedCol).action == ACT_SHIFT) {
                        shiftPointerId = e.getPointerId(0);
                    }
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                // 主指按住 Shift 时，副指点字母 = 直接上屏大写字母（不进入长按/滑动流程）
                if (!shiftHeld) return true;
                int idx = e.getActionIndex();
                int[] hit = hitKey(e.getX(idx), e.getY(idx));
                if (hit == null || hit[0] == ROW_TAB) return true;
                Key k = rowKeys(hit[0]).get(hit[1]);
                if (k.action == 0 && k.label != null && k.label.length() == 1
                        && isAsciiLetter(k.label.charAt(0))) {
                    flashRow = hit[0];
                    flashCol = hit[1];
                    handler.removeCallbacks(clearFlash);
                    handler.postDelayed(clearFlash, 90);
                    tapFeedback();
                    listener.onChar(k.label);
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                // 抬起的是按住 Shift 的那根手指 → 结束「按住大写」
                if (shiftHeld && e.getPointerId(e.getActionIndex()) == shiftPointerId) {
                    endShiftHold();
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (shiftHeld) return true;   // 按住 Shift 时忽略主指的滑动判定
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
                    tapFeedback();
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP: {
                handler.removeCallbacks(longPressRunnable);
                handler.removeCallbacks(backspaceRepeat);
                handler.removeCallbacks(clearFlash);
                flashRow = -1;
                flashCol = -1;
                if (shiftHeld) {
                    endShiftHold();
                    return true;
                }
                shiftPointerId = -1;
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
                handler.removeCallbacks(backspaceRepeat);
                handler.removeCallbacks(clearFlash);
                dismissPopup();
                swipeFired = false;
                bubbleChar = null;
                flashRow = -1;
                flashCol = -1;
                if (shiftHeld) {
                    endShiftHold();
                } else {
                    shiftPointerId = -1;
                    pressedRow = -1;
                    invalidate();
                }
                return true;
            }
        }
        return super.onTouchEvent(e);
    }

    /** 结束「按住 Shift」：恢复默认大小写，并清掉主指状态（2026-10-25）。 */
    private void endShiftHold() {
        if (!shiftHeld) return;
        shiftHeld = false;
        shiftPointerId = -1;
        handler.removeCallbacks(longPressRunnable);
        pressedRow = -1;
        pressedCol = -1;
        invalidate();
        listener.onShiftHoldChanged(false);
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
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
        if (key.action == ACT_BACK) {
            // 长按删除：先删一个，之后每 70ms 连续删除，松手停止（2026-10-25 新需求）
            longPressDone = true;
            listener.onAction(ACT_BACK);
            handler.postDelayed(backspaceRepeat, 70);
            return;
        }
        if (key.action == ACT_SHIFT) {
            // 长按 Shift：进入「按住大写」；另一根手指可连续点字母，松手恢复小写
            longPressDone = true;
            shiftHeld = true;
            listener.onShiftHoldChanged(true);
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
        // 符号页里带「+ / −」气泡的 N 键：长按候选补上标电荷符号 ⁺ / ⁻（2026-10-27）
        if ("N".equals(key.label) && "+".equals(key.swipe)) {
            addOpt(opts, "⁺");
            addOpt(opts, "⁻");
        }
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
        bx = Math.max(contentLeft + padH, Math.min(bx, contentLeft + contentW - bw - padH));
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

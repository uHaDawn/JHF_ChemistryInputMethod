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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.jhf.jiehuafa.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 接化发·化学输入法核心服务。
 * 拼音模式：输入中文名拼音 → 候选化学式（如 liusu-antong → 硫酸铜 → CuSO₄）。
 * 直通模式：字母直出，元素符号自动大小写；上滑输出数字角标与化学符号。
 * 候选栏：[化|符] 切换符号页 · 组合区 · 候选芯片 · ⌄ 收起键盘（长按 ⌄ 展开候选）。
 * 键盘底部 ⇌：调起系统输入法切换器。
 *
 * 修改日志（之后每次改动都在这里追加一条：日期 + 改动内容）：
 *   2026-10-25 元素模式默认 autoCase=true：第一个字母大写、第二个字母小写（双字母元素自动校正；
 *              连续元素如 NaCl 也正确，不会出现 NaCL）；
 *              大写键改为一次性生效（shiftOn，输出一个字母后自动恢复小写）；
 *              长按大写键期间 shiftHeld=true，另一指点字母连续输出大写，松手恢复默认大小写；
 *              注册 ACTION_WALLPAPER_CHANGED 广播，自动主题在壁纸更换后立即刷新；
 *              词库扩至 3897 条（新增有机化学基团/化合物 61 条）。
 *   2026-10-26 候选栏左上「化｜符」芯片字号 13sp → 18dp，与空格键（18 * density px）一致。
 *              新增按键触感反馈开关（主界面「通用 → 触感反馈」，prefs 键 haptic，默认开）；
 *              符号页点「mol」不再直接上屏，先进组合区并给出 mol/L、mol/mL 等复合单位候选；
 *              直通模式手打 kg、L、mol 等单位时也给出复合单位候选（UNIT_COMBOS）；
 *              单位候选排序规则：直通模式、无拼音候选、组合区为多字母单位（kg/mol…）时单位置顶；
 *              拼音模式单字母 L 仍拼音优先、单位接在后面，保证手打 L 也能选到 L/mol。
 *   2026-10-27 读取 kb_layout 布局偏好并下发键盘（0 手机默认 / 1 限宽居中 / 2 横屏分体 / 3 悬浮小键盘）；
 *              新增 syncCandBar()：限宽与悬浮模式下候选栏宽度/位移跟随键区，不再横跨整屏；
 *              横竖屏切换时重新读取偏好并重建布局模式。
 *   2026-10-27 新增上屏联想符号：沉淀化合物（AgCl/BaSO₄/Fe(OH)₃ 等）上屏后候选栏给出「↓」，
 *              气体（H₂/O₂/CO₂/NH₃ 等）给出「↑」，单独元素给出常见离子电荷（Fe → ²⁺、³⁺，
 *              Cl → ⁻ 等）；直通模式下组合区成化学式时实时联想，上屏后保留到下一次输入。
 *              所有上屏路径（拼音候选 / 空格 / 回车 / 规范化学式 / 空格直出）统一走 commitAndSuggest()；
 *              候选芯片行内边距 4dp→2dp 并关闭字体预留，修复 35dp 候选栏里副标题被裁掉的问题。
 *              直通模式支持同位素质量数写法：整串为「数字 + 单个元素」时数字写成上标，
 *              2H → ²H、14C → ¹⁴C、235U → ²³⁵U（2H2O 这类仍按系数处理）；
 *              直通模式下数字（含符号页角标的上标数字）可开头进入组合区，才能拼出上述同位素写法。
 *   2026-10-28 布局收敛为 铺满 / 左右断开 / 浮窗 三种（删除限宽居中）；候选栏不再单独对齐，
 *              因为浮窗模式下候选栏已在卡片内、随卡片整体移动；
 *              浮窗实现：输入视图铺满整屏当画布，卡片（20dp 抓手 + 候选栏 + 键盘）是画布里的
 *              一个子 View，位置由外边距决定，抓手按住后可上下左右任意拖动；
 *              onComputeInsets 把卡片外的触摸区域让回应用（浮窗不挤压、也不独占屏幕）。
 *              键盘布局在输入法显示/隐藏、横竖屏切换、设置项变化时统一走 applyLayoutMode()。
 *   2026-10-28 浮窗改为「整屏输入视图 + 卡片子 View」：原先 PopupWindow 挂在 IME 窗口下，
 *              会被按 1px 高的父窗口裁成 0 高——卡片收不到触摸、候选栏量不出高度所以空白；
 *              现在输入视图铺满整屏当画布，卡片用它自己的外边距定位，抓手拖动只改边距，
 *              上下左右都跟手（拖动中不重算 insets，松手才刷新，避免手势被重布局打断）；
 *              onComputeInsets 把可见区让回应用（浮窗不挤压应用排版），
 *              可触摸区域收窄成卡片矩形，卡片外的点击照常落到应用上。
 *   2026-10-28 浮窗卡片宽度改为固定 420dp（原来取屏宽 42%，横屏会撑到 1431px 显得过宽），
 *              横竖屏浮窗尺寸与键面大小完全一致。
 *   2026-10-28 手机不给手选布局：非平板（最小宽度 < 600dp）时横屏自动悬浮、竖屏自动平铺，
 *              旋转后由 onConfigurationChanged 重新 loadPrefs + applyLayoutMode 生效；
 *              平板仍按设置里的选择走。
 */
public class JhfImeService extends InputMethodService implements ChemKeyboardView.Listener {

    private static final Set<String> TWO_LETTER_ELEMENTS = new HashSet<>(Arrays.asList(
            "He", "Li", "Be", "Ne", "Na", "Mg", "Al", "Si", "Cl", "Ar", "Ca", "Sc", "Ti",
            "Cr", "Mn", "Fe", "Co", "Ni", "Cu", "Zn", "Ga", "Ge", "As", "Se", "Br", "Kr",
            "Rb", "Sr", "Zr", "Nb", "Mo", "Tc", "Ru", "Rh", "Pd", "Ag", "Cd", "In", "Sn",
            "Sb", "Te", "Xe", "Cs", "Ba", "La", "Ce", "Pr", "Nd", "Pm", "Sm", "Eu", "Gd",
            "Tb", "Dy", "Ho", "Er", "Tm", "Yb", "Lu", "Hf", "Ta", "Re", "Os", "Ir", "Pt",
            "Au", "Hg", "Tl", "Pb", "Bi", "Po", "At", "Rn", "Fr", "Ra", "Ac", "Th", "Pa",
            "Np", "Pu", "Am", "Cm", "Bk", "Cf", "Es", "Fm", "Md", "No", "Lr", "Rf", "Db",
            "Sg", "Bh", "Hs", "Mt", "Ds", "Rg", "Cn", "Nh", "Fl", "Mc", "Lv", "Ts", "Og"));

    private PinyinMatcher matcher;
    private ChemKeyboardView keyboard;
    /** 长按浮层（叠在根布局最上层，键盘负责算位置、这里负责画）。 */
    private KeyPopupView popupView;
    private TextView pageChip, composingView;
    /** 收起键盘箭头（客户提供的 SVG，随主题色着色）。 */
    private ImageView collapseChip;
    private LinearLayout candBar, candList, candExpanded;
    private FrameLayout candContainer;
    private HorizontalScrollView candScroll;
    private boolean expanded = false;
    private final StringBuilder composing = new StringBuilder();
    private boolean pinyinMode = true;
    /**
     * 元素模式默认开启自动大小写（2026-10-25 客户要求）：每个元素首字母大写、第二个字母小写。
     * 仍可在「拼｜素」键上长按关闭。
     */
    private boolean autoCase = true;
    /** 点按 Shift 后的一次性大写（输出一个字母后自动清除）。 */
    private boolean shiftOn = false;
    /** 长按 Shift 的按住状态：期间连续输出大写，松手恢复小写。 */
    private boolean shiftHeld = false;
    private List<PinyinMatcher.Candidate> current;
    private String themeId = "cucl2";
    private boolean darkMode = false;
    /** 平板布局模式（2026-10-28）：0 铺满 / 1 左右断开 / 2 浮窗。 */
    private int kbLayout = ChemKeyboardView.LAYOUT_FULL;
    /**
     * 浮窗卡片：整张卡（抓手 + 候选栏 + 键盘）是输入视图里的一个普通子 View，
     * 位置用外边距控制（2026-10-28 二次实现：原来的 PopupWindow 会被系统按 IME
     * 窗口高度裁成 0 高，导致卡片收不到触摸、候选栏不显示）。
     */
    private FrameLayout inputHost, floatCard, gripBar;
    private View gripLineView;
    private LinearLayout cardColumn;
    private boolean floatPositioned = false;
    private int floatX, floatY;
    /** 抓手拖动起点（屏幕坐标与起始边距）。 */
    private float dragRawX, dragRawY;
    private int dragStartX, dragStartY;
    private static final float GRIP_DP = 20f;
    /** 设置项变化监听：键盘布局改动需要即时下发（2026-10-27）。 */
    private SharedPreferences prefs;
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener;
    /** 按键震动反馈（主界面「通用 → 触感反馈」，2026-10-26）。 */
    private boolean haptic = true;
    /** 壁纸变了：「自动」主题的缓存色作废，键盘在屏上时立即换色。 */
    private BroadcastReceiver wallpaperReceiver;

    private static final String[] THEME_NAMES = {"AgBr", "KMnO₄", "CuCl₂", "Fe₂O₃"};
    private static final String[] THEME_IDS = {"agbr", "kmno4", "cucl2", "fe2o3"};

    @Override
    public void onCreate() {
        super.onCreate();
        matcher = new PinyinMatcher(this);
        wallpaperReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                ChemKeyboardView.invalidateAutoColors();
                if (ChemKeyboardView.THEME_AUTO.equals(themeId) && keyboard != null) {
                    loadPrefs();
                    applyTheme();
                }
            }
        };
        registerReceiver(wallpaperReceiver, new IntentFilter(Intent.ACTION_WALLPAPER_CHANGED));
        // 设置页改动「键盘布局」后立即生效：输入法进程会常驻，光靠下次重建输入视图不会刷新
        prefs = getSharedPreferences("jhf_settings", Context.MODE_PRIVATE);
        prefListener = (sp, key) -> {
            if ("kb_layout".equals(key)) {
                loadPrefs();
                applyLayoutMode();
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
    }

    @Override
    public void onDestroy() {
        if (prefs != null && prefListener != null) {
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
            prefs = null;
            prefListener = null;
        }
        if (wallpaperReceiver != null) {
            try {
                unregisterReceiver(wallpaperReceiver);
            } catch (Throwable ignored) {
            }
            wallpaperReceiver = null;
        }
        super.onDestroy();
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false; // 避免横屏抽取全屏
    }

    private void loadPrefs() {
        SharedPreferences p = getSharedPreferences("jhf_settings", Context.MODE_PRIVATE);
        themeId = p.getString("theme", "cucl2");
        boolean follow = p.getBoolean("follow", false);
        darkMode = follow ? systemDark() : p.getBoolean("dark", false);
        haptic = p.getBoolean("haptic", true);
        kbLayout = p.getInt("kb_layout", ChemKeyboardView.LAYOUT_FULL);
        // 手机端不给手动选：横屏自动悬浮、竖屏自动平铺（2026-10-28）
        if (!isTablet()) {
            kbLayout = isLandscape()
                    ? ChemKeyboardView.LAYOUT_FLOAT : ChemKeyboardView.LAYOUT_FULL;
        }
        // 「自动」主题的颜色来自系统壁纸，进程起来后必须解析一次
        ChemKeyboardView.resolveAutoColors(this);
    }

    /** 平板判定（与主界面一致）：最小宽度 ≥ 600dp。 */
    private boolean isTablet() {
        return getResources().getConfiguration().smallestScreenWidthDp >= 600;
    }

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private boolean systemDark() {
        int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    /** 跟随系统时，系统深浅色一变，输入法要立刻跟着换肤。 */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        loadPrefs();
        if (keyboard != null) {
            applyTheme();
            floatPositioned = false;   // 横竖屏尺寸变了，浮窗按新屏幕重新落位
            applyLayoutMode();
        }
    }

    private boolean isDarkColor(int c) {
        return (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) < 160;
    }

    private int onAccent() {
        return isDarkColor(ChemKeyboardView.themeColor(themeId)) ? 0xFFFFFFFF : 0xE6101418;
    }

    @Override
    public View onCreateInputView() {
        float d = getResources().getDisplayMetrics().density;
        loadPrefs();

        // 候选栏：[化|符] [组合区+候选…] [⌄]
        candBar = new LinearLayout(this);
        candBar.setOrientation(LinearLayout.HORIZONTAL);
        candBar.setGravity(Gravity.CENTER_VERTICAL);
        candBar.setPadding((int) (8 * d), 0, (int) (7 * d), 0);

        pageChip = outlineChip("化｜符", (int) (75 * d));
        pageChip.setOnClickListener(v -> {
            keyboard.togglePage();
            restylePageChip();
        });

        composingView = new TextView(this);
        composingView.setTextSize(14);
        composingView.setSingleLine();
        composingView.setTextColor(darkMode ? 0xFF8A959B : 0xFF8A959B);
        composingView.setVisibility(View.GONE);

        candList = new LinearLayout(this);
        candList.setOrientation(LinearLayout.HORIZONTAL);
        candList.setGravity(Gravity.CENTER_VERTICAL);
        candScroll = new HorizontalScrollView(this);
        candScroll.setHorizontalScrollBarEnabled(false);
        candScroll.setClipToPadding(false);
        LinearLayout hsvInner = new LinearLayout(this);
        hsvInner.setOrientation(LinearLayout.HORIZONTAL);
        hsvInner.setGravity(Gravity.CENTER_VERTICAL);
        hsvInner.addView(composingView);
        hsvInner.addView(candList);
        candScroll.addView(hsvInner);

        candExpanded = new LinearLayout(this);
        candExpanded.setOrientation(LinearLayout.VERTICAL);
        candExpanded.setVisibility(View.GONE);

        candContainer = new FrameLayout(this);
        LinearLayout.LayoutParams contLp = new LinearLayout.LayoutParams(
                0, (int) (35 * d), 1f);
        contLp.setMargins(0, (int) (9 * d), 0, (int) (8 * d));
        candContainer.setLayoutParams(contLp);
        candScroll.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        candExpanded.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        candContainer.addView(candScroll);
        candContainer.addView(candExpanded);

        // 候选栏右侧 ⌄ = 收起键盘（客户 2026-10-15 明确：底部 ⇌ 才是切换输入法，⌄ 是这个）
        collapseChip = iconChip(R.drawable.ic_chevron_down, (int) (35 * d));
        collapseChip.setOnClickListener(v -> requestHideSelf(0));
        // 展开/收起候选改挂长按，功能不丢（客户只要求 ⌄ 用作收起键盘，没要求删掉展开态）
        collapseChip.setOnLongClickListener(v -> {
            toggleCandExpanded();
            return true;
        });

        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                (int) (75 * d), (int) (35 * d));
        chipLp.setMargins(0, 0, (int) (8 * d), 0);
        pageChip.setLayoutParams(chipLp);
        LinearLayout.LayoutParams expLp = new LinearLayout.LayoutParams(
                (int) (35 * d), (int) (35 * d));
        expLp.setMargins((int) (8 * d), 0, 0, 0);
        collapseChip.setLayoutParams(expLp);
        candBar.addView(pageChip);
        candBar.addView(candContainer);
        candBar.addView(collapseChip);
        candBar.setElevation(2 * d);

        keyboard = new ChemKeyboardView(this, this);
        keyboard.setPinyinMode(pinyinMode);
        keyboard.setAutoCase(autoCase);
        keyboard.setLayoutMode(kbLayout);

        // ---- 浮窗抓手（2026-10-28）：整张卡片顶部 20dp 横条，按住可上下左右拖动整卡 ----
        gripBar = new FrameLayout(this);
        gripLineView = new View(this);
        gripBar.addView(gripLineView, new FrameLayout.LayoutParams(
                (int) (44 * d), (int) (4 * d), Gravity.CENTER));
        gripBar.setOnTouchListener((v, ev) -> handleGripTouch(v, ev));

        // 一张卡片 = 抓手 + 候选栏 + 键盘；浮窗时整张卡片在输入视图里按边距定位，候选栏随卡片移动
        cardColumn = new LinearLayout(this);
        cardColumn.setOrientation(LinearLayout.VERTICAL);
        cardColumn.addView(gripBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (GRIP_DP * d)));
        cardColumn.addView(candBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        cardColumn.addView(keyboard, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // 卡片外层再套一层：长按浮层要盖在键区上方（键盘自己画不出界外）
        floatCard = new FrameLayout(this);
        floatCard.addView(cardColumn, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        popupView = new KeyPopupView(this);
        // 不传 LayoutParams：它自己在显示时按浮层区域设置宽高与边距。
        floatCard.addView(popupView);

        // 输入视图宿主：铺满/断开时高度正好一张卡片；浮窗时铺满整屏当画布（触摸区域收窄见 onComputeInsets）
        inputHost = new FrameLayout(this);
        applyTheme();
        applyLayoutMode();
        refreshCandidates();
        return inputHost;
    }

    /** 卡片底色（浮窗时的卡片本身；直角，跟随深浅色）。 */
    private GradientDrawable cardBackground() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(darkMode ? 0xFF000000 : 0xFFEAEAEA);
        g.setCornerRadius(0);
        return g;
    }

    /** 抓手横杠颜色跟随深浅色。 */
    private void restyleGripLine() {
        if (gripLineView == null) return;
        GradientDrawable g = new GradientDrawable();
        g.setColor(darkMode ? 0x59FFFFFF : 0x59000000);
        g.setCornerRadius(2 * getResources().getDisplayMetrics().density);
        gripLineView.setBackground(g);
    }

    /**
     * 按当前模式装配卡片（2026-10-28 二次实现）：
     * 铺满 / 左右断开 → 输入视图正好一张卡片高，卡片铺满宽度；
     * 浮窗 → 输入视图铺满整屏当画布，卡片在其中按边距定位、可上下左右拖动。
     */
    private void applyLayoutMode() {
        if (keyboard == null || inputHost == null || floatCard == null) return;
        keyboard.setLayoutMode(kbLayout);
        boolean floatMode = kbLayout == ChemKeyboardView.LAYOUT_FLOAT;
        if (gripBar != null) gripBar.setVisibility(floatMode ? View.VISIBLE : View.GONE);
        if (cardColumn != null) cardColumn.setBackground(floatMode ? cardBackground() : null);
        restyleGripLine();
        if (floatMode) {
            inputHost.setMinimumHeight(displaySize().y);
            if (floatCard.getParent() != inputHost) {
                inputHost.addView(floatCard, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            if (!floatPositioned) {
                // 卡片高度要等它真正布局完才准（未布局时手量会偏小，卡片会掉到屏幕外）
                inputHost.post(() -> {
                    if (kbLayout != ChemKeyboardView.LAYOUT_FLOAT || floatPositioned) return;
                    setFloatDefaultPosition();
                    floatPositioned = true;
                    placeFloatCard();
                });
            } else {
                placeFloatCard();
            }
        } else {
            inputHost.setMinimumHeight(1);
            if (floatCard.getParent() != inputHost) {
                inputHost.addView(floatCard, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) floatCard.getLayoutParams();
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.leftMargin = 0;
            lp.topMargin = 0;
            floatCard.setLayoutParams(lp);
            inputHost.requestLayout();
        }
    }

    /** 浮窗卡片宽度：固定 420dp（横屏与竖屏同尺寸，键面大小一致），且两侧各留 8dp。 */
    private int floatCardWidth() {
        float d = getResources().getDisplayMetrics().density;
        int sw = displaySize().x;
        int w = (int) (420 * d);
        return Math.min(w, sw - (int) (16 * d));
    }

    /** 屏幕真实尺寸（横屏时宽高互换，不受输入法 Resources 的陈旧配置影响）。 */
    private Point displaySize() {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect b = wm.getCurrentWindowMetrics().getBounds();
            return new Point(b.width(), b.height());
        }
        Point p = new Point();
        wm.getDefaultDisplay().getRealSize(p);
        return p;
    }

    /** 按给定宽度量一次卡片高度（还没布局时用它决定默认落点与拖动边界）。 */
    private int measureCardHeight(int cardW) {
        if (floatCard == null) return 0;
        floatCard.measure(View.MeasureSpec.makeMeasureSpec(cardW, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return floatCard.getMeasuredHeight();
    }

    /** 首次进入浮窗：水平居中、贴底上方 24dp。 */
    private void setFloatDefaultPosition() {
        int cardW = floatCardWidth();
        int cardH = floatCard.getHeight() > 0 ? floatCard.getHeight() : measureCardHeight(cardW);
        Point size = displaySize();
        floatX = (size.x - cardW) / 2;
        floatY = size.y - cardH - (int) (24 * getResources().getDisplayMetrics().density);
    }

    /**
     * 把 floatX / floatY 落到卡片外边距上；整卡（抓手 + 候选栏 + 键盘）一起移动。
     *
     * @param refreshTouchRegion 是否顺手刷新可触摸区域。拖动过程中不能刷：
     *                           输入法窗口一旦重算 insets 会重新布局，进行中的手势会被系统取消
     *                           （表现为拖一下就不跟手），所以拖动时只挪位置，松手才刷新。
     */
    private void placeFloatCard(boolean refreshTouchRegion) {
        if (floatCard == null || inputHost == null) return;
        int cardW = floatCardWidth();
        int cardH = floatCard.getHeight() > 0 ? floatCard.getHeight() : measureCardHeight(cardW);
        clampFloat(cardW, cardH);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) floatCard.getLayoutParams();
        if (lp == null) return;
        lp.width = cardW;
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        lp.leftMargin = floatX;
        lp.topMargin = floatY;
        floatCard.setLayoutParams(lp);
        if (refreshTouchRegion) inputHost.requestLayout();
    }

    private void placeFloatCard() {
        placeFloatCard(true);
    }

    /** 把卡片位置限制在屏幕内（上下左右都不能拖出屏）。 */
    private void clampFloat(int cardW, int cardH) {
        Point size = displaySize();
        floatX = Math.max(0, Math.min(floatX, size.x - cardW));
        floatY = Math.max(0, Math.min(floatY, Math.max(0, size.y - cardH)));
    }

    /** 抓手拖动：整卡（含候选栏）跟着手指上下左右走。 */
    private boolean handleGripTouch(View v, MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragRawX = e.getRawX();
                dragRawY = e.getRawY();
                dragStartX = floatX;
                dragStartY = floatY;
                return true;
            case MotionEvent.ACTION_MOVE:
                floatX = dragStartX + Math.round(e.getRawX() - dragRawX);
                floatY = dragStartY + Math.round(e.getRawY() - dragRawY);
                placeFloatCard(false);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                // 松手后再刷新触摸区域，避免拖动中途被窗口重布局打断
                placeFloatCard(true);
                return true;
            default:
                return true;
        }
    }

    /**
     * 浮窗不挤压应用、只让卡片本身接收触摸（2026-10-28）。
     * 输入视图在浮窗模式下铺满整屏，系统默认会把整屏都当成键盘热区，
     * 这里把可见区/内容区都顶到屏幕底部（应用按“没有键盘”排版），
     * 可触摸区域收窄成卡片矩形，卡片外的点击照常到达应用。
     */
    @Override
    public void onComputeInsets(Insets outInsets) {
        super.onComputeInsets(outInsets);
        if (kbLayout != ChemKeyboardView.LAYOUT_FLOAT || floatCard == null
                || floatCard.getParent() != inputHost) return;
        Point size = displaySize();
        outInsets.contentTopInsets = size.y;
        outInsets.visibleTopInsets = size.y;
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION;
        int w = floatCard.getWidth() > 0 ? floatCard.getWidth() : floatCardWidth();
        int h = floatCard.getHeight() > 0 ? floatCard.getHeight() : measureCardHeight(w);
        outInsets.touchableRegion.set(floatX, floatY, floatX + w, floatY + h);
    }

    @Override
    public void onLongPressPopup(List<String> opts, android.graphics.RectF box,
                                 int hover, float ax, float ay) {
        if (popupView != null) popupView.update(opts, box, hover, ax, ay);
    }

    private TextView outlineChip(String text, int width) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setGravity(Gravity.CENTER);
        // 2026-10-26 候选栏「化｜符」芯片字号与空格键对齐：键盘 canvas 的字号是 18 * density px
        // （功能键 STYLE_SPECIAL），换算成 dp 即 18dp；这里用 COMPLEX_UNIT_DIP 而非 sp，
        // 才不会随系统字体缩放变大、与键盘字不同步。
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tv.setMaxLines(1);
        restyleOutline(tv, width);
        return tv;
    }

    /** 图标芯片：与文字芯片同样的直角描边框，图标按主题色着色。 */
    private ImageView iconChip(int res, int width) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(res);
        iv.setScaleType(ImageView.ScaleType.CENTER);
        restyleIconChip(iv, width);
        return iv;
    }

    private void restyleIconChip(ImageView iv, int width) {
        if (iv == null) return;
        float d = getResources().getDisplayMetrics().density;
        GradientDrawable g = new GradientDrawable();
        g.setColor(darkMode ? 0xFF000000 : Color.WHITE);
        g.setCornerRadius(0); // 设计稿直角
        g.setStroke((int) (1.2f * d), ChemKeyboardView.themeColor(themeId));
        iv.setBackground(g);
        iv.setColorFilter(ChemKeyboardView.themeColor(themeId));
        if (width > 0) iv.setMinimumWidth(width);
    }

    private void restyleOutline(TextView tv, int width) {
        float d = getResources().getDisplayMetrics().density;
        GradientDrawable g = new GradientDrawable();
        g.setColor(darkMode ? 0xFF000000 : Color.WHITE);
        g.setCornerRadius(0); // 设计稿直角
        g.setStroke((int) (1.2f * d), ChemKeyboardView.themeColor(themeId));
        tv.setBackground(g);
        tv.setTextColor(ChemKeyboardView.themeColor(themeId));
        tv.setMinimumWidth(width);
    }

    private void restylePageChip() {
        boolean symbolPage = keyboard.isSymbolPage();
        float d = getResources().getDisplayMetrics().density;
        GradientDrawable g = new GradientDrawable();
        if (symbolPage) {
            // 符号页：描边态
            g.setColor(darkMode ? 0xFF000000 : Color.WHITE);
            g.setCornerRadius(0);
            g.setStroke((int) (1.2f * d), ChemKeyboardView.themeColor(themeId));
            pageChip.setTextColor(ChemKeyboardView.themeColor(themeId));
        } else {
            // 字母页（默认）：主题色填充白字（设计稿）
            g.setColor(ChemKeyboardView.themeColor(themeId));
            g.setCornerRadius(0);
            pageChip.setTextColor(onAccent());
        }
        pageChip.setBackground(g);
        pageChip.setText("化｜符");
    }

    /**
     * 候选「展开态」开关（98dp / 每行 5 个）。
     * 客户 2026-10-15 要求 ⌄ 芯片做收起键盘，于是这里改挂长按；单击行为与芯片外观都不变。
     */
    private void toggleCandExpanded() {
        float d = getResources().getDisplayMetrics().density;
        expanded = !expanded;
        candScroll.setVisibility(expanded ? View.GONE : View.VISIBLE);
        candExpanded.setVisibility(expanded ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) candContainer.getLayoutParams();
        lp.height = (int) ((expanded ? 98 : 35) * d);
        candContainer.setLayoutParams(lp);
        refreshCandidates();
        toast(expanded ? "候选已展开（长按 ⌄ 收起）" : "候选已收起");
    }

    private void applyTheme() {
        keyboard.setThemeColors(themeId, darkMode);
        keyboard.setHapticEnabled(haptic);
        if (popupView != null) popupView.setTheme(darkMode, ChemKeyboardView.themeColor(themeId));
        restylePageChip();
        restyleIconChip(collapseChip, 0);
        composingView.setTextColor(darkMode ? 0x8AFFFFFF : 0xFF8A959B);
        // 设计稿：候选栏卡片底 + 输入区主题色描边
        float d = getResources().getDisplayMetrics().density;
        GradientDrawable barBg = new GradientDrawable();
        barBg.setColor(darkMode ? 0xFF000000 : Color.WHITE);
        candBar.setBackground(barBg);
        GradientDrawable ring = new GradientDrawable();
        ring.setColor(Color.TRANSPARENT);
        ring.setCornerRadius(0); // 设计稿直角
        ring.setStroke((int) (1.2f * d), ChemKeyboardView.themeColor(themeId));
        candContainer.setBackground(ring);
        // 浮窗卡片底色与抓手颜色跟随深浅色
        restyleGripLine();
        if (cardColumn != null && kbLayout == ChemKeyboardView.LAYOUT_FLOAT) {
            cardColumn.setBackground(cardBackground());
        }
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        loadPrefs();
        if (keyboard != null) {
            applyTheme();
            composing.setLength(0);
            updateComposition();
            applyLayoutMode();
        }
    }

    // ---------- 键盘回调 ----------

    @Override
    public void onChar(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        suggestSymbols.clear();   // 重新输入后，上一次上屏的 ↓/↑/离子联想作废
        if (s.length() == 1 && ((s.charAt(0) >= 'A' && s.charAt(0) <= 'Z')
                || (s.charAt(0) >= 'a' && s.charAt(0) <= 'z'))) {
            char ch = s.charAt(0);
            if (pinyinMode) {
                composing.append(Character.toLowerCase(ch));
            } else {
                composing.append(caseFor(ch));
                // 点按 Shift 只生效一次：输出一个字母后立刻恢复默认大小写（2026-10-25）
                if (shiftOn && !shiftHeld) {
                    shiftOn = false;
                    keyboard.setShiftOn(false);
                }
            }
            updateComposition();
        } else {
            // 直通模式下数字可以开头进入组合区（2026-10-27）：用于同位素质量数，
            // 如 2H → ²H、14C → ¹⁴C、235U → ²³⁵U；拼音模式数字仍直接上屏。
            // 符号页「角标」的上标数字也按数字处理，用户点 ² 再打 H 就能得到 ²H。
            if (!pinyinMode && composing.length() == 0 && s.length() == 1) {
                char c0 = s.charAt(0);
                int sup = SUP_DIGIT_CHARS.indexOf(c0);
                if ((c0 >= '0' && c0 <= '9') || sup >= 0) {
                    composing.append(sup >= 0 ? (char) ('0' + sup) : c0);
                    updateComposition();
                    return;
                }
            }
            // 符号页的「mol」等基础单位（2026-10-26）：不直接上屏，先进组合区，
            // 候选栏给出 mol/L、mol/mL 这类复合单位；不选候选时按空格仍原样上屏。
            if (composing.length() == 0 && UNIT_COMBOS.containsKey(s.toLowerCase(Locale.ROOT))) {
                composing.append(s);
                updateComposition();
                return;
            }
            if (composing.length() == 0) ic.commitText(s, 1);
            else {
                composing.append(s);
                updateComposition();
            }
        }
    }

    /**
     * 长按 Shift 的按住/松开回调（2026-10-25）。
     * held=true 时一直保持大写，另一根手指连打字母也不会消耗该状态；
     * held=false 时立即恢复默认小写。
     */
    @Override
    public void onShiftHoldChanged(boolean held) {
        shiftHeld = held;
        shiftOn = held;
        if (keyboard != null) keyboard.setShiftOn(held);
    }

    /**
     * 直通模式大小写规则（2026-10-25 更新）：
     * <ul>
     *   <li>按住 Shift（shiftHeld）或点按 Shift 的一次性状态（shiftOn）→ 大写；</li>
     *   <li>点按 Shift 只影响下一个字母，输出后自动恢复；</li>
     *   <li>默认按元素书写习惯自动大写：新元素首字母大写；同一元素第二个字母小写
 *       （如 C + u → Cu）。判断时只看「最后一个大写字母起的后缀」，因此连续元素
 *       也能正确书写（Na + C + l → NaCl，而不是 NaCL）。</li>
 * </ul>
 */
    private String caseFor(char ch) {
        char c = Character.toLowerCase(ch);
        if (shiftHeld || shiftOn) return String.valueOf(Character.toUpperCase(c));
        if (!autoCase) return String.valueOf(c);
        if (continuesTwoLetterElement(c)) {
            return String.valueOf(c);                       // 元素续写：C + u → Cu / NaC + l → NaCl
        }
        return String.valueOf(Character.toUpperCase(c));    // 新元素首字母自动大写
    }

    /**
     * 判断当前字母是否应作为「双字母元素的第二个字母」输出小写。
     * 取组合区末尾连续字母中最后一个大写字母起的后缀：后缀只剩 1 个字母
     * 且与大写字母拼成的元素存在时，才小写。这样 NaCl 的 l 会小写，
     * 而 Cu 之后的 S（新元素）仍保持大写。
     */
    private boolean continuesTwoLetterElement(char c) {
        int end = composing.length();
        int start = end;
        while (start > 0 && isLetter(composing.charAt(start - 1))) start--;
        int lastUpper = -1;
        for (int k = start; k < end; k++) {
            if (Character.isUpperCase(composing.charAt(k))) lastUpper = k;
        }
        if (lastUpper < 0 || end - lastUpper != 1) return false;
        return TWO_LETTER_ELEMENTS.contains(composing.charAt(lastUpper) + String.valueOf(c));
    }

    private static boolean isLetter(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    @Override
    public void onAction(int action) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        switch (action) {
            case ChemKeyboardView.ACT_SPACE:
                if (pinyinMode && current != null && !current.isEmpty()) {
                    commitCandidate(current.get(0));
                } else if (composing.length() > 0) {
                    // 直接上屏也走联想：沉淀/气体/单质元素上屏后给出 ↓、↑ 或离子符号
                    commitAndSuggest(composing.toString());
                } else {
                    ic.commitText(" ", 1);
                    clearSuggestions(); // 空组合区再按空格：上一轮联想符号作废
                }
                break;
            case ChemKeyboardView.ACT_ENTER:
                if (composing.length() > 0) {
                    commitAndSuggest(composing.toString());
                }
                sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
                break;
            case ChemKeyboardView.ACT_BACK:
                if (composing.length() > 0) {
                    composing.deleteCharAt(composing.length() - 1);
                    updateComposition();
                } else {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
                    clearSuggestions(); // 删除已上屏内容后，旧联想符号不再适用
                }
                break;
            case ChemKeyboardView.ACT_SHIFT:
                // 一次性大写：再点一次可取消（长按「按住大写」由 onShiftHoldChanged 处理）
                shiftOn = !shiftOn;
                shiftHeld = false;
                keyboard.setShiftOn(shiftOn);
                toast(shiftOn ? "Shift：下一个字母大写" : "Shift：小写");
                break;
            case ChemKeyboardView.ACT_CASE:
                autoCase = !autoCase;
                keyboard.setAutoCase(autoCase);
                toast(autoCase ? "元素自动大小写：开（新元素首字母自动大写）"
                        : "元素自动大小写：关（Shift 决定大小写）");
                break;
            case ChemKeyboardView.ACT_MODE:
                pinyinMode = !pinyinMode;
                shiftOn = false;
                shiftHeld = false;
                keyboard.setPinyinMode(pinyinMode);
                composing.setLength(0);
                suggestSymbols.clear(); // 换模式后旧联想不再适用
                updateComposition();
                toast(pinyinMode ? "拼音模式：输入中文名，如 liusu-antong → CuSO₄"
                        : "直通模式：字母直出，元素自动大小写");
                break;
            case ChemKeyboardView.ACT_PAGE:
                keyboard.togglePage();
                restylePageChip();
                break;
            case ChemKeyboardView.ACT_IME: {
                // 底部 ⇌ = 切换输入法（设计稿 §3.5.4）
                InputMethodManager imm =
                        (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showInputMethodPicker();
                break;
            }
            case ChemKeyboardView.ACT_HIDE:
                // 候选栏右侧 ⌄ = 收起键盘（客户 2026-10-15 明确）
                requestHideSelf(0);
                break;
        }
    }

    // ---------- 组词与候选 ----------

    private void updateComposition() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            if (composing.length() == 0) ic.finishComposingText();
            else ic.setComposingText(composing.toString(), 1);
        }
        refreshCandidates();
    }

    private void refreshCandidates() {
        candList.removeAllViews();
        candExpanded.removeAllViews();
        current = null;
        boolean hasComposing = composing.length() > 0;
        composingView.setVisibility(hasComposing ? View.VISIBLE : View.GONE);
        composingView.setText(hasComposing ? composing + " │" : "");

        List<PinyinMatcher.Candidate> list = null;
        if (pinyinMode && hasComposing) {
            current = matcher.match(composing.toString(), 20);
            list = current;
        }
        // 直通模式：识别化学式并给出规范化候选（CuSO4 → CuSO₄）
        String formulaCand = (!pinyinMode && hasComposing)
                ? normalizeFormula(composing.toString()) : null;
        // 复合单位候选（2026-10-26）：点符号页「mol」或手打 kg、L、g 等基础单位时给出
        List<String> unitCands = hasComposing ? unitCandidates(composing.toString()) : null;

        // 单位候选优先规则（2026-10-26）：直通模式、没有拼音候选、或组合区已是多字母单位
        // （kg / ml / mol…，不会与拼音前缀冲突）时，单位排到最前；
        // 只有拼音模式下的单字母 L 仍把拼音候选放前面，单位接在后面，避免打 li、liu 时单位抢位。
        boolean unitsFirst = unitCands != null
                && (!pinyinMode || list == null || list.isEmpty() || composing.length() >= 2);

        if (unitsFirst) {
            addUnitChips(unitCands, true);
            if (list != null && !list.isEmpty()) addPinyinChips(list);
        } else if (list != null && !list.isEmpty()) {
            addPinyinChips(list);
            if (unitCands != null) addUnitChips(unitCands, false);
        } else {
            // 联想符号（2026-10-27）：直通模式下组合区已成化学式时实时给出 ↓/↑/离子；
            // 上屏后组合区为空，则继续保留刚上屏内容的联想符号，直到下一次输入。
            List<String> symbolCands = hasComposing
                    ? composingSuggestions()
                    : (suggestSymbols.isEmpty() ? null : suggestSymbols);
            if (formulaCand != null) {
                // 规范化学式优先占首位，符号候选紧跟其后
                candList.addView(makeFormulaCand(formulaCand));
                if (symbolCands != null) addSymbolChips(symbolCands, false);
            } else if (hasComposing) {
                // 「空格直接上屏」的提示条很宽，符号候选必须排在它前面才不会被挤出屏幕
                if (symbolCands != null) addSymbolChips(symbolCands, false);
                candList.addView(makeFallback());
            } else if (symbolCands != null) {
                addSymbolChips(symbolCands, true);
            }
        }
    }

    /**
     * 直通模式下组合区内容的联想符号：沉淀 → ↓、气体 → ↑、单独元素 → 常见离子。
     * 拼音模式的组合区是拼音串，不参与化学式判断，返回 null。
     */
    private List<String> composingSuggestions() {
        if (pinyinMode || composing.length() == 0) return null;
        String raw = composing.toString();
        String norm = normalizeFormula(raw);
        return suggestionsFor(norm != null ? norm : raw);
    }

    /**
     * 根据化学式生成联想符号：沉淀化合物 → ↓、气体 → ↑、单独元素 → 常见离子电荷符号。
     * 无匹配时返回 null。
     */
    private List<String> suggestionsFor(String formula) {
        if (formula == null || formula.isEmpty()) return null;
        if (PRECIPITATE_FORMULAS.contains(formula)) return Arrays.asList("↓");
        if (GAS_FORMULAS.contains(formula)) return Arrays.asList("↑");
        String[] ions = ELEMENT_IONS.get(formula);
        return ions == null ? null : Arrays.asList(ions);
    }

    /** 铺拼音候选芯片：展开态按 5 个一行，收起态单行横向滚动。 */
    private void addPinyinChips(List<PinyinMatcher.Candidate> list) {
        int perRow = expanded ? 5 : 20;
        LinearLayout row = null;
        float d = getResources().getDisplayMetrics().density;
        for (int i = 0; i < list.size(); i++) {
            if (expanded && i % perRow == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                candExpanded.addView(row, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, (int) (46 * d)));
            }
            View chip = makeCand(list.get(i), i == 0);
            if (expanded) row.addView(chip);
            else candList.addView(chip);
        }
    }

    /** 铺复合单位芯片；head 为 true 表示排在最前、第一个高亮。 */
    private void addUnitChips(List<String> units, boolean head) {
        if (expanded) {
            LinearLayout urow = new LinearLayout(this);
            urow.setOrientation(LinearLayout.HORIZONTAL);
            urow.setGravity(Gravity.CENTER_VERTICAL);
            candExpanded.addView(urow, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (int) (46 * getResources().getDisplayMetrics().density)));
            for (int i = 0; i < units.size(); i++) {
                urow.addView(makeUnitCand(units.get(i), head && i == 0));
            }
        } else {
            for (int i = 0; i < units.size(); i++) {
                candList.addView(makeUnitCand(units.get(i), head && i == 0));
            }
        }
    }

    /** 铺联想符号芯片（↓ / ↑ / 离子）；head 为 true 时首个高亮。 */
    private void addSymbolChips(List<String> symbols, boolean head) {
        if (expanded) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            candExpanded.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (int) (46 * getResources().getDisplayMetrics().density)));
            for (int i = 0; i < symbols.size(); i++) {
                row.addView(makeSymbolCand(symbols.get(i), head && i == 0));
            }
        } else {
            for (int i = 0; i < symbols.size(); i++) {
                candList.addView(makeSymbolCand(symbols.get(i), head && i == 0));
            }
        }
    }

    // ---------- 复合单位候选（2026-10-26） ----------

    /**
     * 基础单位 → 常见复合单位。键统一用小写（直通模式自动大写会把 kg 变成 KG，
     * 这里按小写查表）；字母大小写与上下标按化学/物理书写习惯固定，不受输入影响。
     */
    private static final Map<String, String[]> UNIT_COMBOS = new LinkedHashMap<>();

    static {
        UNIT_COMBOS.put("mol", new String[]{"mol/L", "mol/mL", "mol/m³", "mol/kg",
                "mol/(L·s)", "mmol/L", "μmol/L"});
        UNIT_COMBOS.put("mmol", new String[]{"mmol/L", "mmol/mL", "mmol/kg"});
        UNIT_COMBOS.put("kg", new String[]{"kg/m³", "kg/mol", "kg/m²", "kg/(m·s)"});
        UNIT_COMBOS.put("g", new String[]{"g/mol", "g/L", "g/mL", "g/cm³"});
        UNIT_COMBOS.put("mg", new String[]{"mg/L", "mg/mL", "mg/kg"});
        UNIT_COMBOS.put("l", new String[]{"L/mol", "L/(mol·s)", "L/min", "L/s"});
        UNIT_COMBOS.put("ml", new String[]{"mL/min", "mL/s", "mL/mol"});
        UNIT_COMBOS.put("m", new String[]{"m/s", "m/s²", "m³", "m³/s"});
        UNIT_COMBOS.put("cm", new String[]{"cm/s", "cm³", "cm²"});
        UNIT_COMBOS.put("s", new String[]{"s⁻¹", "s/mol", "s/(mol·L)"});
        UNIT_COMBOS.put("pa", new String[]{"Pa·s", "Pa/(m·s)", "Pa·m³/mol"});
        UNIT_COMBOS.put("n", new String[]{"N/m²", "N·m", "N/m"});
        UNIT_COMBOS.put("j", new String[]{"J/mol", "J/(mol·K)", "J/(kg·K)", "J/K"});
        UNIT_COMBOS.put("kj", new String[]{"kJ/mol", "kJ/(kg·K)", "kJ/kg"});
        UNIT_COMBOS.put("w", new String[]{"W/m²", "W/(m·K)", "W/(m²·K)"});
        UNIT_COMBOS.put("v", new String[]{"V/m", "V/mol"});
        UNIT_COMBOS.put("a", new String[]{"A/m²", "A·h", "A·h/kg"});
    }

    // ---------- 上屏联想符号（2026-10-27） ----------

    /** 常见气体（规范化化学式）：上屏后在联想栏给出「↑」。 */
    private static final Set<String> GAS_FORMULAS = new HashSet<>(Arrays.asList(
            "H₂", "O₂", "N₂", "F₂", "Cl₂", "O₃", "CO", "CO₂", "SO₂", "SO₃", "NO", "NO₂",
            "N₂O", "NH₃", "HCl", "H₂S", "HF", "HBr", "HI", "CH₄", "C₂H₄", "C₂H₂"));

    /** 常见难溶物/沉淀（规范化化学式）：上屏后在联想栏给出「↓」。 */
    private static final Set<String> PRECIPITATE_FORMULAS = new HashSet<>(Arrays.asList(
            "AgCl", "AgBr", "AgI", "Ag₂CO₃", "Ag₂S", "BaSO₄", "BaSO₃", "BaCO₃", "BaCrO₄",
            "CaCO₃", "Ca₃(PO₄)₂", "Cu(OH)₂", "CuCO₃", "CuS", "Fe(OH)₂", "Fe(OH)₃", "FeS",
            "FeCO₃", "Mg(OH)₂", "MgCO₃", "Al(OH)₃", "Zn(OH)₂", "ZnS", "PbSO₄", "PbI₂",
            "PbS", "MnCO₃"));

    /** 单独元素 → 该单质常见离子电荷符号（Fe → ²⁺、³⁺；Cl → ⁻）。 */
    private static final Map<String, String[]> ELEMENT_IONS = new LinkedHashMap<>();
    static {
        ELEMENT_IONS.put("H", new String[]{"⁺"});
        ELEMENT_IONS.put("Na", new String[]{"⁺"});
        ELEMENT_IONS.put("K", new String[]{"⁺"});
        ELEMENT_IONS.put("Ag", new String[]{"⁺"});
        ELEMENT_IONS.put("Mg", new String[]{"²⁺"});
        ELEMENT_IONS.put("Ca", new String[]{"²⁺"});
        ELEMENT_IONS.put("Ba", new String[]{"²⁺"});
        ELEMENT_IONS.put("Zn", new String[]{"²⁺"});
        ELEMENT_IONS.put("Cu", new String[]{"²⁺", "⁺"});
        ELEMENT_IONS.put("Fe", new String[]{"²⁺", "³⁺"});
        ELEMENT_IONS.put("Al", new String[]{"³⁺"});
        ELEMENT_IONS.put("F", new String[]{"⁻"});
        ELEMENT_IONS.put("Cl", new String[]{"⁻"});
        ELEMENT_IONS.put("Br", new String[]{"⁻"});
        ELEMENT_IONS.put("I", new String[]{"⁻"});
        ELEMENT_IONS.put("O", new String[]{"²⁻"});
        ELEMENT_IONS.put("S", new String[]{"²⁻"});
        ELEMENT_IONS.put("N", new String[]{"³⁻"});
    }

    /** 上屏后保留在联想栏的符号（↓ / ↑ / 离子）；重新输入任何字符即清空。 */
    private final List<String> suggestSymbols = new ArrayList<>();

    /**
     * 组合区内容正好是一个基础单位时，返回对应的复合单位列表；否则 null。
     * 单字母只放行 L（升），避免输入元素符号 N、S、J 时被单位候选干扰。
     */
    private List<String> unitCandidates(String raw) {
        if (raw.length() < 2 && !raw.equalsIgnoreCase("L")) return null;
        String[] arr = UNIT_COMBOS.get(raw.toLowerCase(Locale.ROOT));
        return arr == null ? null : Arrays.asList(arr);
    }

    /** 单位候选芯片：副标题标明「复合单位」，点选上屏并清空组合区。 */
    private View makeUnitCand(String unit, boolean primary) {
        View box = makeMiniCand(unit, "复合单位", primary);
        box.setOnClickListener(v -> {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.commitText(unit, 1);
                composing.setLength(0);
                updateComposition();
            }
        });
        return box;
    }

    /**
     * 联想符号芯片（2026-10-27）：↓ 沉淀符号 / ↑ 气体符号 / 离子电荷。
     * 点选只上屏符号本身并清空联想（如 Fe 之后点 ³⁺ → Fe³⁺）。
     */
    private View makeSymbolCand(String symbol, boolean primary) {
        String subtitle = "↓".equals(symbol) ? "沉淀符号"
                : "↑".equals(symbol) ? "气体符号" : "离子";
        View box = makeMiniCand(symbol, subtitle, primary);
        box.setOnClickListener(v -> {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.commitText(symbol, 1);
                composing.setLength(0);
                suggestSymbols.clear();
                updateComposition();
            }
        });
        return box;
    }

    /** 候选小芯片的公共外观（主文本 + 副标题 + 直角描边），点击行为由调用方挂。 */
    private View makeMiniCand(String main, String subtitle, boolean primary) {
        float d = getResources().getDisplayMetrics().density;
        int accent = ChemKeyboardView.themeColor(themeId);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        // 候选栏容器只有 35dp 高，两行芯片必须收紧上下内边距，否则副标题会被裁掉
        box.setPadding((int) (12 * d), (int) (1 * d), (int) (12 * d), (int) (1 * d));

        TextView f = new TextView(this);
        f.setText(main);
        f.setTextSize(16);
        f.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        f.setSingleLine();
        f.setIncludeFontPadding(false);
        f.setTextColor(primary ? accent : (darkMode ? 0xFFE9EDF0 : 0xFF1B2226));
        TextView n = new TextView(this);
        n.setText(subtitle);
        n.setTextSize(10);
        n.setSingleLine();
        n.setIncludeFontPadding(false);
        n.setTextColor(primary ? (darkMode ? 0xFFB8C2C8 : 0xFF6B767D)
                : (darkMode ? 0x80FFFFFF : 0xFF9AA6AC));
        box.addView(f);
        box.addView(n);

        GradientDrawable g = new GradientDrawable();
        if (primary) {
            g.setColor(darkMode ? 0x33FFFFFF : 0x1A000000);
            g.setStroke((int) d, accent);
        } else {
            g.setColor(darkMode ? 0xFF000000 : Color.WHITE);
            g.setStroke((int) d, darkMode ? 0x33FFFFFF : 0xFFE2E7EA);
        }
        g.setCornerRadius(0);
        box.setBackground(g);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        // 上下不留边距，否则两行芯片高度超过 35dp 候选栏会被裁切
        lp.setMargins((int) (4 * d), 0, (int) (4 * d), 0);
        box.setLayoutParams(lp);
        return box;
    }

    private View makeCand(PinyinMatcher.Candidate c, boolean primary) {
        float d = getResources().getDisplayMetrics().density;
        int accent = ChemKeyboardView.themeColor(themeId);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding((int) (12 * d), (int) (2 * d), (int) (12 * d), (int) (2 * d));

        TextView f = new TextView(this);
        f.setText(c.formula);
        f.setTextSize(16);
        f.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        f.setSingleLine();
        f.setIncludeFontPadding(false);
        TextView n = new TextView(this);
        n.setText(c.entry.name);
        n.setTextSize(10);
        n.setSingleLine();
        n.setIncludeFontPadding(false);
        box.addView(f);
        box.addView(n);

        GradientDrawable g = new GradientDrawable();
        if (primary) {
            g.setColor(darkMode ? 0x33FFFFFF : 0x1A000000);
            g.setStroke((int) d, accent);
            f.setTextColor(accent);
            n.setTextColor(darkMode ? 0xFFB8C2C8 : 0xFF6B767D);
            f.setTextColor(accent);
        } else {
            g.setColor(darkMode ? 0xFF000000 : Color.WHITE);
            g.setStroke((int) d, darkMode ? 0x33FFFFFF : 0xFFE2E7EA);
            f.setTextColor(darkMode ? 0xFFE9EDF0 : 0xFF1B2226);
            n.setTextColor(darkMode ? 0x80FFFFFF : 0xFF9AA6AC);
        }
        g.setCornerRadius(0); // 设计稿直角
        box.setBackground(g);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins((int) (4 * d), 0, (int) (4 * d), 0);
        box.setLayoutParams(lp);

        PinyinMatcher.Candidate cand = c;
        box.setOnClickListener(v -> commitCandidate(cand));
        box.setOnLongClickListener(v -> {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.commitText(cand.entry.name, 1);
                composing.setLength(0);
                updateComposition();
                toast("已输入名称：" + cand.entry.name);
            }
            return true;
        });
        return box;
    }

    private View makeFallback() {
        float d = getResources().getDisplayMetrics().density;
        TextView tv = new TextView(this);
        tv.setText("空格：直接上屏 " + composing);
        tv.setTextSize(13);
        tv.setTextColor(darkMode ? 0x80FFFFFF : 0xFF9AA6AC);
        tv.setPadding((int) (10 * d), 0, (int) (10 * d), 0);
        tv.setSingleLine();
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setOnClickListener(v -> commitAndSuggest(composing.toString()));
        return tv;
    }

    private void commitCandidate(PinyinMatcher.Candidate c) {
        commitAndSuggest(c.formula);
    }

    /**
     * 上屏文本并挂上联想符号（2026-10-27）：沉淀化合物 → ↓、气体 → ↑、
     * 单独元素 → 常见离子电荷符号。所有「上屏」路径都走这里，
     * 保证无论是选拼音候选、按空格还是点规范化学式，联想栏都能给出对应符号。
     */
    private void commitAndSuggest(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.commitText(text, 1);
        composing.setLength(0);
        suggestSymbols.clear();
        List<String> s = suggestionsFor(text);
        if (s != null) suggestSymbols.addAll(s);
        updateComposition();
    }

    /** 清掉上屏后的联想符号并刷新候选栏（空格/删除导致联想失效时调用）。 */
    private void clearSuggestions() {
        if (suggestSymbols.isEmpty()) return;
        suggestSymbols.clear();
        refreshCandidates();
    }

    // ---------- 直通模式的化学式规范化 ----------

    private static final String[] SUB_DIGITS = {"₀", "₁", "₂", "₃", "₄", "₅", "₆", "₇", "₈", "₉"};

    /** 全部元素符号（含历史名），用于判断一段字母串是不是化学式。 */
    private static final Set<String> ALL_ELEMENTS = new HashSet<>(Arrays.asList(
            "H", "He", "Li", "Be", "B", "C", "N", "O", "F", "Ne", "Na", "Mg", "Al", "Si",
            "P", "S", "Cl", "Ar", "K", "Ca", "Sc", "Ti", "V", "Cr", "Mn", "Fe", "Co", "Ni",
            "Cu", "Zn", "Ga", "Ge", "As", "Se", "Br", "Kr", "Rb", "Sr", "Y", "Zr", "Nb", "Mo",
            "Tc", "Ru", "Rh", "Pd", "Ag", "Cd", "In", "Sn", "Sb", "Te", "I", "Xe", "Cs", "Ba",
            "La", "Ce", "Pr", "Nd", "Pm", "Sm", "Eu", "Gd", "Tb", "Dy", "Ho", "Er", "Tm", "Yb",
            "Lu", "Hf", "Ta", "W", "Re", "Os", "Ir", "Pt", "Au", "Hg", "Tl", "Pb", "Bi", "Po",
            "At", "Rn", "Fr", "Ra", "Ac", "Th", "Pa", "U", "Np", "Pu", "Am", "Cm", "Bk", "Cf",
            "Es", "Fm", "Md", "No", "Lr", "Rf", "Db", "Sg", "Bh", "Hs", "Mt", "Ds", "Rg", "Cn",
            "Nh", "Fl", "Mc", "Lv", "Ts", "Og"));

    private static final String[] SUP_DIGITS = {"⁰", "¹", "²", "³", "⁴", "⁵", "⁶", "⁷", "⁸", "⁹"};
    /** 上标数字串（符号页「角标」键的顺序），用于把 ⁿ 还原成普通数字。 */
    private static final String SUP_DIGIT_CHARS = "⁰¹²³⁴⁵⁶⁷⁸⁹";

    private static final Pattern FORMULA_SHAPE =
            Pattern.compile("^[0-9]*([A-Z][a-z]?[0-9]*|\\([^)]*\\)[0-9]*)+$");
    /** 同位素写法：质量数 + 单个元素符号，如 2H、14C、235U（2026-10-27）。 */
    private static final Pattern ISOTOPE_SHAPE = Pattern.compile("^(\\d{1,3})([A-Z][a-z]?)$");
    private static final Pattern ELEM_TOKEN = Pattern.compile("[A-Z][a-z]?");

    /**
     * 直通模式下的化学式规范化：把 CuSO4 → CuSO₄、Fe2O3 → Fe₂O₃、Ca(OH)2 → Ca(OH)₂。
     * 开头系数保留为普通数字。不是化学式（或无需改动）时返回 null。
     */
    private String normalizeFormula(String raw) {
        String t = raw.trim();
        if (t.length() < 2 || !FORMULA_SHAPE.matcher(t).matches()) return null;
        // 至少要含一个真实元素符号，避免把英文单词误判成化学式
        Matcher m = ELEM_TOKEN.matcher(t);
        int elems = 0;
        while (m.find()) {
            if (ALL_ELEMENTS.contains(m.group())) elems++;
        }
        if (elems == 0) return null;

        // 同位素质荷数（2026-10-27）：整串只有「数字 + 单个元素」时数字是质量数，写成上标，
        // 如 2H → ²H、14C → ¹⁴C、235U → ²³⁵U；带其他原子的 2H2O 仍按系数处理，不误伤配平系数。
        Matcher iso = ISOTOPE_SHAPE.matcher(t);
        if (iso.matches() && ALL_ELEMENTS.contains(iso.group(2))) {
            String mass = iso.group(1);
            StringBuilder sb = new StringBuilder(mass.length() + 2);
            for (int i = 0; i < mass.length(); i++) sb.append(SUP_DIGITS[mass.charAt(i) - '0']);
            return sb.append(iso.group(2)).toString();
        }

        StringBuilder sb = new StringBuilder(t.length() + 4);
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            boolean afterAtom = i > 0
                    && (Character.isLetter(t.charAt(i - 1)) || t.charAt(i - 1) == ')');
            if (c >= '0' && c <= '9' && afterAtom) {
                sb.append(SUB_DIGITS[c - '0']);
            } else {
                sb.append(c);
            }
        }
        String out = sb.toString();
        return out.equals(t) ? null : out;
    }

    /** 规范化候选芯片（与化学式候选同风格，副标题说明用途）。 */
    private View makeFormulaCand(String formula) {
        float d = getResources().getDisplayMetrics().density;
        int accent = ChemKeyboardView.themeColor(themeId);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding((int) (12 * d), (int) (2 * d), (int) (12 * d), (int) (2 * d));

        TextView f = new TextView(this);
        f.setText(formula);
        f.setTextSize(16);
        f.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        f.setSingleLine();
        f.setIncludeFontPadding(false);
        f.setTextColor(accent);
        TextView n = new TextView(this);
        n.setText("规范化学式");
        n.setTextSize(10);
        n.setSingleLine();
        n.setIncludeFontPadding(false);
        n.setTextColor(darkMode ? 0xFFB8C2C8 : 0xFF6B767D);
        box.addView(f);
        box.addView(n);

        GradientDrawable g = new GradientDrawable();
        g.setColor(darkMode ? 0x33FFFFFF : 0x1A000000);
        g.setStroke((int) d, accent);
        g.setCornerRadius(0);
        box.setBackground(g);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins((int) (4 * d), 0, (int) (4 * d), 0);
        box.setLayoutParams(lp);

        box.setOnClickListener(v -> commitAndSuggest(formula));
        return box;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // 供主题名展示使用（保持常量引用，避免裁剪）
    static {
        if (THEME_NAMES.length != THEME_IDS.length) throw new IllegalStateException();
    }
}

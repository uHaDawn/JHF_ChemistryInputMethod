package com.jhf.jiehuafa.ime;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 接化发·化学输入法核心服务。
 * 拼音模式：输入中文名拼音 → 候选化学式（如 liusu-antong → 硫酸铜 → CuSO₄）。
 * 直通模式：字母直出，元素符号自动大小写；上滑输出数字角标与化学符号。
 * 候选栏：[化|符] 切换符号页 · 组合区 · 候选芯片 · ⌄ 收起键盘（长按 ⌄ 展开候选）。
 * 键盘底部 ⇌：调起系统输入法切换器。
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
    private TextView pageChip, collapseChip, composingView;
    private LinearLayout candBar, candList, candExpanded;
    private FrameLayout candContainer;
    private HorizontalScrollView candScroll;
    private boolean expanded = false;
    private final StringBuilder composing = new StringBuilder();
    private boolean pinyinMode = true;
    /** 默认关闭：直通模式下由 Shift 决定大小写，保证能正常输入小写字母。 */
    private boolean autoCase = false;
    private boolean shiftOn = false;
    private List<PinyinMatcher.Candidate> current;
    private String themeId = "cucl2";
    private boolean darkMode = false;

    private static final String[] THEME_NAMES = {"AgBr", "KMnO₄", "CuCl₂", "Fe₂O₃"};
    private static final String[] THEME_IDS = {"agbr", "kmno4", "cucl2", "fe2o3"};

    @Override
    public void onCreate() {
        super.onCreate();
        matcher = new PinyinMatcher(this);
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
        // 「自动」主题的颜色来自系统壁纸，进程起来后必须解析一次
        ChemKeyboardView.resolveAutoColors(this);
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
        if (keyboard != null) applyTheme();
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

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

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
        collapseChip = outlineChip("⌄", (int) (35 * d));
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
        applyTheme();

        root.addView(candBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(keyboard, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // 外层套 FrameLayout：长按浮层要盖在键盘上方（键盘自己画不出界外），
        // 该层不可点击，触摸仍然落到下面的键盘上。
        FrameLayout wrap = new FrameLayout(this);
        wrap.addView(root, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        popupView = new KeyPopupView(this);
        popupView.setTheme(darkMode, ChemKeyboardView.themeColor(themeId));
        // 不传 LayoutParams：它自己在显示时按浮层区域设置宽高与边距（用 MATCH_PARENT 会把
        // WRAP_CONTENT 的宿主撑到整屏，IME 窗口跟着变满屏）。
        wrap.addView(popupView);

        refreshCandidates();
        return wrap;
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
        tv.setTextSize(13);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tv.setMaxLines(1);
        restyleOutline(tv, width);
        return tv;
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
        if (popupView != null) popupView.setTheme(darkMode, ChemKeyboardView.themeColor(themeId));
        restylePageChip();
        restyleOutline(collapseChip, 0);
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
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        loadPrefs();
        if (keyboard != null) {
            applyTheme();
            composing.setLength(0);
            updateComposition();
        }
    }

    // ---------- 键盘回调 ----------

    @Override
    public void onChar(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        if (s.length() == 1 && ((s.charAt(0) >= 'A' && s.charAt(0) <= 'Z')
                || (s.charAt(0) >= 'a' && s.charAt(0) <= 'z'))) {
            char ch = s.charAt(0);
            if (pinyinMode) {
                composing.append(Character.toLowerCase(ch));
            } else {
                composing.append(caseFor(ch));
            }
            updateComposition();
        } else {
            if (composing.length() == 0) ic.commitText(s, 1);
            else {
                composing.append(s);
                updateComposition();
            }
        }
    }

    /**
     * 直通模式大小写规则（客户意见 12：必须能打出小写字母）。
     * <ul>
     *   <li>大写锁定开启 → 一律大写；</li>
     *   <li>关闭（默认）→ 一律小写，可以正常输入小写字母；</li>
     *   <li>开启「元素自动大小写」时，额外保留元素书写习惯：已输入 1 个大写首字母
     *       （如 Shift+C），紧随其后的字母若能构成合法双字母元素（C+u→Cu）则输出小写。</li>
     * </ul>
     */
    private String caseFor(char ch) {
        char c = Character.toLowerCase(ch);
        if (shiftOn) return String.valueOf(Character.toUpperCase(c));
        if (!autoCase) return String.valueOf(c);
        String token = trailingLetterToken();
        if (token.length() == 1 && Character.isUpperCase(token.charAt(0))
                && TWO_LETTER_ELEMENTS.contains(token + Character.toUpperCase(c))) {
            return String.valueOf(c);                       // 元素续写：C + u → Cu
        }
        return String.valueOf(Character.toUpperCase(c));    // 新元素首字母自动大写
    }

    private String trailingLetterToken() {
        int i = composing.length();
        while (i > 0 && isLetter(composing.charAt(i - 1))) i--;
        return composing.substring(i);
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
                    ic.commitText(composing.toString(), 1);
                    composing.setLength(0);
                    updateComposition();
                } else {
                    ic.commitText(" ", 1);
                }
                break;
            case ChemKeyboardView.ACT_ENTER:
                if (composing.length() > 0) {
                    ic.commitText(composing.toString(), 1);
                    composing.setLength(0);
                    updateComposition();
                }
                sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
                break;
            case ChemKeyboardView.ACT_BACK:
                if (composing.length() > 0) {
                    composing.deleteCharAt(composing.length() - 1);
                    updateComposition();
                } else {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
                }
                break;
            case ChemKeyboardView.ACT_SHIFT:
                shiftOn = !shiftOn;
                keyboard.setShiftOn(shiftOn);
                toast(shiftOn ? "Shift：大写" : "Shift：小写");
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
                keyboard.setPinyinMode(pinyinMode);
                composing.setLength(0);
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

        if (list != null && !list.isEmpty()) {
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
        } else if (formulaCand != null) {
            candList.addView(makeFormulaCand(formulaCand));
        } else if (hasComposing) {
            candList.addView(makeFallback());
        }
    }

    private View makeCand(PinyinMatcher.Candidate c, boolean primary) {
        float d = getResources().getDisplayMetrics().density;
        int accent = ChemKeyboardView.themeColor(themeId);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding((int) (12 * d), (int) (4 * d), (int) (12 * d), (int) (4 * d));

        TextView f = new TextView(this);
        f.setText(c.formula);
        f.setTextSize(16);
        f.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        f.setSingleLine();
        TextView n = new TextView(this);
        n.setText(c.entry.name);
        n.setTextSize(10);
        n.setSingleLine();
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
        lp.setMargins((int) (4 * d), (int) (4 * d), (int) (4 * d), (int) (4 * d));
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
        tv.setOnClickListener(v -> {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.commitText(composing.toString(), 1);
                composing.setLength(0);
                updateComposition();
            }
        });
        return tv;
    }

    private void commitCandidate(PinyinMatcher.Candidate c) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(c.formula, 1);
            composing.setLength(0);
            updateComposition();
        }
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

    private static final Pattern FORMULA_SHAPE =
            Pattern.compile("^[0-9]*([A-Z][a-z]?[0-9]*|\\([^)]*\\)[0-9]*)+$");
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
        box.setPadding((int) (12 * d), (int) (4 * d), (int) (12 * d), (int) (4 * d));

        TextView f = new TextView(this);
        f.setText(formula);
        f.setTextSize(16);
        f.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        f.setSingleLine();
        f.setTextColor(accent);
        TextView n = new TextView(this);
        n.setText("规范化学式");
        n.setTextSize(10);
        n.setSingleLine();
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
        lp.setMargins((int) (4 * d), (int) (4 * d), (int) (4 * d), (int) (4 * d));
        box.setLayoutParams(lp);

        box.setOnClickListener(v -> {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.commitText(formula, 1);
                composing.setLength(0);
                updateComposition();
            }
        });
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

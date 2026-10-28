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

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import com.jhf.jiehuafa.ime.ChemKeyboardView;

import java.util.ArrayList;
import java.util.List;

/**
 * 接化发主界面（Figma 402 画板，px=dp 一比一还原）：
 * 顶栏卡 381×146 @(10,11)（logo 56×50 卡内(24,24)、标题 32sp 卡内(24,82)、关于 78×33 卡内上11右10）→
 * ◥通用（卡底下8）→ 主题条 381×51（下距5）→ 芯片行 → 模式卡 381×51（上距8）
 * → 触感反馈卡 381×51（上距8，右侧 78×33 开关）→
 * 弹性间隔（设计稿 y309→y504 空白，自适应）→ ◥键盘预览（下距10）→ 候选栏 402×52 + 键盘全宽贴底。
 *
 * 修改日志（之后每次改动都在这里追加一条：日期 + 改动内容）：
 *   2026-10-25 「自动」主题接入莫奈壁纸取色（WallpaperColors → 壁纸位图量化 → accent 兜底）；
 *              预览区收起箭头由文字 ⌄ 改为 ImageView，统一使用 ic_chevron_down 矢量图并随主题色着色；
 *              注册壁纸变更广播，壁纸更换后重新计算主题色并刷新界面。
 *   2026-10-26 板块标题「外观和显示」改为「通用」；新增「触感反馈」卡（78×33 自绘开关，
 *              prefs 键 haptic，默认开启），关闭后键盘按键不再震动（键盘侧 setHapticEnabled）；
 *              开关去掉全部圆角（轨道/滑块均直角，drawRect 绘制），震动实现改走 Vibrator 服务。
 *   2026-10-27 新增「键盘布局」卡（位于触感反馈下方，prefs 键 kb_layout）：点按循环切换
 *              手机默认 / 限宽居中 / 横屏分体 / 悬浮小键盘，右侧实时显示当前模式并弹出提示。
 *   2026-10-28 布局收敛为 铺满 / 左右断开 / 浮窗 三种（删除限宽居中），点按循环这三项。
 *   2026-10-28 「键盘布局」改为与「当前主题」一致的气泡框选择：点卡片弹出靠右对齐的浮层
 *              （头部主题色 + ✕、三行布局、当前项填主题色并打勾），选完即保存；不再点按循环。
 *   2026-10-28 「键盘布局」卡右侧按钮改为与其它卡片一致的 78×33 带边框值框（文字居中），
 *              三个模式显示名改为 平铺 / 悬浮 / 分离（浮层顺序见 KB_ORDER）。
 *   2026-10-28 进入界面检测设备类型：最小宽度 ≥ 600dp 视为平板，可手动选布局；
 *              手机隐藏「键盘布局」卡，布局随屏幕方向自动切换（横屏悬浮、竖屏平铺）。
 *   2026-10-28 深浅色模式改为气泡框选择（浅色 / 深色 / 跟随系统），尺寸与主题/布局浮层一致；
 *              该卡右侧由「切换」按钮改为 78×33 带边框值框，卡标题固定为「深浅色模式」。
 *   2026-10-28 「触感反馈」开关按客户 Figma（Frame 13 / Frame 14）重绘：轨道直角实心
 *              （关 #DDDDDD、开主题色），滑块 34×25 白色、距边 3dp、带 2dp 实心投影，
 *              滑块内标记为关闭圆环 / 开启竖线。
 */
public class MainActivity extends Activity {

    static final String[] THEME_NAMES = {"AgBr", "KMnO₄", "CuCl₂", "Fe₂O₃", "自动"};
    static final String[] THEME_IDS = {"agbr", "kmno4", "cucl2", "fe2o3", ChemKeyboardView.THEME_AUTO};
    /** 主题选择浮层里的显示顺序（设计稿 8a653cd5）。 */
    static final int[] PICKER_ORDER = {3, 2, 1, 0, 4};
    /** 键盘布局浮层里的显示顺序：平铺 → 悬浮 → 分离（常量值不变，避免旧偏好错位）。 */
    static final int[] KB_ORDER = {ChemKeyboardView.LAYOUT_FULL, ChemKeyboardView.LAYOUT_FLOAT,
            ChemKeyboardView.LAYOUT_SPLIT};
    /** 深浅色模式：0 浅色、1 深色、2 跟随系统。 */
    static final String[] MODE_NAMES = {"浅色", "深色", "跟随系统"};

    private String themeId = "cucl2";
    private boolean darkMode = false;
    /** 深浅色是否跟随系统（客户意见 11）。 */
    private boolean followSystem = false;
    /** 按键触感反馈开关（2026-10-26，与输入法共用 prefs 键 haptic）。 */
    private boolean haptic = true;

    private ScrollView scroll;
    private FrameLayout headCard, themeBar, modeCard, hapticCard, kbLayoutCard;
    private TextView hapticLabel;
    private SwitchView hapticSwitch;
    /** 键盘布局：标题 + 右侧当前模式值框（点卡弹出气泡选择，2026-10-28）。 */
    private TextView kbLayoutTitle, kbLayoutValue;
    private int kbLayout = ChemKeyboardView.LAYOUT_FULL;
    private LinearLayout previewCard, prevBar;
    private TextView themeBarLabel, themeBarValue, modeLabel;
    /** 深浅色当前值框（与主题条/键盘布局同款 78×33 带边框，2026-10-28）。 */
    private TextView modeValue;
    private TextView aboutBtn;
    /** 品牌标题：必须持有引用，深浅色切换时要跟着刷新（漏刷会白底白字/黑底黑字直接消失）。 */
    private TextView brandTitle, brandSub;
    private ImageView headIcon;
    private TextView prevChip, prevField;
    private ImageView prevExp;
    private ChemKeyboardView preview;
    private GuidePanel guide;
    /** 壁纸换了要立刻丢掉「自动」主题的旧色并重刷（客户 2026-10-25 反馈自动模式不生效）。 */
    private BroadcastReceiver wallpaperReceiver;

    /** 主题选择浮层（点击「当前主题」条弹出，背景模糊）。竖排列表，对齐设计稿 8a653cd5。 */
    private FrameLayout wrap, pickerLayer;
    private LinearLayout pickerPanel, pickerHeader;
    private TextView pickerHeaderLabel, pickerClose;
    private final List<FrameLayout> pickerRowBoxes = new ArrayList<>();
    private final List<TextView> pickerRowLabels = new ArrayList<>();
    private final List<TextView> pickerRowChecks = new ArrayList<>();

    /** 键盘布局选择浮层（2026-10-28：与主题浮层同款气泡框，点「键盘布局」卡弹出）。 */
    private FrameLayout kbPickerLayer;
    private LinearLayout kbPickerPanel, kbPickerHeader;
    private TextView kbPickerHeaderLabel, kbPickerClose;
    private final List<FrameLayout> kbRowBoxes = new ArrayList<>();
    private final List<TextView> kbRowLabels = new ArrayList<>();
    private final List<TextView> kbRowChecks = new ArrayList<>();

    /** 深浅色选择浮层（2026-10-28：与主题/键盘布局同款气泡框，点「浅色/深色」卡弹出）。 */
    private FrameLayout modePickerLayer;
    private LinearLayout modePickerPanel, modePickerHeader;
    private TextView modePickerHeaderLabel, modePickerClose;
    private final List<FrameLayout> modeRowBoxes = new ArrayList<>();
    private final List<TextView> modeRowLabels = new ArrayList<>();
    private final List<TextView> modeRowChecks = new ArrayList<>();
    private final List<TextView> sectionArrows = new ArrayList<>();
    private final List<TextView> sectionTexts = new ArrayList<>();

    private int cBg, cCard, cText, cSub;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            // 「自动」主题要先解析系统壁纸色，下面所有取色都依赖它
            ChemKeyboardView.resolveAutoColors(this);
            loadPrefs();
            buildUi();
            wallpaperReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    ChemKeyboardView.invalidateAutoColors();
                    if (ChemKeyboardView.THEME_AUTO.equals(themeId)) restyleAll();
                }
            };
            registerReceiver(wallpaperReceiver, new IntentFilter(Intent.ACTION_WALLPAPER_CHANGED));
        } catch (Throwable e) {
            ErrorActivity.launch(this, e);
        }
    }

    private void buildUi() {
        restyleColors();

        scroll = new ScrollView(this);
        scroll.setBackgroundColor(cBg);
        scroll.setFillViewport(true); // 内容不足一屏时撑满，键盘预览贴底（设计稿 y543/874）
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, dp(11), 0, 0);
        scroll.addView(root);

        // ---- 顶栏卡 381×146 @(10,11) ----
        headCard = new FrameLayout(this);

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(buildLogo());
        headIcon = icon;
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(dp(56), dp(50));
        iconLp.gravity = Gravity.TOP | Gravity.LEFT;
        iconLp.setMargins(dp(24), dp(24), 0, 0); // 设计稿 logo 卡内 (24,24)
        headCard.addView(icon, iconLp);

        aboutBtn = pill("关于", dp(78), dp(33));
        aboutBtn.setOnClickListener(v -> {
            try {
                startActivity(new Intent(this, AboutActivity.class));
            } catch (Throwable e) {
                ErrorActivity.launch(this, e);
            }
        });
        FrameLayout.LayoutParams aboutLp = new FrameLayout.LayoutParams(dp(78), dp(33));
        aboutLp.gravity = Gravity.TOP | Gravity.RIGHT;
        aboutLp.setMargins(0, dp(11), dp(10), 0); // 设计稿：卡内上 11、右 10
        headCard.addView(aboutBtn, aboutLp);

        LinearLayout titleRow = new LinearLayout(this);
        TextView brand = new TextView(this);
        brand.setText("接化发");
        brand.setTextSize(32);
        brand.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        brand.setTextColor(cText);
        TextView sub = new TextView(this);
        sub.setText(" | 化学输入法");
        sub.setTextSize(32);
        sub.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        sub.setTextColor(cText);
        brandTitle = brand;
        brandSub = sub;
        titleRow.addView(brand);
        titleRow.addView(sub);
        FrameLayout.LayoutParams titleLp = new FrameLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.gravity = Gravity.TOP | Gravity.LEFT;
        titleLp.setMargins(dp(24), dp(82), 0, 0); // 设计稿标题卡内 y82
        headCard.addView(titleRow, titleLp);

        LinearLayout.LayoutParams headLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(146));
        headLp.setMargins(dp(10), 0, dp(11), 0); // 设计稿 x10 宽381（右距11）
        root.addView(headCard, headLp);

        // ---- ◥通用（2026-10-26 由「外观和显示」改名，设计稿 138-1558） ----
        LinearLayout sec1 = section("通用");
        LinearLayout.LayoutParams sec1Lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sec1Lp.setMargins(dp(10), dp(8), 0, dp(5)); // 设计稿 y157卡底→y165文字、文字底194→条y199
        root.addView(sec1, sec1Lp);

        // ---- 当前主题条 381×51（label 左24、值框 78×33 右10） ----
        themeBar = new FrameLayout(this);
        themeBarLabel = new TextView(this);
        themeBarLabel.setText("当前主题");
        themeBarLabel.setTextSize(20);
        themeBarLabel.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        FrameLayout.LayoutParams tblLp = new FrameLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tblLp.gravity = Gravity.CENTER_VERTICAL | Gravity.LEFT;
        tblLp.setMargins(dp(24), 0, 0, 0);
        themeBar.addView(themeBarLabel, tblLp);

        themeBarValue = new TextView(this);
        themeBarValue.setText(themeFormula());
        themeBarValue.setTextSize(20);
        // 设计稿实测：值框内文字是水平居中的（文字中心 343 vs 框中心 341），不是右对齐
        themeBarValue.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams tbvLp = new FrameLayout.LayoutParams(dp(78), dp(33));
        tbvLp.gravity = Gravity.CENTER_VERTICAL | Gravity.RIGHT;
        tbvLp.setMargins(0, 0, dp(10), 0);
        themeBar.addView(themeBarValue, tbvLp);

        // 点击「当前主题」条 → 弹出主题选择浮层（背景模糊），对齐设计稿 1f61f7d2
        themeBar.setOnClickListener(v -> showThemePicker());
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(51));
        barLp.setMargins(dp(10), 0, dp(11), 0);
        root.addView(themeBar, barLp);

        // 主题选择浮层的内容在 buildThemePicker() 里构建（竖排列表，不占主界面版面）

        // ---- 模式卡 381×51（与主题条间距8） ----
        modeCard = new FrameLayout(this);
        modeLabel = new TextView(this);
        modeLabel.setText("深浅色模式");
        modeLabel.setTextSize(20);
        modeLabel.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        modeLabel.setTextColor(cText);
        FrameLayout.LayoutParams mllLp = new FrameLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mllLp.gravity = Gravity.CENTER_VERTICAL | Gravity.LEFT;
        mllLp.setMargins(dp(24), 0, 0, 0);
        modeCard.addView(modeLabel, mllLp);

        // 右侧值框 78×33（与主题条/键盘布局同款，带边框），点整卡弹出气泡选择（2026-10-28）
        modeValue = new TextView(this);
        modeValue.setText(MODE_NAMES[modeIndex()]);
        modeValue.setTextSize(16);
        modeValue.setGravity(Gravity.CENTER);
        modeValue.setMaxLines(1);
        modeValue.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        FrameLayout.LayoutParams mvLp = new FrameLayout.LayoutParams(dp(78), dp(33));
        mvLp.gravity = Gravity.CENTER_VERTICAL | Gravity.RIGHT;
        mvLp.setMargins(0, 0, dp(10), 0);
        modeCard.addView(modeValue, mvLp);
        modeCard.setOnClickListener(v -> showModePicker());

        LinearLayout.LayoutParams modeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(51));
        modeLp.setMargins(dp(10), dp(8), dp(11), 0);
        root.addView(modeCard, modeLp);

        // ---- 触感反馈卡 381×51（与模式卡间距8，右侧 78×33 开关，设计稿 138-1558） ----
        hapticCard = new FrameLayout(this);
        hapticLabel = new TextView(this);
        hapticLabel.setText("触感反馈");
        hapticLabel.setTextSize(20);
        hapticLabel.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        hapticLabel.setTextColor(cText);
        FrameLayout.LayoutParams hlLp = new FrameLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hlLp.gravity = Gravity.CENTER_VERTICAL | Gravity.LEFT;
        hlLp.setMargins(dp(24), 0, 0, 0);
        hapticCard.addView(hapticLabel, hlLp);

        // 自绘开关：轨道 78×33、滑块 34×22（尺寸取自设计稿实测）
        hapticSwitch = new SwitchView(this);
        hapticSwitch.setChecked(haptic);
        FrameLayout.LayoutParams hsLp = new FrameLayout.LayoutParams(dp(78), dp(33));
        hsLp.gravity = Gravity.CENTER_VERTICAL | Gravity.RIGHT;
        hsLp.setMargins(0, 0, dp(10), 0);
        hapticCard.addView(hapticSwitch, hsLp);
        // 整行可点，点哪儿都能切（键盘侧下读到同一个 prefs 键会立即生效）
        hapticCard.setOnClickListener(v -> {
            haptic = !haptic;
            hapticSwitch.setChecked(haptic);
            savePrefs();
            toast(haptic ? "触感反馈已开启" : "触感反馈已关闭");
        });
        LinearLayout.LayoutParams hapticLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(51));
        hapticLp.setMargins(dp(10), dp(8), dp(11), 0);
        root.addView(hapticCard, hapticLp);

        // ---- 键盘布局卡（仅平板，2026-10-28）：点卡弹出气泡选择 平铺 / 悬浮 / 分离 ----
        kbLayoutCard = new FrameLayout(this);
        kbLayoutTitle = new TextView(this);
        kbLayoutTitle.setText("键盘布局");
        kbLayoutTitle.setTextSize(20);
        kbLayoutTitle.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        FrameLayout.LayoutParams klLp = new FrameLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        klLp.gravity = Gravity.CENTER_VERTICAL | Gravity.LEFT;
        klLp.setMargins(dp(24), 0, 0, 0);
        kbLayoutCard.addView(kbLayoutTitle, klLp);

        kbLayoutValue = new TextView(this);
        kbLayoutValue.setText(ChemKeyboardView.LAYOUT_NAMES[kbLayout]);
        kbLayoutValue.setTextSize(16);
        // 值框与主题条/深浅色卡一致：78×33、文字居中、带边框（2026-10-28）
        kbLayoutValue.setGravity(Gravity.CENTER);
        kbLayoutValue.setMaxLines(1);
        kbLayoutValue.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        FrameLayout.LayoutParams kvLp = new FrameLayout.LayoutParams(dp(78), dp(33));
        kvLp.gravity = Gravity.CENTER_VERTICAL | Gravity.RIGHT;
        kvLp.setMargins(0, 0, dp(10), 0);
        kbLayoutCard.addView(kbLayoutValue, kvLp);

        // 点按弹出气泡框选择（与「当前主题」条一致，2026-10-28）
        kbLayoutCard.setOnClickListener(v -> showKbLayoutPicker());
        LinearLayout.LayoutParams kbLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(51));
        kbLp.setMargins(dp(10), dp(8), dp(11), 0);
        root.addView(kbLayoutCard, kbLp);
        // 手机上不显示这张卡：布局由系统方向自动决定，不需要也不允许手选（2026-10-28）
        if (!isTabletDevice()) kbLayoutCard.setVisibility(View.GONE);

        // ---- 弹性间隔：设计稿 y309→y504 空白区（自适应撑满，键盘贴底） ----
        Space spacer = new Space(this);
        root.addView(spacer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // ---- ◥键盘预览（下距10 → 候选栏顶 y543） ----
        LinearLayout sec2 = section("键盘预览");
        LinearLayout.LayoutParams sec2Lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sec2Lp.setMargins(dp(10), 0, 0, dp(10));
        root.addView(sec2, sec2Lp);

        // ---- 键盘预览：全宽 402（候选栏 402×52 + 键盘） ----
        previewCard = new LinearLayout(this);
        previewCard.setOrientation(LinearLayout.VERTICAL);

        prevBar = new LinearLayout(this);
        prevBar.setGravity(Gravity.CENTER_VERTICAL);
        prevBar.setPadding(dp(8), dp(9), dp(7), dp(8)); // 设计稿 chip(8,9)、⌄右距7
        prevChip = new TextView(this);
        prevChip.setText("化｜符");
        prevChip.setTextSize(14);
        prevChip.setGravity(Gravity.CENTER);
        prevChip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        prevBar.addView(prevChip, new LinearLayout.LayoutParams(dp(75), dp(35)));
        prevField = new TextView(this);
        prevField.setTextSize(14);
        prevField.setTextColor(cSub);
        prevField.setPadding(dp(10), 0, dp(10), 0);
        prevField.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams fieldLp = new LinearLayout.LayoutParams(0, dp(35), 1f);
        fieldLp.setMargins(dp(8), 0, dp(8), 0);
        prevBar.addView(prevField, fieldLp);
        prevExp = new ImageView(this);
        prevExp.setImageResource(R.drawable.ic_chevron_down);
        prevExp.setScaleType(ImageView.ScaleType.CENTER);
        prevBar.addView(prevExp, new LinearLayout.LayoutParams(dp(35), dp(35)));
        previewCard.addView(prevBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        preview = new ChemKeyboardView(this, new ChemKeyboardView.Listener() {
            @Override public void onChar(String s) { }

            @Override public void onAction(int action) { }
        });
        preview.setLayoutMode(kbLayout);
        previewCard.addView(preview, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(previewCard, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // ---- 浮层：主题选择 + 操作引导（叠在主界面之上） ----
        wrap = new FrameLayout(this);
        wrap.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        buildThemePicker();
        wrap.addView(pickerLayer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        buildKbLayoutPicker();
        wrap.addView(kbPickerLayer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        buildModePicker();
        wrap.addView(modePickerLayer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        guide = new GuidePanel(this);
        guide.setBackdropSource(scroll);
        wrap.addView(guide, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(wrap);

        restyleAll();
    }

    // ---------- 主题选择浮层（设计稿 1f61f7d2：弹窗式 + 背景模糊） ----------

    private void buildThemePicker() {
        pickerLayer = new FrameLayout(this);
        pickerLayer.setVisibility(View.GONE);
        pickerLayer.setClickable(true);
        pickerLayer.setOnClickListener(v -> hideThemePicker());

        pickerPanel = new LinearLayout(this);
        pickerPanel.setOrientation(LinearLayout.VERTICAL);
        pickerPanel.setClickable(true); // 消费点击，避免穿透到遮罩把自己关掉

        // ---- 头部：「当前主题」+ ✕ ----
        pickerHeader = new LinearLayout(this);
        pickerHeader.setOrientation(LinearLayout.HORIZONTAL);
        pickerHeader.setGravity(Gravity.CENTER_VERTICAL);
        pickerHeader.setPadding(dp(16), 0, dp(12), 0);
        pickerHeaderLabel = new TextView(this);
        pickerHeaderLabel.setText("当前主题");
        pickerHeaderLabel.setTextSize(18);
        pickerHeaderLabel.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        // 客户 2026-10-15：弹窗头部文字要垂直居中（高度 MATCH_PARENT，漏了就贴顶）
        pickerHeaderLabel.setGravity(Gravity.CENTER_VERTICAL);
        pickerHeader.addView(pickerHeaderLabel, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        pickerClose = new TextView(this);
        pickerClose.setText("✕");
        pickerClose.setTextSize(18);
        pickerClose.setGravity(Gravity.CENTER);
        pickerClose.setOnClickListener(v -> hideThemePicker());
        pickerHeader.addView(pickerClose, new LinearLayout.LayoutParams(dp(28), dp(28)));
        pickerPanel.addView(pickerHeader, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));

        // ---- 主题行（每行左侧主题名用其主题色，右端选中打勾） ----
        for (int idx : PICKER_ORDER) {
            final String id = THEME_IDS[idx];
            FrameLayout row = new FrameLayout(this);
            row.setClickable(true);
            TextView label = new TextView(this);
            label.setText(THEME_NAMES[idx]);
            label.setTextSize(18);
            label.setGravity(Gravity.CENTER_VERTICAL);
            FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT);
            llp.gravity = Gravity.CENTER_VERTICAL | Gravity.LEFT;
            llp.setMargins(dp(16), 0, 0, 0);
            row.addView(label, llp);
            TextView check = new TextView(this);
            check.setText("✓");
            check.setTextSize(16);
            check.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(dp(28), dp(28));
            clp.gravity = Gravity.CENTER_VERTICAL | Gravity.RIGHT;
            clp.setMargins(0, 0, dp(12), 0);
            row.addView(check, clp);
            row.setOnClickListener(v -> {
                themeId = id;
                savePrefs();
                hideThemePicker();
                restyleAll();
                toast("主题：" + labelOf(id));
            });
            pickerRowBoxes.add(row);
            pickerRowLabels.add(label);
            pickerRowChecks.add(check);
            pickerPanel.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(46)));
        }

        // 面板靠右贴着值框的位置，纵向对齐「当前主题」条
        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(
                dp(168), FrameLayout.LayoutParams.WRAP_CONTENT);
        panelLp.gravity = Gravity.TOP | Gravity.RIGHT;
        panelLp.setMargins(0, 0, dp(11), 0);
        pickerLayer.addView(pickerPanel, panelLp);
    }

    private void showThemePicker() {
        if (pickerLayer.getVisibility() == View.VISIBLE) {
            hideThemePicker();
            return;
        }
        // 面板与「当前主题」条所在位置对齐（滚动后仍然对得上）
        hideKbLayoutPicker();
        hideModePicker();
        int[] bar = new int[2], w = new int[2];
        themeBar.getLocationInWindow(bar);
        wrap.getLocationInWindow(w);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) pickerPanel.getLayoutParams();
        lp.topMargin = Math.max(0, bar[1] - w[1]);
        pickerLayer.removeAllViews();

        ImageView backdrop = new ImageView(this);
        UiBlur.apply(backdrop, scroll, 0x66000000, cBg);
        pickerLayer.addView(backdrop, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        pickerLayer.addView(pickerPanel, lp);
        // 每次弹出都重挑一次颜色：自动主题随壁纸变，列表里的色块也要跟着变
        restylePicker();
        pickerLayer.setVisibility(View.VISIBLE);
    }

    private void hideThemePicker() {
        if (pickerLayer.getVisibility() != View.GONE) pickerLayer.setVisibility(View.GONE);
    }

    // ---------- 键盘布局选择浮层（与主题浮层同款气泡框，2026-10-28） ----------

    private void buildKbLayoutPicker() {
        kbPickerLayer = new FrameLayout(this);
        kbPickerLayer.setVisibility(View.GONE);
        kbPickerLayer.setClickable(true);
        kbPickerLayer.setOnClickListener(v -> hideKbLayoutPicker());

        kbPickerPanel = new LinearLayout(this);
        kbPickerPanel.setOrientation(LinearLayout.VERTICAL);
        kbPickerPanel.setClickable(true); // 消费点击，避免穿透到遮罩把自己关掉

        // ---- 头部：「键盘布局」+ ✕ ----
        kbPickerHeader = new LinearLayout(this);
        kbPickerHeader.setOrientation(LinearLayout.HORIZONTAL);
        kbPickerHeader.setGravity(Gravity.CENTER_VERTICAL);
        kbPickerHeader.setPadding(dp(16), 0, dp(12), 0);
        kbPickerHeaderLabel = new TextView(this);
        kbPickerHeaderLabel.setText("键盘布局");
        kbPickerHeaderLabel.setTextSize(18);
        kbPickerHeaderLabel.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        kbPickerHeaderLabel.setGravity(Gravity.CENTER_VERTICAL);
        kbPickerHeader.addView(kbPickerHeaderLabel, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        kbPickerClose = new TextView(this);
        kbPickerClose.setText("✕");
        kbPickerClose.setTextSize(18);
        kbPickerClose.setGravity(Gravity.CENTER);
        kbPickerClose.setOnClickListener(v -> hideKbLayoutPicker());
        kbPickerHeader.addView(kbPickerClose, new LinearLayout.LayoutParams(dp(28), dp(28)));
        kbPickerPanel.addView(kbPickerHeader, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));

        // ---- 布局行（平铺 / 悬浮 / 分离，顺序见 KB_ORDER，选中打勾） ----
        for (int i = 0; i < KB_ORDER.length; i++) {
            final int mode = KB_ORDER[i];
            FrameLayout row = new FrameLayout(this);
            row.setClickable(true);
            TextView label = new TextView(this);
            label.setText(ChemKeyboardView.LAYOUT_NAMES[mode]);
            label.setTextSize(18);
            label.setGravity(Gravity.CENTER_VERTICAL);
            FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT);
            llp.gravity = Gravity.CENTER_VERTICAL | Gravity.LEFT;
            llp.setMargins(dp(16), 0, 0, 0);
            row.addView(label, llp);
            TextView check = new TextView(this);
            check.setText("✓");
            check.setTextSize(16);
            check.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(dp(28), dp(28));
            clp.gravity = Gravity.CENTER_VERTICAL | Gravity.RIGHT;
            clp.setMargins(0, 0, dp(12), 0);
            row.addView(check, clp);
            row.setOnClickListener(v -> {
                kbLayout = mode;
                kbLayoutValue.setText(ChemKeyboardView.LAYOUT_NAMES[mode]);
                if (preview != null) preview.setLayoutMode(mode);   // 预览区同步切换，便于对照
                savePrefs();
                hideKbLayoutPicker();
                restyleAll();
                toast("键盘布局：" + ChemKeyboardView.LAYOUT_NAMES[mode]);
            });
            kbRowBoxes.add(row);
            kbRowLabels.add(label);
            kbRowChecks.add(check);
            kbPickerPanel.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(46)));
        }

        // 面板靠右贴着值框的位置，纵向对齐「键盘布局」卡
        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(
                dp(168), FrameLayout.LayoutParams.WRAP_CONTENT);
        panelLp.gravity = Gravity.TOP | Gravity.RIGHT;
        panelLp.setMargins(0, 0, dp(11), 0);
        kbPickerLayer.addView(kbPickerPanel, panelLp);
    }

    private void showKbLayoutPicker() {
        if (kbPickerLayer.getVisibility() == View.VISIBLE) {
            hideKbLayoutPicker();
            return;
        }
        hideThemePicker();
        hideModePicker();
        // 面板与「键盘布局」卡所在位置对齐（滚动后仍然对得上）
        int[] card = new int[2], w = new int[2];
        kbLayoutCard.getLocationInWindow(card);
        wrap.getLocationInWindow(w);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) kbPickerPanel.getLayoutParams();
        lp.topMargin = Math.max(0, card[1] - w[1]);
        kbPickerLayer.removeAllViews();

        ImageView backdrop = new ImageView(this);
        UiBlur.apply(backdrop, scroll, 0x66000000, cBg);
        kbPickerLayer.addView(backdrop, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        kbPickerLayer.addView(kbPickerPanel, lp);
        restyleKbPicker();
        kbPickerLayer.setVisibility(View.VISIBLE);
    }

    private void hideKbLayoutPicker() {
        if (kbPickerLayer != null && kbPickerLayer.getVisibility() != View.GONE) {
            kbPickerLayer.setVisibility(View.GONE);
        }
    }

    /** 键盘布局浮层配色（与主题浮层同一套规则：头部主题色，选中行填主题色并打勾）。 */
    private void restyleKbPicker() {
        if (kbPickerPanel == null) return;
        int accent = ChemKeyboardView.themeColor(themeId);
        kbPickerHeader.setBackground(filled(accent, 0));
        kbPickerHeaderLabel.setTextColor(onAccent(accent));
        kbPickerClose.setTextColor(onAccent(accent));
        int stroke = (int) (1.3f * getResources().getDisplayMetrics().density);
        for (int i = 0; i < kbRowBoxes.size(); i++) {
            boolean sel = KB_ORDER[i] == kbLayout;
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(0);
            g.setColor(sel ? accent : cCard);
            g.setStroke(stroke, darkMode ? 0x1AFFFFFF : 0x14000000);
            kbRowBoxes.get(i).setBackground(g);
            kbRowLabels.get(i).setTextColor(sel ? onAccent(accent) : accent);
            kbRowChecks.get(i).setTextColor(sel ? onAccent(accent) : Color.TRANSPARENT);
        }
    }

    // ---------- 深浅色选择浮层（与主题/键盘布局同款气泡框，2026-10-28） ----------

    private void buildModePicker() {
        modePickerLayer = new FrameLayout(this);
        modePickerLayer.setVisibility(View.GONE);
        modePickerLayer.setClickable(true);
        modePickerLayer.setOnClickListener(v -> hideModePicker());

        modePickerPanel = new LinearLayout(this);
        modePickerPanel.setOrientation(LinearLayout.VERTICAL);
        modePickerPanel.setClickable(true); // 消费点击，避免穿透到遮罩把自己关掉

        // ---- 头部：「深浅色模式」+ ✕ ----
        modePickerHeader = new LinearLayout(this);
        modePickerHeader.setOrientation(LinearLayout.HORIZONTAL);
        modePickerHeader.setGravity(Gravity.CENTER_VERTICAL);
        modePickerHeader.setPadding(dp(16), 0, dp(12), 0);
        modePickerHeaderLabel = new TextView(this);
        modePickerHeaderLabel.setText("深浅色模式");
        modePickerHeaderLabel.setTextSize(18);
        modePickerHeaderLabel.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        modePickerHeaderLabel.setGravity(Gravity.CENTER_VERTICAL);
        modePickerHeader.addView(modePickerHeaderLabel, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        modePickerClose = new TextView(this);
        modePickerClose.setText("✕");
        modePickerClose.setTextSize(18);
        modePickerClose.setGravity(Gravity.CENTER);
        modePickerClose.setOnClickListener(v -> hideModePicker());
        modePickerHeader.addView(modePickerClose, new LinearLayout.LayoutParams(dp(28), dp(28)));
        modePickerPanel.addView(modePickerHeader, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));

        // ---- 模式行（浅色 / 深色 / 跟随系统，选中打勾） ----
        for (int i = 0; i < MODE_NAMES.length; i++) {
            final int idx = i;
            FrameLayout row = new FrameLayout(this);
            row.setClickable(true);
            TextView label = new TextView(this);
            label.setText(MODE_NAMES[idx]);
            label.setTextSize(18);
            label.setGravity(Gravity.CENTER_VERTICAL);
            FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT);
            llp.gravity = Gravity.CENTER_VERTICAL | Gravity.LEFT;
            llp.setMargins(dp(16), 0, 0, 0);
            row.addView(label, llp);
            TextView check = new TextView(this);
            check.setText("✓");
            check.setTextSize(16);
            check.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(dp(28), dp(28));
            clp.gravity = Gravity.CENTER_VERTICAL | Gravity.RIGHT;
            clp.setMargins(0, 0, dp(12), 0);
            row.addView(check, clp);
            row.setOnClickListener(v -> {
                // 0 浅色 / 1 深色：固定；2 跟随系统：立即取系统当前深浅
                followSystem = idx == 2;
                darkMode = idx == 2 ? systemDark() : idx == 1;
                savePrefs();
                hideModePicker();
                restyleAll();
                toast(MODE_NAMES[idx]);
            });
            modeRowBoxes.add(row);
            modeRowLabels.add(label);
            modeRowChecks.add(check);
            modePickerPanel.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(46)));
        }

        // 面板靠右贴着值框的位置，纵向对齐「深浅色模式」卡（宽度与其它浮层一致）
        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(
                dp(168), FrameLayout.LayoutParams.WRAP_CONTENT);
        panelLp.gravity = Gravity.TOP | Gravity.RIGHT;
        panelLp.setMargins(0, 0, dp(11), 0);
        modePickerLayer.addView(modePickerPanel, panelLp);
    }

    private void showModePicker() {
        if (modePickerLayer.getVisibility() == View.VISIBLE) {
            hideModePicker();
            return;
        }
        hideThemePicker();
        hideKbLayoutPicker();
        // 面板与「深浅色模式」卡所在位置对齐（滚动后仍然对得上）
        int[] card = new int[2], w = new int[2];
        modeCard.getLocationInWindow(card);
        wrap.getLocationInWindow(w);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) modePickerPanel.getLayoutParams();
        lp.topMargin = Math.max(0, card[1] - w[1]);
        modePickerLayer.removeAllViews();

        ImageView backdrop = new ImageView(this);
        UiBlur.apply(backdrop, scroll, 0x66000000, cBg);
        modePickerLayer.addView(backdrop, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        modePickerLayer.addView(modePickerPanel, lp);
        restyleModePicker();
        modePickerLayer.setVisibility(View.VISIBLE);
    }

    private void hideModePicker() {
        if (modePickerLayer != null && modePickerLayer.getVisibility() != View.GONE) {
            modePickerLayer.setVisibility(View.GONE);
        }
    }

    /** 深浅色浮层配色（与主题/键盘布局浮层同一套规则：头部主题色，选中行填主题色并打勾）。 */
    private void restyleModePicker() {
        if (modePickerPanel == null) return;
        int accent = ChemKeyboardView.themeColor(themeId);
        modePickerHeader.setBackground(filled(accent, 0));
        modePickerHeaderLabel.setTextColor(onAccent(accent));
        modePickerClose.setTextColor(onAccent(accent));
        int stroke = (int) (1.3f * getResources().getDisplayMetrics().density);
        for (int i = 0; i < modeRowBoxes.size(); i++) {
            boolean sel = i == modeIndex();
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(0);
            g.setColor(sel ? accent : cCard);
            g.setStroke(stroke, darkMode ? 0x1AFFFFFF : 0x14000000);
            modeRowBoxes.get(i).setBackground(g);
            modeRowLabels.get(i).setTextColor(sel ? onAccent(accent) : accent);
            modeRowChecks.get(i).setTextColor(sel ? onAccent(accent) : Color.TRANSPARENT);
        }
    }

    /** 「自动」主题取色可能随壁纸变，每次刷新都重新解析一次。 */
    private void restylePicker() {
        if (pickerPanel == null) return;
        ChemKeyboardView.resolveAutoColors(this);
        int accent = ChemKeyboardView.themeColor(themeId);
        pickerHeader.setBackground(filled(accent, 0));
        pickerHeaderLabel.setTextColor(onAccent(accent));
        pickerClose.setTextColor(onAccent(accent));
        int stroke = (int) (1.3f * getResources().getDisplayMetrics().density);
        for (int i = 0; i < pickerRowBoxes.size(); i++) {
            String id = THEME_IDS[PICKER_ORDER[i]];
            int col = ChemKeyboardView.themeColor(id);
            boolean sel = id.equals(themeId);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(0);
            g.setColor(sel ? col : cCard);
            // 行之间用浅分隔线，不是主题色描边（设计稿里行是平铺的，只有细分隔）
            g.setStroke(stroke, darkMode ? 0x1AFFFFFF : 0x14000000);
            pickerRowBoxes.get(i).setBackground(g);
            pickerRowLabels.get(i).setTextColor(sel ? onAccent(col) : col);
            pickerRowChecks.get(i).setTextColor(sel ? onAccent(col) : Color.TRANSPARENT);
        }
    }

    /** 主题色偏暗用白字，偏亮用深字。 */
    private static int onAccent(int accent) {
        double l = 0.299 * Color.red(accent) + 0.587 * Color.green(accent) + 0.114 * Color.blue(accent);
        return l < 160 ? 0xFFFFFFFF : 0xE6101418;
    }

    /** 系统当前是否深色。 */
    private boolean systemDark() {
        int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    /** 当前深浅色模式下标：0 浅色、1 深色、2 跟随系统（2026-10-28）。 */
    private int modeIndex() {
        return followSystem ? 2 : (darkMode ? 1 : 0);
    }

    /** 平板判定：最小宽度 ≥ 600dp（与输入法侧 JhfImeService.isTablet() 一致）。 */
    private boolean isTabletDevice() {
        return getResources().getConfiguration().smallestScreenWidthDp >= 600;
    }

    private boolean isLandscapeNow() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    // ---------- 样式 ----------

    private void restyleColors() {
        if (darkMode) {
            cBg = 0xFF313131;
            cCard = 0xFF000000;
            cText = 0xFFFFFFFF;
            cSub = 0x70FFFFFF;
        } else {
            cBg = 0xFFEAEAEA;
            cCard = 0xFFFFFFFF;
            cText = 0xFF000000;
            cSub = 0x66000000;
        }
    }

    /** 主题/深浅变化后统一刷新（避免重建 Activity 造成闪烁）。 */
    private void restyleAll() {
        // 「自动」主题的颜色来自系统壁纸，必须在算 accent 之前重新解析：
        // 否则换过壁纸、或从后台回到前台时（只走 onResume）拿到的还是进程启动时的旧色，
        // 表现就是客户反馈的「壁纸取色没生效」。
        ChemKeyboardView.resolveAutoColors(this);
        int accent = ChemKeyboardView.themeColor(themeId);
        restyleColors();
        if (scroll != null) scroll.setBackgroundColor(cBg);

        // 状态栏与页面背景同色（原来用主题深色，顶部一条青/红条很割裂）
        getWindow().setStatusBarColor(cBg);
        applyStatusBarIcons();
        for (TextView a : sectionArrows) a.setTextColor(cText);
        for (TextView t : sectionTexts) t.setTextColor(cText);
        // 品牌标题也要跟着刷新：它是唯一「漏刷就整条消失」的控件
        // （浅色卡上是黑字，深色卡上是白字；切完不刷新就变成同色）。
        if (brandTitle != null) brandTitle.setTextColor(cText);
        if (brandSub != null) brandSub.setTextColor(cText);
        restylePicker();
        restyleKbPicker();
        restyleModePicker();
        if (headIcon != null) {
            headIcon.setImageDrawable(buildLogo());
        }
        if (preview != null) preview.setThemeColors(themeId, darkMode);

        if (themeBar != null) {
            themeBar.setBackground(filled(accent, 0));
            themeBarLabel.setTextColor(Color.WHITE);
            themeBarValue.setTextColor(Color.WHITE);
            themeBarValue.setBackground(ring(Color.WHITE, 0));
            themeBarValue.setText(themeFormula());
        }

        if (aboutBtn != null) stylePill(aboutBtn, "关于", cCard, accent, accent);
        // 深浅色卡右侧改成与主题条同款 78×33 带边框值框（2026-10-28）
        if (modeValue != null) {
            modeValue.setText(MODE_NAMES[modeIndex()]);
            modeValue.setTextColor(accent);
            modeValue.setBackground(ring(accent, 0));
        }
        if (modeLabel != null) {
            modeLabel.setText("深浅色模式");
            modeLabel.setTextColor(cText);
        }

        if (headCard != null) headCard.setBackground(rounded(cCard, 0));
        if (modeCard != null) modeCard.setBackground(rounded(cCard, 0));
        if (hapticCard != null) {
            hapticCard.setBackground(rounded(cCard, 0));
            hapticLabel.setTextColor(cText);
            hapticSwitch.setStyle(accent, darkMode);
            hapticSwitch.setChecked(haptic);
        }
        if (kbLayoutCard != null) {
            kbLayoutCard.setBackground(rounded(cCard, 0));
            kbLayoutTitle.setTextColor(cText);
            // 值框与主题条/深浅色卡一致：主题色描边 + 主题色文字（2026-10-28）
            kbLayoutValue.setTextColor(accent);
            kbLayoutValue.setBackground(ring(accent, 0));
            kbLayoutValue.setText(ChemKeyboardView.LAYOUT_NAMES[kbLayout]);
        }
        if (prevBar != null) prevBar.setBackground(rounded(cCard, 0));
        if (prevChip != null) {
            prevChip.setBackground(filled(accent, 0));
            prevChip.setTextColor(Color.WHITE);
            prevField.setBackground(ring(accent, 0));
            prevExp.setBackground(ring(accent, 0));
            prevExp.setColorFilter(accent);
        }
        if (guide != null) guide.refresh(themeId, darkMode);
    }

    /** 浅色背景用深色状态栏图标，深色背景用浅色图标。 */
    private void applyStatusBarIcons() {
        try {
            View decor = getWindow().getDecorView();
            int flags = decor.getSystemUiVisibility();
            if (darkMode) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            decor.setSystemUiVisibility(flags);
        } catch (Throwable ignored) {
        }
    }

    private String themeFormula() {
        return labelOf(themeId);
    }

    private static String labelOf(String id) {
        for (int i = 0; i < THEME_IDS.length; i++) {
            if (THEME_IDS[i].equals(id)) return THEME_NAMES[i];
        }
        return "CuCl₂";
    }

    private void loadPrefs() {
        SharedPreferences p = getSharedPreferences("jhf_settings", MODE_PRIVATE);
        themeId = p.getString("theme", "cucl2");
        followSystem = p.getBoolean("follow", false);
        darkMode = followSystem ? systemDark() : p.getBoolean("dark", false);
        haptic = p.getBoolean("haptic", true);
        kbLayout = p.getInt("kb_layout", ChemKeyboardView.LAYOUT_FULL);
        // 手机不给手动选布局：横屏自动悬浮、竖屏自动平铺（2026-10-28）
        if (!isTabletDevice()) {
            kbLayout = isLandscapeNow()
                    ? ChemKeyboardView.LAYOUT_FLOAT : ChemKeyboardView.LAYOUT_FULL;
        }
    }

    private void savePrefs() {
        SharedPreferences p = getSharedPreferences("jhf_settings", MODE_PRIVATE);
        p.edit().putString("theme", themeId)
                .putBoolean("dark", darkMode)
                .putBoolean("follow", followSystem)
                .putBoolean("haptic", haptic)
                .putInt("kb_layout", kbLayout)
                .apply();
    }

    /** 跟随系统时，系统深浅色一变就刷新；手机旋转后自动布局也要重算（2026-10-28）。 */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        boolean changed = false;
        if (followSystem) {
            darkMode = systemDark();
            changed = true;
        }
        if (!isTabletDevice()) {
            int mode = isLandscapeNow()
                    ? ChemKeyboardView.LAYOUT_FLOAT : ChemKeyboardView.LAYOUT_FULL;
            if (mode != kbLayout) {
                kbLayout = mode;
                if (preview != null) preview.setLayoutMode(kbLayout);
                changed = true;
            }
        }
        if (changed) restyleAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            loadPrefs();
            restyleAll();
            refreshGuide();
        } catch (Throwable e) {
            ErrorActivity.launch(this, e);
        }
    }

    /**
     * 系统输入法选择器是 Dialog，选完之后不会触发 onResume，
     * 所以窗口重新获得焦点时再判一次，引导面板才能自己关掉（客户意见 5）。
     */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) refreshGuide();
    }

    @Override
    public void onBackPressed() {
        if (pickerLayer != null && pickerLayer.getVisibility() == View.VISIBLE) {
            hideThemePicker();
            return;
        }
        if (kbPickerLayer != null && kbPickerLayer.getVisibility() == View.VISIBLE) {
            hideKbLayoutPicker();
            return;
        }
        if (modePickerLayer != null && modePickerLayer.getVisibility() == View.VISIBLE) {
            hideModePicker();
            return;
        }
        super.onBackPressed();
    }

    /** 未启用 / 未切换到本输入法时自动弹出操作引导（已就绪则不再打扰）。 */
    private void refreshGuide() {
        if (guide == null) return;
        if (ImeStatusHelper.isReady(this)) guide.hide();
        else guide.show();
    }

    // ---------- 控件工厂 ----------

    /**
     * 组头：◤ 17sp + 20sp Regular（设计稿 Arrow 1 (Stroke) 17.5×18 + 20px 文字）。
     * 三角形是「左上实心三角」，设计稿量取为 ◤（此前误用 ◥，方向相反）。
     */
    private LinearLayout section(String title) {
        LinearLayout box = new LinearLayout(this);
        box.setGravity(Gravity.CENTER_VERTICAL);
        TextView arrow = new TextView(this);
        arrow.setText("◤");
        arrow.setTextSize(17);
        arrow.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        arrow.setTextColor(cText);
        box.addView(arrow);
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(20);
        tv.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        tv.setTextColor(cText);
        LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tvLp.setMargins(dp(4), 0, 0, 0); // 设计稿箭头右缘17.5→文字x21
        box.addView(tv, tvLp);
        // 深浅色切换时要同步刷新（此前漏刷，深色模式下标题仍是黑字，几乎看不见）
        sectionArrows.add(arrow);
        sectionTexts.add(tv);
        return box;
    }

    /** 直角按钮：bg 填充 + stroke 描边 + 文字色（设计稿 78×33）。 */
    private TextView pill(String text, int w, int h) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(20);
        tv.setGravity(Gravity.CENTER);
        tv.setMaxLines(1);
        tv.setPadding(dp(10), 0, dp(10), 0);
        tv.setLayoutParams(new LinearLayout.LayoutParams(w, h));
        return tv;
    }

    private void stylePill(TextView tv, String text, int bg, int stroke, int textColor) {
        tv.setText(text);
        GradientDrawable g = new GradientDrawable();
        g.setColor(bg);
        g.setCornerRadius(0);
        if (stroke != 0) g.setStroke((int) (1.3f * getResources().getDisplayMetrics().density), stroke);
        tv.setBackground(g);
        tv.setTextColor(textColor);
    }

    private GradientDrawable filled(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private GradientDrawable ring(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.TRANSPARENT);
        g.setCornerRadius(radius);
        g.setStroke((int) (1.3f * getResources().getDisplayMetrics().density), color);
        return g;
    }

    private GradientDrawable rounded(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    /** 顶栏 logo：字层（随深浅色切换）+ 勾层（主题色 tint）。 */
    private Drawable buildLogo() {
        LayerDrawable ld = new LayerDrawable(new Drawable[]{
                getDrawable(darkMode ? R.drawable.ic_logo_dark : R.drawable.ic_logo),
                getDrawable(R.drawable.ic_logo_check)});
        ld.getDrawable(1).mutate().setTint(ChemKeyboardView.themeColor(themeId));
        return ld;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    /**
     * 「触感反馈」开关（2026-10-26 自绘；2026-10-28 按客户 Figma Frame 13 / Frame 14 重绘）：
     * 轨道 78×33 直角实心；关闭 #DDDDDD、开启主题色（设计稿 #05BEC1）。
     * 滑块 34×25 白色，距轨道左/右、上边各 3dp，带向下 2dp 的实心投影
     * （关闭 #ADADAD、开启取主题色的压暗色，设计稿 #009295）。
     * 滑块内标记：关闭为 13×13 黑色 1px 圆环，开启为 11dp 高黑色竖线，均居中。
     */
    private class SwitchView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF track = new RectF();
        private final RectF knob = new RectF();
        private boolean checked;
        private int accent = 0xFF00C4D2;

        SwitchView(Context ctx) {
            super(ctx);
        }

        void setChecked(boolean on) {
            if (checked == on) return;
            checked = on;
            invalidate();
        }

        void setStyle(int accentColor, boolean darkModeNow) {
            accent = accentColor;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth();
            float h = getHeight();

            // 轨道：直角实心，关闭灰底、开启主题色（Frame 13 / Frame 14 的 About Button 底）
            track.set(0, 0, w, h);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(checked ? accent : 0xFFDDDDDD);
            c.drawRect(track, paint);

            // 滑块：34×25，左/右/上各留 3dp
            float kw = dp(34);
            float kh = dp(25);
            float inset = dp(3);
            float left = checked ? w - inset - kw : inset;
            float top = dp(3);
            knob.set(left, top, left + kw, top + kh);

            // 滑块投影：0 2px 0，关闭 #ADADAD、开启为压暗的主题色
            float shadowY = dp(2);
            paint.setColor(checked ? darken(accent, 0.78f) : 0xFFADADAD);
            c.drawRect(left, top + shadowY, left + kw, top + kh + shadowY, paint);

            paint.setColor(Color.WHITE);
            c.drawRect(knob, paint);

            // 滑块内标记：关闭 = 圆环，开启 = 竖线（黑色 1px）
            float cx = (knob.left + knob.right) / 2f;
            float cy = (knob.top + knob.bottom) / 2f;
            float stroke = dp(1);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setColor(Color.BLACK);
            if (checked) {
                float half = dp(11) / 2f;
                c.drawLine(cx, cy - half, cx, cy + half, paint);
            } else {
                c.drawCircle(cx, cy, dp(13) / 2f, paint);
            }
            paint.setStyle(Paint.Style.FILL);
        }
    }

    /** 取主题色的压暗变体（阴影用，0.78 倍即设计稿 #05BEC1 → #009295 的比例）。 */
    private static int darken(int color, float factor) {
        return Color.rgb((int) (Color.red(color) * factor),
                (int) (Color.green(color) * factor),
                (int) (Color.blue(color) * factor));
    }

    @Override
    protected void onDestroy() {
        if (wallpaperReceiver != null) {
            try {
                unregisterReceiver(wallpaperReceiver);
            } catch (Throwable ignored) {
            }
            wallpaperReceiver = null;
        }
        super.onDestroy();
    }
}

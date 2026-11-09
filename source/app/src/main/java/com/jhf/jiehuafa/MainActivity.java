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

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
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
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
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
 *   2026-10-29 交互动画统一：开关状态切换改为 280ms 过渡（滑块滑移 + 轨道/投影颜色插值 +
 *              标记由圆环交叉淡变为竖线）；主题 / 键盘布局 / 深浅色三个气泡浮层打开时
 *              遮罩透明度 0→1、模糊 0→26 同步渐变，面板上移并由 0.97 放大到 1，关闭反向播放、
 *              动画结束后才 GONE；另开浮层时旧浮层 dismissInstant() 直接收掉避免叠影。
 *              后续新增组件/交互须同步配置过渡（时长与曲线统一取 UiBlur.DUR / EASE_OUT）。
 *   2026-10-31 圆润首页细节对齐 Figma 129-1780：关于行数值右缘 325/356（右距 31）、箭头按
 *              绝对定位 left331（16×8 旋转后右距 9）；外观卡两行箭头移到行右内边距区
 *              （left331，right -22，行关闭 clipChildren）；触感行右出 17 让开关右缘落在
 *              342，行3 top 对齐 106（行距 23）；开关改为 49×22 胶囊轨道 + 18×18 白圆滑块、
 *              无投影（SwitchView.setRound）；深浅色浮层锚点按设计稿（面板 top 差 17、
 *              圆润遮罩 #00000080、文案贴面板左内边距 21、勾选 15×10 落在内容右缘外
 *              4px、行间 10）；
 *              圆润候选栏芯片深色改 #2E2E2E 底白字（与输入法候选栏一致，原固定浅灰白字看不清）。
 *   2026-10-31 真机验证修复：外观卡右内边距 31→14（设计稿 17/14/19/19），卡片 setClipChildren/
 *              setClipToPadding(false)，行内箭头右距 -22→-5、触感行去掉 -17 负边距：
 *              原来箭头整体落在内边距盒外被裁掉不可见，开关右缘 17dp 也被裁成缺角。
 *   2026-10-31 按客户新版 Figma 一比一重排圆润首页（129-1780）、深浅色浮层（131-4550）：
 *              底色 #F3F3F3、强调色 #2E73F3；
 *              logo 白卡 62×62 圆角 16 描边 #EDEDED、标题 20sp（「接化发」加粗）+ 13sp 副标题；
 *              组头 12sp #949FAB 左 28 高 27；关于卡 378×60 圆角 22（值 15sp #9F9F9F 右距 31、
 *              箭头 12×6 右距 11）；外观卡 378×174 圆角 22（行距 35/32、开关 50×28 圆滑块 22）；
 *              键盘块上距 140，候选栏顶部圆角 18、芯片 75×35 圆角 9 字号 15sp；
 *              深浅色浮层文案改「深色模式/浅色模式/跟随系统」、行 37、勾 15×10。
 *   2026-10-31 按深色稿 177-1169 一比一校准圆润首页：底色 #252525、卡片 #373737、组头 #949FAB、
 *              右值 #868686、logo 白卡白描边、开关深色轨道蓝 + 滑块 #484848、副标题浅色可读；
 *              「外观和显示」组头按设计更名「通用」；新增「键盘试用输入框」组头 + 378×48
 *              圆角 30 卡（文字 16sp、箭头 @x352），点卡在原位展开键盘预览（175-618），
 *              候选栏右下 ⌄ 收起还原卡片；深色候选栏 #0E0E0E。
 *   2026-10-31 真机深色验证修复：圆润 logo 卡在深色稿里仍是白卡（177-1169），buildLogo 圆润时
 *              恒用浅色字层 ic_logo（白卡上深色层不可见，只剩蓝色勾）；
 *              候选栏按 175-618/177-751/177-1655 改为与键盘同底色（浅 #EAEAEA / 深 #252525）、
 *              无圆角、中间无输入框底；左芯片 75×35 白/ #484848 胶囊圆角 25、17sp「符号」，
 *              ⌄ 芯片 35×35 同色圆角 23；去掉旧的 #0E0E0E 栏底 + #2E2E2E 圆角9 芯片。
 *   2026-10-31 首页「键盘试用输入框」的页内预览键盘跟随 kb_mode（化学 / 普通），
 *              展开预览时再对齐一次；预览恒为化学布局是之前误判「模式被设置页重置」的原因。
 *   2026-10-31 圆润首页两处纵向对齐修复：触感反馈行 TextView 补 CENTER_VERTICAL
 *              （原来内部默认 TOP，字形墨迹偏上，与右侧开关轨道中心不齐）；「点击唤起键盘」
 *              的 TextView 同样补内部 CENTER_VERTICAL（外层 LayoutParams 居中只管视图盒，
 *              墨迹中心仍比卡片中心高约 5dp）。
 *              试用卡按 129-1780 补 2dp 青蓝横向渐变描边（#2B73DB→#00B4E8，外圆角 30、
 *              内层卡片色圆角 28、2dp 内缩），buildRoundUi 与 restyleAll 两处统一用
 *              roundTrialBg()，深浅色切换后描边不丢失。
 *   2026-10-31 圆润卡片指向箭头按设计稿重做：新增 12×6 的 ic_chevron_12（比例 2:1），
 *              原来复用的 18×12 图标塞进 12×6 会被 fitCenter 缩成 9×6、旋转后视觉只有 6×9；
 *              颜色由正文色改为设计稿实测值（浅 #B2B2B2 / 深 #6D6D6D），
 *              buildRoundUi 与 restyleAll 统一走 roundArrowColor()。
 *              触感开关深色滑块由 #484848 改为纯白（177-1169 实测轨道 #2E73F3、滑块 #FFFFFF）。
 *   2026-11-01 沉浸式适配：新增 applyNavigationBar()，导航栏填页面底色（浅 #F3F3F3 / 深 #252525），
 *              Android 9+ 去掉分隔线、Android 10+ 关闭强制对比度黑纱、按深浅色切换导航栏图标，
 *              首页底部不再出现系统黑条。
 *   2026-11-01 删除首页自绘预览键盘（展开式预览卡）：
 *              「点击唤起键盘」改为聚焦一个 1dp 全透明的真实 EditText，再由
 *              InputMethodManager 唤起系统输入法，看到的是真正的系统键盘。
 *   2026-11-03 按客户要求只保留圆润风格：删除首页「界面风格」卡、风格选择浮层与首启选择弹窗；
 *              外观白卡由三行改两行（174 → 120 高，触感反馈上移一行）。
 *              所有气泡浮层出现时状态栏/导航栏与遮罩同步压暗 50%（并转白色图标），
 *              最后一个浮层收起动画结束后恢复沉浸式系统栏。
 *   2026-11-07 图标随深浅色自动切换：深色用 ic_app_logo_dark（图标-1.png）与桌面
 *              DarkAlias（图标.png），浅色回退默认图标；统一由 restyleAll() 走
 *              AppIcon，状态未变时不重复切换（避免桌面闪烁）。
 *   2026-11-07 深色配色调整（客户要求）：页面底色 #252525 → #000000，卡片 #373737 → #242424，
 *              深浅色等气泡浮层同步改 #242424；输入法（键盘）配色不动。
 *              首页 / 关于软件页的应用图标增加 1dp 灰色细描边（AppIcon.stroke，前景层绘制，
 *              圆角取图标 26%，不改尺寸与内边距）。
 *   2026-11-07 应用内图标圆角改为 G2 连续曲率（SmoothCorners，移植 Capsule）：
 *              drawable-nodpi 图标按 SmoothCorners 重新生成 alpha 遮罩，
 *              描边改用 SmoothDrawable 走同一条 G2 路径；描边色浅色 #E7E7E7、深色 #242424。
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
    /** 圆润浮层（Figma 131-4550）：深色 / 浅色 / 跟随系统。 */
    static final String[] ROUND_MODE_NAMES = {"深色模式", "浅色模式", "跟随系统"};

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
    private TextView themeBarLabel, themeBarValue, modeLabel;
    /** 深浅色当前值框（与主题条/键盘布局同款 78×33 带边框，2026-10-28）。 */
    private TextView modeValue;
    private TextView aboutBtn;
    /** 品牌标题：必须持有引用，深浅色切换时要跟着刷新（漏刷会白底白字/黑底黑字直接消失）。 */
    private TextView brandTitle, brandSub;
    private ImageView headIcon;
    /** 隐藏的真实输入框：点「点击唤起键盘」时聚焦它，由系统输入法真正弹出（2026-11-01）。 */
    private EditText trialInput;
    /** 键盘模式（2026-10-31）：0 化学 / 1 普通，与输入法共用 prefs 键 kb_mode。 */
    private int kbMode = ChemKeyboardView.MODE_CHEM;
    private GuidePanel guide;
    /** 壁纸换了要立刻丢掉「自动」主题的旧色并重刷（客户 2026-10-25 反馈自动模式不生效）。 */
    private BroadcastReceiver wallpaperReceiver;

    /** 主题选择浮层（点击「当前主题」条弹出，背景模糊）。竖排列表，对齐设计稿 8a653cd5。 */
    private FrameLayout wrap, pickerLayer;
    private LinearLayout pickerPanel, pickerHeader;
    private TextView pickerHeaderLabel, pickerClose;
    private final List<FrameLayout> pickerRowBoxes = new ArrayList<>();
    private final List<TextView> pickerRowLabels = new ArrayList<>();
    private final List<CheckView> pickerRowChecks = new ArrayList<>();

    /** 键盘布局选择浮层（2026-10-28：与主题浮层同款气泡框，点「键盘布局」卡弹出）。 */
    private FrameLayout kbPickerLayer;
    private LinearLayout kbPickerPanel, kbPickerHeader;
    private TextView kbPickerHeaderLabel, kbPickerClose;
    private final List<FrameLayout> kbRowBoxes = new ArrayList<>();
    private final List<TextView> kbRowLabels = new ArrayList<>();
    private final List<CheckView> kbRowChecks = new ArrayList<>();

    /** 深浅色选择浮层（2026-10-28：与主题/键盘布局同款气泡框，点「浅色/深色」卡弹出）。 */
    private FrameLayout modePickerLayer;
    private LinearLayout modePickerPanel, modePickerHeader;
    private TextView modePickerHeaderLabel, modePickerClose;
    private final List<FrameLayout> modeRowBoxes = new ArrayList<>();
    private final List<TextView> modeRowLabels = new ArrayList<>();
    private final List<CheckView> modeRowChecks = new ArrayList<>();
    private final List<TextView> sectionTexts = new ArrayList<>();

    private int cBg, cCard, cText, cSub;

    // ---------- 首页控件引用（圆润版面 Figma 129-1780） ----------
    /** 圆润首页专用引用（restyleAll 里全部做空判断）。 */
    private FrameLayout roundHeadCard, roundAboutCard;
    private LinearLayout roundAppearCard, roundModeRow;
    private TextView roundBrandTitle, roundBrandSub, roundAboutValue;
    private TextView roundModeValue;
    private SwitchView roundHapticSwitch;
    /** 圆润首页「键盘试用输入框」卡（378×48 圆角30，点击唤起系统输入法）。 */
    private FrameLayout roundTrialCard;

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
        buildRoundUi();
    }

    // ---------- 圆润首页（Figma 129-1780 2026-10-31 版，402 宽基准，全部尺寸按 dp 自适应） ----------
    private void buildRoundUi() {
        scroll = new ScrollView(this);
        scroll.setBackgroundColor(cBg);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, dp(28), 0, dp(24));   // 设计稿 padding-top 28，底部留滚动余量
        scroll.addView(root);

        // ---- 顶栏 62 高：logo 62×62（2026-11-07 去掉原来的白卡与描边，图标自带圆角，不再套一圈白边）----
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(dp(27), 0, dp(13), 0); // 设计稿左右 27/13

        FrameLayout logoCard = new FrameLayout(this);
        roundHeadCard = logoCard;
        logoCard.setBackground(null);
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(buildLogo());
        headIcon = icon;
        AppIcon.stroke(icon, 62, darkMode);   // 细描边 G2 圆角（2026-11-07）
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(dp(62), dp(62));
        iconLp.setMargins(0, 0, 0, 0); // 图标直接铺满 62dp 顶栏高度
        logoCard.addView(icon, iconLp);
        header.addView(logoCard, new LinearLayout.LayoutParams(dp(62), dp(62)));

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        colLp.leftMargin = dp(13);
        colLp.topMargin = dp(9);                // 设计稿文字列 margin-top 9
        header.addView(titleCol, colLp);

        TextView brand = new TextView(this);
        SpannableStringBuilder brandText = new SpannableStringBuilder("接化发输入法");
        brandText.setSpan(new StyleSpan(Typeface.BOLD), 0, 3, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
        brand.setText(brandText);
        brand.setTextSize(20);
        brand.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        brand.setTextColor(cText);
        brand.setIncludeFontPadding(false);
        roundBrandTitle = brand;
        titleCol.addView(brand, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(27)));

        TextView sub = new TextView(this);
        sub.setText("你的化学学习利器");
        sub.setTextSize(13);
        sub.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        sub.setTextColor(0xFF091F26);
        sub.setIncludeFontPadding(false);
        roundBrandSub = sub;
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(27));
        subLp.leftMargin = dp(1);               // 设计稿 text4 margin-left 1
        titleCol.addView(sub, subLp);

        root.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(62)));

        // ---- 「应用信息」组头 12sp #949FAB（上 5、左 28）----
        root.addView(roundGroupLabel("应用信息", 5));

        // ---- 关于软件行 378×60 圆角22（上 6、左11/右13，内边距 19/31/19/16）----
        roundAboutCard = new FrameLayout(this);
        roundAboutCard.setBackground(roundWhiteCard(dp(22)));
        TextView aboutLabel = new TextView(this);
        aboutLabel.setText("关于软件");
        aboutLabel.setTextSize(17);
        aboutLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        aboutLabel.setTextColor(cText);
        FrameLayout.LayoutParams alLp = new FrameLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(22));
        alLp.gravity = Gravity.CENTER_VERTICAL | Gravity.LEFT;
        alLp.leftMargin = dp(16);
        roundAboutCard.addView(aboutLabel, alLp);

        roundAboutValue = new TextView(this);
        roundAboutValue.setText(appVersion());
        roundAboutValue.setTextSize(15);
        roundAboutValue.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        roundAboutValue.setTextColor(0xFF9F9F9F);
        roundAboutValue.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        FrameLayout.LayoutParams avLp = new FrameLayout.LayoutParams(dp(65), dp(22));
        avLp.gravity = Gravity.CENTER_VERTICAL | Gravity.RIGHT;
        avLp.rightMargin = dp(31);              // 设计稿值右缘 347（卡右 378）
        roundAboutCard.addView(roundAboutValue, avLp);

        ImageView aboutArrow = new ImageView(this);
        aboutArrow.setImageResource(R.drawable.ic_chevron_12);
        aboutArrow.setRotation(-90f);           // 设计稿 vector 旋转 -90°：指向右
        aboutArrow.setColorFilter(roundArrowColor());
        FrameLayout.LayoutParams aaR = new FrameLayout.LayoutParams(dp(12), dp(6));
        aaR.gravity = Gravity.CENTER_VERTICAL | Gravity.RIGHT;
        aaR.rightMargin = dp(11);               // 设计稿箭头 355..367
        roundAboutCard.addView(aboutArrow, aaR);
        roundAboutCard.setOnClickListener(v -> {
            try {
                startActivity(new Intent(this, AboutActivity.class));
            } catch (Throwable e) {
                ErrorActivity.launch(this, e);
            }
        });
        LinearLayout.LayoutParams aboutLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(60));
        aboutLp.setMargins(dp(11), dp(6), dp(13), 0);
        root.addView(roundAboutCard, aboutLp);

        // ---- 「通用」组头 12sp #949FAB（上 6、左 28）----
        root.addView(roundGroupLabel("通用", 6));

        // ---- 外观白卡 378×174 圆角22（上 7、左12/右12，内边距 17/15/18/15）----
        roundAppearCard = new LinearLayout(this);
        roundAppearCard.setOrientation(LinearLayout.VERTICAL);
        roundAppearCard.setPadding(dp(15), dp(17), dp(15), dp(18));
        roundAppearCard.setBackground(roundWhiteCard(dp(22)));
        roundAppearCard.setClipChildren(false);
        roundAppearCard.setClipToPadding(false);

        // 行1 浅色模式（17sp + 71 宽「切换」15sp + 12×6 箭头）→ 深浅色浮层
        roundModeRow = roundRow("浅色模式", roundModeValue = valueText(), true);
        roundModeRow.setOnClickListener(v -> showModePicker());
        roundAppearCard.addView(roundModeRow,
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(22)));

        // 行2 触感反馈 + 圆润开关 50×28（轨道 #2E73F3、白圆 22、内缩 3）
        // 2026-11-03 删除「界面风格」行，触感反馈上移一行。
        LinearLayout hapticRow = new LinearLayout(this);
        hapticRow.setOrientation(LinearLayout.HORIZONTAL);
        hapticRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView hapticText = new TextView(this);
        hapticText.setText("触感反馈");
        hapticText.setTextSize(17);
        hapticText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        hapticText.setTextColor(cText);
        // TextView 内部默认 TOP，不设 CENTER_VERTICAL 时字形墨迹会压在行顶；
        // 右侧开关轨道铺满整行（SwitchView 圆形模式 track = 全高），两者中心对不上。
        hapticText.setGravity(Gravity.CENTER_VERTICAL);
        hapticRow.addView(hapticText, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        roundHapticSwitch = new SwitchView(this);
        roundHapticSwitch.setRound(true);
        roundHapticSwitch.setStyle(UiStyle.ACCENT, darkMode);
        roundHapticSwitch.setChecked(haptic);
        hapticRow.addView(roundHapticSwitch, new LinearLayout.LayoutParams(dp(50), dp(28)));
        hapticRow.setOnClickListener(v -> {
            haptic = !haptic;
            roundHapticSwitch.setChecked(haptic);
            savePrefs();
            toast(haptic ? "触感反馈已开启" : "触感反馈已关闭");
        });
        LinearLayout.LayoutParams hapticLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(28));
        hapticLp.topMargin = dp(35);            // 两行间距与设计稿一致（行1 底 39 → 行2 顶 74）
        roundAppearCard.addView(hapticRow, hapticLp);

        // 白卡高度按剩余两行重算：上 17 + 行1 22 + 间距 35 + 行2 28 + 下 18 = 120
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(120));
        cardLp.setMargins(dp(12), dp(7), dp(12), 0);
        root.addView(roundAppearCard, cardLp);

        // ---- 「键盘试用输入框」组头（上 6、左 28）+ 378×48 圆角30 卡（上 8、左12/右12）----
        root.addView(roundGroupLabel("键盘试用输入框", 6));
        roundTrialCard = new FrameLayout(this);
        // 设计稿 129-1780：2dp 青蓝横向渐变描边（#2B73DB→#00B4E8），内层卡片色圆角 28
        roundTrialCard.setBackground(roundTrialBg(cCard));
        TextView trialLabel = new TextView(this);
        trialLabel.setText("点击唤起键盘");
        trialLabel.setTextSize(16);
        trialLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        trialLabel.setTextColor(cText);
        trialLabel.setIncludeFontPadding(false);
        // 外层 LayoutParams 的 CENTER_VERTICAL 只管视图盒；TextView 内部默认 TOP，
        // 字形墨迹会偏高约 5dp，必须内部也居中才是真正的上下居中。
        trialLabel.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams tlLp = new FrameLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(26));
        tlLp.gravity = Gravity.CENTER_VERTICAL | Gravity.LEFT;
        tlLp.leftMargin = dp(21);               // 设计稿卡内左内边距 21
        roundTrialCard.addView(trialLabel, tlLp);
        ImageView trialArrow = new ImageView(this);
        trialArrow.setImageResource(R.drawable.ic_chevron_12);
        trialArrow.setRotation(-90f);           // 指向右
        trialArrow.setColorFilter(roundArrowColor());
        FrameLayout.LayoutParams taLp = new FrameLayout.LayoutParams(dp(12), dp(6));
        taLp.gravity = Gravity.CENTER_VERTICAL | Gravity.RIGHT;
        taLp.rightMargin = dp(14);              // 设计稿箭头 left352（卡宽 378）
        roundTrialCard.addView(trialArrow, taLp);
        roundTrialCard.setOnClickListener(v -> showSystemKeyboard());
        LinearLayout.LayoutParams trialLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        trialLp.setMargins(dp(12), dp(8), dp(12), 0);
        root.addView(roundTrialCard, trialLp);

        // （2026-11-01 客户要求）原来的自绘预览键盘已删除：
        // 点试用卡直接聚焦隐藏输入框，由系统输入法真正弹出，不再展示假键盘。

        // ---- 浮层（主题 / 布局 / 深浅色 / 操作引导）----
        buildOverlays();
    }

    /**
     * 隐藏的真实输入框（2026-11-01）：点「点击唤起键盘」时聚焦它，系统输入法才会真的弹出。
     * 1dp×1dp、全透明，不参与视觉，只负责给 InputMethodManager 一个可聚焦的输入目标。
     */
    private void addTrialInput(FrameLayout parent) {
        if (trialInput != null || parent == null) return;
        EditText et = new EditText(this);
        et.setBackground(null);
        et.setAlpha(0f);
        et.setCursorVisible(false);
        et.setSingleLine(true);
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        trialInput = et;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(1), dp(1));
        lp.gravity = Gravity.BOTTOM | Gravity.LEFT;
        parent.addView(et, lp);
    }

    /** 点「点击唤起键盘」：聚焦隐藏输入框并唤起系统输入法（不再用自绘预览键盘）。 */
    private void showSystemKeyboard() {
        if (trialInput == null) return;
        trialInput.setFocusableInTouchMode(true);
        trialInput.requestFocus();
        trialInput.post(() -> {
            InputMethodManager imm =
                    (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(trialInput, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    /** 圆润白卡背景（可指定圆角/颜色）。 */
    private SmoothDrawable roundWhiteCard(int radius) {
        return roundWhiteCard(radius, 0xFFFFFFFF);
    }

    private SmoothDrawable roundWhiteCard(int radius, int color) {
        SmoothDrawable g = new SmoothDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    /** 圆润卡片指向箭头颜色（设计稿实测：浅色 #B2B2B2、深色 #6D6D6D，比正文/值文字更弱）。 */
    private int roundArrowColor() {
        return darkMode ? 0xFF6D6D6D : 0xFFB2B2B2;
    }

    /**
     * 「点击唤起键盘」试用卡背景（Figma 129-1780）：2dp 青蓝横向渐变描边，
     * 外层 #2B73DB → #00B4E8 铺满、圆角 30，内层卡片色以 2dp 内缩、圆角 28。
     */
    private Drawable roundTrialBg(int innerColor) {
        SmoothDrawable border = new SmoothDrawable();
        border.setGradient(0xFF2B73DB, 0xFF00B4E8);
        border.setCornerRadius(dp(30));
        SmoothDrawable inner = new SmoothDrawable();
        inner.setColor(innerColor);
        inner.setCornerRadius(dp(28));
        LayerDrawable bg = new LayerDrawable(new Drawable[]{border, inner});
        int inset = dp(2);
        bg.setLayerInset(1, inset, inset, inset, inset);
        return bg;
    }

    /** 圆润首页组头：12sp #949FAB、左28、高27；不再用 ◤ 三角（设计稿是纯文字）。 */
    private LinearLayout roundGroupLabel(String title, int topMargin) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(12);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tv.setTextColor(darkMode ? 0xFF8A97A5 : 0xFF949FAB);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout box = new LinearLayout(this);
        box.addView(tv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(27)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(28), dp(topMargin), 0, 0);
        box.setLayoutParams(lp);
        sectionTexts.add(tv);
        return box;
    }

    /** 圆润首页行：17sp 标题 + 71 宽 15sp 右侧值（#9F9F9F）+ 12×6 箭头（可点）。 */
    private LinearLayout roundRow(String label, TextView value, boolean arrow) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        // 箭头按设计稿绝对定位在行右内边距区，允许画出行边界
        r.setClipChildren(false);

        TextView text = new TextView(this);
        text.setText(label);
        text.setTextSize(17);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text.setTextColor(cText);
        r.addView(text, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        value.setTextSize(15);
        value.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        value.setTextColor(0xFF9F9F9F);
        value.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        r.addView(value, new LinearLayout.LayoutParams(dp(71),
                LinearLayout.LayoutParams.MATCH_PARENT));

        if (arrow) {
            ImageView iv = new ImageView(this);
            iv.setImageResource(R.drawable.ic_chevron_12);
            iv.setRotation(-90f);
            iv.setColorFilter(roundArrowColor());
            LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(dp(12), dp(6));
            // 设计稿箭头 349..361（内容右缘 363），距值右缘 8、距内容右缘 2
            ivLp.setMargins(dp(8), 0, dp(2), 0);
            r.addView(iv, ivLp);
        }
        return r;
    }

    private TextView valueText() {
        TextView tv = new TextView(this);
        tv.setText("");
        return tv;
    }

    /** 主题 / 布局 / 深浅色三个气泡浮层 + 引导，统一叠在内容之上。 */
    private void buildOverlays() {
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
        addTrialInput(wrap);
        setContentView(wrap);
        restyleAll();
    }

    /** 取安装包版本号（关于软件行右侧、关于页都用实际版本，避免写死）。 */
    private String appVersion() {
        try {
            String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            if (v != null && !v.isEmpty()) return v;
        } catch (Throwable ignored) {
        }
        return "0.0.1";
    }

    /** 勾选图标（15×10 内绘制对勾；原先用文字 ✓，在 10dp 高盒里会被裁成一条细线）。 */
    private CheckView checkMark() {
        CheckView v = new CheckView(this);
        v.setCheckColor(UiStyle.ACCENT);
        return v;
    }

    /** 对勾绘制：任意尺寸盒子里都按 15:10 比例居中，线宽随高度走。 */
    static class CheckView extends View {
        private int color = 0xFF000000;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        CheckView(Context ctx) {
            super(ctx);
        }

        void setCheckColor(int c) {
            color = c;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density;
            float w = Math.min(getWidth(), 15f * d);
            float h = w * 2f / 3f;
            float x0 = (getWidth() - w) / 2f;
            float y0 = (getHeight() - h) / 2f;
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(h * 0.19f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            path.reset();
            path.moveTo(x0 + w * 0.07f, y0 + h * 0.5f);
            path.lineTo(x0 + w * 0.36f, y0 + h * 0.87f);
            path.lineTo(x0 + w * 0.95f, y0 + h * 0.10f);
            c.drawPath(path, paint);
        }
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
            CheckView check = checkMark();
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
            if (TAG_CLOSING.equals(pickerLayer.getTag())) {
                dismissInstant(pickerLayer);   // 正在收起时再点一次：直接重开，免得像点不动
            } else {
                hideThemePicker();
                return;
            }
        }
        // 面板与「当前主题」条所在位置对齐（滚动后仍然对得上）
        dismissInstant(kbPickerLayer);
        dismissInstant(modePickerLayer);
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
        pickerIn(pickerLayer, backdrop, pickerPanel);
    }

    private void hideThemePicker() {
        pickerOut(pickerLayer);
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
            CheckView check = checkMark();
            FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(dp(28), dp(28));
            clp.gravity = Gravity.CENTER_VERTICAL | Gravity.RIGHT;
            clp.setMargins(0, 0, dp(12), 0);
            row.addView(check, clp);
            row.setOnClickListener(v -> {
                kbLayout = mode;
                kbLayoutValue.setText(ChemKeyboardView.LAYOUT_NAMES[mode]);
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
            if (TAG_CLOSING.equals(kbPickerLayer.getTag())) {
                dismissInstant(kbPickerLayer);
            } else {
                hideKbLayoutPicker();
                return;
            }
        }
        hideThemePicker();
        dismissInstant(modePickerLayer);
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
        pickerIn(kbPickerLayer, backdrop, kbPickerPanel);
    }

    private void hideKbLayoutPicker() {
        pickerOut(kbPickerLayer);
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
            kbRowChecks.get(i).setCheckColor(sel ? onAccent(accent) : Color.TRANSPARENT);
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
            CheckView check = checkMark();
            FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(dp(28), dp(28));
            clp.gravity = Gravity.CENTER_VERTICAL | Gravity.RIGHT;
            clp.setMargins(0, 0, dp(12), 0);
            row.addView(check, clp);
            row.setOnClickListener(v -> {
                // 圆润（Figma 131-4550）：0 深色 / 1 浅色 / 2 跟随
                followSystem = idx == 2;
                darkMode = idx == 2 ? systemDark() : (idx == 0);
                savePrefs();
                hideModePicker();
                restyleAll();
                toast(ROUND_MODE_NAMES[idx]);
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
            if (TAG_CLOSING.equals(modePickerLayer.getTag())) {
                dismissInstant(modePickerLayer);
            } else {
                hideModePicker();
                return;
            }
        }
        hideThemePicker();
        dismissInstant(kbPickerLayer);
        // 面板与「深浅色模式」卡所在位置对齐（滚动后仍然对得上）
        View anchor = roundModeRow != null ? roundModeRow : modeCard;
        int[] card = new int[2], w = new int[2];
        anchor.getLocationInWindow(card);
        wrap.getLocationInWindow(w);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) modePickerPanel.getLayoutParams();
        lp.topMargin = Math.max(0, card[1] - w[1] - dp(15));
        // 设计稿 131-4550：宽163、右距15
        lp.width = dp(163);
        lp.rightMargin = dp(15);
        modePickerLayer.removeAllViews();

        ImageView backdrop = new ImageView(this);
        // 设计稿遮罩 #00000080
        UiBlur.apply(backdrop, scroll, 0x80000000, cBg);
        modePickerLayer.addView(backdrop, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        modePickerLayer.addView(modePickerPanel, lp);
        restyleModePicker();
        pickerIn(modePickerLayer, backdrop, modePickerPanel);
    }

    private void hideModePicker() {
        pickerOut(modePickerLayer);
    }

    // ---------- 气泡浮层显隐过渡（2026-10-29） ----------

    private static final String TAG_CLOSING = "picker-closing";

    /** 淡入：遮罩 0→1 且模糊 0→26，面板下→上位移 + 轻微放大，统一 280ms 缓出曲线。 */
    private void pickerIn(FrameLayout layer, ImageView backdrop, View panel) {
        layer.setTag(null);
        backdrop.animate().cancel();
        panel.animate().cancel();
        backdrop.setAlpha(0f);
        UiBlur.blurIn(backdrop);
        backdrop.animate().alpha(1f).setDuration(UiBlur.DUR)
                .setInterpolator(UiBlur.EASE_OUT).start();
        panel.setAlpha(0f);
        panel.setTranslationY(dp(10));
        panel.setScaleX(0.97f);
        panel.setScaleY(0.97f);
        panel.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                .setDuration(UiBlur.DUR).setInterpolator(UiBlur.EASE_OUT).start();
        layer.setVisibility(View.VISIBLE);
        // 遮罩压暗时系统栏同步压暗（2026-11-03），顶部/底部不再留两条没变色的带子
        dimSystemBars();
    }

    /** 淡出：模糊 26→0、面板下移淡出，动画结束后才 GONE，避免生硬跳变。 */
    private void pickerOut(FrameLayout layer) {
        if (layer == null || layer.getVisibility() != View.VISIBLE
                || TAG_CLOSING.equals(layer.getTag())) return;
        layer.setTag(TAG_CLOSING);
        View bd = layer.getChildAt(0);
        View panel = layer.getChildAt(1);
        if (panel != null) {
            panel.animate().alpha(0f).translationY(dp(8)).scaleX(0.98f).scaleY(0.98f)
                    .setDuration(UiBlur.DUR).setInterpolator(UiBlur.EASE_OUT).start();
        }
        if (bd != null) {
            bd.animate().alpha(0f).setDuration(UiBlur.DUR)
                    .setInterpolator(UiBlur.EASE_OUT).start();
            UiBlur.blurOut((ImageView) bd, () -> finishClose(layer, (ImageView) bd));
        } else {
            finishClose(layer, null);
        }
    }

    private void finishClose(FrameLayout layer, ImageView backdrop) {
        if (!TAG_CLOSING.equals(layer.getTag())) return;   // 期间又被重新打开
        layer.setVisibility(View.GONE);
        layer.setTag(null);
        if (backdrop != null) backdrop.setImageDrawable(null);
        // 最后一个浮层收完才恢复系统栏，否则连开另一个浮层时状态栏会闪一下
        if (!hasVisiblePicker()) restoreSystemBars();
    }

    /** 是否还有正在显示（含淡出中的）气泡浮层或操作引导。 */
    private boolean hasVisiblePicker() {
        return (pickerLayer != null && pickerLayer.getVisibility() == View.VISIBLE)
                || (kbPickerLayer != null && kbPickerLayer.getVisibility() == View.VISIBLE)
                || (modePickerLayer != null && modePickerLayer.getVisibility() == View.VISIBLE)
                || (guide != null && guide.getVisibility() == View.VISIBLE);
    }

    /** 操作引导等全局遮罩显示/收起时，同步系统栏压暗（2026-11-03）。 */
    void onModalOverlayVisibility(boolean visible) {
        if (visible) dimSystemBars();
        else if (!hasVisiblePicker()) restoreSystemBars();
    }

    /** 打开另一个浮层时把旧的直接收掉，避免两层模糊遮罩叠在一起闪烁。 */
    private void dismissInstant(FrameLayout layer) {
        if (layer == null || layer.getVisibility() == View.GONE) return;
        layer.setTag(null);
        View bd = layer.getChildAt(0);
        if (bd != null) {
            bd.animate().cancel();
            if (bd instanceof ImageView) ((ImageView) bd).setRenderEffect(null);
        }
        View panel = layer.getChildAt(1);
        if (panel != null) panel.animate().cancel();
        layer.setVisibility(View.GONE);
    }

    /** 深浅色浮层配色（与主题/键盘布局浮层同一套规则：头部主题色，选中行填主题色并打勾）。 */
    private void restyleModePicker() {
        if (modePickerPanel == null) return;
        int accent = ChemKeyboardView.themeColor(themeId);

        // 按 Figma 131-4550 / 177-1362：白色（深色 #242424）圆角20小卡、无标题、
        // 17sp 列表、选中蓝字 + 勾
        SmoothDrawable g = new SmoothDrawable();
        g.setColor(darkMode ? 0xFF242424 : 0xFFFFFFFF);
        g.setCornerRadius(dp(20));
        modePickerPanel.setBackground(g);
        modePickerPanel.setElevation(dp(14));    // 设计稿阴影 #00000070
        modePickerPanel.setPadding(dp(21), dp(17), dp(25), dp(18));
        modePickerHeader.setVisibility(View.GONE);
        for (int i = 0; i < modeRowBoxes.size(); i++) {
            modeRowLabels.get(i).setText(ROUND_MODE_NAMES[i]);
            boolean sel = i == roundModeIndex();
            modeRowLabels.get(i).setTextSize(17);
            modeRowLabels.get(i).setTextColor(sel ? UiStyle.ACCENT : cText);
            modeRowChecks.get(i).setCheckColor(sel ? UiStyle.ACCENT : Color.TRANSPARENT);
            modeRowBoxes.get(i).setBackgroundColor(Color.TRANSPARENT);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams)
                    modeRowBoxes.get(i).getLayoutParams();
            lp.height = dp(37);
            if (i > 0) lp.topMargin = dp(10);
            modeRowBoxes.get(i).setLayoutParams(lp);
            FrameLayout.LayoutParams llp = (FrameLayout.LayoutParams)
                    modeRowLabels.get(i).getLayoutParams();
            llp.leftMargin = 0;
            modeRowLabels.get(i).setLayoutParams(llp);
            FrameLayout.LayoutParams clp = (FrameLayout.LayoutParams)
                    modeRowChecks.get(i).getLayoutParams();
            clp.width = dp(15);
            clp.height = dp(10);
            clp.rightMargin = dp(-4);     // 设计稿勾 x127..142，内容右缘 138
            modeRowChecks.get(i).setLayoutParams(clp);
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
            pickerRowChecks.get(i).setCheckColor(sel ? onAccent(col) : Color.TRANSPARENT);
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

    /** 圆润浮层下标（Figma 131-4550 顺序）：0 深色、1 浅色、2 跟随系统。 */
    private int roundModeIndex() {
        return followSystem ? 2 : (darkMode ? 0 : 1);
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
            // 深色（2026-11-07 客户要求）：底 #000000、卡 #242424、弱化字 #868686
            cBg = 0xFF000000;
            cCard = 0xFF242424;
            cText = 0xFFFFFFFF;
            cSub = 0xFF868686;
        } else {
            // 浅色按 Figma 129-1780：底 #F3F3F3、卡白
            cBg = 0xFFF3F3F3;
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
        // 桌面图标同步跟随深浅色（2026-11-07）：状态已正确时内部直接返回，不会反复刷新桌面
        AppIcon.syncLauncher(this, darkMode);
        if (scroll != null) scroll.setBackgroundColor(cBg);

        // 状态栏与页面背景同色（原来用主题深色，顶部一条青/红条很割裂）
        getWindow().setStatusBarColor(cBg);
        applyStatusBarIcons();
        // 导航栏同样跟随页面底色（2026-11-01 沉浸式），避免底部出现系统黑条
        applyNavigationBar();
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
            // 描边色随深浅色刷新（浅 #E7E7E7 / 深 #242424），不重建 View 避免闪烁
            AppIcon.stroke(headIcon, 62, darkMode);
        }

        // ---- 圆润首页（Figma 129-1780 2026-10-31 版）：白卡圆角 22、行文字 17sp、蓝色强调 ----
        int blue = UiStyle.ACCENT;
        int valueColor = darkMode ? 0xFF868686 : 0xFF9F9F9F;
        int sectionColor = 0xFF949FAB;      // 深色设计稿组头仍为 #949FAB
        // 组头「应用信息 / 通用 / 键盘试用输入框」是 12sp #949FAB，不能用正文色
        for (TextView t : sectionTexts) t.setTextColor(sectionColor);
        if (roundHeadCard != null) {
            // 2026-11-07 去掉 logo 白卡：图标自带圆角，卡底留空才没有一圈白边
            roundHeadCard.setBackground(null);
        }
        if (roundBrandTitle != null) roundBrandTitle.setTextColor(cText);
        if (roundBrandSub != null) roundBrandSub.setTextColor(darkMode ? 0xCCFFFFFF : 0xFF091F26);
        if (roundAboutCard != null) {
            roundAboutCard.setBackground(rounded(cCard, dp(22)));
            // 标签与箭头随深浅色刷新：漏刷时黑卡上是黑字，整行「关于软件」会消失
            for (int i = 0; i < roundAboutCard.getChildCount(); i++) {
                View v = roundAboutCard.getChildAt(i);
                if (v instanceof TextView) ((TextView) v).setTextColor(cText);
                if (v instanceof ImageView) ((ImageView) v).setColorFilter(roundArrowColor());
            }
        }
        if (roundAboutValue != null) roundAboutValue.setTextColor(valueColor);
        if (roundAppearCard != null) {
            roundAppearCard.setBackground(rounded(cCard, dp(22)));
            for (int i = 0; i < roundAppearCard.getChildCount(); i++) {
                View row = roundAppearCard.getChildAt(i);
                if (row instanceof LinearLayout) {
                    TextView label = (TextView) ((LinearLayout) row).getChildAt(0);
                    label.setTextColor(cText);
                    for (int j = 1; j < ((LinearLayout) row).getChildCount(); j++) {
                        View v = ((LinearLayout) row).getChildAt(j);
                        if (v instanceof ImageView) ((ImageView) v).setColorFilter(roundArrowColor());
                        // 右侧值文字用设计稿弱化色 #9F9F9F
                        if (v instanceof TextView) ((TextView) v).setTextColor(valueColor);
                    }
                }
            }
        }
        if (roundModeValue != null) {
            roundModeValue.setText("切换");     // 设计稿行1 右侧固定文案
            roundModeValue.setTextColor(valueColor);
        }
        if (roundHapticSwitch != null) {
            roundHapticSwitch.setStyle(blue, darkMode);
            roundHapticSwitch.setChecked(haptic);
        }
        if (roundTrialCard != null) {
            roundTrialCard.setBackground(roundTrialBg(cCard));
            for (int i = 0; i < roundTrialCard.getChildCount(); i++) {
                View v = roundTrialCard.getChildAt(i);
                if (v instanceof TextView) ((TextView) v).setTextColor(cText);
                if (v instanceof ImageView) ((ImageView) v).setColorFilter(roundArrowColor());
            }
        }

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

    /**
     * 导航栏沉浸式（2026-11-01）：与页面底色同色，按深浅色切换图标明暗；
     * Android 9+ 去掉分隔线，Android 10+ 关闭系统「强制对比度」遮罩（那层黑纱就是底部的黑条）。
     */
    private void applyNavigationBar() {
        try {
            getWindow().setNavigationBarColor(cBg);
            if (Build.VERSION.SDK_INT >= 28) getWindow().setNavigationBarDividerColor(cBg);
            if (Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);
            View decor = getWindow().getDecorView();
            int flags = decor.getSystemUiVisibility();
            if (darkMode) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            else flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            decor.setSystemUiVisibility(flags);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 浮层出现时系统栏跟着压暗（2026-11-03）：与遮罩同 50% 黑，
     * 并把状态栏/导航栏图标改为白色，避免顶部和底部留下两条没变色的带子。
     */
    private void dimSystemBars() {
        try {
            int dim = blendColor(cBg, 0xFF000000, 0.5f);
            getWindow().setStatusBarColor(dim);
            getWindow().setNavigationBarColor(dim);
            if (Build.VERSION.SDK_INT >= 28) getWindow().setNavigationBarDividerColor(dim);
            if (Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);
            View decor = getWindow().getDecorView();
            int flags = decor.getSystemUiVisibility();
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            decor.setSystemUiVisibility(flags);
        } catch (Throwable ignored) {
        }
    }

    /** 浮层全部关闭后恢复沉浸式系统栏。 */
    private void restoreSystemBars() {
        try {
            getWindow().setStatusBarColor(cBg);
        } catch (Throwable ignored) {
        }
        applyStatusBarIcons();
        applyNavigationBar();
    }

    /** 两个不透明颜色按 k (0..1) 混合。 */
    private static int blendColor(int from, int to, float k) {
        int a = (int) (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * k);
        int r = (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * k);
        int g = (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * k);
        int b = (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * k);
        return Color.argb(a, r, g, b);
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
        kbMode = p.getInt("kb_mode", ChemKeyboardView.MODE_CHEM);
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

    private void stylePill(TextView tv, String text, int bg, int stroke, int textColor) {
        tv.setText(text);
        GradientDrawable g = new GradientDrawable();
        g.setColor(bg);
        g.setCornerRadius(0);
        if (stroke != 0) g.setStroke((int) (1.3f * getResources().getDisplayMetrics().density), stroke);
        tv.setBackground(g);
        tv.setTextColor(textColor);
    }

    private SmoothDrawable filled(int color, float radius) {
        SmoothDrawable g = new SmoothDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private SmoothDrawable ring(int color, float radius) {
        SmoothDrawable g = new SmoothDrawable();
        g.setColor(Color.TRANSPARENT);
        g.setCornerRadius(radius);
        g.setStroke((int) (1.3f * getResources().getDisplayMetrics().density), color);
        return g;
    }

    private SmoothDrawable rounded(int color, float radius) {
        SmoothDrawable g = new SmoothDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    /**
     * 顶栏 logo：2026-11-07 起直接用新的应用图标（drawable/ic_app_logo，四角圆角 26%），
     * 不再拆「字层 + 勾层」两层。
     * 2026-11-07 深色模式改用 ic_app_logo_dark（图标-1.png），浅色恢复默认图标；
     * 取不到深色图时 AppIcon 会自动回退默认图标。
     */
    private Drawable buildLogo() {
        return AppIcon.logo(this, darkMode);
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
        /** G2 连续圆角的胶囊轨道（2026-11-03，Capsule）。 */
        private final Path roundPath = new Path();
        private final ArgbEvaluator argb = new ArgbEvaluator();
        private boolean checked;
        private int accent = 0xFF00C4D2;
        /** true = 圆润风格开关（Figma 129-1780：49×22 胶囊轨道 + 18×18 白色圆滑块）。 */
        private boolean round;
        private boolean dark;
        /* 2026-10-29 过渡动画：anim 为 0(关)→1(开) 的进度，滑块位置/配色/滑块内标记都按它插值 */
        private float anim = 0f;
        private boolean animInit;
        private ValueAnimator animator;

        SwitchView(Context ctx) {
            super(ctx);
        }

        void setChecked(boolean on) {
            if (checked == on) {
                if (!animInit) {
                    anim = on ? 1f : 0f;
                    animInit = true;
                }
                invalidate();
                return;
            }
            checked = on;
            if (!animInit) {           // 首次绑定直接落到目标态，不播动画
                animInit = true;
                anim = on ? 1f : 0f;
                invalidate();
                return;
            }
            if (animator != null) animator.cancel();
            animator = ValueAnimator.ofFloat(anim, on ? 1f : 0f);
            animator.setDuration(UiBlur.DUR);
            animator.setInterpolator(UiBlur.EASE_OUT);
            animator.addUpdateListener(a -> {
                anim = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }

        void setStyle(int accentColor, boolean darkModeNow) {
            accent = accentColor;
            dark = darkModeNow;
            invalidate();
        }

        /** 圆润开关形态（只在圆润首页创建时设置一次）。 */
        void setRound(boolean on) {
            round = on;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth();
            float h = getHeight();
            float p = anim;

            // 圆润（Figma 129-1780 / 177-1169）：50×28 胶囊轨道 + 22×22 圆滑块、内缩 3、无投影。
            // 深浅色设计稿的滑块都是纯白圆（深色稿 177-1169 实测轨道 #2E73F3、滑块 #FFFFFF），
            // 之前深色用了 #484848 滑块，与设计稿不符。
            if (round) {
                track.set(0, 0, w, h);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(dark
                        ? (Integer) argb.evaluate(p, 0xFF6E6E6E, accent)
                        : (Integer) argb.evaluate(p, 0xFFDDDDDD, accent));
                SmoothCorners.build(roundPath, track, h / 2f);
                c.drawPath(roundPath, paint);
                float kw = dp(22);
                float inset = dp(3);
                float left = inset + p * (w - 2 * inset - kw);
                paint.setColor(Color.WHITE);
                c.drawCircle(left + kw / 2f, h / 2f, kw / 2f, paint);
                return;
            }

            // 轨道：直角实心，关闭灰底 → 开启主题色（颜色按进度插值）
            track.set(0, 0, w, h);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor((Integer) argb.evaluate(p, 0xFFDDDDDD, accent));
            c.drawRect(track, paint);

            // 滑块：34×25，左/右/上各留 3dp，位置从左侧滑到右侧
            float kw = dp(34);
            float kh = dp(25);
            float inset = dp(3);
            float left = inset + p * (w - 2 * inset - kw);
            float top = dp(3);
            knob.set(left, top, left + kw, top + kh);

            // 滑块投影：0 2px 0，关闭 #ADADAD，开启为压暗的主题色
            float shadowY = dp(2);
            paint.setColor((Integer) argb.evaluate(p, 0xFFADADAD, darken(accent, 0.78f)));
            c.drawRect(left, top + shadowY, left + kw, top + kh + shadowY, paint);

            paint.setColor(Color.WHITE);
            c.drawRect(knob, paint);

            // 滑块内标记：圆环 → 竖线，按进度交叉淡出淡入（黑色 1px）
            float cx = (knob.left + knob.right) / 2f;
            float cy = (knob.top + knob.bottom) / 2f;
            float stroke = dp(1);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setColor(Color.BLACK);
            if (p < 0.98f) {
                paint.setAlpha((int) (255 * (1f - p)));
                c.drawCircle(cx, cy, dp(13) / 2f * (0.55f + 0.45f * (1f - p)), paint);
            }
            if (p > 0.02f) {
                paint.setAlpha((int) (255 * p));
                float half = dp(11) / 2f * p;
                c.drawLine(cx, cy - half, cx, cy + half, paint);
            }
            paint.setAlpha(255);
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

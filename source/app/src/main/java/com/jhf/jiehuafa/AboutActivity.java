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
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.jhf.jiehuafa.ime.ChemKeyboardView;

/**
 * 关于软件页（Figma 设计稿 402 宽）：底色 #E4E4E4、内边距 47/23，返回箭头 20×10 →
 * 关于软件 24sp Medium → logo 白卡 99×98 圆角 25 → 软件信息组头 15sp → 版本卡 →
 * 作者信息组头 → 作者卡 356×116 圆角 16（18sp 双行 + 16sp #00000033 值 + HAGO logo 86×15）。
 *
 * 修改日志：
 *   2026-10-31 新增圆润风格版面（Figma 130-4004）：返回由描边按钮改为 20×10 旋转箭头，
 *              标题 24sp Medium，logo 白卡 99×98 圆角 25，组头 15sp Medium 左 32，
 *              作者卡圆角 16 内边距 22/16/22/21，并在「软件信息」下方的设计稿留白处
 *              补一张 56 高圆角 16 的版本卡（设计稿未画出，但版本号必须可见）。
 *              卡片宽度随屏宽自适应（左右 23 边距）。
 *   2026-10-31 按新版 Figma 130-4004 一比一重排圆润版面：底色 #F3F3F3、左右边距 12、
 *              返回 20×10 左 6、标题 32sp Medium（上 24 左 11 高 34）；
 *              软件卡 378×208 圆角 22（logo 盒 70×70 + 53×45 图 + 描边 #E7E7E7，
 *              名称 17sp / 版本 14sp #9D9D9D，两行链接值 15sp #9F9F9F、箭头 12×6）；
 *              作者组头 12sp #949FAB（上 8 左 17），作者卡 378×117 圆角 22
 *              （左列 17sp 行距 35，右列「哈潚」15sp + HAGO 盒 76×27 圆角 6）。
 *              强调色随 UiStyle.ACCENT 改为 #2E73F3。
 *   2026-10-31 深色圆润按 Figma 177-1311：底色 #252525、卡片 #373737、组头 #949FAB、
 *              版本 #9D9D9D、右值/作者名 #868686、logo 盒白底白描边（图用深色版）、
 *              HAGO 盒 #373737 + 描边 #3D3D3D。
 *   2026-10-31 圆润版面修复与文案：返回箭头 20×10 旋转 90° 后视觉盒为 10×20，
 *              上半截（约 5dp）落到根布局上内边距区域，被默认 clipToPadding 裁掉，
 *              改为 root.setClipToPadding(false) 让箭头完整显示（布局坐标不变）；
 *              作者卡左列第二行文案「界面风格」改为「出品工作室」。
 *   2026-10-31 「软件交流群」「应用文档」两行接入真实跳转：交流群点击时若已安装 QQ，
 *              优先 mqqopensdkapi://bizAgent/qm/qr?url=<编码后的群链接> 拉起 QQ 加群页，
 *              失败再试 https 群链接限定 QQ 包，最后用系统浏览器 ACTION_VIEW 打开；
 *              应用文档用系统浏览器（ACTION_VIEW + CATEGORY_BROWSABLE）打开飞书手册。
 *              QQ 安装检测依赖 AndroidManifest 的 <queries> 包可见性声明（Android 11+）。
 *   2026-10-31 圆润版面箭头按设计稿重做：返回键改用 20×10 的 ic_back_arrow（旋转后视觉
 *              10×20，旧的 18×12 塞进 20×10 只能画出 15×10）；软件卡两行指向箭头改用
 *              12×6 的 ic_chevron_12（旋转后 6×12），颜色取设计稿实测值（浅 #B2B2B2 /
 *              深 #6D6D6D），返回键为浅 #5B5B5B / 深 #838383。
 *   2026-11-01 沉浸式适配：新增 applyImmersiveBars()，状态栏与导航栏都填页面底色，
 *              Android 9+ 去掉导航栏分隔线、Android 10+ 关闭强制对比度黑纱，底部不再有黑条。
 *   2026-11-01 按 Figma 209-60 / 212-72 补齐圆润版面：新增「许可与鸣谢」组头与三行
 *              许可卡（第三方许可清单 / 第三方组件 / 前往 GitHub），并加入滚动吸顶栏
 *              （168 高白渐变 + 44 白色圆钮返回 + 20sp 标题，随 scrollY 淡入）。
 *              三行分别打开 AboutSheet 的许可清单、组件清单和 GitHub 链接。
 *   2026-11-01 新增 AboutSheet：底部大弹层（Figma 209-105 / 209-144 / 209-185），
 *              iOS 风格上浮曲线 + 背景快照渐进模糊（API 31+ RenderEffect），
 *              许可证全文以二级页从右推入。
 *   2026-11-05 吸顶栏垂直对齐修复：标题盒原 top 10（中心 27），比 44 圆钮（top 12、中心 34）
 *              高 7dp，上滑后返回键与标题看起来错位；改为 top 17，与弹层 AboutSheet 的
 *              「文字盒中心 = 圆钮中心」基准一致（盒高 34 不变，Gravity.CENTER 保证任意字号居中）。
 *   2026-11-07 页面 logo 跟随深浅色：深色取 ic_app_logo_dark（图标-1.png），
 *              浅色取默认 ic_app_logo，统一走 AppIcon.logo()，取不到时自动回退默认图标。
 *   2026-11-07 深色配色调整（客户要求）：页面底色 #252525 → #000000，卡片 #373737 → #242424，
 *              吸顶栏渐变与返回圆钮同步；页面 logo 增加 1dp 灰色细描边（AppIcon.stroke）。
 *   2026-11-07 页面 logo 圆角改为 G2 连续曲率（SmoothCorners / Capsule 移植），
 *              描边改用 SmoothDrawable 的 G2 路径，浅色 #E7E7E7、深色 #242424。
 *   2026-11-09 作者卡与许可卡链接按客户要求拆开：整卡点击取消，改为右侧「哈潚」单独点击
 *              跳作者主页 https://github.com/uHaDawn；「出品工作室」右侧 HAGo 图标不做点击；
 *              许可卡「前往 GitHub · 访问」跳项目仓库 https://github.com/uHaDawn/JHF_ChemistryInputMethod。
 */
public class AboutActivity extends Activity {

    /** QQ 群「接化发·软件交流群｜化学输入法」加群链接。 */
    private static final String QQ_GROUP_URL = "https://qm.qq.com/q/6qnVMKTrz2";
    /** 应用文档：接化发 · 化学输入法 用户使用手册。 */
    private static final String DOC_URL =
            "https://my.feishu.cn/docx/PHNfdxuEYoLYJpx0IXUcMurD6id?from=from_copylink";
    /** QQ Android 包名（加群深链与安装检测用）。 */
    private static final String QQ_PACKAGE = "com.tencent.mobileqq";
    /** 作者主页（作者信息卡右侧「哈潚」）。 */
    private static final String AUTHOR_URL = "https://github.com/uHaDawn";
    /** 项目仓库（许可与鸣谢卡「前往 GitHub · 访问」）。 */
    private static final String REPO_URL = "https://github.com/uHaDawn/JHF_ChemistryInputMethod";

    private int cBg, cCard, cText;
    private boolean dark;
    private String themeId;
    /** logo 对勾的主题色（品牌青 #05BEC1，不随链接蓝变化）。 */
    private int logoColor;
    /** 页面根（滚动内容 + 吸顶栏），截图与弹层定位用（Figma 212-72）。 */
    private FrameLayout pageRoot;
    /** 圆润版面的滚动容器：弹层关闭后要把滚动位置复原（否则会跳到底部的许可卡）。 */
    private ScrollView pageScroll;
    /** 底部大弹层（Figma 209-105 / 209-144 / 209-185）。 */
    private AboutSheet sheet;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            ChemKeyboardView.resolveAutoColors(this);
            SharedPreferences sp = getSharedPreferences("jhf_settings", MODE_PRIVATE);
            themeId = sp.getString("theme", "cucl2");
            dark = sp.getBoolean("follow", false)
                    ? ((getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                        == Configuration.UI_MODE_NIGHT_YES)
                    : sp.getBoolean("dark", false);
            // 标准蓝 #2E73F3（设计稿）
            int accent = UiStyle.ACCENT;
            // logo 的对勾是品牌青（主题色 #05BEC1），不跟着链接色变蓝。
            logoColor = ChemKeyboardView.themeColor(themeId);

            if (dark) {
                // 深色（2026-11-07 客户要求）：底 #000000、卡 #242424
                cBg = 0xFF000000;
                cCard = 0xFF242424;
                cText = 0xFFFFFFFF;
            } else {
                // 底色按 Figma 130-4004：#F3F3F3
                cBg = 0xFFF3F3F3;
                cCard = 0xFFFFFFFF;
                cText = 0xFF000000;
            }
            // 状态栏与导航栏都与页面背景同色（2026-11-01 沉浸式），避免顶部/底部出现系统色带
            applyImmersiveBars();

            buildRoundUi(accent);
        } catch (Throwable e) {
            ErrorActivity.launch(this, e);
        }
    }

    /**
     * 沉浸式系统栏（2026-11-01）：状态栏与导航栏都填成页面底色，并按深浅色切换图标明暗；
     * Android 10+ 关闭导航栏「强制对比度」（系统会自己加一层半透明黑纱），避免底部黑条。
     */
    private void applyImmersiveBars() {
        try {
            // 状态栏透明 + 内容延伸到状态栏区域（2026-11-08）：窗口系统栏不再盖一条不透明色带，
            // 吸顶栏返回圆钮的柔和阴影可以完整绘制进状态栏区域，不会被截断；
            // 状态栏区域由页面自身绘制（状态栏底垫按吸顶栏同色渐变，见 addStickyHeader）。
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(cBg);
            if (Build.VERSION.SDK_INT >= 28) getWindow().setNavigationBarDividerColor(cBg);
            if (Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);
            View decor = getWindow().getDecorView();
            int flags = decor.getSystemUiVisibility();
            flags |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
            flags |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            if (dark) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            } else {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            decor.setSystemUiVisibility(flags);
            // 刘海屏 / 挖孔屏：状态栏区域也按沉浸式铺满，避免顶部又被退回黑边。
            if (Build.VERSION.SDK_INT >= 28) {
                getWindow().getAttributes().layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
        } catch (Throwable ignored) {
        }
    }

    /** 状态栏高度（沉浸式下页面需要给内容让出这段高度）。 */
    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        int h = id > 0 ? getResources().getDimensionPixelSize(id) : dp(24);
        return h > 0 ? h : dp(24);
    }

    /**
     * 圆润风格（Figma 130-4004 2026-10-31 版）：402 画板下左右 12、上 47；
     * 返回箭头 20×10（左 6）→ 标题 32sp Medium（上 24、左 11、高 34）→
     * 软件卡 378×208 圆角 22（上 22、内边距 15/35/19/16）→
     * 「作者信息」组头（上 8、左 17）→ 作者卡 378×117 圆角 22（上 7、内边距 18/17/17/16）。
     */
    private void buildRoundUi(int accent) {
        pageRoot = new FrameLayout(this);
        pageRoot.setBackgroundColor(cBg);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(cBg);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        // 底部留白按 Figma 209-60 的 154：内容总高 874，上滑时吸顶栏才有足够的行程显形。
        root.setPadding(dp(12), dp(47), dp(12), dp(154));
        // 返回箭头 20×10 旋转 90° 后视觉盒超出自身布局盒上下各 5dp；上面那截会落到根布局的
        // 上内边距里，默认 clipToPadding=true 会沿内容区顶边裁掉，导致箭头只显示下半截。
        root.setClipToPadding(false);
        scroll.addView(root);
        FrameLayout.LayoutParams scrollLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        // 状态栏已透明、页面从屏幕顶端开始绘制，滚动内容让出状态栏高度，标题等视觉位置不变。
        scrollLp.topMargin = statusBarHeight();
        pageRoot.addView(scroll, scrollLp);
        pageScroll = scroll;
        addStickyHeader(scroll, root);
        setContentView(pageRoot);

        // ---- 返回：无柄左指箭头（12×22 原始比例，盒 11×20 由 fitCenter 等比缩放）----
        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_back_chevron);
        back.setColorFilter(dark ? 0xFF838383 : 0xFF5B5B5B);   // 设计稿实测返回箭头灰度
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(dp(11), dp(20));
        backLp.leftMargin = dp(6);
        root.addView(back, backLp);

        // ---- 标题 32sp Medium 高 34（上 24、左 11）----
        TextView title = new TextView(this);
        title.setText("关于软件");
        title.setTextSize(32);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setTextColor(cText);
        title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(34));
        titleLp.topMargin = dp(24);
        titleLp.leftMargin = dp(11);
        root.addView(title, titleLp);

        // ---- 软件卡 378×208 圆角 22（上 22、内边距 15/35/19/16）----
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setBackground(roundCardBg());
        info.setPadding(dp(16), dp(15), dp(15), dp(19));

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        // logo 盒 70×70（2026-11-07 去掉白底与描边，直接用新应用图标 58×58）
        LinearLayout logoBox = new LinearLayout(this);
        logoBox.setGravity(Gravity.CENTER);
        ImageView logo = new ImageView(this);
        logo.setImageDrawable(AppIcon.logo(this, dark));
        AppIcon.stroke(logo, 58, dark);   // 细描边 G2 圆角（2026-11-07）
        logoBox.addView(logo, new LinearLayout.LayoutParams(dp(58), dp(58)));
        topRow.addView(logoBox, new LinearLayout.LayoutParams(dp(70), dp(70)));

        LinearLayout nameCol = new LinearLayout(this);
        nameCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams nameColLp = new LinearLayout.LayoutParams(dp(139), dp(70));
        nameColLp.leftMargin = dp(15);
        TextView name = roundMediumText("接化发", 17);
        name.setGravity(Gravity.CENTER_VERTICAL);
        nameCol.addView(name, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(22)));
        TextView version = roundMediumText("版本：" + appVersion(), 14);
        version.setTextColor(0xFF9D9D9D);
        version.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams verLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(22));
        verLp.topMargin = dp(1);
        nameCol.addView(version, verLp);
        topRow.addView(nameCol, nameColLp);
        info.addView(topRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(70)));

        LinearLayout rowGroup = roundLinkRow("软件交流群", "加入");
        rowGroup.setPadding(dp(2), 0, 0, 0);
        rowGroup.setOnClickListener(v -> openQqGroup());
        LinearLayout.LayoutParams rowGroupLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(22));
        rowGroupLp.topMargin = dp(22);
        info.addView(rowGroup, rowGroupLp);

        LinearLayout rowDoc = roundLinkRow("应用文档", "访问");
        rowDoc.setPadding(dp(2), 0, 0, 0);
        rowDoc.setOnClickListener(v -> openInBrowser(DOC_URL));
        LinearLayout.LayoutParams rowDocLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(22));
        rowDocLp.topMargin = dp(36);
        info.addView(rowDoc, rowDocLp);

        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(208));
        infoLp.topMargin = dp(22);
        root.addView(info, infoLp);

        // ---- 「作者信息」组头 12sp #949FAB（上 8、左 17、高 27）----
        root.addView(roundGroup("作者信息", 8));

        // ---- 作者卡 378×117 圆角 22（上 7、内边距 18/17/17/16）----
        LinearLayout author = new LinearLayout(this);
        author.setOrientation(LinearLayout.HORIZONTAL);
        author.setPadding(dp(16), dp(18), dp(17), dp(17));
        author.setBackground(roundCardBg());

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView designLabel = roundMediumText("设计制作", 17);
        designLabel.setGravity(Gravity.CENTER_VERTICAL);
        left.addView(designLabel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(22)));
        TextView styleLabel = roundMediumText("出品工作室", 17);
        styleLabel.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams styleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(22));
        styleLp.topMargin = dp(35);
        left.addView(styleLabel, styleLp);
        author.addView(left, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.RIGHT);
        TextView authorName = new TextView(this);
        authorName.setText("哈潚");
        authorName.setTextSize(15);
        authorName.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        authorName.setTextColor(dark ? 0xFF868686 : 0xFF9F9F9F);
        authorName.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        // 右侧「哈潚」可点击 → 作者主页；下方 HAGo 工作室图标不做点击
        authorName.setOnClickListener(v -> openInBrowser(AUTHOR_URL));
        right.addView(authorName, new LinearLayout.LayoutParams(dp(71), dp(22)));

        LinearLayout hagoBox = new LinearLayout(this);
        hagoBox.setGravity(Gravity.CENTER_VERTICAL);
        SmoothDrawable hg = new SmoothDrawable();
        hg.setColor(dark ? 0xFF242424 : 0xFFFFFFFF);
        hg.setCornerRadius(dp(6));
        hg.setStroke(dp(1), dark ? 0xFF3D3D3D : 0xFFF2F2F2);
        hagoBox.setBackground(hg);
        hagoBox.setPadding(dp(8), dp(8), dp(10), dp(9));
        ImageView hago = new ImageView(this);
        hago.setImageResource(dark ? R.drawable.ic_studio_dark : R.drawable.ic_studio);
        hagoBox.addView(hago, new LinearLayout.LayoutParams(dp(58), dp(10)));
        LinearLayout.LayoutParams hagoLp = new LinearLayout.LayoutParams(dp(76), dp(27));
        hagoLp.topMargin = dp(33);
        right.addView(hagoBox, hagoLp);
        author.addView(right, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT));

        LinearLayout.LayoutParams authorLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(117));
        authorLp.topMargin = dp(7);
        root.addView(author, authorLp);

        // ---- 「许可与鸣谢」组头 12sp #949FAB（上 7、左 17、高 27）----
        root.addView(roundGroup("许可与鸣谢", 7));

        // ---- 许可卡 378×175 圆角 22（上 7、内边距 18/35/19/16）----
        // 三行 17sp：第三方许可清单 / 第三方组件 / 前往 GitHub，右侧 15sp 值 + 12×6 箭头；
        // 行间距 33、35（Figma 209-60 的 autoWrapper8/9/10）。
        LinearLayout lic = new LinearLayout(this);
        lic.setOrientation(LinearLayout.VERTICAL);
        lic.setBackground(roundCardBg());
        lic.setPadding(dp(16), dp(18), dp(17), dp(19));

        LinearLayout licRow = roundLinkRow("第三方许可清单", "查看");
        licRow.setOnClickListener(v -> openSheet(ThirdParty.licenses(), "第三方许可清单"));
        lic.addView(licRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(22)));

        LinearLayout compRow = roundLinkRow("第三方组件", "查看");
        compRow.setOnClickListener(v -> openSheet(ThirdParty.components(), "第三方组件"));
        LinearLayout.LayoutParams compLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(22));
        compLp.topMargin = dp(33);
        lic.addView(compRow, compLp);

        LinearLayout githubRow = roundLinkRow("前往 GitHub", "访问");
        githubRow.setOnClickListener(v -> openInBrowser(REPO_URL));
        LinearLayout.LayoutParams githubLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(22));
        githubLp.topMargin = dp(35);
        lic.addView(githubRow, githubLp);

        LinearLayout.LayoutParams licLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        licLp.topMargin = dp(7);
        root.addView(lic, licLp);
    }

    /**
     * 滚动吸顶栏（Figma 212-72「关于软件 上滑后」）：
     * 高 168 的白色渐变（0 → 33.72% 实白 → 透明），左上 44 白色圆钮 + 左指箭头（投影 47px），
     * 中间 20sp 标题；随 scrollY 淡入，滚回顶部隐藏。
     * 渐变之下再压一层「内容缩略图」做渐变模糊，底边不会出现硬切痕；
     * 状态栏颜色同步过渡到吸顶栏顶部色，避免顶部出现两条不同颜色的带子。
     */
    private void addStickyHeader(ScrollView scroll, LinearLayout root) {
        final FrameLayout bar = new FrameLayout(this);
        final int solid = dark ? 0xFF000000 : 0xFFFFFFFF;
        final int clear = dark ? 0x00000000 : 0x00FFFFFF;

        final BarBlurView blur = new BarBlurView(this);
        blur.setAlpha(0f);
        bar.addView(blur, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        View gradient = new View(this) {
            private final Paint paint = new Paint();

            @Override
            protected void onDraw(Canvas c) {
                paint.setShader(new LinearGradient(0, 0, 0, getHeight(),
                        new int[]{solid, solid, clear},
                        new float[]{0f, 0.3372f, 1f}, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, getWidth(), getHeight(), paint);
            }
        };
        bar.addView(gradient, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        TextView title = new TextView(this);
        title.setText("关于软件");
        title.setTextSize(20);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setTextColor(cText);
        title.setGravity(Gravity.CENTER);
        // 标题与左侧返回圆钮按「文字盒中心 = 圆钮中心」对齐（与弹层 AboutSheet 同一基准：
        // 圆钮 44/12 → 中心 34，标题盒 34 高 → top 34 - 34/2 = 17）。
        // 两者都用固定 dp 盒 + Gravity.CENTER，字形在盒内垂直居中，系统字号放大时也不会跑偏。
        FrameLayout.LayoutParams titleLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(34));
        titleLp.topMargin = dp(17);
        bar.addView(title, titleLp);

        FrameLayout circle = new FrameLayout(this);
        // 返回圆钮阴影按客户 SVG 212-116：圆形四周均匀扩散、黑色 19%、模糊 23.35dp。
        // 阴影是 bar 的子 View，随吸顶栏 alpha 一起渐显——吸顶栏没触发时 bar 为 GONE，
        // 阴影也不会出现；触发后与背景模糊层同时淡入，不会有阴影突然满强度的硬切。
        bar.setClipChildren(false);
        CircleShadow shadow = new CircleShadow(this, 22f, 23.35f, 0.45f);
        FrameLayout.LayoutParams shadowLp = new FrameLayout.LayoutParams(dp(91), dp(91));
        shadowLp.leftMargin = dp(-15);
        shadowLp.topMargin = dp(-11);
        bar.addView(shadow, shadowLp);
        GradientDrawable oval = new GradientDrawable();
        oval.setShape(GradientDrawable.OVAL);
        oval.setColor(dark ? 0xFF242424 : 0xFFFFFFFF);
        circle.setBackground(oval);
        circle.setOnClickListener(v -> finish());
        ImageView icon = new ImageView(this);
        // 与页面顶部同一支无柄左指箭头（12×22 原始比例，盒 11×20 等比缩放）。
        icon.setImageResource(R.drawable.ic_back_chevron);
        icon.setColorFilter(dark ? 0xFF838383 : 0xFF5B5B5B);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(dp(11), dp(20));
        iconLp.gravity = Gravity.CENTER;
        circle.addView(icon, iconLp);
        FrameLayout.LayoutParams circleLp = new FrameLayout.LayoutParams(dp(44), dp(44));
        circleLp.leftMargin = dp(8);
        circleLp.topMargin = dp(12);
        bar.addView(circle, circleLp);

        bar.setAlpha(0f);
        bar.setVisibility(View.GONE);
        final int sbh = statusBarHeight();
        // 状态栏底垫：颜色与吸顶栏顶部一致，随吸顶栏同一 t 值淡入。它垫在吸顶栏下面，
        // 返回圆钮的阴影绘制在它之上，所以阴影上半部分可以完整显示到状态栏区域。
        final View statusFill = new View(this);
        statusFill.setBackgroundColor(solid);
        statusFill.setAlpha(0f);
        statusFill.setVisibility(View.GONE);
        pageRoot.addView(statusFill, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, sbh));
        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(168));
        barLp.topMargin = sbh;
        pageRoot.addView(bar, barLp);

        // 首次布局后拍一张 1/2 的内容快照（绘制时放大 2 倍 + GPU 高斯）；滚动时只平移，不再重绘。
        root.getViewTreeObserver().addOnPreDrawListener(
                new android.view.ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        root.getViewTreeObserver().removeOnPreDrawListener(this);
                        Bitmap bmp = shrink(root, BarBlurView.K);
                        if (bmp != null) blur.setSource(bmp, BarBlurView.K, 0);
                        return true;
                    }
                });

        scroll.setOnScrollChangeListener((v, sx, sy, oldX, oldY) -> {
            float t = (sy - dp(4)) / (float) dp(40);
            t = Math.max(0f, Math.min(1f, t));
            bar.setAlpha(t);
            bar.setVisibility(t > 0f ? View.VISIBLE : View.GONE);
            blur.setScroll(sy);
            blur.setAlpha(t);
            // 状态栏底垫与吸顶栏同步淡入，不再由窗口系统栏取色（状态栏保持完全透明）。
            statusFill.setAlpha(t);
            statusFill.setVisibility(t > 0f ? View.VISIBLE : View.GONE);
        });
    }

    /** 内容快照（1/K 比例），用于吸顶栏的 GPU 高斯渐变模糊。 */
    private Bitmap shrink(View v, float k) {
        try {
            int w = v.getWidth(), h = v.getHeight();
            if (w <= 0 || h <= 0) return null;
            Bitmap b = Bitmap.createBitmap(Math.max(1, (int) (w / k)),
                    Math.max(1, (int) (h / k)), Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            c.scale(1f / k, 1f / k);
            c.drawColor(cBg);
            v.draw(c);
            return b;
        } catch (Throwable e) {
            return null;
        }
    }

    /** 打开清单弹层：先截当前整窗作为「模糊背景」，再由 AboutSheet 做上浮 + 渐进模糊。 */
    private void openSheet(java.util.List<ThirdParty.Item> items, String title) {
        if (sheet == null) sheet = new AboutSheet(this, dark, UiStyle.ACCENT);
        final ScrollView ps = pageScroll;
        final int y = ps != null ? ps.getScrollY() : 0;
        // 弹层移除后窗口重新获得焦点，ScrollView 会滚到刚才点过的行（许可卡在最底部）。
        sheet.onClosed = () -> {
            if (ps != null) ps.scrollTo(0, y);
        };
        sheet.openList(items, title, windowSnapshot());
    }

    /**
     * 整窗背景快照（半分辨率，供弹层背景模糊）：先铺页面底色，再把 pageRoot 按窗口坐标
     * 画进画布，避免状态栏区域出现黑块。
     */
    private Bitmap windowSnapshot() {
        try {
            View decor = getWindow().getDecorView();
            int w = decor.getWidth();
            int h = decor.getHeight();
            if (w <= 0 || h <= 0) return null;
            float scale = 0.5f;
            Bitmap bmp = Bitmap.createBitmap((int) (w * scale), (int) (h * scale),
                    Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            c.scale(scale, scale);
            c.drawColor(cBg);
            if (pageRoot != null) {
                int[] loc = new int[2];
                pageRoot.getLocationInWindow(loc);
                c.save();
                c.translate(loc[0], loc[1]);
                pageRoot.draw(c);
                c.restore();
            }
            return bmp;
        } catch (Throwable e) {
            return null;
        }
    }

    @Override
    public void onBackPressed() {
        // 面板打开时先由面板处理：内部还有全文页就退一层，回到列表主页才关闭整块面板。
        if (sheet != null && sheet.isOpen() && sheet.handleBack()) return;
        super.onBackPressed();
    }

    /** 软件卡内一行：17sp 标题 + 71 宽右对齐 15sp 值 + 12×6 箭头（右缘贴内容右缘）。 */
    private LinearLayout roundLinkRow(String label, String value) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);

        TextView l = roundMediumText(label, 17);
        l.setGravity(Gravity.CENTER_VERTICAL);
        r.addView(l, new LinearLayout.LayoutParams(0, dp(22), 1f));

        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(15);
        v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        v.setTextColor(dark ? 0xFF868686 : 0xFF9F9F9F);
        v.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        r.addView(v, new LinearLayout.LayoutParams(dp(71), dp(22)));

        ImageView arrow = new ImageView(this);
        arrow.setImageResource(R.drawable.ic_chevron_12);
        arrow.setRotation(-90f);
        arrow.setColorFilter(roundArrowColor());
        LinearLayout.LayoutParams arrowLp = new LinearLayout.LayoutParams(dp(12), dp(6));
        arrowLp.leftMargin = dp(8);
        r.addView(arrow, arrowLp);
        return r;
    }

    /** 圆润卡片指向箭头颜色（设计稿实测：浅色 #B2B2B2、深色 #6D6D6D，比正文/值文字更弱）。 */
    private int roundArrowColor() {
        return dark ? 0xFF6D6D6D : 0xFFB2B2B2;
    }

    /** 圆润组头：12sp #949FAB、高 27、左缩进 17（无三角）。 */
    private TextView roundGroup(String s, int topMargin) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(12);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tv.setTextColor(0xFF949FAB);        // 深色设计稿组头同为 #949FAB
        tv.setGravity(Gravity.BOTTOM);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(27));
        lp.topMargin = dp(topMargin);
        lp.leftMargin = dp(17);
        tv.setLayoutParams(lp);
        return tv;
    }

    private TextView roundMediumText(String s, int size) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(size);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tv.setTextColor(cText);
        return tv;
    }

    private SmoothDrawable roundCardBg() {
        SmoothDrawable g = new SmoothDrawable();
        g.setColor(cCard);
        g.setCornerRadius(dp(22));
        return g;
    }

    /** 取安装包版本号（与首页「关于软件」行一致，避免写死）。 */
    private String appVersion() {
        try {
            String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            if (v != null && !v.isEmpty()) return v;
        } catch (Throwable ignored) {
        }
        return "0.0.1";
    }

    /** 加入 QQ 群：装了 QQ 就直接跳 QQ 内加群页，否则用系统浏览器打开群链接。 */
    private void openQqGroup() {
        if (isQqInstalled()) {
            // QQ 官方二维码加群深链：mqqopensdkapi://bizAgent/qm/qr?url=<编码后的群链接>
            String encoded = Uri.encode(QQ_GROUP_URL, "UTF-8");
            Intent deep = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("mqqopensdkapi://bizAgent/qm/qr?url=" + encoded));
            deep.setPackage(QQ_PACKAGE);
            if (tryStart(deep)) return;
            // 个别 QQ 版本不接自定义 scheme，只接管 http(s) 链接本身
            Intent https = new Intent(Intent.ACTION_VIEW, Uri.parse(QQ_GROUP_URL));
            https.setPackage(QQ_PACKAGE);
            if (tryStart(https)) return;
        }
        openInBrowser(QQ_GROUP_URL);
    }

    /** 系统浏览器打开链接（ACTION_VIEW + BROWSABLE，只命中浏览器类应用）。 */
    private void openInBrowser(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(i);
        } catch (Throwable e) {
            Toast.makeText(this, "无法打开浏览器", Toast.LENGTH_SHORT).show();
        }
    }

    /** QQ 是否已安装（Android 11+ 需要在 Manifest 声明 <queries> 才能查到）。 */
    private boolean isQqInstalled() {
        try {
            getPackageManager().getPackageInfo(QQ_PACKAGE, 0);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private boolean tryStart(Intent intent) {
        try {
            startActivity(intent);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}

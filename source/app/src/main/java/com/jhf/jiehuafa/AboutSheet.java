/*
 * 接化发 · 化学输入法
 * Copyright (C) 2026 哈潚
 *
 * 本程序是自由软件：你可以依据自由软件基金会发布的 GNU 通用公共许可证
 * （第 3 版，或你选择的任何更高版本）的条款重新分发和/或修改它。
 */
package com.jhf.jiehuafa;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 关于软件页的底部大弹层（Figma 212-121 顶部态 / 212-160 上滑后 / 209-144 / 209-185，2026-11-01）。
 *
 * 还原要点：
 *   遮罩 #00000080；面板 #F3F3F3、顶部圆角 40、显示在内容区下沿 40 处；
 *   吸顶栏高 171：把手 70×3 @10、标题 20sp 居中提示（34 高 @33）、
 *   48 白圆钮（✕，左 16、上 22，投影 20px #0000001A）；
 *   列表首卡顶部 81（标题底 67 + 14），卡片白底圆角 22、内边距 20/162/16/18，
 *   项目名 20sp、作者 17sp #B2B2B2、操作行 17sp #2E73F3。
 *
 * 交互：
 *   列表滚到吸顶栏下方时，吸顶栏从「纯渐变」过渡为「渐变 + 模糊」（BarBlurView），
 *   标题与 ✕ 固定不动；把手向下拖时整块面板跟手下移，超过阈值自动收起。
 *
 * 动效：出现与关闭都走 300ms / cubic-bezier(0.4, 0, 1, 1)，遮罩与背景模糊同步，
 *   二级页从右推入 320ms。出现动画在窗口首次绘制前就置到屏幕外，避免首帧闪一下再回弹。
 *
 * 修改日志：
 *   2026-11-03 吸顶栏标题 top 33 → 29，标题字形中心 46 与左侧 ✕ 圆钮中心对齐；
 *              弹层出现时状态栏/导航栏与遮罩同步压暗 50%（图标转白），
 *              关闭动画结束后还原打开前的系统栏颜色与图标明暗。
 *   2026-11-07 深色弹层配色（客户要求）：面板与卡片统一 #242424，吸顶栏渐变异步同色。
 */
final class AboutSheet {

    private static final int HEAD_H_DP = 171;
    private static final int LIST_TOP_DP = 81;
    private static final int DOC_TOP_DP = 117;
    private static final int BLUR_DP = 14;
    private static final long MS = 380;
    private static final long PUSH_MS = 320;

    private final Activity act;
    private final FrameLayout decor;
    private final boolean dark;
    private final int accent;

    private int cCard, cText, cSub, cPanel, cHandle, cCircle;
    private int radiusPx;

    private FrameLayout scrim;
    private View dim;
    private ImageView blurView;
    private SmoothDrawable panelBg;
    private ImageView closeIcon;
    /** 半屏时的面板顶部偏移（屏高 25%，即露出约 75%）。 */
    private int halfTop;
    /** 向上拖到露出约 80% 时的面板顶部（屏高 20%），越过即吸附为完整页面。 */
    private int expandTop;
    /** 面板顶部覆盖状态栏时，铺在面板顶边的色块（让吸顶栏白底连续延伸到状态栏下方）。 */
    private View statusFill;
    /** 吸顶栏渐显进度（0=未滚动、顶部仍是面板色，1=吸顶栏完全显出）。状态栏底色随之过渡。 */
    private float barTint;
    /** 已应用到窗口的状态栏底色与图标明暗（只在变化时调用 setStatusBarColor，避免逐帧抖动）。 */
    private int statusAppliedColor;
    private boolean statusAppliedLight;
    /** 状态栏高度：面板吸附到顶部后内容整体下移，避免与状态栏图标重叠。 */
    private int statusBarPx;
    /** 面板导航状态（页面栈深度 + 是否吸附成全屏）：关闭键图标与点击动作都由它推导。 */
    private final SheetNav nav = new SheetNav();
    /** 面板内页面栈（栈底为列表主页），与 nav 的深度保持同步。 */
    private final List<View> pageStack = new ArrayList<>();
    /** 各层页面对应的吸顶栏关闭图标，与 pageStack 同序；返回上一层后要切回下一层的图标。 */
    private final List<ImageView> pageIcons = new ArrayList<>();
    /** head() 刚创建的关闭图标，页面登记进栈时取走。 */
    private ImageView lastHeadIcon;
    /** 全文页推入 / 推出动画进行中：期间忽略导航操作，避免层级与图标错位。 */
    private boolean pageAnimating;
    private FrameLayout pages;
    private FrameLayout panel;
    private ValueAnimator anim;
    private boolean open;
    private boolean closing;
    /** 弹层打开前的系统栏状态（2026-11-03：弹层压暗时系统栏同步压暗，关闭后还原）。 */
    private int savedStatusColor, savedNavColor;
    private int savedUiFlags = -1;
    /** 关闭动画结束后的回调（AboutActivity 用它把页面滚动位置复原）。 */
    Runnable onClosed;

    AboutSheet(Activity activity, boolean dark, int accent) {
        this.act = activity;
        this.dark = dark;
        this.accent = accent;
        this.decor = (FrameLayout) activity.getWindow().getDecorView();
        if (dark) {
            // 2026-11-07 客户要求：弹窗背景与卡片统一 #242424（页面底色改纯黑）
            cPanel = 0xFF242424;
            cCard = 0xFF242424;
            cText = 0xFFFFFFFF;
            cSub = 0xFF8C8C8C;
            cCircle = 0xFF242424;
            cHandle = 0xFF4D4D4D;
        } else {
            cPanel = 0xFFF3F3F3;
            cCard = 0xFFFFFFFF;
            cText = 0xFF000000;
            cSub = 0xFFB2B2B2;
            cCircle = 0xFFFFFFFF;
            cHandle = 0xFFD9D9D9;
        }
        radiusPx = dp(BLUR_DP);
    }

    boolean isOpen() {
        return open;
    }

    /**
     * 打开清单弹层。snapshot 为打开瞬间的整窗背景快照（由 AboutActivity 截取），
     * 用于「背景逐渐模糊」；传 null 则只做遮罩渐变。
     */
    void openList(List<ThirdParty.Item> items, String title, Bitmap snapshot) {
        if (open) return;
        open = true;
        saveAndDimSystemBars();

        scrim = new FrameLayout(act);
        scrim.setBackgroundColor(0x00000000);
        scrim.setClickable(true);
        scrim.setOnClickListener(v -> dismiss());
        scrim.addView(makeBlur(snapshot), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        dim = new View(act);
        dim.setBackgroundColor(0xFF000000);
        dim.setAlpha(0f);
        scrim.addView(dim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        panel = new FrameLayout(act);
        SmoothDrawable bg = new SmoothDrawable();
        bg.setColor(cPanel);
        bg.setCornerRadii(new float[]{dp(40), dp(40), dp(40), dp(40), 0, 0, 0, 0});
        panelBg = bg;
        panel.setBackground(bg);
        panel.setClipToOutline(true);
        panel.setClickable(true);
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        // 半屏态：面板露出约 75%（顶部落在屏高 25%），上方四分之一显示被模糊的底层页面。
        int winH = decor.getHeight() > 0 ? decor.getHeight()
                : act.getResources().getDisplayMetrics().heightPixels;
        halfTop = (int) (winH * 0.25f);
        expandTop = (int) (winH * 0.20f);
        statusBarPx = statusBarHeight();
        nav.reset();
        pageStack.clear();
        pageIcons.clear();
        lastHeadIcon = null;
        pageAnimating = false;
        barTint = 0f;
        statusAppliedColor = 0;
        statusAppliedLight = false;
        closeIcon = null;
        pl.topMargin = halfTop;
        // 底部多留 32dp 到屏外：出现动画末端有 2% 回弹，面板往上冲时不会在底部露出缝。
        pl.bottomMargin = -dp(32);
        // 加进窗口前就放到屏幕外：否则首帧会在终点画一次再跳回底部，看起来像「卡一下」。
        panel.setTranslationY(dp(1000));
        scrim.addView(panel, pl);

        // 预置一次极小的模糊，让 RenderEffect 的图层在动画开始前就建好，避免第一帧掉帧。
        if (blurView != null && Build.VERSION.SDK_INT >= 31) {
            blurView.setRenderEffect(RenderEffect.createBlurEffect(0.5f, 0.5f, Shader.TileMode.CLAMP));
        }

        pages = new FrameLayout(act);
        panel.addView(pages, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View listPage = buildList(items, title);
        pages.addView(listPage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        registerPage(listPage);

        // 完整页面时面板内容整体下移让出状态栏，状态栏下方会留下一条面板色顶边；
        // 用一个同高色块把这条填上（叠在内容之上），滚动时与吸顶栏一起渐变到同色，
        // 顶部才是一条连续的白（深色为 #242424）而不是「白状态栏 + 灰窄条 + 白吸顶栏」。
        statusFill = new View(act);
        statusFill.setBackgroundColor(cPanel);
        statusFill.setVisibility(View.GONE);
        panel.addView(statusFill, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(0, statusBarPx)));

        decor.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        panel.post(this::animateIn);
    }

    void dismiss() {
        if (!open || closing) return;
        open = false;
        closing = true;
        if (anim != null) anim.cancel();
        int h = panel.getHeight() > 0 ? panel.getHeight() : panel.getMeasuredHeight();
        final float startY = panel.getTranslationY();
        final float startDim = dim.getAlpha();
        final FrameLayout dying = scrim;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(MS);
        anim.setInterpolator(new Spring(0.999f, 13f)); // 临界阻尼：只减速、不回弹
        anim.addUpdateListener(a -> {
            float v = (float) a.getAnimatedValue();
            panel.setTranslationY(startY + (h - startY) * v);
            dim.setAlpha(startDim * (1 - v));
            setBlur(radiusPx * (startDim / 0.5f) * (1 - v));
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                closing = false;
                try { decor.removeView(dying); } catch (Throwable ignored) { }
                scrim = null; panel = null; pages = null; blurView = null; dim = null;
                statusFill = null;
                closeIcon = null;
                lastHeadIcon = null;
                pageStack.clear();
                pageIcons.clear();
                nav.reset();
                pageAnimating = false;
                barTint = 0f;
                statusAppliedColor = 0;
                statusAppliedLight = false;
                restoreSystemBars();
                if (onClosed != null) onClosed.run();
            }
        });
        anim.start();
    }

    /** 出现动画：380ms 弹簧（阻尼 0.75），末端 2% 轻微回弹，与关闭动画同一套手感。 */
    private void animateIn() {
        if (!open || panel == null) return;
        int h = panel.getHeight();
        if (h <= 0) h = panel.getMeasuredHeight();
        if (h <= 0) h = act.getResources().getDisplayMetrics().heightPixels;
        panel.setTranslationY(h);
        final float from = h;
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(MS);
        anim.setInterpolator(new Spring(0.75f, 12f));
        anim.addUpdateListener(a -> {
            float v = (float) a.getAnimatedValue();
            panel.setTranslationY(from * (1 - v));
            dim.setAlpha(0.5f * v);
            setBlur(radiusPx * v);
        });
        anim.start();
    }

    private ImageView makeBlur(Bitmap snapshot) {
        blurView = new ImageView(act);
        blurView.setScaleType(ImageView.ScaleType.FIT_XY);
        if (snapshot != null) {
            blurView.setImageBitmap(snapshot);
        } else {
            blurView.setBackgroundColor(cPanel);
        }
        return blurView;
    }

    private void setBlur(float px) {
        if (blurView == null || Build.VERSION.SDK_INT < 31) return;
        blurView.setRenderEffect(px <= 0.4f ? null
                : RenderEffect.createBlurEffect(px, px, Shader.TileMode.CLAMP));
    }

    /** 记下原系统栏状态，并把状态栏/导航栏压到与遮罩同一档（50% 黑）。 */
    private void saveAndDimSystemBars() {
        try {
            android.view.Window w = act.getWindow();
            savedStatusColor = w.getStatusBarColor();
            savedNavColor = w.getNavigationBarColor();
            savedUiFlags = decor.getSystemUiVisibility();
            int dim = blendColor(cPanel, 0xFF000000, 0.5f);
            w.setStatusBarColor(dim);
            w.setNavigationBarColor(dim);
            if (Build.VERSION.SDK_INT >= 28) w.setNavigationBarDividerColor(dim);
            int flags = savedUiFlags;
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            decor.setSystemUiVisibility(flags);
        } catch (Throwable ignored) {
        }
    }

    /** 关闭弹层后还原原系统栏颜色与图标明暗。 */
    private void restoreSystemBars() {
        if (savedUiFlags < 0) return;
        try {
            android.view.Window w = act.getWindow();
            w.setStatusBarColor(savedStatusColor);
            w.setNavigationBarColor(savedNavColor);
            if (Build.VERSION.SDK_INT >= 28) w.setNavigationBarDividerColor(savedNavColor);
            decor.setSystemUiVisibility(savedUiFlags);
        } catch (Throwable ignored) {
        }
    }

    /** 两个不透明颜色按 k (0..1) 混合。 */
    private static int blendColor(int from, int to, float k) {
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * k);
        int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * k);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * k);
        return (0xFF << 24) | ((int) r << 16) | ((int) g << 8) | (int) b;
    }

    // ---------------------------------------------------------------- 页面

    private View buildList(List<ThirdParty.Item> items, String titleText) {
        FrameLayout page = new FrameLayout(act);
        page.setBackgroundColor(cPanel);
        page.setClickable(true);

        ScrollView sv = new ScrollView(act);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipToPadding(false);
        // 首卡顶部 81（标题底 67 + 14）：把 81 做成滚动区上内边距，上滑时内容从吸顶栏下面穿过。
        sv.setPadding(0, dp(LIST_TOP_DP), 0, 0);

        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), 0, dp(12), dp(120));
        sv.addView(col, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(sv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        for (int i = 0; i < items.size(); i++) {
            LinearLayout card = card(items.get(i));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(i == 0 ? 0 : 14);
            col.addView(card, lp);
        }

        page.addView(head(titleText, col, dp(LIST_TOP_DP), sv),
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(HEAD_H_DP)));
        return page;
    }

    private LinearLayout card(ThirdParty.Item it) {
        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        SmoothDrawable bg = new SmoothDrawable();
        bg.setColor(cCard);
        bg.setCornerRadius(dp(22));
        card.setBackground(bg);
        card.setPadding(dp(18), dp(20), dp(18), dp(16));

        TextView name = medium(it.title, 20);
        name.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(22)));

        TextView authors = medium(it.authors, 17);
        authors.setTextColor(cSub);
        authors.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams authorLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(22));
        authorLp.topMargin = dp(6);
        card.addView(authors, authorLp);

        LinearLayout links = new LinearLayout(act);
        links.setOrientation(LinearLayout.HORIZONTAL);
        links.setGravity(Gravity.CENTER_VERTICAL);

        TextView action = medium(it.action, 17);
        action.setTextColor(accent);
        action.setGravity(Gravity.CENTER_VERTICAL);
        action.setOnClickListener(v -> openDoc(it));
        links.addView(action, new LinearLayout.LayoutParams(0, dp(22), 1f));

        // 许可卡（212-121）带「查看项目地址」，组件卡（209-144）只有「查看文档」。
        if (!it.url.isEmpty() && it.asset.startsWith("licenses/")) {
            TextView url = medium("查看项目地址", 17);
            url.setTextColor(accent);
            url.setGravity(Gravity.CENTER_VERTICAL);
            url.setPadding(dp(10), 0, 0, 0);
            url.setOnClickListener(v -> openUrl(it.url));
            links.addView(url, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(22)));
        }

        ImageView chevron = new ImageView(act);
        chevron.setImageResource(R.drawable.ic_chevron_12);
        chevron.setRotation(-90f);
        chevron.setColorFilter(cSub);
        LinearLayout.LayoutParams chevronLp = new LinearLayout.LayoutParams(dp(12), dp(6));
        chevronLp.leftMargin = dp(8);
        links.addView(chevron, chevronLp);

        LinearLayout.LayoutParams linksLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(22));
        linksLp.topMargin = dp(9);
        card.addView(links, linksLp);
        return card;
    }

    private void openDoc(ThirdParty.Item it) {
        if (pages == null || pageAnimating) return;
        String body = readAsset(it.asset);
        boolean isLicense = it.asset.startsWith("licenses/");
        FrameLayout doc = buildDoc(isLicense ? "许可证全文" : "组件文档", body);
        pages.addView(doc, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // 入栈：层级 +1，关闭键立刻切成箭头，点击与系统返回都只退这一层。
        nav.push();
        registerPage(doc);
        pageAnimating = true;
        doc.setTranslationX(pages.getWidth() > 0 ? pages.getWidth() : act.getResources()
                .getDisplayMetrics().widthPixels);
        doc.animate()
                .translationX(0f)
                .setDuration(PUSH_MS)
                .setInterpolator(new PathInterpolator(0.32f, 0.72f, 0f, 1f))
                .withEndAction(() -> pageAnimating = false)
                .start();
    }

    /** 页面登记进面板导航栈，并把关闭键指向这一层自己的图标。 */
    private void registerPage(View page) {
        pageStack.add(page);
        pageIcons.add(lastHeadIcon);
        lastHeadIcon = null;
        closeIcon = pageIcons.get(pageIcons.size() - 1);
        applyCloseIcon();
    }

    /**
     * 面板内返回上一层（如许可证全文 → 列表）。栈底主页不再退页；
     * 退页动画期间忽略后续导航操作，避免层级、图标与页面动画错位。
     */
    private void popPage() {
        if (pages == null || pageAnimating || pageStack.size() <= 1) return;
        if (!nav.pop()) return;
        final View top = pageStack.remove(pageStack.size() - 1);
        pageIcons.remove(pageIcons.size() - 1);
        closeIcon = pageIcons.get(pageIcons.size() - 1);
        applyCloseIcon();
        pageAnimating = true;
        top.animate()
                .translationX(pages.getWidth() > 0 ? pages.getWidth() : act.getResources()
                        .getDisplayMetrics().widthPixels)
                .setDuration(PUSH_MS)
                .setInterpolator(new PathInterpolator(0.36f, 0f, 0.66f, -0.05f))
                .withEndAction(() -> {
                    pageAnimating = false;
                    if (pages != null) pages.removeView(top);
                })
                .start();
    }

    /** 关闭键点击：非主页先退页；主页时全屏收回半屏、半屏关闭弹层。 */
    private void onCloseButton() {
        if (pageAnimating) return;
        switch (nav.closeButtonAction()) {
            case SheetNav.ACT_POP:
                popPage();
                break;
            case SheetNav.ACT_COLLAPSE:
                collapse();
                break;
            default:
                dismiss();
                break;
        }
    }

    /**
     * 系统返回键：面板内还有页面就先退一层，否则关闭整块面板。
     * 返回 true 表示这次返回由面板消费（动画期间同样消费，防止状态被连续打断）。
     */
    boolean handleBack() {
        if (!open || closing) return true;
        if (pageAnimating) return true;
        if (nav.backAction() == SheetNav.ACT_POP) {
            popPage();
            return true;
        }
        dismiss();
        return true;
    }

    private FrameLayout buildDoc(String titleText, String body) {
        FrameLayout page = new FrameLayout(act);
        page.setBackgroundColor(cPanel);
        page.setClickable(true);

        ScrollView sv = new ScrollView(act);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipToPadding(false);
        // 文档卡顶部 117（标题底 70 + 47），上滑时正文从吸顶栏下面穿过。
        sv.setPadding(0, dp(DOC_TOP_DP), 0, 0);

        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), 0, dp(12), dp(120));
        sv.addView(col, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(sv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout card = new LinearLayout(act);
        SmoothDrawable bg = new SmoothDrawable();
        bg.setColor(cCard);
        bg.setCornerRadius(dp(22));
        card.setBackground(bg);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        TextView text = medium(body, 17);
        text.setTextColor(cText);
        text.setLineSpacing(0f, 1.45f);
        card.addView(text, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        page.addView(head(titleText, col, dp(DOC_TOP_DP), sv),
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(HEAD_H_DP)));
        return page;
    }

    // ---------------------------------------------------------------- 吸顶栏

    /**
     * 吸顶栏（Figma 212-160 rectangle543）：高 171、顶部圆角 40、
     * 白色渐变到 17.73% 后淡出，底下压一层随滚动移动的模糊切片；
     * 把手 70×3 @10、标题 20sp @33、48 圆钮（✕）@ (16, 22)。
     */
    private View head(String titleText, View content, int originPx, ScrollView sv) {
        FrameLayout head = new FrameLayout(act);
        head.setClipChildren(true);

        final BarBlurView blur = new BarBlurView(act);
        blur.setVisibility(View.GONE);
        head.addView(blur, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        final View shade = new View(act) {
            private final Paint p = new Paint();

            @Override
            protected void onDraw(Canvas c) {
                int solid = dark ? 0xFF242424 : 0xFFFFFFFF;
                int clear = dark ? 0x00242424 : 0x00FFFFFF;
                p.setShader(new LinearGradient(0, 0, 0, getHeight(),
                        new int[]{solid, solid, clear},
                        new float[]{0f, 0.1773f, 1f}, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, getWidth(), getHeight(), p);
            }
        };
        shade.setVisibility(View.GONE);
        head.addView(shade, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // 把手视觉只有 3dp，给一个 100×26 的透明热区（顶 4dp），手指好按住往下拖。
        FrameLayout grip = new FrameLayout(act);
        View handle = handle();
        FrameLayout.LayoutParams handleLp = new FrameLayout.LayoutParams(dp(70), dp(3));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.topMargin = dp(6);
        grip.addView(handle, handleLp);
        FrameLayout.LayoutParams gripLp = new FrameLayout.LayoutParams(dp(100), dp(26));
        gripLp.gravity = Gravity.CENTER_HORIZONTAL;
        gripLp.topMargin = dp(4);
        head.addView(grip, gripLp);

        TextView title = medium(titleText, 20);
        title.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams titleLp = new FrameLayout.LayoutParams(dp(287), dp(34));
        titleLp.gravity = Gravity.CENTER_HORIZONTAL;
        titleLp.topMargin = dp(29);         // 与左侧 ✕ 圆钮中心对齐（钮 22+48/2=46，标题 29+34/2=46）
        head.addView(title, titleLp);

        FrameLayout circle = new FrameLayout(act);
        // 关闭圆钮的阴影按客户 SVG 212-116：圆形四周均匀扩散、黑色 19%、模糊 23.35dp
        // （旧 elevation 阴影有方向性，且不跟随父布局 alpha），改为垫一层自绘径向阴影；
        // 阴影盒 = 2×(24 + 23.35)，中心与 48dp 圆钮同心。
        // 阴影强度取 0.45（与关于软件页吸顶栏返回圆钮同一档）：圆心约 8.6% 黑、圆边约 4.6% 黑，
        // 比 SVG 原值 19% 更柔和，不压住弹层头部文字。
        CircleShadow shadow = new CircleShadow(act, 24f, 23.35f, 0.45f);
        // 未滚动（没触发吸顶栏）时阴影完全隐藏，滚动后随吸顶进度 t 渐显，不出现硬切。
        shadow.setAlpha(0f);
        FrameLayout.LayoutParams shadowLp = new FrameLayout.LayoutParams(dp(94.7f), dp(94.7f));
        shadowLp.leftMargin = dp(-7.35f);
        shadowLp.topMargin = dp(-1.35f);
        head.addView(shadow, shadowLp);
        GradientDrawable oval = new GradientDrawable();
        oval.setShape(GradientDrawable.OVAL);
        oval.setColor(cCircle);
        circle.setBackground(oval);
        // 圆钮：非主页层级先退页（与系统返回键一致）；主页时全屏收回半屏、半屏关闭弹层。
        circle.setOnClickListener(v -> onCloseButton());
        ImageView icon = new ImageView(act);
        // 每页各有一个图标，先给 ✕，页面登记进栈后由 applyCloseIcon() 按层级统一修正。
        icon.setImageResource(R.drawable.ic_close_16);
        icon.setColorFilter(dark ? 0xFFBFBFBF : 0xFF4D4D4D);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(dp(16), dp(16));
        iconLp.gravity = Gravity.CENTER;
        circle.addView(icon, iconLp);
        lastHeadIcon = icon;
        FrameLayout.LayoutParams circleLp = new FrameLayout.LayoutParams(dp(48), dp(48));
        circleLp.leftMargin = dp(16);
        circleLp.topMargin = dp(22);
        head.addView(circle, circleLp);

        // 滚动：吸顶栏的渐变 + 模糊随 scrollY 淡入；标题与 ✕ 始终不动，关闭圆钮的阴影
        // 只在这个阶段出现（未触发吸顶栏时 alpha 为 0，完全隐藏）。
        sv.setOnScrollChangeListener((v, sx, sy, ox, oy) -> {
            float t = Math.min(1f, Math.max(0f, (sy - dp(4)) / (float) dp(40)));
            // 吸顶栏顶部的实色是 #FFFFFF（深色 #242424）；面板顶边色块与状态栏底色跟着同一个进度过渡。
            barTint = t;
            int barSolid = dark ? 0xFF242424 : 0xFFFFFFFF;
            if (statusFill != null) statusFill.setBackgroundColor(blendColor(cPanel, barSolid, t));
            syncSheetStatusBar();
            if (t <= 0f) {
                shade.setVisibility(View.GONE);
                blur.setVisibility(View.GONE);
                shadow.setAlpha(0f);
                return;
            }
            shade.setVisibility(View.VISIBLE);
            blur.setVisibility(View.VISIBLE);
            shade.setAlpha(t);
            blur.setAlpha(t);
            shadow.setAlpha(t);
            blur.setScroll(sy);
        });

        // 滚动开始前先给内容拍一张缩小图，滚动时只切图不重绘。
        content.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                content.getViewTreeObserver().removeOnPreDrawListener(this);
                Bitmap bmp = shrink(content, BarBlurView.K);
                if (bmp != null) blur.setSource(bmp, BarBlurView.K, originPx);
                return true;
            }
        });

        attachDrag(grip);
        attachDrag(title);
        return head;
    }

    /** 内容按 k 倍缩小的位图（供吸顶栏模糊），只拍一次。 */
    private Bitmap shrink(View v, float k) {
        try {
            int w = v.getWidth(), h = v.getHeight();
            if (w <= 0 || h <= 0) return null;
            int bw = Math.max(1, (int) (w / k));
            int bh = Math.max(1, (int) (h / k));
            Bitmap b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            c.scale(1f / k, 1f / k);
            c.drawColor(cPanel);
            v.draw(c);
            return b;
        } catch (Throwable e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- 拖拽

    /**
     * 把手 / 标题拖拽（2026-11-08）：
     * 向上拖：面板顶部偏移减小、露出更多，拖到屏高 80% 左右（top ≤ 半屏位置的一半）松手
     * 自动吸附到屏幕顶部成为完整页面，关闭钮同时由 ✕ 变为返回箭头；
     * 向下拖：整块面板跟手下移，半屏态超过阈值收起，全屏态超过阈值收回半屏。
     */
    private void attachDrag(View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
            VelocityTracker vt;
            float downRaw;
            int startTop;
            boolean active;

            @Override
            public boolean onTouch(View view, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRaw = e.getRawY();
                        startTop = topMargin();
                        active = true;
                        if (anim != null && !closing) anim.cancel();
                        if (vt == null) vt = VelocityTracker.obtain();
                        vt.clear();
                        vt.addMovement(e);
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        if (!active) return false;
                        vt.addMovement(e);
                        int top = startTop + (int) (e.getRawY() - downRaw);
                        if (nav.isExpanded()) {
                            // 全屏态：只回退顶部偏移，绝不整块平移（否则会把已覆盖的状态栏一起拖下来）。
                            // 面板顶部下落时圆角按位置恢复，内容让位与状态栏同步归位。
                            panel.setTranslationY(0f);
                            setPanelTop(Math.min(top, halfTop));
                            syncShellChrome();
                            dim.setAlpha(0.5f);
                            setBlur(radiusPx);
                        } else if (top <= startTop) {
                            // 净上移：面板顶部上移（露出更多），不做整体平移。
                            panel.setTranslationY(0f);
                            setPanelTop(top);
                            dim.setAlpha(0.5f);
                            setBlur(radiusPx);
                        } else {
                            // 净下移：顶部停在半屏位，面板整块跟手下移，遮罩与模糊同步变淡。
                            // （用净位移判断，先上拖再回拉时不会瞬移回原位）
                            setPanelTop(startTop);
                            float out = top - startTop;
                            panel.setTranslationY(out);
                            int ph = panel.getHeight();
                            float k = ph > 0 ? 1f - Math.min(1f, out / ph) : 1f;
                            dim.setAlpha(0.5f * k);
                            setBlur(radiusPx * k);
                        }
                        syncSheetStatusBar();
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL: {
                        if (!active) return false;
                        active = false;
                        vt.addMovement(e);
                        vt.computeCurrentVelocity(1000);
                        float vy = vt.getYVelocity();
                        // 全屏态向下拖走的是顶部偏移（translationY 恒为 0），两种手势统一折算成位移。
                        float dy = panel.getTranslationY() > 0f
                                ? panel.getTranslationY() : (topMargin() - startTop);
                        int top = topMargin();
                        if (dy > 0f) {
                            if (nav.isExpanded()) {
                                if (dy > dp(110) || (dy > dp(24) && vy > dp(900))) collapse();
                                else settleShell(0);
                            } else {
                                if (dy > dp(110) || (dy > dp(24) && vy > dp(900))) dismiss();
                                else settleShell(halfTop);
                            }
                            return true;
                        }
                        if (nav.isExpanded()) {
                            settleShell(0);
                        } else if (top <= expandTop || vy < -dp(900)) {
                            // 露出达到 80% 左右（或快速上甩）：吸附到顶部。
                            expand();
                        } else {
                            settleShell(halfTop);
                        }
                        return true;
                    }
                    default:
                        return false;
                }
            }
        });
    }

    /** 吸附到屏幕顶部，变成完整页面（顶部圆角收起、内容让出状态栏、关闭钮变返回箭头）。 */
    private void expand() {
        if (nav.isExpanded() || panel == null) return;
        nav.setExpanded(true);
        animateShell(true);
    }

    /** 从完整页面收回半屏（恢复圆角、内容位置；非主页层级仍保持返回箭头）。 */
    private void collapse() {
        if (!nav.isExpanded() || panel == null) return;
        nav.setExpanded(false);
        animateShell(false);
    }

    /** 展开 / 收回动画：面板顶部偏移、顶部圆角、内容上沿与图标一起过渡。 */
    private void animateShell(final boolean full) {
        if (panel == null || pages == null) return;
        final int fromTop = topMargin();
        final int targetTop = full ? 0 : halfTop;
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(320);
        anim.setInterpolator(new Spring(0.75f, 12f));
        anim.addUpdateListener(a -> {
            float v = (float) a.getAnimatedValue();
            setPanelTop(fromTop + (int) ((targetTop - fromTop) * v));
            panel.setTranslationY(0f);
            // 圆角与内容让位统一由顶部偏移驱动，和跟手拖动用同一套映射，避免中途曲线跳变。
            syncShellChrome();
            dim.setAlpha(0.5f);
            setBlur(radiusPx);
            syncSheetStatusBar();
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                setPanelTop(targetTop);
                syncShellChrome();
                applyCloseIcon();
                syncSheetStatusBar();
            }
        });
        anim.start();
    }

    /** 把面板顶部偏移与下移量平滑回到目标值（未达吸附阈值的回弹也走这里）。 */
    private void settleShell(int targetTop) {
        if (panel == null) return;
        final int fromTop = topMargin();
        final float fromY = panel.getTranslationY();
        final float fromDim = Math.max(0f, dim.getAlpha());
        if (fromTop == targetTop && fromY == 0f) {
            dim.setAlpha(0.5f);
            setBlur(radiusPx);
            syncShellChrome();
            syncSheetStatusBar();
            return;
        }
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(320);
        anim.setInterpolator(new Spring(0.75f, 12f));
        anim.addUpdateListener(a -> {
            float v = (float) a.getAnimatedValue();
            setPanelTop(fromTop + (int) ((targetTop - fromTop) * v));
            panel.setTranslationY(fromY * (1 - v));
            dim.setAlpha(fromDim + (0.5f - fromDim) * v);
            setBlur(radiusPx * Math.min(1f, fromDim / 0.5f + (1f - fromDim / 0.5f) * v));
            syncShellChrome();
            syncSheetStatusBar();
        });
        anim.start();
    }

    /** 按当前顶部偏移同步顶部圆角与内容让位：顶部 0 = 直角 + 让出状态栏，半屏 = 40dp 圆角 + 内容归位。 */
    private void syncShellChrome() {
        float p = halfTop > 0 ? Math.min(1f, topMargin() / (float) halfTop) : 0f;
        // 圆角用三次缓出：刚离开顶部就快速给出可感知的圆角，避免全屏向下拖动初段看起来仍是直角。
        float q = 1f - (float) Math.pow(1f - p, 3);
        setPanelRadius(dp(40) * q);
        float pad = statusBarPx * (1f - p);
        if (pages != null) pages.setTranslationY(pad);
        if (statusFill != null) {
            // 顶边色块的高度跟着内容让位收缩：内容上移到哪里，色块就只铺到哪里，不会盖住列表。
            int h = Math.max(0, Math.round(pad));
            FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) statusFill.getLayoutParams();
            if (flp != null && flp.height != h) {
                flp.height = h;
                statusFill.setLayoutParams(flp);
            }
            statusFill.setVisibility(h > 0 ? View.VISIBLE : View.GONE);
        }
    }

    private int topMargin() {
        if (panel == null) return 0;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) panel.getLayoutParams();
        return lp == null ? 0 : lp.topMargin;
    }

    private void setPanelTop(int top) {
        if (panel == null) return;
        top = Math.max(0, top);   // 弹簧回弹不越过屏幕顶部
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) panel.getLayoutParams();
        if (lp == null || lp.topMargin == top) return;
        lp.topMargin = top;
        panel.setLayoutParams(lp);
    }

    /** 顶部两角圆角（px）：半屏 40dp，完整页面为直角。 */
    private void setPanelRadius(float px) {
        if (panelBg == null) return;
        panelBg.setCornerRadii(new float[]{px, px, px, px, 0f, 0f, 0f, 0f});
        panelBg.invalidateSelf();
        // clipToOutline 用的是 View 缓存的 outline，圆角变化后必须让它重建，否则内容仍按旧轮廓裁剪。
        if (panel != null) panel.invalidateOutline();
    }

    /**
     * 关闭键图标与当前层级同步：非主页层级一律是左指箭头，主页仅在吸附成全屏时是箭头，
     * 收回半屏并回到主页后自动切回 ✕。
     */
    private void applyCloseIcon() {
        if (closeIcon == null) return;
        closeIcon.setImageResource(nav.closeGlyphIsArrow()
                ? R.drawable.ic_back_chevron : R.drawable.ic_close_16);
    }

    /**
     * 状态栏实时自适应（拖动、展开动画与列表滚动时调用）：
     * 面板顶部覆盖大半状态栏时，状态栏底色跟随吸顶栏渐显进度从面板色过渡到吸顶栏实色
     * （浅色 #FFFFFF、深色 #242424），图标按面板深浅反色——浅色用深色图标、深色用白色图标；
     * 否则状态栏在遮罩下，保持压暗底色 + 白色图标。
     * 切换点取状态栏高度的一半：面板从全屏向下拖出时，状态栏先露出底部一半背景，
     * 此时立刻换成压暗色，顶部圆角缺口才不会被同色状态栏填平（看起来像没有圆角）。
     */
    private void syncSheetStatusBar() {
        if (panel == null) return;
        boolean covered = topMargin() <= statusBarPx * 0.5f;
        int barColor;
        boolean lightIcons;
        if (covered) {
            int solid = dark ? 0xFF242424 : 0xFFFFFFFF;
            barColor = barTint > 0f ? blendColor(cPanel, solid, barTint) : cPanel;
            lightIcons = !dark;
        } else {
            barColor = blendColor(cPanel, 0xFF000000, 0.5f);
            lightIcons = false;
        }
        // 只在颜色/图标真正变化时才调窗口方法：滚动渐变只有十几级色阶，不会逐帧触发重绘。
        if (barColor == statusAppliedColor && lightIcons == statusAppliedLight) return;
        statusAppliedColor = barColor;
        statusAppliedLight = lightIcons;
        try {
            android.view.Window w = act.getWindow();
            int flags = decor.getSystemUiVisibility();
            if (lightIcons) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            w.setStatusBarColor(barColor);
            decor.setSystemUiVisibility(flags);
        } catch (Throwable ignored) {
        }
    }

    // ---------------------------------------------------------------- 组件

    /**
     * iOS 弹簧曲线（阻尼谐振子）：x(t) = 1 - e^(-ζωt) · [cos(ω_d t) + (ζω/ω_d) sin(ω_d t)]。
     * 起点/终点速度为 0，中间先快后慢，阻尼比略小于 1 时末端有 2% 左右的回弹，
     * 比三次贝塞尔「一上来就匀速冲」更接近 iOS 弹层的体感。
     * ζ→1 时退化为临界阻尼（单调减速、不回弹），适合收起方向。
     */
    private static final class Spring implements android.view.animation.Interpolator {
        private final float zeta, omega, wd;

        Spring(float zeta, float omega) {
            this.zeta = zeta;
            this.omega = omega;
            this.wd = omega * (float) Math.sqrt(1f - zeta * zeta);
        }

        @Override public float getInterpolation(float t) {
            if (t <= 0f) return 0f;
            if (t >= 1f) return 1f;
            return 1f - (float) (Math.exp(-zeta * omega * t)
                    * (Math.cos(wd * t) + (zeta * omega / wd) * Math.sin(wd * t)));
        }
    }

    private View handle() {
        View v = new View(act);
        SmoothDrawable g = new SmoothDrawable();
        g.setColor(cHandle);
        g.setCornerRadius(dp(2));
        v.setBackground(g);
        return v;
    }

    private TextView medium(String s, int size) {
        TextView tv = new TextView(act);
        tv.setText(s);
        tv.setTextSize(size);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tv.setTextColor(cText);
        tv.setIncludeFontPadding(false);
        return tv;
    }

    private void openUrl(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addCategory(Intent.CATEGORY_BROWSABLE);
            act.startActivity(i);
        } catch (Throwable e) {
            Toast.makeText(act, "无法打开浏览器", Toast.LENGTH_SHORT).show();
        }
    }

    private String readAsset(String path) {
        try (InputStream in = act.getAssets().open(path)) {
            byte[] buf = new byte[in.available()];
            int n = in.read(buf);
            return new String(buf, 0, Math.max(n, 0), "UTF-8");
        } catch (Throwable e) {
            return "文件读取失败：" + path;
        }
    }

    /** 状态栏高度：面板吸附到顶部后，页面内容整体让出这段高度。 */
    private int statusBarHeight() {
        int id = act.getResources().getIdentifier("status_bar_height", "dimen", "android");
        int h = id > 0 ? act.getResources().getDimensionPixelSize(id) : dp(24);
        return Math.max(1, h);
    }

    private int dp(float v) {
        return (int) (v * act.getResources().getDisplayMetrics().density + 0.5f);
    }
}

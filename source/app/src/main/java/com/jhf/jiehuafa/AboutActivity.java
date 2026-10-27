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
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.jhf.jiehuafa.ime.ChemKeyboardView;

/**
 * 关于软件页（Figma 设计稿 402 宽）：
 * 顶部白卡 381×146（返回 78×33 @ (12,11) + 关于软件 Bold 32 @ (24,82)）→
 * ◥软件信息 + 当前行卡 381×56 → ◥作者信息 + 单卡 381×114（行57 + 全宽分割线 + 行56）→
 * 底部 80×80 白盒内 logo（字层 + 主题色勾层）。全直角。
 */
public class AboutActivity extends Activity {

    private int cBg, cCard, cText;
    private boolean dark;
    private String themeId;

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
            int accent = ChemKeyboardView.themeColor(themeId);

            if (dark) {
                cBg = 0xFF313131;
                cCard = 0xFF000000;
                cText = 0xFFFFFFFF;
            } else {
                cBg = 0xFFEAEAEA;
                cCard = 0xFFFFFFFF;
                cText = 0xFF000000;
            }
            // 状态栏与页面背景同色，避免顶上一整条主题色（客户意见 9）
            getWindow().setStatusBarColor(cBg);
            View decor = getWindow().getDecorView();
            decor.setSystemUiVisibility(dark
                    ? (decor.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
                    : (decor.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR));

            ScrollView scroll = new ScrollView(this);
            scroll.setBackgroundColor(cBg);
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(0, dp(11), 0, dp(30)); // 画板左右边距改为逐卡 margin（键盘区全宽对齐）
            scroll.addView(root);
            setContentView(scroll);

            // ---- 顶部卡 381×146 @(10,11)：返回 @(12,11) + 关于软件 @(24,82) ----
            // ⚠ 这里必须给卡片设白底：漏了它整页就只剩灰底，卡片边界、返回按钮的填充色
            //   全部消失，看起来与设计稿「完全不一样」（客户意见 8 的根因）。
            FrameLayout topCard = new FrameLayout(this);
            topCard.setBackground(cardBg());
            TextView back = pill("返回", accent);
            back.setOnClickListener(v -> finish());
            FrameLayout.LayoutParams backLp = new FrameLayout.LayoutParams(dp(78), dp(33));
            backLp.gravity = Gravity.TOP | Gravity.LEFT;
            backLp.setMargins(dp(12), dp(11), 0, 0); // 设计稿：组件x10+矩形内偏移12=视觉x22 → 卡内12
            topCard.addView(back, backLp);

            TextView title = new TextView(this);
            title.setText("关于软件");
            title.setTextSize(32);
            title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            title.setTextColor(cText);
            FrameLayout.LayoutParams titleLp = new FrameLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            titleLp.gravity = Gravity.TOP | Gravity.LEFT;
            titleLp.setMargins(dp(24), dp(82), 0, 0); // 设计稿标题卡内 y82（绝对定位）
            topCard.addView(title, titleLp);
            LinearLayout.LayoutParams topLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(146));
            topLp.setMargins(dp(10), 0, dp(11), 0); // 设计稿 x10 宽381（右距11）
            root.addView(topCard, topLp);

            // ---- 软件信息（组头：卡底157→文字165 上8；文字底194→卡片198 下4）----
            root.addView(groupHeader("软件信息", 8, 4));
            root.addView(card(rowText("当前版本", "0.0.1", false)), cardLp());

            // ---- 作者信息：单卡 381×114 = 行57 + 全宽分割线1 + 行56 ----
            root.addView(groupHeader("作者信息", 4, 4));
            LinearLayout authorCard = new LinearLayout(this);
            authorCard.setOrientation(LinearLayout.VERTICAL);
            authorCard.setBackground(cardBg());
            authorCard.addView(rowText("设计制作", "哈潚", false), rowLp(57));
            View div = new View(this);
            div.setBackgroundColor(dark ? 0x1AFFFFFF : 0x1A000000); // 设计稿 black 10% 全宽
            authorCard.addView(div, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
            authorCard.addView(rowStudio(accent), rowLp(56));
            root.addView(authorCard, cardLp());

            // ---- 底部图标：80×80 白盒 @(10,415)（卡底405→415 上距10），logo 59×50 ----
            LinearLayout iconBox = new LinearLayout(this);
            iconBox.setGravity(Gravity.CENTER);
            iconBox.setBackground(cardBg());
            ImageView icon = new ImageView(this);
            Drawable word = getDrawable(dark ? R.drawable.ic_logo_dark : R.drawable.ic_logo);
            Drawable check = getDrawable(R.drawable.ic_logo_check);
            check.mutate().setTint(accent);
            icon.setImageDrawable(new LayerDrawable(new Drawable[]{word, check}));
            iconBox.addView(icon, new LinearLayout.LayoutParams(dp(59), dp(50)));
            LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(dp(80), dp(80));
            boxLp.setMargins(dp(10), dp(10), 0, 0);
            root.addView(iconBox, boxLp);
        } catch (Throwable e) {
            ErrorActivity.launch(this, e);
        }
    }

    /** 组头：实心三角 17.5×18 + 20sp Regular，左距10、上/下距按设计稿传入。 */
    private LinearLayout groupHeader(String s, int top, int bottom) {
        LinearLayout box = new LinearLayout(this);
        box.setGravity(Gravity.CENTER_VERTICAL);
        TextView arrow = new TextView(this);
        arrow.setText("◤"); // 设计稿为左上实心三角（原为 ◥，方向相反）
        arrow.setTextSize(17);
        arrow.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        arrow.setTextColor(cText);
        box.addView(arrow);
        TextView tv = new TextView(this);
        tv.setText(" " + s);
        tv.setTextSize(20);
        tv.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        tv.setTextColor(cText);
        box.addView(tv);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(10), dp(top), 0, dp(bottom));
        box.setLayoutParams(lp);
        return box;
    }

    /** 信息行固定高度（设计稿 57/56）。 */
    private LinearLayout.LayoutParams rowLp(int h) {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(h));
    }

    /** 信息行卡统一左右边距（设计稿 x10 宽381）。 */
    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(10), 0, dp(11), 0);
        return lp;
    }

    private GradientDrawable cardBg() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(cCard);
        g.setCornerRadius(0); // 设计稿直角
        return g;
    }

    private LinearLayout card(View inner) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(cardBg());
        box.addView(inner, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56)));
        return box;
    }

    /** 信息行：左 label Bold 20（x24）、右 value Regular 20（宽102 右对齐、右缩进21）。 */
    private LinearLayout rowText(String label, String value, boolean clickable) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(24), 0, dp(21), 0);
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(20);
        l.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        l.setTextColor(cText);
        // 客户 2026-10-15：文字要垂直居中。label 的高度是 MATCH_PARENT，漏了 CENTER_VERTICAL
        // 就会被画在行顶（设计稿里 label 与右侧值都是 19/19 居中，我们原来是 2/49）。
        l.setGravity(Gravity.CENTER_VERTICAL);
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(20);
        v.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        v.setTextColor(cText);
        v.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        r.addView(l, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        r.addView(v, new LinearLayout.LayoutParams(dp(102),
                LinearLayout.LayoutParams.MATCH_PARENT));
        if (clickable) {
            r.setOnClickListener(clk -> openGitHub());
        }
        return r;
    }

    /** 出品工作室行：右侧 HAGO logo 86×15（右缩进21、垂直居中）。 */
    private LinearLayout rowStudio(int accent) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(24), 0, dp(21), 0);
        TextView l = new TextView(this);
        l.setText("出品工作室");
        l.setTextSize(20);
        l.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        l.setTextColor(cText);
        l.setGravity(Gravity.CENTER_VERTICAL); // 同 rowText：客户 2026-10-15 要求文字垂直居中
        ImageView logo = new ImageView(this);
        logo.setImageResource(dark ? R.drawable.ic_studio_dark : R.drawable.ic_studio);
        r.addView(l, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        r.addView(logo, new LinearLayout.LayoutParams(dp(86), dp(15)));
        r.setOnClickListener(clk -> openGitHub());
        return r;
    }

    private void openGitHub() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com")));
        } catch (Throwable e) {
            Toast.makeText(this, "无法打开浏览器", Toast.LENGTH_SHORT).show();
        }
    }

    /** 描边直角按钮：卡片底 + 主题色描边与文字（返回，设计稿 78×33、字 20px）。 */
    private TextView pill(String text, int accent) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(20);
        tv.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(cCard);
        g.setCornerRadius(0);
        g.setStroke((int) (1.3f * getResources().getDisplayMetrics().density), accent);
        tv.setBackground(g);
        tv.setTextColor(accent);
        return tv;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}

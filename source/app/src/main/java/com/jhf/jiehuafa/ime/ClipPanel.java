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
import android.graphics.Color;
import android.graphics.drawable.LayerDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.jhf.jiehuafa.R;
import com.jhf.jiehuafa.SmoothDrawable;

import java.util.List;

/**
 * 剪切板面板（2026-11-04，Figma 248-16「键盘／普通输入／浅色·圆润／剪切板」）：
 * 顶替键盘视图（候选栏保留，显示「返回」），列出最近复制的内容。
 * 列表区与键盘底板同色（2026-11-09 新配色：浅 #E0E0E0 / 深 #252525），内边距 8/7；
 * 每行按「字符键」配色——浅色键帽 #FFFFFF + 下描边黑 20%，深色键帽 #606060 + 下描边纯黑，
 * 圆角 10，行内 17sp 文字（最多两行），右侧 14×14 删除 X；行间距 9。
 * 点条目上屏（面板保持打开，可连续粘贴多条），点 X 删除该条。
 */
public class ClipPanel extends FrameLayout {

    public interface Listener {
        void onPick(String text);

        void onDelete(String text);
    }

    private final Listener listener;
    private final LinearLayout list;
    private boolean dark;
    private List<String> items;

    public ClipPanel(Context ctx, Listener l) {
        super(ctx);
        listener = l;
        ScrollView scroll = new ScrollView(ctx);
        scroll.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    public void setDark(boolean d) {
        dark = d;
        // 与键盘底板同色（2026-11-09 新配色：浅 #E0E0E0 / 深 #252525）
        setBackgroundColor(dark ? 0xFF252525 : 0xFFE0E0E0);
    }

    public void setItems(List<String> data) {
        items = data;
        rebuild();
    }

    private void rebuild() {
        float d = getResources().getDisplayMetrics().density;
        list.removeAllViews();
        list.setPadding((int) (8 * d), (int) (8 * d), (int) (7 * d), (int) (8 * d));
        if (items == null || items.isEmpty()) {
            TextView empty = new TextView(getContext());
            empty.setText("暂无剪切内容，复制后自动出现在这里");
            empty.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            empty.setTextColor(dark ? 0x80FFFFFF : 0x80000000);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, (int) (40 * d), 0, (int) (40 * d));
            list.addView(empty, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            return;
        }
        for (String text : items) {
            list.addView(row(text, d), rowLp(d));
        }
    }

    private LinearLayout.LayoutParams rowLp(float d) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, (int) (9 * d));
        return lp;
    }

    /** 一行白卡：左文字 + 右删除 X；卡片按「字符键」配色，下垫 1px 描边（2026-11-09 新配色）。 */
    private View row(String text, float d) {
        Context ctx = getContext();
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding((int) (12 * d), (int) (14 * d), (int) (14 * d), (int) (16 * d));

        // LayerDrawable：底层描边向下错 1px，上层键帽盖住，底缘露出 1px
        // 字符键下描边：浅色 黑 20%（#33000000）/ 深色 纯黑
        SmoothDrawable shadow = new SmoothDrawable();
        shadow.setColor(dark ? 0xFF000000 : 0x33000000);
        shadow.setCornerRadius(10 * d);
        SmoothDrawable card = new SmoothDrawable();
        card.setColor(dark ? 0xFF606060 : Color.WHITE);
        card.setCornerRadius(10 * d);
        LayerDrawable bg = new LayerDrawable(new SmoothDrawable[]{shadow, card});
        bg.setLayerInset(0, 0, (int) (1 * d), 0, 0);
        bg.setLayerInset(1, 0, 0, 0, (int) (1 * d));
        row.setBackground(bg);

        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        tv.setTextColor(dark ? 0xFFFFFFFF : 0xFF000000);
        tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        tv.setMaxLines(2);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(tv, tvLp);

        ImageView del = new ImageView(ctx);
        del.setImageResource(R.drawable.ic_close);
        del.setColorFilter(dark ? 0xFFFFFFFF : 0xFF000000);
        LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(
                (int) (14 * d), (int) (14 * d));
        delLp.setMargins((int) (10 * d), (int) (4 * d), 0, 0);
        row.addView(del, delLp);
        del.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(text);
        });

        row.setOnClickListener(v -> {
            if (listener != null) listener.onPick(text);
        });
        return row;
    }
}

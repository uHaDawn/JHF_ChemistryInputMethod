package com.jhf.jiehuafa;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 崩溃错误页：红底白字显示完整堆栈，供截图反馈定位闪退根因。
 * 自身逻辑极简（纯静态布局），避免二次崩溃。
 */
public class ErrorActivity extends Activity {

    private static final String EXTRA_STACK = "stack";
    private static String lastStack;

    public static void launch(Context c, Throwable e) {
        try {
            String stack = stackOf(e);
            lastStack = stack;
            Context app = c.getApplicationContext();
            Intent i = new Intent(app, ErrorActivity.class);
            i.putExtra(EXTRA_STACK, stack);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            app.startActivity(i);
            if (c instanceof Activity) ((Activity) c).finish();
        } catch (Throwable ignore) {
            // 显示错误页失败时静默，避免连环崩溃
        }
    }

    private static String stackOf(Throwable e) {
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        while (e != null && depth < 5) {
            if (depth > 0) sb.append("\nCaused by: ");
            sb.append(e.getClass().getName());
            if (e.getMessage() != null) sb.append(": ").append(e.getMessage());
            sb.append('\n');
            StackTraceElement[] st = e.getStackTrace();
            if (st != null) {
                for (StackTraceElement el : st) {
                    sb.append("    at ").append(el.toString()).append('\n');
                }
            }
            e = e.getCause();
            depth++;
        }
        return sb.length() > 0 ? sb.toString() : "未知错误";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String stack = null;
        try {
            Intent i = getIntent();
            if (i != null) stack = i.getStringExtra(EXTRA_STACK);
        } catch (Throwable ignore) { }
        if (stack == null || stack.length() == 0) stack = "未知错误";

        float d = getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFC62828);
        root.setPadding((int) (20 * d), (int) (24 * d), (int) (20 * d), (int) (16 * d));
        setContentView(root);

        TextView title = new TextView(this);
        title.setText("应用出错了");
        title.setTextSize(20);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setTextColor(Color.WHITE);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("请截图此页面并发给开发者，以便定位问题");
        hint.setTextSize(13);
        hint.setTextColor(0xB3FFFFFF);
        hint.setPadding(0, (int) (4 * d), 0, (int) (10 * d));
        root.addView(hint);

        TextView close = new TextView(this);
        close.setText("关闭应用");
        close.setTextSize(14);
        close.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        close.setTextColor(0xFFC62828);
        close.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(Color.WHITE);
        g.setCornerRadius(20 * d);
        close.setBackground(g);
        close.setOnClickListener(v -> finishAffinity());
        root.addView(close, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, (int) (36 * d)));

        ScrollView sv = new ScrollView(this);
        TextView tv = new TextView(this);
        tv.setText(stack);
        tv.setTextSize(12);
        tv.setTextColor(Color.WHITE);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextIsSelectable(true);
        sv.addView(tv);
        LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        svLp.setMargins(0, (int) (12 * d), 0, 0);
        root.addView(sv, svLp);
    }
}

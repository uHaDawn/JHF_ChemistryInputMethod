/*
 * 接化发 · 化学输入法
 * Copyright (C) 2026 哈潚
 *
 * 本程序是自由软件：你可以依据自由软件基金会发布的 GNU 通用公共许可证
 * （第 3 版，或你选择的任何更高版本）的条款重新分发和/或修改它。
 */
package com.jhf.jiehuafa.ime;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.kingzcheung.xime.rime.RimeEngine;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Rime 内核初始化（2026-11-01）。
 *
 * 首次使用普通输入法时，把 assets/rime 里的方案与词库解到
 * {@code filesDir/rime_shared}，用户目录用 {@code filesDir/rime_user}，
 * 然后初始化引擎并部署（编译词库）。部署完成后按 {@link #SCHEMAS} 切到当前方案。
 *
 * 该流程较慢（首次编译词库），必须在后台线程执行；失败时 {@link Listener#onReady}
 * 返回 false，输入法回退到旧的离线词库（NormalPinyinMatcher）。
 *
 * 修改日志：
 *   2026-11-01 ASSET_VERSION 升到 jhf-dict-1：词库从 librime 自带的 luna_pinyin（无词频、
 *              缺常用词）换成 pinyin_dict.txt 生成的 jhf_pinyin 加权词库；资源版本变化时
 *              连同 user/build 一起清掉，强制重新部署编译，否则旧 prism 会被复用、
 *              新词库永远不生效。
 */
public final class RimeSetup {

    private static final String TAG = "JhfRimeSetup";

    /** 方案顺序与偏好 normal_scheme 一致：0 全拼 / 1 小鹤双拼 / 2 九键（2026-11-02 恢复）。 */
    public static final int SCHEME_PINYIN = 0;
    public static final int SCHEME_FLYPY = 1;
    public static final int SCHEME_T9 = 2;
    public static final String[] SCHEMAS = {"jhf_pinyin", "jhf_flypy", "jhf_t9"};
    public static final String[] SCHEME_NAMES = {"全拼", "双拼", "九键"};

    /** assets 版本号：内容有改动时递增，触发重新解包。 */
    private static final String ASSET_VERSION = "jhf-dict-10";

    public interface Listener {
        /** 主线程回调：ready=false 表示内核不可用（继续用旧引擎）。 */
        void onReady(boolean ready);
    }

    private static volatile boolean sReady;
    private static volatile boolean sStarting;

    private RimeSetup() {
    }

    public static boolean isReady() {
        return sReady;
    }

    public static boolean isStarting() {
        return sStarting;
    }

    /** 后台准备内核；重复调用只跑一次。 */
    public static void start(final Context context, final int scheme, final Listener listener) {
        if (sReady || sStarting) return;
        sStarting = true;
        final Handler main = new Handler(Looper.getMainLooper());
        Thread t = new Thread(() -> {
            boolean ok = false;
            try {
                ok = prepare(context.getApplicationContext(), scheme);
            } catch (Throwable e) {
                Log.e(TAG, "rime prepare failed: " + e, e);
            }
            sReady = ok;
            sStarting = false;
            final boolean result = ok;
            main.post(() -> {
                if (listener != null) listener.onReady(result);
            });
        }, "rime-init");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        t.start();
    }

    private static boolean prepare(Context ctx, int scheme) throws IOException {
        // 非 arm64 设备装不了 librime_jni.so：直接判失败，回退旧引擎。
        RimeEngine engine;
        try {
            engine = RimeEngine.get();
        } catch (Throwable e) {
            Log.w(TAG, "librime_jni unavailable: " + e);
            return false;
        }

        File userDir = new File(ctx.getFilesDir(), "rime_user");
        File sharedDir = new File(ctx.getFilesDir(), "rime_shared");
        if (!userDir.exists() && !userDir.mkdirs()) return false;
        if (!sharedDir.exists() && !sharedDir.mkdirs()) return false;

        File versionFile = new File(sharedDir, "asset_version.txt");
        boolean needExtract = !versionFile.exists()
                || !ASSET_VERSION.equals(readText(versionFile));
        if (needExtract) {
            // 整目录重建而不是覆盖写：删掉旧方案后（如九键 t9_pinyin），
            // 覆盖写会把已下线的文件留在 shared 目录里。
            deleteTree(sharedDir);
            if (!sharedDir.exists() && !sharedDir.mkdirs()) return false;
            copyAssetTree(ctx, "rime", sharedDir);
            writeText(versionFile, ASSET_VERSION);
            // 词库/方案换了必须让部署生成的 *.prism.bin / *.table.bin 重新编译，
            // 否则旧词库的编译产物会被直接复用，新词库永远不生效（候选仍是旧的生僻字）。
            deleteTree(new File(userDir, "build"));
            // user.yaml 记忆着上次激活的方案；资源换版后不清掉会让新会话先落回旧方案。
            deleteTree(new File(userDir, "user.yaml"));
        }

        engine.initialize(userDir.getAbsolutePath(), sharedDir.getAbsolutePath());

        File buildMarker = new File(userDir, "build/jhf_pinyin.prism.bin");
        if (!buildMarker.exists()) {
            Log.i(TAG, "deploying rime (first run)");
            if (!engine.deploy()) {
                Log.e(TAG, "rime deploy failed");
                return false;
            }
        } else if (!engine.createSession()) {
            Log.w(TAG, "session missing, redeploy");
            if (!engine.deploy()) return false;
        }

        int idx = Math.max(0, Math.min(SCHEMAS.length - 1, scheme));
        Log.i(TAG, "before switch, current=" + engine.getCurrentSchema());
        boolean switched = engine.switchSchema(SCHEMAS[idx]);
        Log.i(TAG, "rime ready, schema=" + SCHEMAS[idx] + " switched=" + switched
                + " current=" + engine.getCurrentSchema());
        return switched;
    }

    /** 切换方案（内核已就绪时调用）；失败返回 false。 */
    public static boolean switchScheme(int scheme) {
        if (!sReady) return false;
        int idx = Math.max(0, Math.min(SCHEMAS.length - 1, scheme));
        try {
            RimeEngine engine = RimeEngine.get();
            String before = engine.getCurrentSchema();
            boolean ok = engine.switchSchema(SCHEMAS[idx]);
            Log.i(TAG, "switchScheme " + SCHEMAS[idx] + " ok=" + ok
                    + " " + before + " -> " + engine.getCurrentSchema());
            return ok;
        } catch (Throwable e) {
            Log.w(TAG, "switchScheme failed: " + e);
            return false;
        }
    }

    /** 递归删除目录（部署产物重建时用）；失败不致命，交给后续部署覆盖。 */
    private static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) {
                for (File c : children) deleteTree(c);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    private static void copyAssetTree(Context ctx, String assetPath, File targetDir)
            throws IOException {
        String[] children = ctx.getAssets().list(assetPath);
        if (children == null || children.length == 0) {
            copyAssetFile(ctx, assetPath, targetDir);
            return;
        }
        for (String child : children) {
            String full = assetPath + "/" + child;
            String[] grand = ctx.getAssets().list(full);
            if (grand != null && grand.length > 0) {
                File dir = new File(targetDir, child);
                if (!dir.exists() && !dir.mkdirs()) throw new IOException("mkdir " + dir);
                copyAssetTree(ctx, full, dir);
            } else {
                copyAssetFile(ctx, full, new File(targetDir, child));
            }
        }
    }

    private static void copyAssetFile(Context ctx, String assetPath, File out) throws IOException {
        out.getParentFile().mkdirs();
        try (InputStream in = ctx.getAssets().open(assetPath);
             OutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        }
    }

    private static String readText(File f) {
        try {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            try (InputStream in = new java.io.FileInputStream(f)) {
                byte[] buf = new byte[256];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            }
            return new String(bo.toByteArray(), "UTF-8").trim();
        } catch (IOException e) {
            return "";
        }
    }

    private static void writeText(File f, String text) throws IOException {
        try (OutputStream os = new FileOutputStream(f)) {
            os.write(text.getBytes("UTF-8"));
        }
    }
}

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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 普通输入法的中文拼音引擎（2026-10-31）。
 *
 * 轻量离线方案：assets/pinyin_dict.txt 由 pinyin-data（单字读音）与 jieba 词频离线合成，
 * 每行「词 \t 音节(空格分隔，多读音用 | 分隔) \t 词频」，按词频降序排列。
 * 匹配按四级排序：全拼精确 → 首字母精确 → 全拼前缀 → 首字母前缀，
 * 同级内保持词频顺序（加载时文件已按词频排序）。
 *
 * 之所以不用 CyIME 的 Rime 引擎：其轻量 APK 122MB、需 native .so 与完整方案词库，
 * 本项目是手写 aapt2/javac/d8 构建，不打包 native lib；改用该紧凑词库即可中文输入。
 */
public class NormalPinyinMatcher {

    private final String[] words;
    private final String[][] flats;
    private final String[][] initials;
    private final int[] freqs;

    public NormalPinyinMatcher(Context ctx) throws Exception {
        List<String[]> rows = new ArrayList<>(60000);
        InputStream in = ctx.getAssets().open("pinyin_dict.txt");
        BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String line;
        while ((line = br.readLine()) != null) {
            int t1 = line.indexOf('\t');
            if (t1 <= 0) continue;
            int t2 = line.indexOf('\t', t1 + 1);
            if (t2 < 0) continue;
            String w = line.substring(0, t1);
            String py = line.substring(t1 + 1, t2);
            String fs = line.substring(t2 + 1);
            rows.add(new String[]{w, py, fs});
        }
        br.close();

        words = new String[rows.size()];
        flats = new String[rows.size()][];
        initials = new String[rows.size()][];
        freqs = new int[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            String[] r = rows.get(i);
            words[i] = r[0];
            String[] variants = r[1].split("\\|");
            flats[i] = new String[variants.length];
            initials[i] = new String[variants.length];
            for (int v = 0; v < variants.length; v++) {
                String flat = variants[v].replace(" ", "");
                flats[i][v] = flat;
                StringBuilder ini = new StringBuilder();
                for (String syl : variants[v].split(" ")) {
                    if (!syl.isEmpty()) ini.append(syl.charAt(0));
                }
                initials[i][v] = ini.toString();
            }
            try {
                freqs[i] = Integer.parseInt(r[2]);
            } catch (NumberFormatException e) {
                freqs[i] = 0;
            }
        }
    }

    public int size() {
        return words.length;
    }

    /**
     * 返回最多 limit 个候选词。
     * rank 0 全拼精确 / 1 首字母精确 / 2 全拼前缀 / 3 首字母前缀。
     */
    public List<String> match(String typedRaw, int limit) {
        List<String> out = new ArrayList<>();
        String typed = typedRaw == null ? "" : typedRaw.toLowerCase().replaceAll("[^a-z]", "");
        if (typed.isEmpty()) return out;

        int[] best = new int[words.length];
        java.util.Arrays.fill(best, -1);
        for (int i = 0; i < words.length; i++) {
            int rank = -1;
            for (int v = 0; v < flats[i].length; v++) {
                String flat = flats[i][v];
                if (flat.equals(typed)) {
                    rank = 0;
                    break;
                }
                if (rank < 0 && flat.startsWith(typed)) rank = 2;
            }
            if (rank < 0) {
                for (int v = 0; v < initials[i].length; v++) {
                    String ini = initials[i][v];
                    if (ini.equals(typed)) {
                        rank = 1;
                        break;
                    }
                    if (ini.startsWith(typed)) rank = 3;
                }
            }
            best[i] = rank;
        }

        for (int target = 0; target < 4 && out.size() < limit; target++) {
            for (int i = 0; i < words.length && out.size() < limit; i++) {
                if (best[i] == target) addUnique(out, words[i]);
            }
        }
        return out;
    }

    private static void addUnique(List<String> out, String w) {
        if (!out.contains(w)) out.add(w);
    }

    // ---------- 单字声母表（候选字界面「首字母」筛选用，2026-11-01） ----------

    /** 首字 → 声母。懒加载一次，进程内复用。 */
    private static volatile java.util.Map<Character, Character> initialsMap;

    /**
     * 从 assets/pinyin_dict.txt 的单字行建「字 → 声母」表（只取每行第一个读音）。
     * 要在后台线程调用：60k 行文本首次解析约几百毫秒。
     */
    public static java.util.Map<Character, Character> loadInitials(Context ctx) {
        if (initialsMap != null) return initialsMap;
        synchronized (NormalPinyinMatcher.class) {
            if (initialsMap != null) return initialsMap;
            java.util.Map<Character, Character> map = new java.util.HashMap<>();
            try {
                InputStream in = ctx.getAssets().open("pinyin_dict.txt");
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = br.readLine()) != null) {
                    int t1 = line.indexOf('\t');
                    if (t1 != 1) continue;     // 只收单字行（第一列长度 1）
                    int t2 = line.indexOf('\t', t1 + 1);
                    if (t2 < 0) continue;
                    String syl = line.substring(t1 + 1, t2).split("\\|")[0].trim();
                    if (syl.isEmpty()) continue;
                    map.put(line.charAt(0), syl.charAt(0));
                }
                br.close();
            } catch (Throwable ignored) {
            }
            initialsMap = map;
            return map;
        }
    }

    /** 候选词首字的声母（表未加载或字表里没有时返回空串）。 */
    public static String initialOf(String word) {
        java.util.Map<Character, Character> m = initialsMap;
        if (m == null || word == null || word.isEmpty()) return "";
        Character c = m.get(word.charAt(0));
        return c == null ? "" : String.valueOf(c);
    }
}

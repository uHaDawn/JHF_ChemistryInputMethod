package com.jhf.jiehuafa.ime;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 化学词库 + 拼音匹配引擎。
 * 输入全拼（如 liusuvtong 之外的 liusu-antong 场景由分词 DP 处理）或首字母缩写，
 * 匹配中文化学名称，给出对应化学式候选。
 */
public class PinyinMatcher {

    public static class Entry {
        public String name;
        public String[] formulas;
        /** 每个读音一组音节，支持 "chong ge …|zhong ge …" 多读音。 */
        public String[][] pinyins;
        public String[] initials;
        public int nameLen;
    }

    public static class Candidate {
        public Entry entry;
        public String formula;
        public int rank;
    }

    public final List<Entry> entries = new ArrayList<>();

    public PinyinMatcher(Context ctx) {
        try {
            JSONArray arr = new JSONArray(readAsset(ctx, "chem_dict.json"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Entry e = new Entry();
                e.name = o.getString("n");
                e.nameLen = e.name.length();
                JSONArray fs = o.getJSONArray("f");
                e.formulas = new String[fs.length()];
                for (int j = 0; j < fs.length(); j++) e.formulas[j] = fs.getString(j);
                String[] alts = o.getString("p").trim().split("\\|");
                e.pinyins = new String[alts.length][];
                e.initials = new String[alts.length];
                for (int k = 0; k < alts.length; k++) {
                    String[] syls = alts[k].trim().split("\\s+");
                    e.pinyins[k] = syls;
                    StringBuilder ini = new StringBuilder();
                    for (String s : syls) if (!s.isEmpty()) ini.append(s.charAt(0));
                    e.initials[k] = ini.toString();
                }
                entries.add(e);
            }
        } catch (Exception ignored) {
        }
    }

    private static String readAsset(Context ctx, String name) throws Exception {
        InputStream in = ctx.getAssets().open(name);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return new String(bo.toByteArray(), StandardCharsets.UTF_8);
    }

    /** 匹配：rank 0=全拼精确 1=全拼前缀 2=首字母精确 3=首字母前缀。 */
    public List<Candidate> match(String typedRaw, int limit) {
        String typed = typedRaw.toLowerCase().replaceAll("[^a-z]", "");
        List<Entry> exact = new ArrayList<>(), prefix = new ArrayList<>();
        List<Entry> ini = new ArrayList<>(), iniP = new ArrayList<>();
        if (!typed.isEmpty()) {
            for (Entry e : entries) {
                int rank = bestRank(typed, e);
                if (rank == 0) exact.add(e);
                else if (rank == 1) prefix.add(e);
                else if (rank == 2) ini.add(e);
                else if (rank == 3) iniP.add(e);
            }
        }
        // 只按名称长度排序，同长度保持词库顺序（手写常用词优先，其次按酸根/阳离子常用度生成）。
        Comparator<Entry> byLen = Comparator.comparingInt(a -> a.nameLen);
        exact.sort(byLen);
        prefix.sort(byLen);
        ini.sort(byLen);
        iniP.sort(byLen);

        List<Entry[]> groups = new ArrayList<>();
        groups.add(exact.toArray(new Entry[0]));
        groups.add(prefix.toArray(new Entry[0]));
        groups.add(ini.toArray(new Entry[0]));
        groups.add(iniP.toArray(new Entry[0]));

        List<Candidate> out = new ArrayList<>();
        int rank = 0;
        for (Entry[] g : groups) {
            for (Entry e : g) {
                for (String f : e.formulas) {
                    Candidate c = new Candidate();
                    c.entry = e;
                    c.formula = f;
                    c.rank = rank;
                    out.add(c);
                    if (out.size() >= limit) return out;
                }
            }
            rank++;
        }
        return out;
    }

    /** 返回该词目在所有读音中的最佳匹配等级；-1 表示不匹配。 */
    private int bestRank(String typed, Entry e) {
        for (String[] syls : e.pinyins) if (fullMatch(typed, syls)) return 0;
        for (String[] syls : e.pinyins) if (prefixMatch(typed, syls)) return 1;
        for (String s : e.initials) if (s.equals(typed)) return 2;
        for (String s : e.initials) if (s.startsWith(typed)) return 3;
        return -1;
    }

    /**
     * 音节等价判断：'lv'(lü) 允许用户打成 'lu'。
     * 快路径零分配——只有词库音节里确实含 'v' 时才走字符串替换。
     */
    private static boolean sylEquals(String syl, String piece) {
        if (syl.equals(piece)) return true;
        if (syl.indexOf('v') < 0) return false;
        return syl.replace('v', 'u').equals(piece.replace('v', 'u'));
    }

    /** typed 能否恰好被词目拼音逐字消耗完。 */
    private boolean fullMatch(String t, String[] syls) {
        memo.clear();
        return fullDp(t, syls, 0, 0);
    }

    private final Map<Long, Boolean> memo = new HashMap<>();

    private boolean fullDp(String t, String[] syls, int i, int j) {
        int T = t.length(), S = syls.length;
        if (j == T) return i == S;
        if (i >= S) return false;
        long key = (long) i * 1000 + j;
        Boolean cached = memo.get(key);
        if (cached != null) return cached;
        boolean ok = false;
        String syl = syls[i];
        int maxLen = Math.min(6, T - j);
        for (int len = 1; len <= maxLen && !ok; len++) {
            if (sylEquals(syl, t.substring(j, j + len))) ok = fullDp(t, syls, i + 1, j + len);
        }
        memo.put(key, ok);
        return ok;
    }

    /** typed 能否被消耗完（允许末音节只打了一部分，用于联想）。 */
    private boolean prefixMatch(String t, String[] syls) {
        memo.clear();
        return prefixDp(t, syls, 0, 0);
    }

    private boolean prefixDp(String t, String[] syls, int i, int j) {
        int T = t.length(), S = syls.length;
        if (j == T) return i >= 1;
        if (i >= S) return false;
        long key = (long) i * 1000 + j;
        Boolean cached = memo.get(key);
        if (cached != null) return cached;
        boolean ok = false;
        String syl = syls[i];
        int maxLen = Math.min(6, T - j);
        for (int len = 1; len <= maxLen && !ok; len++) {
            String piece = t.substring(j, j + len);
            if (sylEquals(syl, piece)) {
                ok = prefixDp(t, syls, i + 1, j + len);
            } else if (syl.startsWith(piece) && j + len == T) {
                ok = true; // 末音节前缀联想
            }
        }
        memo.put(key, ok);
        return ok;
    }
}

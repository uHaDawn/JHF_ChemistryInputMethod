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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 反应库查询引擎（2026-11-08）。
 *
 * 反应数据由 tools/gen_reaction_dict.py / gen_ion_reactions.py 构建期配平后写入
 * assets/reaction_dict.json 与 assets/ion_reaction_dict.json，运行时只查表，不做在线配平。
 *
 * 查找 key：反应物规范式排序后用 | 连接（顺序无关），故 ["H2O","Na"]
 * 与 ["Na","H2O"] 命中同一条。
 *
 * 离子场景（2026-11-08 修复）：输入含电荷离子（Fe、Cu^2+、SO4^2- 等）时，
 * 先按完整组合精确匹配；未命中则尝试剔除旁观离子（如 SO4^2-、Na^+、NO3^-），
 * 用剩余离子组合再查一次，输出净离子式（net_ionic），
 * 因此「铁 + 硫酸根 + 铜离子」会命中 Fe + Cu^2+ = Fe^2+ + Cu。
 */
public final class ReactionEngine {

    /** 一条配平后的反应。 */
    public static final class Reaction {
        public final List<String> reactants;
        public final List<String> products;
        public final List<Integer> coeffs;
        public final String conditions;
        public final List<Boolean> gas;
        public final List<Boolean> precip;
        public final String type;       // molecular / ionic
        /** 已带 ↑↓ 与条件的显示串，可直接上屏。 */
        public final String equation;
        /** 分子式（ion_reaction_dict 字段，可能为空）。 */
        public final String molecular;
        /** 完整离子式（ion_reaction_dict 字段，可能为空）。 */
        public final String fullIonic;
        /** 净离子式（ion_reaction_dict 字段，可能为空）。 */
        public final String netIonic;

        Reaction(JSONObject o) throws Exception {
            this.reactants = toStringList(o.getJSONArray("reactants"));
            this.products = toStringList(o.getJSONArray("products"));
            JSONArray c = o.optJSONArray("coeffs");
            this.coeffs = new ArrayList<>(c == null ? 0 : c.length());
            if (c != null) {
                for (int i = 0; i < c.length(); i++) this.coeffs.add(c.getInt(i));
            }
            this.conditions = o.optString("conditions", "");
            JSONArray g = o.optJSONArray("gas");
            this.gas = g == null ? Collections.<Boolean>emptyList() : toBoolList(g);
            JSONArray p = o.optJSONArray("precip");
            this.precip = p == null ? Collections.<Boolean>emptyList() : toBoolList(p);
            this.type = o.optString("type", "molecular");
            this.equation = o.getString("equation");
            this.molecular = o.optString("molecular", "");
            this.fullIonic = o.optString("full_ionic", "");
            this.netIonic = o.optString("net_ionic", "");
        }

        /**
         * 按输入场景选择显示方程式：输入含电荷离子时优先净离子式，
         * 否则保持原来的分子式 / 离子式。
         */
        public String displayEquation(List<String> inputs) {
            boolean ionInput = false;
            if (inputs != null) {
                for (String s : inputs) {
                    if (s != null && s.indexOf('^') >= 0) {
                        ionInput = true;
                        break;
                    }
                }
            }
            if (ionInput && !netIonic.isEmpty()) return netIonic;
            return equation;
        }
    }

    private final Map<String, List<Reaction>> index = new HashMap<>();
    /** 净离子式索引：net_ionic 左侧的离子组合 → 对应反应（补齐「Mg + H^+」这类常见离子场景）。 */
    private final Map<String, List<Reaction>> netIndex = new HashMap<>();
    private boolean loaded = false;

    public ReactionEngine() {
    }

    /** 从 assets 加载反应库（数据量小，主线程同步读即可）。 */
    public synchronized void load(Context ctx) {
        if (loaded) return;
        // 离子库优先：同一条目重复时保留带 net_ionic / full_ionic 的版本
        List<JSONObject> raw = new ArrayList<>(1600);
        readAsset(ctx, "ion_reaction_dict.json", raw);
        readAsset(ctx, "reaction_dict.json", raw);
        Set<String> seen = new HashSet<>();
        for (JSONObject o : raw) {
            try {
                Reaction r = new Reaction(o);
                // 同 key + 同方程式视为重复，避免同一反应弹两次选择框
                String dedup = reactantKey(r.reactants) + "\n" + r.equation;
                if (!seen.add(dedup)) continue;
                String key = reactantKey(r.reactants);
                List<Reaction> list = index.get(key);
                if (list == null) {
                    list = new ArrayList<>();
                    index.put(key, list);
                }
                list.add(r);
                // 净离子式反向索引：把 net_ionic 左侧的离子组合也登记一份，
                // 这样 Mg + H^+、Fe + H^+ 等「离子形态」输入也能命中对应反应。
                String netKey = netReactantKey(r.netIonic);
                if (netKey != null && netKey.indexOf('^') >= 0 && !index.containsKey(netKey)) {
                    List<Reaction> nl = netIndex.get(netKey);
                    if (nl == null) {
                        nl = new ArrayList<>();
                        netIndex.put(netKey, nl);
                    }
                    boolean dup = false;
                    for (Reaction x : nl) {
                        if (x.netIonic.equals(r.netIonic)) {
                            dup = true;
                            break;
                        }
                    }
                    if (!dup) nl.add(r);
                }
            } catch (Throwable ignored) {
            }
        }
        loaded = true;
    }

    private void readAsset(Context ctx, String name, List<JSONObject> sink) {
        try {
            InputStream is = ctx.getAssets().open(name);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            is.close();
            JSONArray arr = new JSONArray(bos.toString("UTF-8"));
            for (int i = 0; i < arr.length(); i++) sink.add(arr.getJSONObject(i));
        } catch (Throwable ignored) {
        }
    }

    public boolean isLoaded() {
        return loaded;
    }

    /** 反应物规范化：去空白、排序、| 连接。 */
    public static String reactantKey(List<String> reactants) {
        String[] arr = new String[reactants.size()];
        for (int i = 0; i < reactants.size(); i++) {
            arr[i] = reactants.get(i).trim();
        }
        Arrays.sort(arr);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append('|');
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    /**
     * 查询匹配的反应。
     *
     * @return 匹配列表（可能 0 / 1 / N 条）。N>1 时由调用方弹窗让用户选择。
     */
    public synchronized List<Reaction> query(List<String> reactants) {
        if (!loaded || reactants == null || reactants.isEmpty()) {
            return Collections.emptyList();
        }
        List<Reaction> exact = index.get(reactantKey(reactants));
        if (exact != null && !exact.isEmpty()) return new ArrayList<>(exact);
        // 精确组合未命中：尝试剔除旁观离子（铁 + 硫酸根 + 铜离子 → 铁 + 铜离子）
        List<Reaction> sub = queryBySubset(reactants);
        if (!sub.isEmpty()) return sub;
        // 最后查净离子式索引（Mg + H^+ → Mg + 2H^+ = Mg^2+ + H2↑）
        List<Reaction> net = netIndex.get(reactantKey(reactants));
        if (net != null && !net.isEmpty()) return new ArrayList<>(net);
        return Collections.emptyList();
    }

    /**
     * 从净离子式左侧解析反应物 key：去掉系数、把 Unicode 上下标改成 ASCII。
     * 例：「Mg + 2H⁺ = Mg²⁺ + H₂↑」→「H^+|Mg」；「CO₃²⁻ + 2H⁺ = …」→「CO3^2-|H^+」。
     * 条件标注（CO₂(少量)、HCl(浓)）不是物质本身，解析前先剥掉。
     */
    private static String netReactantKey(String netIonic) {
        if (netIonic == null || netIonic.isEmpty()) return null;
        int eq = netIonic.indexOf('=');
        if (eq <= 0) return null;
        String left = netIonic.substring(0, eq).trim();
        List<String> rs = new ArrayList<>();
        for (String raw : left.split("\\+")) {
            String s = raw.trim().replaceFirst("^\\d+", "");
            s = s.replaceFirst("[（(](?:极稀|少量|过量|足量|微量|浓|稀)[)）]$", "");
            if (!s.isEmpty()) rs.add(asciiIonic(s));
        }
        return rs.isEmpty() ? null : reactantKey(rs);
    }

    private static final String SUP_DIGITS = "⁰¹²³⁴⁵⁶⁷⁸⁹";
    private static final String SUB_DIGITS = "₀₁₂₃₄₅₆₇₈₉";

    /** Unicode 上下标 / 电荷符号 → 客户端 toAsciiFormula 输出的 ASCII 写法。 */
    private static String asciiIonic(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char ch = s.charAt(i);
            int sub = SUB_DIGITS.indexOf(ch);
            if (sub >= 0) {
                sb.append(sub);
                i++;
                continue;
            }
            int sup = SUP_DIGITS.indexOf(ch);
            if (sup >= 0) {
                StringBuilder digits = new StringBuilder();
                while (i < s.length() && SUP_DIGITS.indexOf(s.charAt(i)) >= 0) {
                    digits.append(SUP_DIGITS.indexOf(s.charAt(i)));
                    i++;
                }
                char sign = i < s.length() ? s.charAt(i) : 0;
                if (sign == '⁺' || sign == '⁻') i++;
                sb.append('^').append(digits);
                if (sign == '⁺') sb.append('+');
                else if (sign == '⁻') sb.append('-');
                continue;
            }
            if (ch == '⁺') {
                sb.append("^+");
                i++;
                continue;
            }
            if (ch == '⁻') {
                sb.append("^-");
                i++;
                continue;
            }
            sb.append(ch);
            i++;
        }
        return sb.toString();
    }

    /**
     * 旁观离子容错：依次尝试剔除 1、2… 个反应物，用剩下的组合再查库。
     * 先命中的（保留反应物更多的）优先，避免误删真正的反应物。
     */
    private List<Reaction> queryBySubset(List<String> reactants) {
        int n = reactants.size();
        for (int drop = 1; drop < n; drop++) {
            List<Reaction> found = new ArrayList<>();
            collectSubsets(reactants, new boolean[n], 0, 0, drop, found);
            if (!found.isEmpty()) return dedupeByEquation(found);
        }
        return Collections.emptyList();
    }

    private void collectSubsets(List<String> list, boolean[] skip, int idx,
                                int skipped, int target, List<Reaction> out) {
        if (skipped == target) {
            List<String> kept = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                if (!skip[i]) kept.add(list.get(i));
            }
            if (!kept.isEmpty()) {
                List<Reaction> hit = index.get(reactantKey(kept));
                if (hit != null) out.addAll(hit);
            }
            return;
        }
        if (idx >= list.size() || list.size() - idx < target - skipped) return;
        skip[idx] = true;
        collectSubsets(list, skip, idx + 1, skipped + 1, target, out);
        skip[idx] = false;
        collectSubsets(list, skip, idx + 1, skipped, target, out);
    }

    private static List<Reaction> dedupeByEquation(List<Reaction> list) {
        List<Reaction> out = new ArrayList<>(list.size());
        Set<String> seen = new HashSet<>();
        for (Reaction r : list) {
            if (seen.add(r.equation)) out.add(r);
        }
        return out;
    }

    private static List<String> toStringList(JSONArray a) throws Exception {
        List<String> l = new ArrayList<>(a.length());
        for (int i = 0; i < a.length(); i++) l.add(a.getString(i));
        return l;
    }

    private static List<Boolean> toBoolList(JSONArray a) throws Exception {
        List<Boolean> l = new ArrayList<>(a.length());
        for (int i = 0; i < a.length(); i++) l.add(a.getBoolean(i));
        return l;
    }
}

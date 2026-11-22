/*
 * 接化发 · 化学输入法
 * Copyright (C) 2026 哈潚
 *
 * 本程序是自由软件：你可以依据自由软件基金会发布的 GNU 通用公共许可证
 * （第 3 版，或你选择的任何更高版本）的条款重新分发和/或修改它。
 */
package com.jhf.jiehuafa;

import java.util.ArrayList;
import java.util.List;

/**
 * 「关于软件 → 许可与鸣谢」数据（Figma 209-105 / 209-144 / 209-185，2026-11-01）。
 *
 * 清单只列真正随包分发的内容：GPL 许可全文、第三方库许可、内置词库与设计资源。
 * 每条记录对应 assets/licenses 或 assets/docs 里的一份全文，点击「查看…」直接读取，
 * 不依赖网络；「查看项目地址」才用系统浏览器打开上游仓库。
 *
 * 2026-11-03：加入 Capsule（Kyant0，Apache License 2.0）。应用内所有圆角
 * 使用该项目提供的 G2 连续圆角算法（Java 移植见 SmoothCorners）。
 */
public final class ThirdParty {

    /** 一条许可 / 组件记录。 */
    public static final class Item {
        public final String title;      // 项目名 + 版本，设计稿 20sp
        public final String authors;    // 作者/团体，设计稿 17sp #B2B2B2
        public final String license;    // 许可标识（用于文档标题）
        public final String asset;      // assets 内全文路径（许可证全文 / 组件文档）
        public final String url;        // 项目地址，空串表示没有外链
        public final String action;     // 第一行操作文案：「查看许可证全文」/「查看文档」

        Item(String title, String authors, String license, String asset, String url, String action) {
            this.title = title;
            this.authors = authors;
            this.license = license;
            this.asset = asset;
            this.url = url;
            this.action = action;
        }
    }

    /** 第三方许可清单（209-105）：每项可看许可证全文 + 项目地址。 */
    public static List<Item> licenses() {
        List<Item> list = new ArrayList<>();
        list.add(new Item("接化发 · 化学输入法 v1.0.2", "哈潚",
                "GNU GPL v3", "licenses/gpl-3.0.txt",
                "https://github.com/uHaDawn/JHF_ChemistryInputMethod", "查看许可证全文"));
        list.add(new Item("CyIME（Xime 派生）v1.0", "Cyletix",
                "GNU GPL v3", "licenses/gpl-3.0.txt",
                "https://github.com/Cyletix/CyIME", "查看许可证全文"));
        list.add(new Item("librime v1.17", "Rime Developers",
                "BSD 3-Clause", "licenses/librime-bsd-3.txt",
                "https://github.com/rime/librime", "查看许可证全文"));
        list.add(new Item("OpenCC v1.1", "BYVoid 等",
                "Apache License 2.0", "licenses/apache-2.0.txt",
                "https://github.com/BYVoid/OpenCC", "查看许可证全文"));
        list.add(new Item("Capsule（G2 连续圆角）", "Kyant0",
                "Apache License 2.0", "licenses/apache-2.0.txt",
                "https://github.com/Kyant0/Capsule", "查看许可证全文"));
        list.add(new Item("Android 拼音词库", "Android Open Source Project",
                "Apache License 2.0", "licenses/apache-2.0.txt",
                "https://android.googlesource.com/platform/packages/inputmethods/PinyinIME/",
                "查看许可证全文"));
        list.add(new Item("中文词库（rime-ice）", "iDvel / Rime 社区",
                "CC BY-SA 3.0", "licenses/cc-by-sa-3.0.txt",
                "https://github.com/iDvel/rime-ice", "查看许可证全文"));
        list.add(new Item("Boost v1.89.0", "Boost 社区",
                "Boost Software License 1.0", "licenses/boost-bsl-1.0.txt",
                "https://www.boost.org", "查看许可证全文"));
        list.add(new Item("glog v0.6", "Google",
                "BSD 3-Clause", "licenses/glog-bsd-3.txt",
                "https://github.com/google/glog", "查看许可证全文"));
        list.add(new Item("LevelDB v1.23", "Google",
                "BSD 3-Clause", "licenses/leveldb-bsd-3.txt",
                "https://github.com/google/leveldb", "查看许可证全文"));
        list.add(new Item("marisa-trie v0.2", "Susumu Yata",
                "BSD 2-Clause / LGPL 2.1", "licenses/marisa-copying.md",
                "https://github.com/s-yata/marisa-trie", "查看许可证全文"));
        list.add(new Item("yaml-cpp v0.8", "Jesse Beder",
                "MIT License", "licenses/mit.txt",
                "https://github.com/jbeder/yaml-cpp", "查看许可证全文"));
        list.add(new Item("ONNX Runtime v1.28", "Microsoft",
                "MIT License", "licenses/onnxruntime-mit.txt",
                "https://github.com/microsoft/onnxruntime", "查看许可证全文"));
        return list;
    }

    /** 第三方组件（209-144）：每项只有「查看文档」。 */
    public static List<Item> components() {
        List<Item> list = new ArrayList<>();
        list.add(new Item("Rime 内核与 JNI 桥", "Rime Developers / Cyletix",
                "组件文档", "docs/component-rime.txt",
                "https://github.com/rime/librime", "查看文档"));
        list.add(new Item("简繁转换 OpenCC", "BYVoid",
                "组件文档", "docs/component-opencc.txt",
                "https://github.com/BYVoid/OpenCC", "查看文档"));
        list.add(new Item("离线拼音匹配", "接化发",
                "组件文档", "docs/component-pinyin.txt",
                "", "查看文档"));
        list.add(new Item("化学术语词库", "接化发",
                "组件文档", "docs/component-chem.txt",
                "", "查看文档"));
        list.add(new Item("G2 连续圆角（Capsule）", "Kyant0",
                "组件文档", "docs/component-capsule.txt",
                "https://github.com/Kyant0/Capsule", "查看文档"));
        return list;
    }

    private ThirdParty() {
    }
}

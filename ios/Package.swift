// swift-tools-version: 6.0
// 接化发 · 化学输入法 iOS 版 —— 阶段 0 最小验证工程
// 目的：验证 xtool 能否把「宿主 App + 键盘扩展」一起构建、签名、安装到真机。
// 结构对齐 Android 侧：App（设置/引导）、Keyboard（IME 本体）、Core（引擎，后续移植 PinyinMatcher 等）。
import PackageDescription

let package = Package(
    name: "JieHuaFa",
    platforms: [.iOS(.v16)],
    products: [
        .library(name: "JieHuaFaApp", targets: ["JieHuaFaApp"]),
        .library(name: "JieHuaFaKeyboard", targets: ["JieHuaFaKeyboard"]),
    ],
    targets: [
        .target(name: "JieHuaFaCore"),
        .target(name: "JieHuaFaApp", dependencies: ["JieHuaFaCore"]),
        .target(name: "JieHuaFaKeyboard", dependencies: ["JieHuaFaCore"]),
    ]
)

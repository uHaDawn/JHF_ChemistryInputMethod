# prebuilt/arm64-v8a

构建 APK 时直接打进 `lib/arm64-v8a/` 的两个原生库，不入源码编译链，只需随仓库分发：

| 文件 | 大小 | SHA256 |
|---|---|---|
| `librime_jni.so` | 7,380,488 B | `97C13B0BA962AD8B309E02B485CF82C2EFBF8F0B2E682FCB0AC1491B37EA892F` |
| `libonnxruntime.so` | 28,637,280 B | `F826D8EFB03ADF0A84F10E7BA408F9D4CD11B0A2CCD8D08AEB0F7451FB50CACC` |

- `librime_jni.so`：librime 1.17.0 + CyIME 的 T9/cyl m/onnx 模块，JNI 导出 `com_kingzcheung_xime_rime_*` 符号。
- `libonnxruntime.so`：ONNX Runtime 1.28（Microsoft，MIT），`librime_jni.so` 依赖它，二者必须同目录。

## 来源与许可

两个库由本项目的 Rime 内核构建产出，上游为 [CyIME](https://github.com/Cyletix/CyIME) 与
[librime](https://github.com/rime/librime)，整体以 **GPL-3.0** 分发（见根目录 LICENSE）；
`libonnxruntime.so` 单独以 **MIT** 许可分发（全文见
`source/app/src/main/assets/licenses/onnxruntime-mit.txt`）。

重新编译步骤见 [docs/build-native.md](../docs/build-native.md)。替换任一 `.so` 后请同步更新上表
哈希，否则 `build.cmd` 仍会使用旧库。

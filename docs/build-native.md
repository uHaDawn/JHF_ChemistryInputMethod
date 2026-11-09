# 编译 Rime 原生库（arm64-v8a）

`source/build.cmd` 不编译 C/C++，只从 `prebuilt/arm64-v8a/` 取现成库打进 APK。
需要重新生成 `.so` 时按本页操作，产物覆盖 `prebuilt/arm64-v8a/`（并更新
`prebuilt/README.md` 里的 SHA256）。

## 固定版本

| 组件 | 版本 / 来源 |
|---|---|
| Rime 源码树 | CyIME `main` 快照（librime 子模块 1.17.0），含 librime-lua / octagram / predict / t9 / cylm |
| 交叉编译工具链 | Android NDK **29.0.14206865**（r29），`build/cmake/android.toolchain.cmake` |
| CMake | 3.22.1；生成器 Ninja（随 CMake 附带） |
| Boost | **1.89.0**（`boost-1.89.0-cmake.tar.xz`，SHA256 `67acec02d0d118b5de9eb441f5fb707b3a1cdd884be00ca24b9a73c995511f74`） |
| ONNX Runtime | 1.28.0，Android arm64-v8a 预编译包（`onnxruntime_c_api.h` 的 `ORT_API_VERSION=28`） |
| ABI / 平台 | `arm64-v8a` / `android-28`（minSdk 24 安装，但原生库按 API 28 编译） |
| 构建类型 | `Release` |

## 1. 取源码树

CyIME 与各子模块按 `main` / `master` 直接取 codeload zip（不用 git clone，避开 443 不稳定）：

```powershell
# 主树
Invoke-WebRequest https://codeload.github.com/Cyletix/CyIME/zip/refs/heads/main -OutFile $env:TEMP\cyime.zip
Expand-Archive $env:TEMP\cyime.zip -DestinationPath rime
# 子模块依次解到 rime/app/src/main/jni/ 下：
#   librime              → ximeiorg/librime          refs/heads/master
#   snappy               → google/snappy              refs/heads/main
#   librime-lua          → hchunhui/librime-lua       refs/heads/master
#   librime-lua-deps     → hchunhui/librime-lua-deps  refs/heads/thirdparty
#   librime-octagram     → lotem/librime-octagram     refs/heads/master
#   librime-predict      → rime/librime-predict       refs/heads/master
```

> 上游跑的是移动分支，没有固定 commit。可复现性以 `prebuilt/` 里的 `.so` 哈希为准；
> 若要完全一致，请把上面各 URL 的 `refs/heads/...` 换成发布时记录的 commit sha。

## 2. Boost 1.89.0

```powershell
# 解压后目录名统一为 boost，放到 jni/boost
Invoke-WebRequest https://github.com/boostorg/boost/releases/download/boost-1.89.0/boost-1.89.0-cmake.tar.xz -OutFile boost-1.89.0.tar.xz
# 校验 SHA256 = 67acec02d0d118b5de9eb441f5fb707b3a1cdd884be00ca24b9a73c995511f74
tar -xf boost-1.89.0.tar.xz
Move-Item boost-1.89.0 rime\app\src\main\jni\boost
```

整包解压后是 CMake superproject，`CMakeLists.txt` 里 `project(Boost VERSION 1.89.0)`；
`jni/CMakeLists.txt` 只编 algorithm/crc/dll/interprocess/range/regex/scope_exit/signals2/utility/uuid 子集。

压缩包本身 102 MB，不入库（`.gitignore` 已忽略 `boost-*.tar.xz`）。

## 3. ONNX Runtime

下载 ONNX Runtime 1.28 Android 版，把 `libonnxruntime.so` 与头文件放到：

```
rime/app/src/main/jni/onnxruntime/include/onnxruntime_c_api.h 等
rime/app/src/main/jni/onnxruntime/lib/arm64-v8a/libonnxruntime.so
```

## 4. 配置与编译

```powershell
$SDK = $env:ANDROID_SDK_ROOT
$NDK = "$SDK\ndk\29.0.14206865"
$CMAKE = "C:\path\to\cmake\3.22.1"

& "$CMAKE\bin\cmake.exe" -S rime/app/src/main/jni -B rime/build-arm64 -G Ninja `
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" `
  -DANDROID_ABI=arm64-v8a `
  -DANDROID_PLATFORM=android-28 `
  -DCMAKE_BUILD_TYPE=Release `
  -DCMAKE_MAKE_PROGRAM="$CMAKE\bin\ninja.exe"

& "$CMAKE\bin\cmake.exe" --build rime/build-arm64 --target rime_jni --parallel
```

其余选项（`BUILD_SHARED_LIBS=OFF`、`BUILD_TESTING=OFF`、glog/yaml-cpp/leveldb/snappy 的
测试与工具开关）都由 `jni/CMakeLists.txt` 写入，无需额外传参。

## 5. 收集产物

```powershell
New-Item -ItemType Directory -Force prebuilt\arm64-v8a | Out-Null
Copy-Item rime\build-arm64\librime_jni\librime_jni.so prebuilt\arm64-v8a\
Copy-Item rime\app\src\main\jni\onnxruntime\lib\arm64-v8a\libonnxruntime.so prebuilt\arm64-v8a\
Get-FileHash prebuilt\arm64-v8a\*.so -Algorithm SHA256
```

然后回到 `source/` 执行 `build.cmd`；脚本会直接读取 `prebuilt/arm64-v8a/*.so`，
缺少任一个都会以 `MISSING:` 提示并退出，不会静默产出缺库的 APK。

## 已知差异

- `.so` 已 strip 调试符号；崩溃栈只有地址，需要符号化时自行保留未 strip 版本。
- 只产出 arm64-v8a。32 位与 x86 未适配，`build.cmd` 也只打包这一套 ABI。

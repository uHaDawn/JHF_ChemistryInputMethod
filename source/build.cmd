@echo off
rem JieHuaFa one-shot build script (no Gradle needed)
rem Requires: JDK 17, Android SDK (platforms/android-34 + build-tools/35.0.0), Python 3
rem NOTE: keep this file ASCII-only with CRLF line endings; non-ASCII or LF-only breaks cmd parsing.
setlocal enabledelayedexpansion
set PYTHONDONTWRITEBYTECODE=1

rem ---------- toolchain from environment ----------
rem SDK: ANDROID_SDK_ROOT (or ANDROID_HOME)
rem JDK: JAVA_HOME must point to a JDK 17 install
set SDK=%ANDROID_SDK_ROOT%
if not defined SDK set SDK=%ANDROID_HOME%
if not defined SDK (
    echo ERROR: set ANDROID_SDK_ROOT ^(or ANDROID_HOME^) to your Android SDK path
    goto :err
)
if not exist "%JAVA_HOME%\bin\javac.exe" (
    echo ERROR: set JAVA_HOME to a JDK 17 installation
    goto :err
)
set "PATH=%JAVA_HOME%\bin;%PATH%"

set BT=%SDK%\build-tools\35.0.0
set AJAR=%SDK%\platforms\android-34\android.jar
set ROOT=%~dp0
set APP=%ROOT%app\src\main
set OUT=%ROOT%out
set INT=%OUT%\interim
rem Signed APK output goes to delivery\apk (outside source\)
set APKDIR=%ROOT%..\apk
rem Keystore lives OUTSIDE out\ on purpose: out\ is disposable, and a regenerated
rem key would make new builds un-installable over an already installed package.
set KEYSTORE=%ROOT%keystore\debug.jks

rmdir /s /q "%INT%" 2>nul
mkdir "%INT%\gen" "%INT%\classes" "%INT%\dex" 2>nul

echo [1/7] aapt2 compile + link ...
python "%ROOT%tools\patch_manifest.py" "%APP%\AndroidManifest.xml" "%INT%\AndroidManifest.xml" || goto :err
"%BT%\aapt2.exe" compile --dir "%APP%\res" -o "%OUT%\res.zip" || goto :err
"%BT%\aapt2.exe" link -o "%OUT%\unsigned.apk" -I "%AJAR%" --manifest "%INT%\AndroidManifest.xml" -A "%APP%\assets" --java "%INT%\gen" --min-sdk-version 24 --target-sdk-version 34 --version-code 21 --version-name 1.0.2 "%OUT%\res.zip" || goto :err

echo [2/7] javac ...
set SRCS=
for /r "%INT%\gen" %%f in (*.java) do set SRCS=!SRCS! "%%f"
for /r "%APP%\java" %%f in (*.java) do set SRCS=!SRCS! "%%f"
javac --release 11 -encoding UTF-8 -cp "%AJAR%" -d "%INT%\classes" !SRCS! || goto :err

echo [3/7] d8 dex ...
set CLS=
for /r "%INT%\classes" %%f in (*.class) do set CLS=!CLS! "%%f"
call "%BT%\d8.bat" --release --lib "%AJAR%" --min-api 24 --output "%INT%\dex" !CLS! || goto :err

echo [4/7] insert classes.dex + native libraries ...
rem 2026-11-02 t9 re-enabled: librime_jni.so depends on libonnxruntime.so, keep both.
set PREBUILT=%ROOT%..\prebuilt\arm64-v8a
set RIMELIB=%PREBUILT%\librime_jni.so
set ONNXlib=%PREBUILT%\libonnxruntime.so
if not exist "%RIMELIB%" (
    echo MISSING: %RIMELIB%
    echo Put prebuilt arm64-v8a libraries in prebuilt\ or see docs\build-native.md
    goto :err
)
if not exist "%ONNXlib%" (
    echo MISSING: %ONNXlib%
    echo Put prebuilt arm64-v8a libraries in prebuilt\ or see docs\build-native.md
    goto :err
)
python "%ROOT%tools\zip_insert.py" "%OUT%\unsigned.apk" "%OUT%\unsigned_patched.apk" classes.dex=%INT%\dex\classes.dex lib/arm64-v8a/librime_jni.so=%RIMELIB% lib/arm64-v8a/libonnxruntime.so=%ONNXlib% || goto :err

echo [5/7] zipalign ...
"%BT%\zipalign.exe" -f 4 "%OUT%\unsigned_patched.apk" "%OUT%\aligned.apk" || goto :err

echo [6/7] apksigner sign ...
if not exist "%ROOT%keystore" mkdir "%ROOT%keystore"
if not exist "%KEYSTORE%" (
    keytool -genkeypair -keystore "%KEYSTORE%" -alias jhf -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=JieHuaFa,O=JHF,C=CN" || goto :err
)
if not exist "%APKDIR%" mkdir "%APKDIR%"
call "%BT%\apksigner.bat" sign --ks "%KEYSTORE%" --ks-pass pass:android --key-pass pass:android --out "%APKDIR%\JieHuaFa.apk" "%OUT%\aligned.apk" || goto :err

echo.
echo DONE: %APKDIR%\JieHuaFa.apk
exit /b 0

:err
echo BUILD FAILED
exit /b 1



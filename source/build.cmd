@echo off
rem JieHuaFa one-shot build script (no Gradle needed)
rem Requires: JDK 17, Android SDK (platforms/android-34 + build-tools/35.0.0), Python 3
rem NOTE: keep this file ASCII-only with CRLF line endings; non-ASCII or LF-only breaks cmd parsing.
setlocal enabledelayedexpansion
set PYTHONDONTWRITEBYTECODE=1

rem ---------- local toolchain ----------
set SDK=E:\AndroidSDK
rem Pin JDK 17 (build-tools official target; also avoids JDK 24 native-access warnings)
set JDK=D:\development\Java\jdk-17
if exist "%JDK%\bin\javac.exe" (
    set "JAVA_HOME=%JDK%"
    set "PATH=%JDK%\bin;%PATH%"
)

set BT=%SDK%\build-tools\35.0.0
set AJAR=%SDK%\platforms\android-34\android.jar
set ROOT=%~dp0
set APP=%ROOT%app\src\main
set OUT=%ROOT%out
set INT=%OUT%\interim
rem Keystore lives OUTSIDE out\ on purpose: out\ is disposable, and a regenerated
rem key would make new builds un-installable over an already installed package.
set KEYSTORE=%ROOT%keystore\debug.jks

rmdir /s /q "%INT%" 2>nul
mkdir "%INT%\gen" "%INT%\classes" "%INT%\dex" 2>nul

echo [1/7] aapt2 compile + link ...
python "%ROOT%tools\patch_manifest.py" "%APP%\AndroidManifest.xml" "%INT%\AndroidManifest.xml" || goto :err
"%BT%\aapt2.exe" compile --dir "%APP%\res" -o "%OUT%\res.zip" || goto :err
"%BT%\aapt2.exe" link -o "%OUT%\unsigned.apk" -I "%AJAR%" --manifest "%INT%\AndroidManifest.xml" -A "%APP%\assets" --java "%INT%\gen" --min-sdk-version 24 --target-sdk-version 34 --version-code 1 --version-name 0.0.1 "%OUT%\res.zip" || goto :err

echo [2/7] javac ...
set SRCS=
for /r "%INT%\gen" %%f in (*.java) do set SRCS=!SRCS! "%%f"
for /r "%APP%\java" %%f in (*.java) do set SRCS=!SRCS! "%%f"
javac --release 11 -encoding UTF-8 -cp "%AJAR%" -d "%INT%\classes" !SRCS! || goto :err

echo [3/7] d8 dex ...
set CLS=
for /r "%INT%\classes" %%f in (*.class) do set CLS=!CLS! "%%f"
call "%BT%\d8.bat" --release --lib "%AJAR%" --min-api 24 --output "%INT%\dex" !CLS! || goto :err

echo [4/7] insert classes.dex ...
python "%ROOT%tools\zip_insert.py" "%OUT%\unsigned.apk" "%INT%\dex\classes.dex" classes.dex "%OUT%\unsigned_patched.apk" || goto :err

echo [5/7] zipalign ...
"%BT%\zipalign.exe" -f 4 "%OUT%\unsigned_patched.apk" "%OUT%\aligned.apk" || goto :err

echo [6/7] apksigner sign ...
if not exist "%ROOT%keystore" mkdir "%ROOT%keystore"
if not exist "%KEYSTORE%" (
    keytool -genkeypair -keystore "%KEYSTORE%" -alias jhf -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=JieHuaFa,O=JHF,C=CN" || goto :err
)
call "%BT%\apksigner.bat" sign --ks "%KEYSTORE%" --ks-pass pass:android --key-pass pass:android --out "%OUT%\JieHuaFa.apk" "%OUT%\aligned.apk" || goto :err

echo.
echo DONE: %OUT%\JieHuaFa.apk
exit /b 0

:err
echo BUILD FAILED
exit /b 1

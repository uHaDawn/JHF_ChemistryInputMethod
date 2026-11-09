@echo off
rem SheetNav unit tests (plain Java, no Android SDK / JUnit required)
rem Requires: JAVA_HOME pointing to a JDK 17 install
setlocal
if not exist "%JAVA_HOME%\bin\javac.exe" (
    echo ERROR: set JAVA_HOME to a JDK 17 installation
    exit /b 1
)
set "PATH=%JAVA_HOME%\bin;%PATH%"
set ROOT=%~dp0
set OUT=%ROOT%out\test-classes
rmdir /s /q "%OUT%" 2>nul
mkdir "%OUT%" 2>nul
javac --release 11 -encoding UTF-8 -d "%OUT%" "%ROOT%app\src\main\java\com\jhf\jiehuafa\SheetNav.java" "%ROOT%app\src\test\java\com\jhf\jiehuafa\SheetNavTest.java" || exit /b 1
java -cp "%OUT%" com.jhf.jiehuafa.SheetNavTest || exit /b 1

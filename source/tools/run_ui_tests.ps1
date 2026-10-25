<#
  接化发 · 一键 UI 回归

  用法：
    powershell -ExecutionPolicy Bypass -File tools\run_ui_tests.ps1
    powershell -ExecutionPolicy Bypass -File tools\run_ui_tests.ps1 -SkipBuild      # 跳过构建与安装
    powershell -ExecutionPolicy Bypass -File tools\run_ui_tests.ps1 -UpdateGolden   # 用本轮截图刷新基线

  覆盖：
    L1 构建产物（badging / 签名 / dex 类名）
    L2 逻辑自检（词库 + 拼音 DP）
    L3 主题基线（4 主题 x 深浅色 + 关于页）
    L4 符号页 5 分类 tab 高亮
    L5 几何还原（控件尺寸 + 卡片间距，对比 figma_spec.json）
    L6 视觉回归（golden vs current 像素比对）

  依赖：Android SDK + JDK17 + Python3；模拟器/真机需已连接（adb devices）。
#>
param(
    [switch]$SkipBuild,
    [switch]$UpdateGolden
)

$ErrorActionPreference = "Continue"
$tools   = Split-Path -Parent $MyInvocation.MyCommand.Path
$proj    = Split-Path -Parent $tools
$adb     = "D:\development\Android\Sdk\platform-tools\adb.exe"
$bt      = "D:\development\Android\Sdk\build-tools\35.0.0"
$py      = "python"
$pkg     = "com.jhf.jiehuafa"
$imeId   = "com.jhf.jiehuafa/.ime.JhfImeService"
$ts      = Get-Date -Format "yyyyMMdd_HHmmss"
$report  = Join-Path $proj "tests\reports\$ts"
$current = Join-Path $proj "tests\current"
$golden  = Join-Path $proj "tests\golden"
$diffdir = Join-Path $report "diffs"
New-Item -ItemType Directory -Force -Path $report, $current, $diffdir | Out-Null

$script:failed = @()

function Run-Py([string]$name, [string[]]$pyArgs) {
    Write-Host "`n==== $name ====" -ForegroundColor Cyan
    $log = Join-Path $report ($name + ".log")
    & $py @pyArgs 2>&1 | Tee-Object -FilePath $log
    if ($LASTEXITCODE -ne 0) {
        $script:failed += $name
        Write-Host "  -> FAIL" -ForegroundColor Red
    } else {
        Write-Host "  -> PASS" -ForegroundColor Green
    }
}

Write-Host "`n########## 接化发 UI 回归 $ts ##########" -ForegroundColor Yellow

# ---------- L1 构建 ----------
if (-not $SkipBuild) {
    Write-Host "`n==== L1 构建 ====" -ForegroundColor Cyan
    & cmd /c "cd /d $proj && build.cmd" 2>&1 |
        Tee-Object -FilePath (Join-Path $report "L1_build.log")
    if ($LASTEXITCODE -ne 0) {
        $script:failed += "L1_build"
        Write-Host "  -> FAIL（构建失败，终止）" -ForegroundColor Red
        exit 1
    }

    Write-Host "`n---- L1 产物核验 ----" -ForegroundColor Cyan
    $badging = & "$bt\aapt2.exe" dump badging (Join-Path $proj "out\JieHuaFa.apk") 2>&1
    $okIme = ($badging | Select-String "provides-component:'ime'") -ne $null
    $verify = & "$bt\apksigner.bat" verify (Join-Path $proj "out\JieHuaFa.apk") 2>&1
    $okSign = $LASTEXITCODE -eq 0
    Write-Host ("  provides-component:ime = {0}" -f $okIme)
    Write-Host ("  apksigner verify      = {0}" -f $okSign)
    if (-not ($okIme -and $okSign)) { $script:failed += "L1_verify" }

    Write-Host "`n---- L1 安装 ----" -ForegroundColor Cyan
    # 用 *> 重定向：adb 会把 "All files should be loaded" 写到 stderr，
    # PowerShell 5.1 会因此抛出 NativeCommandError 干扰脚本。
    $installLog = Join-Path $report "L1_install.log"
    & $adb install -r (Join-Path $proj "out\JieHuaFa.apk") *> $installLog
    if ($LASTEXITCODE -ne 0) { $script:failed += "L1_install" }
}

# ---------- 环境预设 ----------
Write-Host "`n==== 环境预设 ====" -ForegroundColor Cyan
& $adb shell settings put secure show_ime_with_hard_keyboard 1 | Out-Null
& $adb shell ime enable $imeId | Out-Null
& $adb shell ime set $imeId | Out-Null
Write-Host ("  default_input_method = {0}" -f (& $adb shell settings get secure default_input_method))

# ---------- L2 逻辑自检 ----------
Run-Py "L2_matcher" @("tools\test_matcher.py")

# ---------- 采集 ----------
Write-Host "`n==== 采集当前界面 ====" -ForegroundColor Cyan
Run-Py "capture" @("tools\capture_baseline.py", "--out", $current)

# ---------- L3 主题基线 ----------
Run-Py "L3_theme" @("tools\verify_theme.py", $current)

# ---------- L4 符号页分类 ----------
Run-Py "L4_tabs" @(
    "tools\verify_tabs.py",
    "$current\kbd_sym_0_duliang.png:0",
    "$current\kbd_sym_1_wuzhi.png:1",
    "$current\kbd_sym_2_fanying.png:2",
    "$current\kbd_sym_3_jiaobiao.png:3",
    "$current\kbd_sym_4_qita.png:4"
)

# ---------- L5 几何还原 ----------
Run-Py "L5_geometry" @("tools\geometry_probe.py", $current)

# ---------- L6 视觉回归 ----------
if ($UpdateGolden) {
    Write-Host "`n==== 刷新基线（-UpdateGolden）====" -ForegroundColor Yellow
    # 只收基线类截图（主界面/关于页/键盘）。it_* 是交互用例的运行产物，
    # 随运行顺序变化，不纳入视觉基线；顺手清掉历史遗留，避免整目录拷贝把它们带回来。
    Remove-Item "$golden\it_*.png", "$golden\_probe.png" -Force -ErrorAction SilentlyContinue
    Copy-Item "$current\main_*.png", "$current\about_*.png", "$current\kbd_*.png" $golden -Force
    Write-Host "  基线已更新：$golden"
} else {
    Run-Py "L6_visual" @("tools\visual_diff.py", $golden, $current, "--out", $diffdir)
}

# ---------- L7 交互回归 ----------
# 放最后：会启动 Chrome / 重启 App，改变界面状态
Run-Py "L7_interaction" @("tools\interaction_tests.py", $current)

# ---------- 汇总 ----------
$summary = @()
$summary += "# 接化发 UI 回归报告"
$summary += ""
$summary += "- 时间：$ts"
$summary += "- 设备：$((& $adb devices | Select-Object -Skip 1 | Select-Object -First 1))"
$summary += "- 基线：$golden"
$summary += "- 产物目录：$report"
$summary += ""
$summary += "## 结果"
$summary += ""
if ($script:failed.Count -eq 0) {
    $summary += "全部通过 ✅"
} else {
    $summary += "失败项："
    foreach ($f in $script:failed) { $summary += "- ``$f``" }
    $summary += ""
    $summary += "差异图见 ``diffs/`` 目录。"
}
$summary | Set-Content -Encoding UTF8 (Join-Path $report "summary.md")

Write-Host "`n########## 汇总 ##########" -ForegroundColor Yellow
if ($script:failed.Count -eq 0) {
    Write-Host "全部通过" -ForegroundColor Green
} else {
    Write-Host ("失败 {0} 项：" -f $script:failed.Count) -ForegroundColor Red
    $script:failed | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
}
Write-Host "报告：$(Join-Path $report 'summary.md')"
exit ($(if ($script:failed.Count -eq 0) { 0 } else { 1 }))

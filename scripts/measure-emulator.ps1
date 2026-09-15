[CmdletBinding()]
param(
    [string]$Serial = 'emulator-5580',
    [int]$Samples = 5,
    [string]$Sdk = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { 'C:\Users\30622\AppData\Local\Android\Sdk' })
)
$ErrorActionPreference = 'Stop'
if ($Serial -notmatch '^emulator-\d+$') { throw 'This script is for the dedicated development emulator only.' }
if ($Samples -lt 1 -or $Samples -gt 20) { throw 'Samples must be 1–20.' }
$projectRoot = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $Sdk 'platform-tools\adb.exe'
$apk = Join-Path $projectRoot 'app\build\outputs\apk\release\app-x86_64-release.apk'
$output = Join-Path $projectRoot 'artifacts'
New-Item -ItemType Directory -Path $output -Force | Out-Null
if (-not (Test-Path -LiteralPath $apk)) { throw 'Build Release first.' }
$watch = [Diagnostics.Stopwatch]::StartNew()
$install = & $adb -s $Serial install -r $apk 2>&1
$watch.Stop()
if ($LASTEXITCODE -ne 0 -or $install -notcontains 'Success') { throw "APK installation failed: $install" }
$installMs = $watch.ElapsedMilliseconds
$runs = @()
for ($i = 1; $i -le $Samples; $i++) {
    & $adb -s $Serial shell am force-stop dev.mediasearch
    if ($LASTEXITCODE -ne 0) { throw 'force-stop failed' }
    $launch = (& $adb -s $Serial shell am start -W -n dev.mediasearch/.MainActivity) -join "`n"
    if ($LASTEXITCODE -ne 0 -or $launch -notmatch '(?m)^Status: ok') { throw "Launch failed: $launch" }
    $total = [regex]::Match($launch, 'TotalTime: (\d+)')
    if (-not $total.Success) { throw "No launch timing: $launch" }
    $runs += [ordered]@{ sample = $i; total_ms = [int]$total.Groups[1].Value; raw = $launch }
}
$timings = @($runs | ForEach-Object { $_.total_ms } | Sort-Object)
$report = [ordered]@{
    measured_at = (Get-Date).ToString('o')
    environment = 'Android emulator; host NAT and software GPU; NOT a phone benchmark'
    serial = $Serial
    apk_bytes = (Get-Item -LiteralPath $apk).Length
    apk_sha256 = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLower()
    install_kind = 'adb streamed update install (-r); not a fresh user package-installer flow'
    install_ms = $installMs
    startup_kind = 'process cold starts, am force-stop then am start -W; system caches retained; TTID not input responsiveness'
    samples = $runs
    p50_nearest_rank_ms = $timings[[Math]::Ceiling($Samples * 0.50) - 1]
    p95_nearest_rank_ms = $timings[[Math]::Ceiling($Samples * 0.95) - 1]
}
$report | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $output 'release-emulator-metrics.json') -Encoding utf8
& $adb -s $Serial shell dumpsys meminfo dev.mediasearch | Set-Content -LiteralPath (Join-Path $output 'release-emulator-meminfo.txt') -Encoding utf8
$report | ConvertTo-Json -Depth 6

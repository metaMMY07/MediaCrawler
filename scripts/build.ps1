<#
.SYNOPSIS
    Build driver for the dev.mediasearch Android app.

.DESCRIPTION
    Runs the standard Gradle entry points with a fully process-local environment:
    JAVA_HOME, ANDROID_HOME/ANDROID_SDK_ROOT and GRADLE_USER_HOME are set only for the
    child process. No system or user environment variable is modified, and no global
    Gradle configuration is touched.

    This script NEVER deletes files or directories and never runs `clean`.
    It always exits with the real Gradle exit code.

.PARAMETER Target
    Debug   -> :app:assembleDebug
    Release -> :app:assembleRelease
    Test    -> :app:testDebugUnitTest
    Lint    -> :app:lintDebug
    Deps    -> :app:dependencies (debugRuntimeClasspath)
    Tasks   -> :app:tasks
    Help    -> print usage (default)

.PARAMETER Offline
    Pass --offline (use only already-cached artifacts).

.PARAMETER NoDaemon
    Pass --no-daemon.

.PARAMETER GradleUserHome
    Override the Gradle user home. Defaults to <repo>\.gradle-user-home.

.PARAMETER DryRun
    Print the resolved Gradle command line and exit 0 without running it.

.EXAMPLE
    pwsh -File scripts\build.ps1 Debug
    pwsh -File scripts\build.ps1 Release

.NOTES
    Exit codes: whatever Gradle returns. 2 = bad usage, 3 = toolchain not found.
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('Debug', 'Release', 'Test', 'Lint', 'Deps', 'Tasks', 'Help')]
    [string]$Target = 'Help',

    [switch]$Offline,
    [switch]$NoDaemon,
    [string]$GradleUserHome,
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# ---------------------------------------------------------------- paths (all local)
$RepoRoot = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path (Join-Path $RepoRoot 'settings.gradle.kts'))) {
    Write-Host "ERROR: settings.gradle.kts not found under '$RepoRoot'." -ForegroundColor Red
    exit 2
}

$JdkHome    = 'D:\CodexToolchains\jdk17\jdk-17.0.16+8'
$AndroidSdk = 'C:\Users\30622\AppData\Local\Android\Sdk'

# Gradle state is stored durably in <repo>\.gradle-user-home, but Gradle is *pointed at*
# an ASCII-only directory junction to it. Reason: Gradle forks its daemon with
# "-javaagent:<GRADLE_USER_HOME>\..." and "-cp <GRADLE_USER_HOME>\..." on the command
# line, and the JDK decodes command-line arguments through the ANSI code page (Cp1252
# here), which corrupts this project's non-ASCII path into "????" and kills the daemon.
# A junction gives an ASCII path to the very same files: nothing is copied, and nothing
# is stored outside the project.
#   NOTE: an 8.3 short name does NOT work here -- the JVM rejects a short path in
#   -javaagent with "Unexpected error (103) returned by AddToSystemClassLoaderSearch".
$GradleHomeStore = Join-Path $RepoRoot '.gradle-user-home'

function New-AsciiGradleHomeAlias {
    param([Parameter(Mandatory)][string]$StorePath)

    $candidates = @()
    if ($env:TEMP) { $candidates += (Join-Path $env:TEMP 'mediasearch-gradle-home') }
    if ($env:TMP)  { $candidates += (Join-Path $env:TMP  'mediasearch-gradle-home') }

    foreach ($link in ($candidates | Select-Object -Unique)) {
        if ($link -match '[^\x20-\x7E]') { continue }        # must be pure ASCII
        if (Test-Path -LiteralPath $link) { return $link }   # already aliased
        if (-not (Test-Path -LiteralPath $StorePath)) {
            New-Item -ItemType Directory -Force -Path $StorePath -ErrorAction SilentlyContinue | Out-Null
        }
        $null = & cmd.exe /c "mklink /J `"$link`" `"$StorePath`"" 2>&1
        if (Test-Path -LiteralPath $link) { return $link }
    }
    return $null
}

if (-not $GradleUserHome) {
    if (-not (Test-Path -LiteralPath $GradleHomeStore)) {
        New-Item -ItemType Directory -Force -Path $GradleHomeStore -ErrorAction SilentlyContinue | Out-Null
    }
    $AsciiHome = New-AsciiGradleHomeAlias -StorePath $GradleHomeStore
    if ($AsciiHome) {
        $GradleUserHome = $AsciiHome
    } else {
        Write-Host 'WARNING: could not create an ASCII junction alias for the Gradle home.' -ForegroundColor Yellow
        Write-Host '         Falling back to the project path. The Gradle daemon will fail while' -ForegroundColor Yellow
        Write-Host '         that path contains non-ASCII characters (see docs/build-setup.md §7).' -ForegroundColor Yellow
        $GradleUserHome = $GradleHomeStore
    }
}

$GradlewBat = Join-Path $RepoRoot 'gradlew.bat'

# ---------------------------------------------------------------- task map
$TaskMap = @{
    'Debug'   = @('--stacktrace', ':app:assembleDebug')
    'Release' = @('--stacktrace', ':app:assembleRelease')
    'Test'    = @('--stacktrace', ':app:testDebugUnitTest')
    'Lint'    = @('--stacktrace', ':app:lintDebug')
    'Deps'    = @('--stacktrace', ':app:dependencies', '--configuration', 'debugRuntimeClasspath')
    'Tasks'   = @('--stacktrace', ':app:tasks', '--all')
}

if ($Target -eq 'Help') {
    Write-Host ''
    Write-Host 'dev.mediasearch build driver' -ForegroundColor Cyan
    Write-Host ''
    Write-Host '  pwsh -File scripts\build.ps1 Debug     # :app:assembleDebug'
    Write-Host '  pwsh -File scripts\build.ps1 Release   # :app:assembleRelease (R8, test-signed)'
    Write-Host '  pwsh -File scripts\build.ps1 Test      # :app:testDebugUnitTest'
    Write-Host '  pwsh -File scripts\build.ps1 Lint      # :app:lintDebug'
    Write-Host '  pwsh -File scripts\build.ps1 Deps      # dependency tree'
    Write-Host ''
    Write-Host '  Options: -Offline  -NoDaemon  -GradleUserHome <path>  -DryRun'
    Write-Host ''
    Write-Host "  JAVA_HOME        = $JdkHome"
    Write-Host "  ANDROID_SDK_ROOT = $AndroidSdk"
    Write-Host "  GRADLE_USER_HOME = $GradleUserHome"
    Write-Host ''
    exit 0
}

# ---------------------------------------------------------------- toolchain checks
if (-not (Test-Path (Join-Path $JdkHome 'bin\java.exe'))) {
    Write-Host "ERROR: JDK not found at '$JdkHome'." -ForegroundColor Red
    exit 3
}
if (-not (Test-Path $AndroidSdk)) {
    Write-Host "ERROR: Android SDK not found at '$AndroidSdk'." -ForegroundColor Red
    exit 3
}
if (-not (Test-Path $GradlewBat)) {
    Write-Host "ERROR: gradlew.bat not found at '$GradlewBat'." -ForegroundColor Red
    exit 3
}
if (-not (Test-Path $GradleUserHome)) {
    New-Item -ItemType Directory -Force -Path $GradleUserHome | Out-Null
}

# ---------------------------------------------------------------- process-local env only
$env:JAVA_HOME         = $JdkHome
$env:ANDROID_HOME      = $AndroidSdk
$env:ANDROID_SDK_ROOT  = $AndroidSdk
$env:GRADLE_USER_HOME  = $GradleUserHome
$env:PATH              = (Join-Path $JdkHome 'bin') + ';' + $env:PATH

# This checkout lives under a non-ASCII path. The JDK decodes the environment block and
# the working directory via sun.jnu.encoding (Cp1252 here), which would turn the project
# path into "????". Forcing UTF-8 keeps them intact. Command-line arguments cannot be
# fixed this way, which is why GRADLE_USER_HOME above is an ASCII directory junction.
# These flags apply to the launcher JVM; the daemon gets its own via org.gradle.jvmargs.
$env:GRADLE_OPTS       = '-Xmx3072m -XX:MaxMetaspaceSize=1024m -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8'

# AGP keeps user-level state (debug.keystore, analytics.settings, cache) in ANDROID_USER_HOME,
# which defaults to %USERPROFILE%\.android -- outside this sandbox, so :app:validateSigningDebug
# dies with "AccessDeniedException: C:\Users\30622\.android\debug.keystore.lock".
# ANDROID_USER_HOME *is* the ".android" directory itself (its contents mirror ~/.android).
# Point it inside the writable, ASCII-aliased Gradle home so the debug keystore is stable
# across runs (a regenerated key would change the APK signature between builds).
# ANDROID_PREFS_ROOT must NOT be set as well: setting both makes AGP fail to create
# AndroidLocationsBuildService ("Could not create provider for value source ...").
$env:ANDROID_USER_HOME = Join-Path $GradleUserHome '.android'
Remove-Item Env:ANDROID_PREFS_ROOT -ErrorAction SilentlyContinue
if (-not (Test-Path $env:ANDROID_USER_HOME)) {
    New-Item -ItemType Directory -Force -Path $env:ANDROID_USER_HOME | Out-Null
}

$gradleArgs = @()
$gradleArgs += $TaskMap[$Target]
$gradleArgs += '--console=plain'
if ($Offline)  { $gradleArgs += '--offline' }
if ($NoDaemon) { $gradleArgs += '--no-daemon' }

Write-Host ''
Write-Host "== build.ps1 target=$Target ==" -ForegroundColor Cyan
Write-Host "   JAVA_HOME        = $env:JAVA_HOME"
Write-Host "   ANDROID_SDK_ROOT = $env:ANDROID_SDK_ROOT"
Write-Host "   GRADLE_USER_HOME = $env:GRADLE_USER_HOME"
Write-Host "   ANDROID_USER_HOME= $env:ANDROID_USER_HOME"
Write-Host "   command          = gradlew.bat $($gradleArgs -join ' ')"
Write-Host ''

if ($DryRun) {
    Write-Host '[dry-run] not executing.' -ForegroundColor Yellow
    exit 0
}

# ---------------------------------------------------------------- run (no clean, no deletes)
# Windows PowerShell 5.1 wraps every line a native command writes to stderr in an
# ErrorRecord; combined with $ErrorActionPreference = 'Stop' that aborts this script
# before Gradle finishes (Gradle writes daemon notices and progress to stderr).
# Relax the preference for this call only, and always restore it afterwards.
$code = 1
$savedEap = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try {
    & $GradlewBat @gradleArgs 2>&1 | ForEach-Object { "$_" }
} catch {
    Write-Host "ERROR: failed to launch gradlew.bat: $($_.Exception.Message)" -ForegroundColor Red
} finally {
    $ErrorActionPreference = $savedEap
}
if (Test-Path variable:LASTEXITCODE) { $code = $LASTEXITCODE }

Write-Host ''
if ($code -eq 0) {
    Write-Host "== build.ps1: '$Target' SUCCEEDED (exit=$code) ==" -ForegroundColor Green
} else {
    Write-Host "== build.ps1: '$Target' FAILED (exit=$code) ==" -ForegroundColor Red
}
exit $code

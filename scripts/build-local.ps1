[CmdletBinding()]
param(
    [ValidateSet('Debug','Release','Test','Lint','Probe','Verify')][string]$Target = 'Debug',
    [string]$Jdk = $(if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'D:\CodexToolchains\jdk17\jdk-17.0.16+8' }),
    [string]$Sdk = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { 'C:\Users\30622\AppData\Local\Android\Sdk' })
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$buildRoot = $projectRoot
if ($projectRoot -match '[^\x00-\x7F]') {
    $buildRoot = Join-Path $env:USERPROFILE '.codex\mediasearch-workspace'
    if (Test-Path -LiteralPath $buildRoot) {
        $link = Get-Item -LiteralPath $buildRoot
        if ($link.LinkType -ne 'Junction' -or $link.Target.TrimEnd('\') -ne $projectRoot.TrimEnd('\')) {
            throw "Build junction exists but points elsewhere: $buildRoot"
        }
    } else {
        New-Item -ItemType Junction -Path $buildRoot -Target $projectRoot | Out-Null
    }
}
if (-not (Test-Path -LiteralPath "$Jdk\bin\java.exe")) { throw "JDK not found: $Jdk" }
if (-not (Test-Path -LiteralPath "$Sdk\platforms\android-35\android.jar")) { throw "Android API 35 SDK not found: $Sdk" }
$env:JAVA_HOME = $Jdk
$env:ANDROID_HOME = $Sdk
$env:ANDROID_SDK_ROOT = $Sdk
$env:GRADLE_USER_HOME = Join-Path $buildRoot '.gradle-user-home'
if (Test-Path -LiteralPath 'D:\CodexToolchains\gradle\caches\modules-2') {
    $env:GRADLE_RO_DEP_CACHE = 'D:\CodexToolchains\gradle\caches'
}
$taskMap = @{
    Debug = @(':app:assembleDebug')
    Release = @(':app:assembleRelease')
    Test = @(':app:testDebugUnitTest')
    Lint = @(':app:lintDebug')
    Probe = @(':app:assembleDebug', ':app:assembleDebugAndroidTest')
    Verify = @(':app:assembleDebug', ':app:testDebugUnitTest', ':app:lintDebug', ':app:assembleRelease')
}
# Use the genuine wrapper launcher directly, avoiding cmd.exe's lossy path expansion.
# No files are deleted and no clean task is invoked.
$wrapperJar = Join-Path $buildRoot 'gradle\wrapper\gradle-wrapper.jar'
Push-Location $buildRoot
try {
    & "$Jdk\bin\java.exe" '-Dfile.encoding=UTF-8' '-Dsun.jnu.encoding=UTF-8' "-Dorg.gradle.java.home=$Jdk" -classpath $wrapperJar org.gradle.wrapper.GradleWrapperMain -p $buildRoot @($taskMap[$Target]) --console=plain --max-workers=2
    $result = $LASTEXITCODE
} finally { Pop-Location }
exit $result

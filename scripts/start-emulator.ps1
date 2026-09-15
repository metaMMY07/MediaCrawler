[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$sdk = 'C:\Users\30622\AppData\Local\Android\Sdk'
$env:ANDROID_AVD_HOME = 'C:\Users\30622\.codex\mediasearch-avd'
if (-not (Test-Path -LiteralPath "$env:ANDROID_AVD_HOME\MediaSearchApi35.ini")) {
    throw 'Create the dedicated MediaSearchApi35 AVD first; see docs/build-verified.md.'
}
$devices = & "$sdk\platform-tools\adb.exe" devices
if ($devices -match 'emulator-5580\s+device') { Write-Output 'MediaSearch emulator already online'; exit 0 }
Start-Process -FilePath "$sdk\emulator\emulator.exe" -ArgumentList '-avd MediaSearchApi35 -port 5580 -no-window -no-audio -no-snapshot -no-boot-anim -gpu swiftshader_indirect' -WindowStyle Hidden -RedirectStandardOutput "$env:ANDROID_AVD_HOME\emulator.stdout.log" -RedirectStandardError "$env:ANDROID_AVD_HOME\emulator.stderr.log" -PassThru | Select-Object Id

param([switch]$SkipBuild, [string]$Avd = '')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent

# Prefer the workspace's ignored .tools directory, then local.properties, then ANDROID_HOME.
function Resolve-Sdk {
    $local = Join-Path $projectRoot '.tools/sdk'
    if (Test-Path -LiteralPath (Join-Path $local 'platform-tools/adb.exe')) { return $local }
    $properties = Join-Path $projectRoot 'local.properties'
    if (Test-Path -LiteralPath $properties) {
        $match = Select-String -LiteralPath $properties -Pattern '^sdk\.dir=(.+)$' | Select-Object -First 1
        if ($match) {
            $candidate = $match.Matches[0].Groups[1].Value -replace '\\:', ':'
            if (Test-Path -LiteralPath (Join-Path $candidate 'platform-tools/adb.exe')) { return $candidate }
        }
    }
    if ($env:ANDROID_HOME) { return $env:ANDROID_HOME }
    throw 'Prepare the Android SDK first; see README.md.'
}
$sdk = Resolve-Sdk
$env:ANDROID_HOME = $sdk
$avdHome = Join-Path (Split-Path $sdk -Parent) 'avd'
if (Test-Path -LiteralPath $avdHome) { $env:ANDROID_AVD_HOME = $avdHome }
$cache = Join-Path $projectRoot '.gradle-user-home'
if (Test-Path -LiteralPath $cache) { $env:GRADLE_USER_HOME = $cache }
$adb = Join-Path $sdk 'platform-tools/adb.exe'
$emulator = Join-Path $sdk 'emulator/emulator.exe'
if (!(Test-Path -LiteralPath $emulator)) { throw 'Prepare the Android Emulator SDK first; see README.md.' }
if (!$env:JAVA_HOME) {
    $jdk = Get-ChildItem (Join-Path $env:ProgramFiles 'Java') -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -match '^jdk-(17|21)' } | Select-Object -First 1
    if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
}
Push-Location $projectRoot
try {
    if (!$SkipBuild) {
        & './gradlew.bat' :app:assembleDebug --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Debug build failed.' }
    }
    if (!$Avd) {
        $avds = @(& $emulator -list-avds 2>$null)
        $Avd = if ($avds -contains 'chronota') { 'chronota' } else { $avds | Select-Object -First 1 }
    }
    if (!$Avd) { throw 'Create an Android Virtual Device first; see README.md.' }
    $connected = & $adb devices
    if (!($connected -match 'emulator-5554\s+device')) {
        Start-Process $emulator -ArgumentList @('-avd',$Avd,'-port','5554','-no-snapshot','-no-boot-anim','-gpu','software','-no-audio','-timezone','Asia/Shanghai') -WindowStyle Normal
    }
    $deadline = (Get-Date).AddMinutes(3)
    do {
        Start-Sleep -Seconds 2
        $booted = & $adb -s emulator-5554 shell getprop sys.boot_completed 2>$null
    } until ($booted -match '1' -or (Get-Date) -ge $deadline)
    if ($booted -notmatch '1') { throw 'The emulator did not finish booting.' }
    & $adb -s emulator-5554 install -r 'app/build/outputs/apk/debug/app-debug.apk'
    if ($LASTEXITCODE -ne 0) { throw 'APK installation failed.' }
    & $adb -s emulator-5554 shell cmd locale set-app-locales app.chronota --user 0 --locales zh-CN
    & $adb -s emulator-5554 shell am start -n app.chronota/.MainActivity
} finally { Pop-Location }

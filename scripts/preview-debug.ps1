param([switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$env:ANDROID_HOME = Join-Path $projectRoot '.tools/sdk'
$env:ANDROID_AVD_HOME = Join-Path $projectRoot '.tools/avd'
$env:GRADLE_USER_HOME = Join-Path $projectRoot '.gradle-user-home'
$adb = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
$emulator = Join-Path $env:ANDROID_HOME 'emulator/emulator.exe'
if (!(Test-Path -LiteralPath $emulator)) { throw 'Prepare the Android Emulator SDK and the planrecord AVD first; see README.md.' }
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
    $connected = & $adb devices
    if (!($connected -match 'emulator-5554\s+device')) {
        Start-Process $emulator -ArgumentList @('-avd','planrecord','-port','5554','-no-snapshot','-no-boot-anim','-gpu','software','-no-audio','-timezone','Asia/Shanghai') -WindowStyle Normal
    }
    $deadline = (Get-Date).AddMinutes(3)
    do {
        Start-Sleep -Seconds 2
        $booted = & $adb -s emulator-5554 shell getprop sys.boot_completed 2>$null
    } until ($booted -match '1' -or (Get-Date) -ge $deadline)
    if ($booted -notmatch '1') { throw 'The emulator did not finish booting.' }
    & $adb -s emulator-5554 install -r 'app/build/outputs/apk/debug/app-debug.apk'
    if ($LASTEXITCODE -ne 0) { throw 'APK installation failed.' }
    & $adb -s emulator-5554 shell cmd locale set-app-locales app.planrecord --user 0 --locales zh-CN
    & $adb -s emulator-5554 shell am start -n app.planrecord/.MainActivity
} finally { Pop-Location }

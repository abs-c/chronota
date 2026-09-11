param([switch]$SkipBuild, [string]$Version = '')
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
$env:ANDROID_HOME = Resolve-Sdk
$cache = Join-Path $projectRoot '.gradle-user-home'
if (Test-Path -LiteralPath $cache) { $env:GRADLE_USER_HOME = $cache }
if (!$env:JAVA_HOME) {
    $jdk = Get-ChildItem (Join-Path $env:ProgramFiles 'Java') -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -match '^jdk-(17|21)' } | Select-Object -First 1
    if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
}
Push-Location $projectRoot
try {
    if (!$Version) {
        $match = Select-String -LiteralPath 'app/build.gradle.kts' -Pattern '^\s*versionName\s*=\s*"([^"]+)"' | Select-Object -First 1
        if (!$match) { throw 'Could not read versionName from app/build.gradle.kts.' }
        $Version = $match.Matches[0].Groups[1].Value
    }
    if (!$SkipBuild) {
        & './gradlew.bat' :app:assembleRelease --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Release build failed.' }
    }
    $apk = Join-Path $projectRoot 'app/build/outputs/apk/release/app-release.apk'
    if (!(Test-Path -LiteralPath $apk)) { throw "Release APK not found at $apk." }
    $desktop = [Environment]::GetFolderPath('Desktop')
    if (!(Test-Path -LiteralPath $desktop)) { throw 'Could not find the Desktop folder.' }
    # One copy per release, named so successive previews stay distinguishable on the Desktop.
    $target = Join-Path $desktop "Chronota-$Version.apk"
    Copy-Item -LiteralPath $apk -Destination $target -Force
    $size = [math]::Round((Get-Item -LiteralPath $target).Length / 1MB, 2)
    Write-Host "Release Chronota $Version ($size MB) copied to $target"
} finally { Pop-Location }

# DroidPad — build + install via PowerShell
# Uso:
#   .\build.ps1              # gera debug + instala se tiver celular no adb
#   .\build.ps1 -NoInstall   # só gera o APK
#   .\build.ps1 -Config release
param(
    [ValidateSet("debug", "release")][string]$Config = "debug",
    [switch]$NoInstall
)

$ErrorActionPreference = "Stop"
Set-Location -LiteralPath $PSScriptRoot

$task = if ($Config -eq "release") { "assembleRelease" } else { "assembleDebug" }
Write-Host ">> gradlew $task" -ForegroundColor Cyan
cmd /c "gradlew.bat $task --console=plain 2>&1"

$apk = if ($Config -eq "release") {
    "app\build\outputs\apk\release\app-release.apk"
} else {
    "app\build\outputs\apk\debug\app-debug.apk"
}

if (-not (Test-Path -LiteralPath $apk)) {
    Write-Host "APK nao encontrado: $apk" -ForegroundColor Red
    exit 1
}
Write-Host ">> APK ok: $apk" -ForegroundColor Green

if ($NoInstall) { exit 0 }

$devices = (cmd /c "adb devices 2>&1") -join "`n"
if ($devices -notmatch "`n\S+\s+device") {
    Write-Host "Nenhum celular via adb. APK gerado, instale manual." -ForegroundColor Yellow
    exit 0
}

Write-Host ">> adb install -r $apk" -ForegroundColor Cyan
cmd /c "adb install -r $apk 2>&1"
Write-Host ">> pronto! Abra o DroidPad no celular." -ForegroundColor Green

$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $Root
if (Test-Path ".\gradlew.bat") {
  & .\gradlew.bat clean test assembleDebug
} elseif (Get-Command gradle -ErrorAction SilentlyContinue) {
  & gradle clean test assembleDebug
} else {
  throw "Gradle is not installed. Open this project in Android Studio or install Gradle 9.6+ and generate the wrapper."
}
Write-Host "APK: $Root\app\build\outputs\apk\debug\app-debug.apk"

@echo off
setlocal
cd /d "%~dp0"
if exist "gradle\wrapper\gradle-wrapper.jar" (
  echo Gradle wrapper is ready.
  goto :done
)
echo Downloading official Gradle 8.7 wrapper from the official Gradle distribution server...
powershell -NoProfile -Command "$ErrorActionPreference='Stop'; $ProgressPreference='SilentlyContinue'; $base='https://services.gradle.org/distributions/gradle-8.7-wrapper.jar'; Invoke-WebRequest -Uri $base -OutFile 'gradle/wrapper/gradle-wrapper.jar'; $expected=(Invoke-WebRequest -Uri ($base+'.sha256')).Content.Trim().ToLower(); $actual=(Get-FileHash 'gradle/wrapper/gradle-wrapper.jar' -Algorithm SHA256).Hash.ToLower(); if ($actual -ne $expected) {throw 'Wrapper SHA-256 mismatch'}"
if errorlevel 1 (
  echo Setup failed. Connect your PC to the internet and run this file again.
  if exist "gradle\wrapper\gradle-wrapper.jar" del "gradle\wrapper\gradle-wrapper.jar"
  pause
  exit /b 1
)
echo Done. Open this project folder in Android Studio.
:done
pause

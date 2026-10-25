@echo off
setlocal
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0gradle.ps1" build %*
exit /b %errorlevel%

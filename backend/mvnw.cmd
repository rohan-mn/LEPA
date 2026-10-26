@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\scripts\maven.ps1" %*
exit /b %ERRORLEVEL%

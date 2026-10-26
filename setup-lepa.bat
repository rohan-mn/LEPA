@echo off
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\setup.ps1"
if errorlevel 1 (
  echo.
  echo Setup failed. Read the message above and README.md.
  pause
  exit /b 1
)
echo.
pause

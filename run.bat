@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
rem Forward to the single launcher (auto-detects bundled runtime / JDK 25,
rem auto-builds, renders a native \r progress bar).
call "%~dp0trial.bat" %*
exit /b %ERRORLEVEL%

@echo off
rem TrialSpawnerFinder CLI (CUDA-accelerated) launcher.
rem Usage: run-cuda.bat --seed 188188 --search-radius 10000 ...
setlocal
cd /d "%~dp0"

rem Use the build JDK recorded by setup.ps1 when available.
if exist "%~dp0.runtime\build-java-home.txt" (
    set /p JAVA_HOME=<"%~dp0.runtime\build-java-home.txt"
)

call "%~dp0gradlew.bat" run --args="%*"
exit /b %ERRORLEVEL%

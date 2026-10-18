@echo off
setlocal
cd /d "%~dp0"

rem Prefer the bundled runtime; fall back to a JDK 25 install.
set "JAVA_EXE="
if exist "%~dp0runtime\bin\java.exe" set "JAVA_EXE=%~dp0runtime\bin\java.exe"
if not defined JAVA_EXE if defined JDK25_HOME if exist "%JDK25_HOME%\bin\java.exe" set "JAVA_EXE=%JDK25_HOME%\bin\java.exe"
if not defined JAVA_EXE if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if not defined JAVA_EXE (
    for /d %%d in ("%ProgramFiles%\Java\jdk-25*") do if exist "%%d\bin\java.exe" set "JAVA_EXE=%%d\bin\java.exe"
)
if not defined JAVA_EXE (
    echo ERROR: Java 25 not found and the bundled runtime is missing.
    pause
    exit /b 1
)

"%JAVA_EXE%" -jar "%~dp0TrialSpawnerFinder-GUI.jar"
if errorlevel 1 (
    echo.
    echo GUI exited with an error. See the message above.
    pause
)

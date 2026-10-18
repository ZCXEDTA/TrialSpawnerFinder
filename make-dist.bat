@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"

rem ============================================================
rem  Build a self-contained dist\ directory (no Java install needed):
rem    dist\
rem      trial-spawner-finder-2.0.0-beta.1.jar   (CLI fat jar, zero third-party deps)
rem      TrialSpawnerFinder-GUI.jar       (GUI fat jar)
rem      trial.bat                        (CLI launcher: bundled runtime first)
rem      start-gui.bat                    (GUI launcher: bundled runtime first)
rem      finder.properties                (config)
rem      runtime\                         (jlink JRE with java.desktop, ~50 MB)
rem      README.md
rem      LICENSE                          (GPL-3.0)
rem  Optionally zip it into dist.zip.
rem
rem  Prerequisites: JDK 25 (javac + jlink) in JAVA_HOME / JDK25_HOME.
rem ============================================================

set "JDK="
if defined JDK25_HOME if exist "%JDK25_HOME%\bin\jlink.exe" set "JDK=%JDK25_HOME%"
if not defined JDK if defined JAVA_HOME if exist "%JAVA_HOME%\bin\jlink.exe" set "JDK=%JAVA_HOME%"
if not defined JDK if exist "C:\Program Files\Java\jdk-25.0.2\bin\jlink.exe" set "JDK=C:\Program Files\Java\jdk-25.0.2"
if not defined JDK (
    echo ERROR: JDK 25 not found ^(need javac + jlink^). Set JAVA_HOME or JDK25_HOME.
    exit /b 1
)
set "JAVA=%JDK%\bin\java.exe"
set "JAR=minecraft-26.2-runtime\build\libs\trial-spawner-finder-2.0.0-beta.1.jar"
set "GUI_JAR=trial-spawner-finder-gui\build\libs\trial-spawner-finder-gui-2.0.0-beta.1.jar"

rem ---- build the fat jars ----
if not exist "%JAR%" (
    echo Building CLI fat jar...
    call "%~dp0gradlew.bat" :minecraft-26.2-runtime:jar
    if errorlevel 1 exit /b 1
)
if not exist "%GUI_JAR%" (
    echo Building GUI fat jar...
    call "%~dp0gradlew.bat" :trial-spawner-finder-gui:jar
    if errorlevel 1 exit /b 1
)
if not exist "%JAR%" (
    echo ERROR: CLI fat jar not found after build.
    exit /b 1
)
if not exist "%GUI_JAR%" (
    echo ERROR: GUI fat jar not found after build.
    exit /b 1
)

rem ---- assemble dist ----
if exist "dist" rmdir /s /q "dist"
mkdir dist
copy /y "%JAR%" dist\ >nul
copy /y "%GUI_JAR%" "dist\TrialSpawnerFinder-GUI.jar" >nul
copy /y trial.bat dist\ >nul
copy /y start-gui.bat dist\ >nul
copy /y finder.properties dist\ >nul
copy /y README.md dist\ >nul
copy /y LICENSE dist\ >nul

rem ---- jlink runtime (java.desktop required by the Swing GUI) ----
echo Generating jlink runtime...
"%JDK%\bin\jlink" --add-modules java.base,java.desktop,java.logging,java.management,java.naming,java.security.jgss,java.instrument,jdk.unsupported --strip-debug --no-man-pages --no-header-files --compress=zip-6 --output dist\runtime
if errorlevel 1 (
    echo ERROR: jlink failed.
    exit /b 1
)

rem ---- smoke test with the bundled runtime ----
if not exist "dist\runtime\bin\java.exe" (
    echo ERROR: bundled runtime missing java.exe.
    exit /b 1
)
echo Bundled runtime: dist\runtime\bin\java.exe
cd dist
call trial.bat query --coords 0,0 --radius 500 >NUL 2>&1
cd ..
if errorlevel 1 (
    echo WARNING: smoke test failed. Check the dist contents manually.
) else (
    echo Smoke test OK
)

rem ---- zip it (if PowerShell available) ----
if exist dist.zip del dist.zip
powershell.exe -NoProfile -Command "Compress-Archive -Path dist\* -DestinationPath dist.zip -Force" >nul 2>&1
if exist dist.zip (
    echo Distribution: dist.zip
) else (
    echo dist directory ready. zip skipped ^(PowerShell unavailable^).
)

echo.
echo Done. Copy the dist folder (or dist.zip) anywhere - it runs without installing Java:
echo   dist\start-gui.bat
echo   dist\trial.bat --seed 123 --search-radius-blocks 5000
echo   dist\trial.bat query --coords 0,0 --radius 1000
exit /b 0

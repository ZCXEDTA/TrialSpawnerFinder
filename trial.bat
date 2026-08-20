@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"

rem ---- locate the fat jar: project build output, or next to this script (dist) ----
set "JAR=%CD%\minecraft-26.2-runtime\build\libs\trial-spawner-finder-1.5.0.jar"
if not exist "%JAR%" set "JAR=%~dp0trial-spawner-finder-1.5.0.jar"
if not exist "%JAR%" (
    echo ERROR: trial-spawner-finder-1.5.0.jar not found. Build it or use a distribution.
    exit /b 1
)
set "JAVA_EXE="

rem ---- bundled jlink runtime first (the "with-runtime" distribution ships this) ----
if not defined JAVA_EXE if exist "%~dp0runtime\bin\java.exe" (
    set "JAVA_EXE=%~dp0runtime\bin\java.exe"
)

rem ---- then system JDK 25 ----
if not defined JAVA_EXE if defined JDK25_HOME (
    if exist "%JDK25_HOME%\bin\java.exe" set "JAVA_EXE=%JDK25_HOME%\bin\java.exe"
)
if not defined JAVA_EXE if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
)
if not defined JAVA_EXE (
    for /d %%d in ("%ProgramFiles%\Java\jdk-25*") do if exist "%%d\bin\java.exe" set "JAVA_EXE=%%d\bin\java.exe"
    for /d %%d in ("%ProgramFiles%\Eclipse Adoptium\jdk-25*") do if exist "%%d\bin\java.exe" set "JAVA_EXE=%%d\bin\java.exe"
    for /d %%d in ("%ProgramFiles%\Java\graalvm-25*") do if exist "%%d\bin\java.exe" set "JAVA_EXE=%%d\bin\java.exe"
)
if not defined JAVA_EXE (
    where java.exe >nul 2>nul && set "JAVA_EXE=java"
)
if not defined JAVA_EXE (
    echo ERROR: Java 25 was not found. Install JDK 25, set JDK25_HOME/JAVA_HOME, or use the "with-runtime" distribution.
    exit /b 1
)

rem ---- derive JAVA_HOME from JAVA_EXE (needed by gradlew.bat) ----
if /i "%JAVA_EXE%"=="java" (
    for /f "delims=" %%p in ('where java.exe') do if not defined JAVA_EXE_PATH set "JAVA_EXE_PATH=%%p"
) else (
    set "JAVA_EXE_PATH=%JAVA_EXE%"
)
set "JAVA_HOME_FINAL=%JAVA_EXE_PATH:\bin\java.exe=%"
set "JAVA_HOME=%JAVA_HOME_FINAL%"
set "PATH=%JAVA_HOME%\bin;%PATH%"

rem ---- verify Java 25 ----
"%JAVA_EXE%" -version 2>&1 | findstr /i "version" | findstr "25" >nul
if errorlevel 1 (
    echo ERROR: "%JAVA_EXE%" is not Java 25. Install JDK 25 or set JDK25_HOME / JAVA_HOME.
    exit /b 1
)

rem ---- auto-build when the fat jar is missing (dev checkout only, has gradlew.bat) ----
if not exist "%JAR%" if exist "%~dp0gradlew.bat" (
    echo Fat jar not found. Building ^(gradlew clean jar^)...
    call "%~dp0gradlew.bat" :minecraft-26.2-runtime:jar
    if errorlevel 1 exit /b 1
    if not exist "%JAR%" (
        echo ERROR: Build finished but the jar is still missing.
        exit /b 1
    )
)

rem ---- run (forward all arguments; the jar renders a \r progress bar natively) ----
"%JAVA_EXE%" -jar "%JAR%" %*
exit /b %errorlevel%

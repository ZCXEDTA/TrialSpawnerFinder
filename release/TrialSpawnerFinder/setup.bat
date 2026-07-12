@echo off
chcp 65001 >nul
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

set "RUNTIME=%~dp0.runtime"
set "JAVA_DIR=%RUNTIME%\java"
set "SERVER_DIR=%RUNTIME%\server"
set "JDK_ZIP=%RUNTIME%\jdk.zip"
set "JAVA_PATH_FILE=%RUNTIME%\java-path.txt"
set "JDK_MIRROR=https://mirrors.tuna.tsinghua.edu.cn/Adoptium/21/jdk/x64/windows/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10.zip"
set "JDK_FALLBACK=https://download.oracle.com/java/21/latest/jdk-21_windows-x64_bin.zip"
set "FABRIC_URL=https://meta.fabricmc.net/v2/versions/loader/1.21.1/0.16.14/1.1.1/server/jar"
set "API_MIRROR=https://cdn.modrinth.com/data/P7dR8mSH/versions/9xIK4e8l/fabric-api-0.116.6+1.21.1.jar"
set "API_FALLBACK=https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.116.6+1.21.1/fabric-api-0.116.6+1.21.1.jar"

if not exist "trial-spawner-finder.jar" goto missing_files
if not exist "finder.properties" goto missing_files
if not exist "%RUNTIME%" mkdir "%RUNTIME%"
if not exist "%SERVER_DIR%\mods" mkdir "%SERVER_DIR%\mods"

set "JAVA_EXE="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" call :check_java "%JAVA_HOME%\bin\java.exe"
if not defined JAVA_EXE (
    for /f "delims=" %%J in ('where.exe java.exe 2^>nul') do if not defined JAVA_EXE call :check_java "%%J"
)
if not defined JAVA_EXE if exist "%JAVA_DIR%\bin\java.exe" call :check_java "%JAVA_DIR%\bin\java.exe"
if defined JAVA_EXE (
    echo Using existing Java 21: !JAVA_EXE!
    >"%JAVA_PATH_FILE%" echo !JAVA_EXE!
    goto java_ready
)
echo [1/3] Downloading Java 21, about 196 MB...
call :download "%JDK_MIRROR%" "%JDK_FALLBACK%" "%JDK_ZIP%"
if errorlevel 1 goto download_failed
if exist "%JAVA_DIR%" rmdir /s /q "%JAVA_DIR%"
mkdir "%JAVA_DIR%"
tar.exe -xf "%JDK_ZIP%" -C "%JAVA_DIR%" --strip-components=1
if errorlevel 1 goto extract_failed
del /q "%JDK_ZIP%"
if not exist "%JAVA_DIR%\bin\java.exe" goto extract_failed
set "JAVA_EXE=%JAVA_DIR%\bin\java.exe"
>"%JAVA_PATH_FILE%" echo !JAVA_EXE!

:java_ready
echo [2/3] Downloading Minecraft 1.21.1 Fabric server launcher...
if not exist "%SERVER_DIR%\fabric-server-launch.jar" (
    call :download "%FABRIC_URL%" "%FABRIC_URL%" "%SERVER_DIR%\fabric-server-launch.jar"
    if errorlevel 1 goto download_failed
)

echo [3/3] Installing Fabric API and TrialSpawnerFinder...
if not exist "%SERVER_DIR%\mods\fabric-api.jar" (
    call :download "%API_MIRROR%" "%API_FALLBACK%" "%SERVER_DIR%\mods\fabric-api.jar"
    if errorlevel 1 goto download_failed
)
copy /y "trial-spawner-finder.jar" "%SERVER_DIR%\mods\trial-spawner-finder.jar" >nul

echo.
echo Setup completed. Edit finder.properties, then run run.bat.
pause
exit /b 0

:download
curl.exe -fL --retry 2 --connect-timeout 20 -o "%~3" "%~1"
if not errorlevel 1 exit /b 0
echo Primary download source failed. Trying fallback...
if exist "%~3" del /q "%~3"
curl.exe -fL --retry 3 --connect-timeout 20 -o "%~3" "%~2"
exit /b %ERRORLEVEL%

:check_java
"%~1" -XshowSettings:properties -version >nul 2>"%RUNTIME%\java-version.txt"
findstr.exe /c:"java.version = 21." "%RUNTIME%\java-version.txt" >nul
if not errorlevel 1 set "JAVA_EXE=%~1"
del /q "%RUNTIME%\java-version.txt" >nul 2>&1
exit /b 0

:missing_files
echo ERROR: trial-spawner-finder.jar or finder.properties is missing.
goto failed

:download_failed
echo ERROR: Download failed. Check the network and run setup.bat again.
goto failed

:extract_failed
echo ERROR: Java 21 extraction failed. Delete .runtime and retry.

:failed
echo.
pause
exit /b 1

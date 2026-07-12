@echo off
chcp 65001 >nul
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

set "RUNTIME=%~dp0.runtime"
set "JAVA_DIR=%RUNTIME%\java"
set "SERVER_DIR=%RUNTIME%\server"
set "JDK_ZIP=%RUNTIME%\jdk.zip"
set "JAVA_PATH_FILE=%RUNTIME%\java-path.txt"
set "LOADER_VERSION_FILE=%RUNTIME%\fabric-loader-version.txt"
set "JDK_MIRROR=https://mirrors.tuna.tsinghua.edu.cn/github-release/graalvm/graalvm-ce-builds/GraalVM%%20Community%%2025%%20Innovation%%201%%20%%28graal%%2025.1.3%%2C%%20jdk%%2025.0.3%%29/graalvm-community-jdk-25i1-25.0.3_windows-x64_bin.zip"
set "JDK_FALLBACK=https://download.oracle.com/graalvm/25/latest/graalvm-jdk-25_windows-x64_bin.zip"
set "LOADER_VERSION=0.19.3"
set "FABRIC_URL=https://meta.fabricmc.net/v2/versions/loader/1.21.1/%LOADER_VERSION%/1.1.1/server/jar"
set "API_MIRROR=https://cdn.modrinth.com/data/P7dR8mSH/versions/9xIK4e8l/fabric-api-0.116.6+1.21.1.jar"
set "API_FALLBACK=https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.116.6+1.21.1/fabric-api-0.116.6+1.21.1.jar"

if not exist "trial-spawner-finder.jar" goto missing_files
if not exist "finder.properties" goto missing_files
if not exist "%RUNTIME%" mkdir "%RUNTIME%"
if not exist "%SERVER_DIR%\mods" mkdir "%SERVER_DIR%\mods"

set "JAVA_EXE="
set "FALLBACK_JAVA_EXE="
set "FALLBACK_JAVA_MAJOR="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" call :check_graal "%JAVA_HOME%\bin\java.exe"
if not defined JAVA_EXE (
    for /f "delims=" %%J in ('where.exe java.exe 2^>nul') do if not defined JAVA_EXE call :check_graal "%%J"
)
if not defined JAVA_EXE if exist "%JAVA_DIR%\bin\java.exe" call :check_graal "%JAVA_DIR%\bin\java.exe"
if defined JAVA_EXE (
    echo Using existing GraalVM 25: !JAVA_EXE!
    >"%JAVA_PATH_FILE%" echo !JAVA_EXE!
    goto java_ready
)
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" call :check_fallback "%JAVA_HOME%\bin\java.exe"
for /f "delims=" %%J in ('where.exe java.exe 2^>nul') do if not defined FALLBACK_JAVA_EXE call :check_fallback "%%J"
if exist "%JAVA_DIR%\bin\java.exe" if not defined FALLBACK_JAVA_EXE call :check_fallback "%JAVA_DIR%\bin\java.exe"
echo [1/3] Downloading GraalVM 25, about 346 MB...
call :download "%JDK_MIRROR%" "%JDK_FALLBACK%" "%JDK_ZIP%"
if errorlevel 1 goto use_fallback_java
if exist "%JAVA_DIR%" rmdir /s /q "%JAVA_DIR%"
mkdir "%JAVA_DIR%"
tar.exe -xf "%JDK_ZIP%" -C "%JAVA_DIR%" --strip-components=1
if errorlevel 1 goto extract_failed
del /q "%JDK_ZIP%"
if not exist "%JAVA_DIR%\bin\java.exe" goto extract_failed
set "JAVA_EXE="
call :check_graal "%JAVA_DIR%\bin\java.exe"
if not defined JAVA_EXE goto extract_failed
>"%JAVA_PATH_FILE%" echo !JAVA_EXE!

:java_ready
echo [2/3] Downloading Minecraft 1.21.1 Fabric server launcher...
set "INSTALLED_LOADER_VERSION="
if exist "%LOADER_VERSION_FILE%" set /p "INSTALLED_LOADER_VERSION="<"%LOADER_VERSION_FILE%"
if not "!INSTALLED_LOADER_VERSION!"=="%LOADER_VERSION%" (
    call :download "%FABRIC_URL%" "%FABRIC_URL%" "%SERVER_DIR%\fabric-server-launch.jar"
    if errorlevel 1 goto download_failed
    >"%LOADER_VERSION_FILE%" echo %LOADER_VERSION%
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

:check_graal
"%~1" -XshowSettings:properties -version >nul 2>"%RUNTIME%\java-version.txt"
findstr.exe /c:"java.version = 25." "%RUNTIME%\java-version.txt" >nul
if errorlevel 1 goto check_graal_done
findstr.exe /c:"GraalVM" "%RUNTIME%\java-version.txt" >nul
if not errorlevel 1 set "JAVA_EXE=%~1"
:check_graal_done
del /q "%RUNTIME%\java-version.txt" >nul 2>&1
exit /b 0

:check_fallback
"%~1" -XshowSettings:properties -version >nul 2>"%RUNTIME%\java-version.txt"
findstr.exe /c:"java.version = 25." "%RUNTIME%\java-version.txt" >nul
if not errorlevel 1 (
    set "FALLBACK_JAVA_EXE=%~1"
    set "FALLBACK_JAVA_MAJOR=25"
    goto check_fallback_done
)
findstr.exe /c:"java.version = 21." "%RUNTIME%\java-version.txt" >nul
if not errorlevel 1 (
    set "FALLBACK_JAVA_EXE=%~1"
    set "FALLBACK_JAVA_MAJOR=21"
)
:check_fallback_done
del /q "%RUNTIME%\java-version.txt" >nul 2>&1
exit /b 0

:use_fallback_java
if defined FALLBACK_JAVA_EXE (
    echo WARNING: GraalVM download failed. Using existing Java !FALLBACK_JAVA_MAJOR!: !FALLBACK_JAVA_EXE!
    set "JAVA_EXE=!FALLBACK_JAVA_EXE!"
    >"%JAVA_PATH_FILE%" echo !JAVA_EXE!
    goto java_ready
)
goto download_failed

:missing_files
echo ERROR: trial-spawner-finder.jar or finder.properties is missing.
goto failed

:download_failed
echo ERROR: Download failed. Check the network and run setup.bat again.
goto failed

:extract_failed
echo ERROR: GraalVM 25 extraction failed. Delete .runtime and retry.

:failed
echo.
pause
exit /b 1

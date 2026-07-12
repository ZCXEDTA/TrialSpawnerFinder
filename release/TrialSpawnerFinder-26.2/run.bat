@echo off
chcp 65001 >nul
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

set "RUNTIME=%~dp0.runtime"
set "JAVA_PATH_FILE=%RUNTIME%\java-path.txt"
set "SERVER=%RUNTIME%\server"
set "WORLD=%SERVER%\trial-finder-world"

for /f %%I in ('powershell.exe -NoProfile -Command "Get-Date -Format yyyyMMdd-HHmmss-fff"') do set "RESULT_FILE=results-%%I.csv"

if not exist "%JAVA_PATH_FILE%" goto not_installed
set /p "JAVA="<"%JAVA_PATH_FILE%"
if not exist "%JAVA%" goto not_installed
if not exist "%SERVER%\fabric-server-launch.jar" goto not_installed
if not exist "trial-spawner-finder.jar" goto missing_files
if not exist "finder.properties" goto missing_files

set "SEED="
for /f "usebackq tokens=1,* delims==" %%A in ("finder.properties") do (
    if /i "%%A"=="seed" set "SEED=%%B"
)
if not defined SEED goto missing_seed

if exist "%WORLD%" rmdir /s /q "%WORLD%"
if exist "%SERVER%\search.failed" del /q "%SERVER%\search.failed"
copy /y "finder.properties" "%SERVER%\finder.properties" >nul
copy /y "trial-spawner-finder.jar" "%SERVER%\mods\trial-spawner-finder.jar" >nul

>"%SERVER%\eula.txt" echo eula=true
>"%SERVER%\server.properties" echo level-name=trial-finder-world
>>"%SERVER%\server.properties" echo level-seed=!SEED!
>>"%SERVER%\server.properties" echo gamemode=spectator
>>"%SERVER%\server.properties" echo generate-structures=true
>>"%SERVER%\server.properties" echo online-mode=false
>>"%SERVER%\server.properties" echo spawn-protection=0
>>"%SERVER%\server.properties" echo view-distance=2
>>"%SERVER%\server.properties" echo simulation-distance=2
>>"%SERVER%\server.properties" echo max-tick-time=-1
>>"%SERVER%\server.properties" echo sync-chunk-writes=false

echo Starting TrialSpawnerFinder. Seed: !SEED!
pushd "%SERVER%"
"%JAVA%" -Xms512M -Xmx4G -Dfile.encoding=UTF-8 "-Dtrialfinder.output=..\..\!RESULT_FILE!" -jar fabric-server-launch.jar nogui
set "EXIT_CODE=!ERRORLEVEL!"
popd

if exist "%SERVER%\search.failed" (
    echo.
    echo Search failed. See: %SERVER%\search.failed
    set "EXIT_CODE=1"
)
if not "!EXIT_CODE!"=="0" (
    echo.
    echo Search failed. Exit code: !EXIT_CODE!
    echo Server log: %SERVER%\logs\latest.log
) else (
    echo.
    echo Search completed. Results: !RESULT_FILE!
    echo Aligned text: !RESULT_FILE:.csv=.txt!
)
echo.
pause
exit /b !EXIT_CODE!

:not_installed
echo ERROR: Runtime is not installed. Run setup.bat first.
goto failed

:missing_files
echo ERROR: trial-spawner-finder.jar or finder.properties is missing.
goto failed

:missing_seed
echo ERROR: finder.properties has no seed setting.

:failed
echo.
pause
exit /b 1

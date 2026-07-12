$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8

$project = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$logPath = Join-Path $project 'launcher.log'
$englishJdk = 'D:\edgedownload\jdk-21_windows-x64_bin\jdk-21.0.8'
$minecraftJdk = Join-Path $env:APPDATA '.minecraft\runtime\java-runtime-delta'
$exitCode = 1

try {
    Start-Transcript -LiteralPath $logPath -Force | Out-Null
    Set-Location $project

    if (Test-Path (Join-Path $englishJdk 'bin\java.exe')) {
        $env:JAVA_HOME = $englishJdk
    } elseif (Test-Path (Join-Path $minecraftJdk 'bin\java.exe')) {
        $env:JAVA_HOME = $minecraftJdk
    } else {
        throw 'JDK 21 was not found. Run setup.ps1 first.'
    }
    $env:GRADLE_USER_HOME = 'C:\GradleCache'

    $jar = Join-Path $project 'build\libs\trial-spawner-finder-1.0.0.jar'
    if (-not (Test-Path $jar)) {
        throw 'The project has not been built. Run setup.ps1 first.'
    }

    & (Join-Path $project 'scripts\prepare-run.ps1')
    Write-Host "Starting TrialSpawnerFinder with $env:JAVA_HOME"
    & (Join-Path $project 'gradlew.bat') runServer --console=plain
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        throw "TrialSpawnerFinder exited with code $exitCode."
    }
    Write-Host 'Search completed successfully.'
} catch {
    Write-Host ''
    Write-Host ('ERROR: ' + $_.Exception.Message) -ForegroundColor Red
    Write-Host ('Full launcher log: ' + $logPath)
    $exitCode = 1
} finally {
    try { Stop-Transcript | Out-Null } catch { }
    Write-Host ''
    Read-Host 'Press Enter to close this window'
}

exit $exitCode

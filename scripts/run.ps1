$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8

$project = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$logPath = Join-Path $project 'launcher.log'
$exitCode = 1

try {
    Start-Transcript -LiteralPath $logPath -Force | Out-Null
    Set-Location $project

    $javaHomes = @(@(
        'D:\edgedownload\graalvm-jdk-25_windows-x64_bin\graalvm-jdk-25.0.2+10.1',
        (Join-Path $project 'java'),
        $env:JAVA_HOME
    ) | Where-Object {
        if (-not $_) { return $false }
        $java = Join-Path $_ 'bin\java.exe'
        (Test-Path $java) -and ((& $java -version 2>&1) -match 'version "25(?:\.|\")')
    })
    if (-not $javaHomes) {
        throw 'JDK 25 was not found. Run setup.ps1 first.'
    }
    $env:JAVA_HOME = $javaHomes[0]
    $env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:Path
    $env:GRADLE_USER_HOME = 'C:\GradleCache'

    $jar = Join-Path $project 'build\libs\trial-spawner-finder-1.0.0.jar'
    if (-not (Test-Path $jar)) {
        throw 'The project has not been built. Run setup.ps1 first.'
    }

    & (Join-Path $project 'scripts\prepare-run.ps1')
    Write-Host "Starting TrialSpawnerFinder with $env:JAVA_HOME"
    & (Join-Path $project 'gradlew.bat') runServer --console=plain
    $exitCode = $LASTEXITCODE
    $failureMarker = Join-Path $project 'run\search.failed'
    if (Test-Path -LiteralPath $failureMarker) {
        $detail = Get-Content -LiteralPath $failureMarker -Raw -Encoding UTF8
        throw "TrialSpawnerFinder search failed: $detail"
    }
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

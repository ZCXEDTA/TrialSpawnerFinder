$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8

$project = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$logPath = Join-Path $project 'launcher.log'
$exitCode = 1

try {
    Start-Transcript -LiteralPath $logPath -Force | Out-Null
    Set-Location $project

    $javaHomePath = Join-Path $project '.runtime\build-java-home.txt'
    if (-not (Test-Path -LiteralPath $javaHomePath)) {
        throw 'The build JDK was not recorded. Run setup.ps1 first.'
    }
    $env:JAVA_HOME = (Get-Content -LiteralPath $javaHomePath -Raw -Encoding UTF8).Trim()
    $java = Join-Path $env:JAVA_HOME 'bin\java.exe'
    if (-not (Test-Path $java)) {
        throw 'The build JDK is no longer available. Run setup.ps1 again.'
    }
    if ((& $java -version 2>&1) -notmatch 'version "25(?:\.|\")') {
        throw 'The recorded build JDK is not Java 25. Run setup.ps1 again.'
    }
    $env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:Path
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
    if (-not [Console]::IsInputRedirected) {
        Read-Host 'Press Enter to close this window'
    }
}

exit $exitCode

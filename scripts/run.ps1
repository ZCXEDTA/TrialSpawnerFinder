$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8

$project = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$logPath = Join-Path $project 'launcher.log'
$exitCode = 1
$progressRenderer = Join-Path $project 'scripts\progress-renderer.ps1'

try {
    Start-Transcript -LiteralPath $logPath -Force | Out-Null
    Set-Location $project
    . $progressRenderer

    $javaHomePath = Join-Path $project '.runtime\build-java-home.txt'
    if (-not (Test-Path -LiteralPath $javaHomePath)) {
        throw 'The build JDK was not recorded. Run setup.ps1 first.'
    }
    $env:JAVA_HOME = (Get-Content -LiteralPath $javaHomePath -Raw -Encoding UTF8).Trim()
    $java = Join-Path $env:JAVA_HOME 'bin\java.exe'
    if (-not (Test-Path $java)) {
        throw 'The build JDK is no longer available. Run setup.ps1 again.'
    }
    $javaVersion = (& $java --version | Select-Object -First 1).ToString()
    if ($javaVersion -notmatch '^(?:openjdk|java) 25(?:\.|\s|$)') {
        throw 'The recorded build JDK is not Java 25. Run setup.ps1 again.'
    }
    $env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:Path
    $jar = Join-Path $project 'minecraft-26.2-runtime\build\libs\trial-spawner-finder-1.0.0.jar'
    if (-not (Test-Path $jar)) {
        throw 'The project has not been built. Run setup.ps1 first.'
    }

    & (Join-Path $project 'scripts\prepare-run.ps1')
    Write-Host "Starting TrialSpawnerFinder with $env:JAVA_HOME"
    $savedErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & (Join-Path $project 'gradlew.bat') :minecraft-26.2-runtime:runServer `
            --console=plain 2>&1 | ForEach-Object {
                $line = $_.ToString()
                $event = ConvertFrom-FinderProgressLine $line
                if ($null -ne $event) {
                    Write-FinderProgressEvent $event
                } elseif ($line -ne 'System.Management.Automation.RemoteException') {
                    Write-FinderConsoleLine $line
                }
            }
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $savedErrorActionPreference
    }
    Close-FinderProgressDisplay
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
    if (Get-Command Close-FinderProgressDisplay -ErrorAction SilentlyContinue) {
        Close-FinderProgressDisplay
    }
    try { Stop-Transcript | Out-Null } catch { }
    Write-Host ''
    if (-not [Console]::IsInputRedirected) {
        Read-Host 'Press Enter to close this window'
    }
}

exit $exitCode

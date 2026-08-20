$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8

$project = Split-Path -Parent $MyInvocation.MyCommand.Path
$pathJavac = Get-Command javac.exe -ErrorAction SilentlyContinue
$pathJavaHome = if ($pathJavac) {
    Split-Path -Parent (Split-Path -Parent $pathJavac.Source)
} else {
    $null
}
$javaHomes = @(@(
    (Join-Path $project 'java'),
    $env:JDK25_HOME,
    $env:JAVA_HOME,
    $pathJavaHome
) | Select-Object -Unique | Where-Object {
    if (-not $_) { return $false }
    $javac = Join-Path $_ 'bin\javac.exe'
    (Test-Path $javac) -and ((& $javac -version 2>&1) -match '^javac 25(?:\.|$)')
})

if (-not $javaHomes) {
    throw 'JDK 25 was not found. Install Java 25 or set JDK25_HOME/JAVA_HOME.'
}

$env:JAVA_HOME = $javaHomes[0]
$env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:Path
Write-Host "Using JDK: $env:JAVA_HOME"

Push-Location $project
try {
    # clean jar: build the standalone fat jar only (no tests, avoids downloading JUnit).
    # Output: minecraft-26.2-runtime\build\libs\trial-spawner-finder-1.5.0.jar
    # Optional: run.bat auto-builds when the jar is missing, so this is not required.
    & .\gradlew.bat clean jar
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed with exit code $LASTEXITCODE" }

    Write-Host 'Build completed. Run run.bat to start searching.'
} finally {
    Pop-Location
}

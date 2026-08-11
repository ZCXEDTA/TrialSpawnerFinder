$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8

$project = Split-Path -Parent $MyInvocation.MyCommand.Path
$minecraftJdk = Join-Path $env:APPDATA '.minecraft\runtime\java-runtime-delta'
$pathJavac = Get-Command javac.exe -ErrorAction SilentlyContinue
$pathJavaHome = if ($pathJavac) {
    Split-Path -Parent (Split-Path -Parent $pathJavac.Source)
} else {
    $null
}
$javaHomes = @(@(
    $env:JDK21_HOME,
    $env:JAVA_HOME,
    $minecraftJdk,
    $pathJavaHome
) | Select-Object -Unique | Where-Object {
    if (-not $_) { return $false }
    $javac = Join-Path $_ 'bin\javac.exe'
    (Test-Path $javac) -and ((& $javac -version 2>&1) -match '^javac 21(?:\.|$)')
})

if (-not $javaHomes) {
    throw 'JDK 21 was not found. Install Java 21 or set JDK21_HOME/JAVA_HOME.'
}

$env:JAVA_HOME = $javaHomes[0]
$env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:Path
Write-Host "Using JDK: $env:JAVA_HOME"

Push-Location $project
try {
    # Run tests through Gradle so the test runtime classpath is correct (includes Gson,
    # Minecraft deps, etc.). The manual JUnit-console classpath was missing those and
    # failed at runtime with NoClassDefFoundError.
    & .\gradlew.bat clean test remapJar --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle build/test failed with exit code $LASTEXITCODE" }

    New-Item -ItemType Directory -Force -Path '.runtime' | Out-Null
    Set-Content -LiteralPath '.runtime\build-java-home.txt' -Value $env:JAVA_HOME -Encoding UTF8
    New-Item -ItemType Directory -Force -Path 'run' | Out-Null
    Copy-Item 'finder.properties' 'run\finder.properties' -Force
    Write-Host 'Build completed. Run run.bat to start searching.'
} finally {
    Pop-Location
}

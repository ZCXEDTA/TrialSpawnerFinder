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
    & .\gradlew.bat clean compileTestJava remapJar
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed with exit code $LASTEXITCODE" }

    $junit = Join-Path $project '.bootstrap-cache\junit-platform-console-standalone-1.11.4.jar'
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $junit) | Out-Null
    if (-not (Test-Path $junit)) {
        Invoke-WebRequest `
            'https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar' `
            -OutFile $junit
    }
    & (Join-Path $env:JAVA_HOME 'bin\java.exe') -jar $junit execute `
        --class-path 'build\classes\java\main;build\classes\java\test' `
        --scan-class-path --details=summary
    if ($LASTEXITCODE -ne 0) { throw "Tests failed with exit code $LASTEXITCODE" }

    New-Item -ItemType Directory -Force -Path '.runtime' | Out-Null
    Set-Content -LiteralPath '.runtime\build-java-home.txt' -Value $env:JAVA_HOME -Encoding UTF8
    New-Item -ItemType Directory -Force -Path 'run' | Out-Null
    Copy-Item 'finder.properties' 'run\finder.properties' -Force
    Write-Host 'Build completed. Run run.bat to start searching.'
} finally {
    Pop-Location
}

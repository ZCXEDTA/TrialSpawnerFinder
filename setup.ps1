$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8

$project = Split-Path -Parent $MyInvocation.MyCommand.Path
$javaHomes = @(@(
    'D:\edgedownload\graalvm-jdk-25_windows-x64_bin\graalvm-jdk-25.0.2+10.1',
    (Join-Path $project 'java'),
    'D:\PyCharm\PyCharm 2025.2.0.1\jbr',
    $env:JAVA_HOME
) | Where-Object {
    if (-not $_) { return $false }
    $javac = Join-Path $_ 'bin\javac.exe'
    (Test-Path $javac) -and ((& $javac -version 2>&1) -match '^javac 25(?:\.|$)')
})

if (-not $javaHomes) {
    throw 'JDK 25 was not found. Install Java 25 or set JAVA_HOME.'
}

$env:JAVA_HOME = $javaHomes[0]
$env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:Path
$env:GRADLE_USER_HOME = 'C:\GradleCache'
Write-Host "Using JDK: $env:JAVA_HOME"
Write-Host "Using Gradle cache: $env:GRADLE_USER_HOME"

Push-Location $project
try {
    & .\gradlew.bat clean compileTestJava :finder-core:compileTestJava jar
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed with exit code $LASTEXITCODE" }

    $junit = Join-Path $project '.bootstrap-cache\junit-platform-console-standalone-1.11.4.jar'
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $junit) | Out-Null
    if (-not (Test-Path $junit)) {
        Invoke-WebRequest `
            'https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar' `
            -OutFile $junit
    }
    & (Join-Path $env:JAVA_HOME 'bin\java.exe') -jar $junit execute `
        --class-path 'finder-core\build\classes\java\main;finder-core\build\classes\java\test;build\classes\java\main;build\classes\java\test' `
        --scan-class-path --details=summary
    if ($LASTEXITCODE -ne 0) { throw "Tests failed with exit code $LASTEXITCODE" }

    New-Item -ItemType Directory -Force -Path 'run' | Out-Null
    Copy-Item 'finder.properties' 'run\finder.properties' -Force
    Write-Host 'Build completed. Run run.bat to start searching.'
} finally {
    Pop-Location
}

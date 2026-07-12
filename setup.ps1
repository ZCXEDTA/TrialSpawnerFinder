$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8

$project = Split-Path -Parent $MyInvocation.MyCommand.Path
$minecraftJdk = Join-Path $env:APPDATA '.minecraft\runtime\java-runtime-delta'
$javaHomes = @(@(
    'D:\edgedownload\jdk-21_windows-x64_bin\jdk-21.0.8',
    'D:\PyCharm\PyCharm 2025.2.0.1\jbr',
    $env:JAVA_HOME,
    $minecraftJdk
) | Where-Object { $_ -and (Test-Path (Join-Path $_ 'bin\javac.exe')) })

if (-not $javaHomes) {
    throw 'JDK 21 was not found. Install Java 21 or set JAVA_HOME.'
}

$env:JAVA_HOME = $javaHomes[0]
$env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:Path
$env:GRADLE_USER_HOME = 'C:\GradleCache'
Write-Host "Using JDK: $env:JAVA_HOME"
Write-Host "Using Gradle cache: $env:GRADLE_USER_HOME"

Push-Location $project
try {
    & .\gradlew.bat clean compileTestJava :finder-core:compileTestJava remapJar
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

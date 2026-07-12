$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8

$project = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$configPath = Join-Path $project 'finder.properties'
$run = Join-Path $project 'run'
if (-not (Test-Path $configPath)) { throw 'finder.properties was not found.' }

$properties = @{}
foreach ($line in Get-Content -LiteralPath $configPath -Encoding UTF8) {
    $trimmed = $line.Trim()
    if (-not $trimmed -or $trimmed.StartsWith('#')) { continue }
    $parts = $trimmed.Split('=', 2)
    if ($parts.Count -eq 2) { $properties[$parts[0].Trim()] = $parts[1].Trim() }
}
if (-not $properties.ContainsKey('seed')) { throw 'finder.properties is missing seed.' }

New-Item -ItemType Directory -Force -Path $run | Out-Null
$world = Join-Path $run 'trial-finder-world'
if (Test-Path -LiteralPath $world) {
    $resolved = (Resolve-Path -LiteralPath $world).Path
    if (-not $resolved.StartsWith((Resolve-Path -LiteralPath $run).Path)) {
        throw 'Temporary world path is invalid.'
    }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}

@"
eula=true
"@ | Set-Content -LiteralPath (Join-Path $run 'eula.txt') -Encoding ASCII

@"
level-name=trial-finder-world
level-seed=$($properties.seed)
gamemode=spectator
generate-structures=true
online-mode=false
spawn-protection=0
view-distance=2
simulation-distance=2
max-tick-time=-1
sync-chunk-writes=false
"@ | Set-Content -LiteralPath (Join-Path $run 'server.properties') -Encoding ASCII

Copy-Item -LiteralPath $configPath -Destination (Join-Path $run 'finder.properties') -Force
Write-Host "Prepared temporary world with seed $($properties.seed)."

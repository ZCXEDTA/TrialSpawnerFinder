$script:FinderProgressPrefix = '@@MFP1|'
$script:FinderCoarsePhase = [Text.Encoding]::UTF8.GetString(
    [Convert]::FromBase64String('57KX562b'))
$script:FinderTotalPhase = [Text.Encoding]::UTF8.GetString(
    [Convert]::FromBase64String('5oC76L+b5bqm'))
$script:FinderProgressLines = @{}
$script:FinderProgressCompleted = @{}
$script:FinderProgressLastLog = @{}
$script:FinderProgressLastFallback = @{}
$script:FinderProgressDashboardActive = $false
$script:FinderProgressDashboardRow = -1
$script:FinderProgressSingleActive = $false
$script:FinderProgressSingleRow = -1

try {
    $script:FinderProgressInteractive =
        -not [Console]::IsOutputRedirected -and [Console]::BufferWidth -gt 20
} catch {
    $script:FinderProgressInteractive = $false
}

function ConvertFrom-FinderProgressLine([string]$Line) {
    if (-not $Line.StartsWith($script:FinderProgressPrefix)) { return $null }
    try {
        $parts = $Line -split '\|', 4
        if ($parts.Count -ne 4 -or $parts[0] -ne '@@MFP1') { return $null }
        return [pscustomobject]@{
            Phase = [Text.Encoding]::UTF8.GetString(
                [Convert]::FromBase64String($parts[1]))
            Complete = $parts[2] -eq '1'
            Line = [Text.Encoding]::UTF8.GetString(
                [Convert]::FromBase64String($parts[3]))
        }
    } catch {
        return $null
    }
}

function Format-FinderProgressRow([string]$Line) {
    $limit = [Math]::Max(10, [Console]::BufferWidth - 1)
    $builder = [Text.StringBuilder]::new()
    $width = 0
    foreach ($character in $Line.ToCharArray()) {
        $characterWidth = if ([int]$character -le 0x7f) { 1 } else { 2 }
        if ($width + $characterWidth -gt $limit) { break }
        [void]$builder.Append($character)
        $width += $characterWidth
    }
    if ($builder.Length -lt $Line.Length -and $limit -ge 3) {
        while ($width -gt $limit - 3 -and $builder.Length -gt 0) {
            $last = $builder[$builder.Length - 1]
            $width -= if ([int]$last -le 0x7f) { 1 } else { 2 }
            $builder.Length--
        }
        [void]$builder.Append('...')
        $width += 3
    }
    [void]$builder.Append(' ' * [Math]::Max(0, $limit - $width))
    return $builder.ToString()
}

function Show-FinderProgressDashboard {
    if (-not $script:FinderProgressLines.ContainsKey($script:FinderCoarsePhase) -or
            -not $script:FinderProgressLines.ContainsKey($script:FinderTotalPhase)) {
        return
    }
    try {
        $coarse = Format-FinderProgressRow `
            $script:FinderProgressLines[$script:FinderCoarsePhase]
        $total = Format-FinderProgressRow `
            $script:FinderProgressLines[$script:FinderTotalPhase]
        if (-not $script:FinderProgressDashboardActive) {
            [Console]::WriteLine($coarse)
            [Console]::WriteLine($total)
            $script:FinderProgressDashboardRow = [Math]::Max(0, [Console]::CursorTop - 2)
            $script:FinderProgressDashboardActive = $true
        } else {
            $returnLeft = [Console]::CursorLeft
            $returnTop = [Console]::CursorTop
            [Console]::SetCursorPosition(0, $script:FinderProgressDashboardRow)
            [Console]::Write($coarse)
            [Console]::SetCursorPosition(0, $script:FinderProgressDashboardRow + 1)
            [Console]::Write($total)
            [Console]::SetCursorPosition($returnLeft, $returnTop)
        }
        if ($script:FinderProgressCompleted[$script:FinderCoarsePhase] -and
                $script:FinderProgressCompleted[$script:FinderTotalPhase]) {
            $script:FinderProgressDashboardActive = $false
        }
    } catch {
        $script:FinderProgressInteractive = $false
        $script:FinderProgressDashboardActive = $false
    }
}

function Show-FinderSingleProgress($Event) {
    try {
        $line = Format-FinderProgressRow $Event.Line
        if (-not $script:FinderProgressSingleActive) {
            $script:FinderProgressSingleRow = [Console]::CursorTop
            $script:FinderProgressSingleActive = $true
        }
        [Console]::SetCursorPosition(0, $script:FinderProgressSingleRow)
        [Console]::Write($line)
        if ($Event.Complete) {
            [Console]::WriteLine()
            $script:FinderProgressSingleActive = $false
        }
    } catch {
        $script:FinderProgressInteractive = $false
        $script:FinderProgressSingleActive = $false
    }
}

function Test-FinderProgressLogDue($Event) {
    $now = [DateTimeOffset]::Now
    $last = $script:FinderProgressLastLog[$Event.Phase]
    if ($Event.Complete -or $null -eq $last -or ($now - $last).TotalSeconds -ge 10) {
        $script:FinderProgressLastLog[$Event.Phase] = $now
        return $true
    }
    return $false
}

function Test-FinderProgressFallbackDue($Event) {
    $now = [DateTimeOffset]::Now
    $last = $script:FinderProgressLastFallback[$Event.Phase]
    if ($Event.Complete -or $null -eq $last -or ($now - $last).TotalSeconds -ge 10) {
        $script:FinderProgressLastFallback[$Event.Phase] = $now
        return $true
    }
    return $false
}

function Write-FinderProgressEvent($Event) {
    if ($Event.Phase -in @($script:FinderCoarsePhase, $script:FinderTotalPhase)) {
        $script:FinderProgressLines[$Event.Phase] = $Event.Line
        $script:FinderProgressCompleted[$Event.Phase] = $Event.Complete
        if ($script:FinderProgressInteractive) {
            Show-FinderProgressDashboard
        } elseif (Test-FinderProgressFallbackDue $Event) {
            [Console]::Out.WriteLine($Event.Line)
        }
        return
    }
    if ($script:FinderProgressInteractive) {
        Show-FinderSingleProgress $Event
    } elseif (Test-FinderProgressFallbackDue $Event) {
        [Console]::Out.WriteLine($Event.Line)
    }
}

function Close-FinderProgressDisplay {
    if ($script:FinderProgressSingleActive) {
        [Console]::WriteLine()
        $script:FinderProgressSingleActive = $false
    }
    $script:FinderProgressDashboardActive = $false
}

function Write-FinderConsoleLine([string]$Line) {
    if ($script:FinderProgressSingleActive) {
        [Console]::WriteLine()
        $script:FinderProgressSingleActive = $false
    }
    Write-Host $Line
}

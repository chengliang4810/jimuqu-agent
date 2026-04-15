$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$sourceRoot = Join-Path $repoRoot "codex-skills"
$targetRoot = Join-Path $env:USERPROFILE ".codex\skills"

if (-not (Test-Path $sourceRoot)) {
    throw "Source skills directory not found: $sourceRoot"
}

New-Item -ItemType Directory -Force -Path $targetRoot | Out-Null

Get-ChildItem -Path $sourceRoot -Directory | ForEach-Object {
    $target = Join-Path $targetRoot $_.Name
    if (Test-Path $target) {
        Remove-Item -Recurse -Force $target
    }

    Copy-Item -Recurse -Force $_.FullName $target
    Write-Host "Synced $($_.Name) -> $target"
}

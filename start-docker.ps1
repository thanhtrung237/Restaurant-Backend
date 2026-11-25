[CmdletBinding()]
param(
    [ValidateSet('All','mysql','backend','frontend')][string]$Service = 'All',
    [switch]$Build,
    [switch]$Recreate,
    [string]$EnvironmentFile,
    [ValidateRange(30,1800)][int]$WaitSeconds = 600
)
$ErrorActionPreference = 'Stop'
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    $dockerBin = Join-Path $env:LOCALAPPDATA 'Programs/DockerDesktop/resources/bin'
    $env:Path = $dockerBin + ';' + $env:Path
}
$node = Get-Command node -ErrorAction Stop
$arguments = @((Join-Path $PSScriptRoot 'docker/start-groups.cjs'), '--service', $Service.ToLowerInvariant(), '--wait', [string]$WaitSeconds)
if ($Build) { $arguments += '--build' }
if ($Recreate) { $arguments += '--recreate' }
if ($EnvironmentFile) { $arguments += @('--env', (Resolve-Path -LiteralPath $EnvironmentFile).Path) }
& $node.Source @arguments
if ($LASTEXITCODE -ne 0) { throw 'Docker startup failed. See the message above.' }
[CmdletBinding()]
param(
    [string]$BackendDirectory = (Split-Path -Parent $PSScriptRoot),

    [Parameter(Mandatory)]
    [string]$Image,

    [switch]$SkipPull
)

$ErrorActionPreference = 'Stop'
$backendPath = (Resolve-Path -LiteralPath $BackendDirectory).Path
$composeOptions = @(
    '--env-file', (Join-Path $backendPath '.env.docker'),
    '-f', (Join-Path $backendPath 'compose.yaml'),
    '-f', (Join-Path $PSScriptRoot 'compose.backend.yaml')
)
$previousImage = $env:RESTAURANT_BACKEND_IMAGE
$env:RESTAURANT_BACKEND_IMAGE = $Image

try {
    $mysql = docker compose @composeOptions ps --status running --quiet mysql
    if ($LASTEXITCODE -ne 0 -or -not $mysql) {
        throw 'Start MySQL in the target Compose project before deploying the backend.'
    }
    $mysqlHealth = docker inspect --format '{{.State.Health.Status}}' $mysql
    if ($LASTEXITCODE -ne 0 -or $mysqlHealth -ne 'healthy') {
        throw 'MySQL must be healthy before deploying the backend.'
    }

    if (-not $SkipPull) {
        docker compose @composeOptions pull --policy always backend
        if ($LASTEXITCODE -ne 0) { throw "Could not pull $Image." }
    }

    docker compose @composeOptions up -d --no-deps --no-build --pull never --wait --wait-timeout 300 backend
    if ($LASTEXITCODE -ne 0) {
        docker compose @composeOptions logs --tail 50 backend
        throw "Backend did not become ready with $Image."
    }
} finally {
    $env:RESTAURANT_BACKEND_IMAGE = $previousImage
}

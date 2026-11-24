<#
Run three standalone containers (no Compose project grouping in Docker Desktop).
  .\start-docker.ps1 -Build
  .\start-docker.ps1 -Service backend -Build -Recreate
  .\start-docker.ps1 -Service frontend -Build -Recreate
Compose is used only to resolve configuration; docker run starts each container.
#>
[CmdletBinding()]
param(
    [ValidateSet('All','mysql','backend','frontend')][string]$Service = 'All',
    [switch]$Build,
    [switch]$Recreate,
    [string]$EnvironmentFile,
    [ValidateRange(30,1800)][int]$WaitSeconds = 300
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
if ([string]::IsNullOrWhiteSpace($EnvironmentFile)) { $EnvironmentFile = Join-Path $repositoryRoot '.env.docker' }
$command = Get-Command docker -ErrorAction SilentlyContinue
$script:DockerExe = if ($command) { $command.Source } else {
    Join-Path $env:LOCALAPPDATA 'Programs/DockerDesktop/resources/bin/docker.exe'
}
if (-not (Test-Path -LiteralPath $script:DockerExe)) { throw 'Docker CLI not found. Install/open Docker Desktop and refresh PATH.' }
# Docker also launches credential helpers from its bin directory.
$env:Path = (Split-Path -Parent $script:DockerExe) + ';' + $env:Path

function Quote-Argument([string]$Value) {
    # Windows native argument quoting, including embedded quotes and backslashes.
    $builder = New-Object Text.StringBuilder
    [void]$builder.Append('"'); $slashes = 0
    foreach ($character in $Value.ToCharArray()) {
        if ($character -eq '\') { $slashes++; continue }
        if ($character -eq '"') {
            [void]$builder.Append(('\' * (2 * $slashes + 1))); [void]$builder.Append('"')
        } else {
            if ($slashes) { [void]$builder.Append(('\' * $slashes)) }
            [void]$builder.Append($character)
        }
        $slashes = 0
    }
    if ($slashes) { [void]$builder.Append(('\' * (2 * $slashes))) }
    [void]$builder.Append('"'); $builder.ToString()
}
function Invoke-Docker {
    param([string[]]$Arguments, [int[]]$AllowedCodes = @(0))
    $info = New-Object Diagnostics.ProcessStartInfo
    $info.FileName = $script:DockerExe
    $info.Arguments = ($Arguments | ForEach-Object { Quote-Argument $_ }) -join ' '
    $info.UseShellExecute = $false; $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true; $info.RedirectStandardError = $true
    $info.StandardOutputEncoding = New-Object Text.UTF8Encoding($false)
    $info.StandardErrorEncoding = New-Object Text.UTF8Encoding($false)
    $process = New-Object Diagnostics.Process; $process.StartInfo = $info
    try {
        [void]$process.Start()
        $stdout = $process.StandardOutput.ReadToEndAsync(); $stderr = $process.StandardError.ReadToEndAsync()
        $process.WaitForExit()
        if ($AllowedCodes -notcontains $process.ExitCode) {
            throw ('docker {0} failed: {1}' -f $Arguments[0],$stderr.Result.Trim())
        }
        [pscustomobject]@{Code=$process.ExitCode;Text=$stdout.Result.TrimEnd()}
    } finally { $process.Dispose() }
}
function Wait-Container([string]$Name) {
    $deadline = [DateTime]::UtcNow.AddSeconds($WaitSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        $state = (Invoke-Docker @('inspect','--format','{{json .State}}',$Name)).Text | ConvertFrom-Json
        if (-not $state.Running) { throw ($Name + ' stopped. Inspect it with: docker logs ' + $Name) }
        if ($state.Health.Status -eq 'healthy') { Write-Host ($Name + ': healthy'); return }
        if ($state.Health.Status -eq 'unhealthy') { throw ($Name + ' is unhealthy. Inspect it with: docker logs ' + $Name) }
        Start-Sleep -Seconds 2
    }
    throw ('Timed out waiting for ' + $Name + '. Inspect it with: docker logs ' + $Name)
}

[void](Invoke-Docker @('info','--format','{{.ServerVersion}}'))
$config = (Invoke-Docker @('compose','--project-directory',$repositoryRoot,'--env-file',$EnvironmentFile,
        '-f',(Join-Path $repositoryRoot 'compose.yaml'),'config','--format','json')).Text | ConvertFrom-Json
if ($config.services.mysql.environment.MYSQL_USER -eq 'root') {
    throw 'MYSQL_USER must be a regular application user, e.g. restaurant. Use MYSQL_ROOT_PASSWORD for the separate root account.'
}
$selected = @('mysql','backend','frontend') | Where-Object { $Service -eq 'All' -or $_ -eq $Service }
$networkName = 'restaurant-network'
$volumeName = $config.volumes.mysql_data.name
$runtimeDirectory = Join-Path $repositoryRoot '.docker-runtime'
$temporaryFiles = @()
try {
    # Finish all builds before stopping/replacing any running container.
    foreach ($key in $selected) {
        $entry = $config.services.PSObject.Properties[$key].Value
        $imageMissing = (Invoke-Docker @('image','inspect',$entry.image) @(0,1)).Code -ne 0
        if ($key -eq 'mysql') {
            if ($imageMissing) { Write-Host 'Pulling MySQL image...'; [void](Invoke-Docker @('pull',$entry.image)) }
        } elseif ($Build -or $imageMissing) {
            Write-Host ('Building ' + $key + ' image (this may take several minutes)...')
            $buildArgs = @('build','--tag',$entry.image,'--file',(Join-Path $entry.build.context $entry.build.dockerfile))
            if ($key -eq 'frontend') {
                foreach ($arg in $entry.build.args.PSObject.Properties) { $buildArgs += @('--build-arg',($arg.Name + '=' + $arg.Value)) }
            }
            [void](Invoke-Docker ($buildArgs + @($entry.build.context)))
            Write-Host ('Built ' + $entry.image)
        }
    }
    if ((Invoke-Docker @('network','inspect',$networkName) @(0,1)).Code -ne 0) {
        [void](Invoke-Docker @('network','create',$networkName))
    }
    [void](Invoke-Docker @('volume','create',$volumeName))
    [void](New-Item -ItemType Directory -Path $runtimeDirectory -Force)
    foreach ($key in $selected) {
        $entry = $config.services.PSObject.Properties[$key].Value
        $name = 'restaurant-' + $key
        $existing = Invoke-Docker @('container','inspect',$name) @(0,1)
        if ($existing.Code -eq 0) {
            $container = @($existing.Text | ConvertFrom-Json)[0]
            $owner = $container.Config.Labels.PSObject.Properties['com.restaurant.stack']
            if ($null -eq $owner -or $owner.Value -ne 'kltn-standalone') { throw ('Container name belongs to another setup: ' + $name) }
            if ($Recreate) {
                [void](Invoke-Docker @('stop',$name)); [void](Invoke-Docker @('rm',$name))
            } else {
                [void](Invoke-Docker @('start',$name)); Wait-Container $name; continue
            }
        }
        if ($key -eq 'mysql') {
            $users = (Invoke-Docker @('ps','--filter',('volume=' + $volumeName),'--format','{{.Names}}')).Text
            if ($users) { throw ('MySQL volume is already used by running container(s): ' + $users + '. Stop that setup first; keep the volume.') }
        }
        $runArgs = @('run','-d','--name',$name,'--network',$networkName,'--network-alias',$key,
            '--label','com.restaurant.stack=kltn-standalone','--restart','unless-stopped',
            '--log-driver','json-file','--log-opt','max-size=10m','--log-opt','max-file=3')
        foreach ($port in $entry.ports) {
            $runArgs += @('--publish',('{0}:{1}:{2}' -f $port.host_ip,$port.published,$port.target))
        }
        if ($key -in @('mysql','backend')) {
            $envPath = Join-Path $runtimeDirectory ([IO.Path]::GetRandomFileName() + '.env')
            $temporaryFiles += $envPath
            $lines = foreach ($variable in $entry.environment.PSObject.Properties) {
                if ([string]$variable.Value -match '[\r\n]') { throw ('Multiline environment value is unsupported: ' + $variable.Name) }
                $variable.Name + '=' + [string]$variable.Value
            }
            [IO.File]::WriteAllLines($envPath,[string[]]$lines,(New-Object Text.UTF8Encoding($false)))
            $runArgs += @('--env-file',$envPath)
        }
        if ($key -eq 'mysql') {
            $runArgs += @('--mount',('type=volume,source=' + $volumeName + ',target=/var/lib/mysql'),
                '--health-cmd',($entry.healthcheck.test[1].Replace('$$','$')),
                '--health-interval','10s','--health-timeout','5s','--health-start-period','40s','--health-retries','20')
        }
        if ($key -eq 'backend') { $runArgs += @('--stop-timeout','30') }
        $runArgs += @($entry.image)
        if ($key -eq 'mysql') { $runArgs += @($entry.command) }
        Write-Host ('Starting standalone container ' + $name + '...')
        [void](Invoke-Docker $runArgs)
        Wait-Container $name
    }
    Write-Host "`nStandalone containers:"
    Write-Host (Invoke-Docker @('ps','--filter','label=com.restaurant.stack=kltn-standalone',
        '--format','table {{.Names}}\t{{.Status}}\t{{.Ports}}')).Text
    $frontendPort = $config.services.frontend.ports[0].published
    $backendPort = $config.services.backend.ports[0].published
    $mysqlPort = $config.services.mysql.ports[0].published
    Write-Host "Frontend: http://localhost:${frontendPort} | Swagger: http://localhost:${backendPort}/swagger-ui/index.html | MySQL: localhost:${mysqlPort}"
} finally {
    foreach ($envPath in $temporaryFiles) {
        if (Test-Path -LiteralPath $envPath) { Remove-Item -LiteralPath $envPath -Force }
    }
}

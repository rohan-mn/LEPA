param(
    [switch]$Postgres,
    [switch]$NoBrowser
)
. (Join-Path $PSScriptRoot 'common.ps1')
$root = Get-ProjectRoot
Set-Location $root
Import-DotEnv (Join-Path $root '.env')

$runDir = Join-Path $root '.run'
$logDir = Join-Path $root 'logs'
New-Item -ItemType Directory -Path $runDir -Force | Out-Null
New-Item -ItemType Directory -Path $logDir -Force | Out-Null

function Test-PortInUse([int]$Port) {
    try {
        $listeners = [System.Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners()
        return [bool]($listeners | Where-Object { $_.Port -eq $Port } | Select-Object -First 1)
    } catch {
        return $false
    }
}

function Find-FreePort([int]$Preferred, [int]$MaxTries = 20) {
    for ($p = $Preferred; $p -lt ($Preferred + $MaxTries); $p++) {
        if (-not (Test-PortInUse $p)) { return $p }
    }
    throw "Could not find a free TCP port starting at $Preferred."
}

function Show-LogTail([string]$Path, [int]$Lines = 80) {
    if (Test-Path $Path) {
        Write-Host ''
        Write-Host "----- $Path (last $Lines lines) -----" -ForegroundColor Yellow
        Get-Content $Path -Tail $Lines -ErrorAction SilentlyContinue
    }
}

$jar = Get-ChildItem 'backend\target\lepa-backend-*.jar' -ErrorAction SilentlyContinue |
       Where-Object { $_.Name -notlike '*.original' } | Select-Object -First 1
$py = Join-Path $root 'ai-service\.venv\Scripts\python.exe'

if (-not $jar -or -not (Test-Path $py)) {
    throw 'LEPA is not built yet. Run setup-lepa.bat first.'
}

# Refuse accidental duplicate launches, while clearing stale PID files.
foreach ($name in @('backend.pid','ai.pid')) {
    $pf = Join-Path $runDir $name
    if (Test-Path $pf) {
        $oldPid = Get-Content $pf -ErrorAction SilentlyContinue
        if ($oldPid -and (Get-Process -Id $oldPid -ErrorAction SilentlyContinue)) {
            throw "LEPA appears to be running already (PID $oldPid). Run stop-lepa.bat first."
        }
        Remove-Item $pf -Force -ErrorAction SilentlyContinue
    }
}

# Pick safe local ports. This avoids common collisions with other developer tools.
$preferredBackendPort = if ($env:LEPA_PORT) { [int]$env:LEPA_PORT } else { 8080 }
$backendPort = Find-FreePort $preferredBackendPort
if ($backendPort -ne $preferredBackendPort) {
    Write-Host "Port $preferredBackendPort is already in use. LEPA will use port $backendPort instead." -ForegroundColor Yellow
}
$env:LEPA_PORT = "$backendPort"

$preferredAiPort = if ($env:AI_PORT) { [int]$env:AI_PORT } else { 8000 }
$aiPort = Find-FreePort $preferredAiPort
if ($aiPort -ne $preferredAiPort) {
    Write-Host "Port $preferredAiPort is already in use. LEPA AI will use port $aiPort instead." -ForegroundColor Yellow
}
$env:AI_PORT = "$aiPort"
$env:AI_SERVICE_URL = "http://localhost:$aiPort"

if ($Postgres) {
    $env:SPRING_PROFILES_ACTIVE = 'postgres'
    $env:DB_URL = "jdbc:postgresql://$($env:POSTGRES_HOST):$($env:POSTGRES_PORT)/$($env:POSTGRES_DB)"
    $env:DB_USER = $env:POSTGRES_USER
    $env:DB_PASSWORD = $env:POSTGRES_PASSWORD
    Write-Host 'Database mode: PostgreSQL' -ForegroundColor Cyan
} else {
    # Force the default H2 profile and clear database overrides inherited from the
    # machine/user environment. application.yml contains the explicit H2 JDBC URL.
    Remove-Item Env:SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue
    Remove-Item Env:DB_URL -ErrorAction SilentlyContinue
    Remove-Item Env:DB_USER -ErrorAction SilentlyContinue
    Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:DB_DRIVER -ErrorAction SilentlyContinue
    Remove-Item Env:SPRING_DATASOURCE_URL -ErrorAction SilentlyContinue
    Remove-Item Env:SPRING_DATASOURCE_USERNAME -ErrorAction SilentlyContinue
    Remove-Item Env:SPRING_DATASOURCE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:SPRING_DATASOURCE_DRIVER_CLASS_NAME -ErrorAction SilentlyContinue
    $env:SPRING_JPA_DATABASE_PLATFORM = 'org.hibernate.dialect.H2Dialect'
    Write-Host 'Database mode: embedded persistent H2 (zero-install)' -ForegroundColor Cyan
    Write-Host 'Database URL : jdbc:h2:file:./data/lepa' -ForegroundColor DarkGray
}

# Start Ollama only if installed and not already answering. Failure is harmless.
try {
    Invoke-WebRequest -Uri 'http://localhost:11434/api/tags' -UseBasicParsing -TimeoutSec 1 | Out-Null
} catch {
    if (Get-Command 'ollama' -ErrorAction SilentlyContinue) {
        $ollama = Start-Process -FilePath 'ollama' -ArgumentList 'serve' -WorkingDirectory $root -WindowStyle Hidden -PassThru
        $ollama.Id | Set-Content (Join-Path $runDir 'ollama.pid')
        Start-Sleep -Seconds 1
    }
}

$aiOut = Join-Path $logDir 'ai.log'
$aiErr = Join-Path $logDir 'ai-error.log'
$beOut = Join-Path $logDir 'backend.log'
$beErr = Join-Path $logDir 'backend-error.log'

# Clear previous log files so diagnostics belong to this launch.
foreach ($log in @($aiOut,$aiErr,$beOut,$beErr)) {
    Remove-Item $log -Force -ErrorAction SilentlyContinue
}

$ai = Start-Process -FilePath $py -ArgumentList @('-m','uvicorn','main:app','--host','127.0.0.1','--port',"$aiPort") `
    -WorkingDirectory (Join-Path $root 'ai-service') -RedirectStandardOutput $aiOut -RedirectStandardError $aiErr -PassThru
$ai.Id | Set-Content (Join-Path $runDir 'ai.pid')

$backend = Start-Process -FilePath 'java' -ArgumentList @('-Dfile.encoding=UTF-8','-jar', $jar.FullName) -WorkingDirectory $root `
    -RedirectStandardOutput $beOut -RedirectStandardError $beErr -PassThru
$backend.Id | Set-Content (Join-Path $runDir 'backend.pid')

Write-Host 'Starting LEPA services...' -ForegroundColor Yellow

$backendUrl = "http://localhost:$backendPort"
$aiUrl = "http://localhost:$aiPort"

# Check for an early process crash before waiting the full health timeout.
$backendOk = $false
for ($i = 0; $i -lt 60; $i++) {
    if (-not (Get-Process -Id $backend.Id -ErrorAction SilentlyContinue)) { break }
    try {
        $r = Invoke-WebRequest -Uri "$backendUrl/api/system/health" -UseBasicParsing -TimeoutSec 2
        if ($r.StatusCode -ge 200 -and $r.StatusCode -lt 500) { $backendOk = $true; break }
    } catch { }
    Start-Sleep -Seconds 1
}

$aiOk = Wait-Http "$aiUrl/health" 20

if (-not $backendOk) {
    Write-Host ''
    Write-Host 'Backend did not become healthy.' -ForegroundColor Red
    Write-Host 'The actual Spring Boot startup diagnostics are shown below.' -ForegroundColor Red
    Show-LogTail $beOut 120
    Show-LogTail $beErr 120
    Write-Host ''
    Write-Host 'You can also run diagnose-lepa.bat for a compact environment report.' -ForegroundColor Yellow
    exit 1
}

if ($aiOk) {
    Write-Host 'AI service: UP' -ForegroundColor Green
} else {
    Write-Host 'AI service is not healthy. Core dashboard still runs; AI diagnostics follow.' -ForegroundColor Yellow
    Show-LogTail $aiOut 60
    Show-LogTail $aiErr 60
}

Write-Host ''
Write-Host 'LEPA is running:' -ForegroundColor Green
Write-Host "  Application : $backendUrl"
Write-Host "  Backend API : $backendUrl/api/system/health"
Write-Host "  AI API      : $aiUrl/docs"
Write-Host '  Logs        : .\logs'
Write-Host ''

$priority = if ($env:AI_PROVIDER_PRIORITY) { $env:AI_PROVIDER_PRIORITY } else { 'openrouter-first' }
if ($priority -eq 'ollama-first') {
    Write-Host 'LLM order: Ollama -> OpenRouter -> deterministic fallback'
} else {
    Write-Host 'LLM order: OpenRouter -> Ollama -> deterministic fallback'
}

if (-not $NoBrowser) {
    Start-Process $backendUrl
}

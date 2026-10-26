param(
    [switch]$SkipBuild
)
. (Join-Path $PSScriptRoot 'common.ps1')
$root = Get-ProjectRoot
Set-Location $root

Write-Host ''
Write-Host '============================================================' -ForegroundColor Cyan
Write-Host ' LEPA Native Windows - One-time setup' -ForegroundColor Cyan
Write-Host '============================================================' -ForegroundColor Cyan

Assert-Command 'java' 'Install a free Java 21 JDK (for example Eclipse Temurin 21).'
Assert-Command 'node' 'Install Node.js 20+ LTS.'
Assert-Command 'npm' 'npm is normally installed with Node.js.'
Assert-Command 'python' 'Install Python 3.11+ and enable Add Python to PATH.'
$mavenExe = Get-MavenExecutable $root

if (-not (Test-Path '.env')) {
    Copy-Item '.env.example' '.env'
    Write-Host 'Created .env from .env.example' -ForegroundColor Green
}

Write-Host '[1/4] Creating Python environment...' -ForegroundColor Yellow
if (-not (Test-Path 'ai-service\.venv\Scripts\python.exe')) {
    python -m venv 'ai-service\.venv'
}
& 'ai-service\.venv\Scripts\python.exe' -m pip install --upgrade pip
& 'ai-service\.venv\Scripts\python.exe' -m pip install -r 'ai-service\requirements.txt'

if (-not $SkipBuild) {
    Write-Host '[2/4] Installing/building React UI...' -ForegroundColor Yellow
    Push-Location 'frontend'
    npm install
    npm run build
    Pop-Location

    Write-Host '[3/4] Embedding React UI into Spring Boot...' -ForegroundColor Yellow
    $static = Join-Path $root 'backend\src\main\resources\static'
    if (Test-Path $static) { Remove-Item $static -Recurse -Force }
    New-Item -ItemType Directory -Path $static -Force | Out-Null
    Copy-Item 'frontend\dist\*' $static -Recurse -Force

    Write-Host '[4/4] Building Spring Boot application...' -ForegroundColor Yellow
    Push-Location 'backend'
    & $mavenExe clean package -DskipTests
    Pop-Location
}

Write-Host ''
Write-Host 'Setup complete.' -ForegroundColor Green
Write-Host 'Next:'
Write-Host '  1. Optional: edit .env and paste OPENROUTER_API_KEY'
Write-Host '  2. Optional Ollama fallback: ollama pull qwen3:4b'
Write-Host '  3. Double-click start-lepa.bat'
Write-Host ''
Write-Host 'Default mode needs NO Docker, Kafka or PostgreSQL.' -ForegroundColor Green

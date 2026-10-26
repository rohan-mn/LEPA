$ErrorActionPreference = 'Continue'
. (Join-Path $PSScriptRoot 'common.ps1')
$root = Get-ProjectRoot
Set-Location $root
Write-Host '============================================================' -ForegroundColor Cyan
Write-Host ' LEPA Native Windows - Diagnostics' -ForegroundColor Cyan
Write-Host '============================================================' -ForegroundColor Cyan
Write-Host ''

Write-Host '[Java]' -ForegroundColor Yellow
& java -version 2>&1
Write-Host ''
Write-Host '[Node]' -ForegroundColor Yellow
& node --version 2>&1
Write-Host ''
Write-Host '[Python]' -ForegroundColor Yellow
& python --version 2>&1
Write-Host ''

foreach ($port in @(8080,8081,8000,8001,11434,5432)) {
    try {
        $used = [bool]([System.Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners() | Where-Object { $_.Port -eq $port } | Select-Object -First 1)
        Write-Host ("Port {0}: {1}" -f $port, $(if($used){'IN USE'}else{'free'}))
    } catch { }
}

Write-Host ''
foreach ($log in @('logs\backend.log','logs\backend-error.log','logs\ai.log','logs\ai-error.log')) {
    if (Test-Path $log) {
        Write-Host "----- $log -----" -ForegroundColor Yellow
        Get-Content $log -Tail 80
        Write-Host ''
    }
}
Write-Host '============================================================' -ForegroundColor Cyan
Write-Host 'Copy this output into ChatGPT if LEPA still fails to start.' -ForegroundColor Cyan

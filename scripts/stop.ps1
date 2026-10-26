. (Join-Path $PSScriptRoot 'common.ps1')
$root = Get-ProjectRoot
$runDir = Join-Path $root '.run'

foreach ($name in @('backend','ai','ollama')) {
    $pidFile = Join-Path $runDir "$name.pid"
    if (Test-Path $pidFile) {
        $processId = Get-Content $pidFile -ErrorAction SilentlyContinue
        if ($processId) {
            $p = Get-Process -Id $processId -ErrorAction SilentlyContinue
            if ($p) {
                Stop-Process -Id $processId -Force
                Write-Host "Stopped $name (PID $processId)"
            }
        }
        Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
    }
}
Write-Host 'LEPA stopped.' -ForegroundColor Green

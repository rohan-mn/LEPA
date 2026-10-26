$ErrorActionPreference = 'Stop'
Write-Host 'Checking backend...'
$health = Invoke-RestMethod 'http://localhost:8080/api/system/health'
if ($health.status -ne 'UP') { throw 'Backend is not healthy' }

Write-Host 'Checking journeys...'
$journeys = Invoke-RestMethod 'http://localhost:8080/api/journeys'
if (-not $journeys -or $journeys.Count -lt 1) { throw 'No seeded journey returned' }

Write-Host 'Checking AI service through backend...'
$ai = Invoke-RestMethod 'http://localhost:8080/api/ai/health'
if ($ai.status -ne 'UP') { throw 'AI service is not healthy' }

Write-Host 'Native LEPA smoke test passed.' -ForegroundColor Green

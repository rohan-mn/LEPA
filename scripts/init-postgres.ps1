. (Join-Path $PSScriptRoot 'common.ps1')
$root = Get-ProjectRoot
Assert-Command 'psql' 'Install PostgreSQL and ensure its bin directory is in PATH.'
Write-Host 'You may be prompted for the PostgreSQL administrator password.' -ForegroundColor Yellow
psql -U postgres -f (Join-Path $root 'scripts\init-postgres.sql')
Write-Host 'PostgreSQL LEPA database initialized. Use start-lepa-postgres.bat.' -ForegroundColor Green

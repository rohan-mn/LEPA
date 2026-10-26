param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$MavenArgs
)
. (Join-Path $PSScriptRoot 'common.ps1')
$root = Get-ProjectRoot
$mavenExe = Get-MavenExecutable $root
Push-Location $root
try {
    & $mavenExe '-f' 'backend\pom.xml' @MavenArgs
    exit $LASTEXITCODE
} finally {
    Pop-Location
}

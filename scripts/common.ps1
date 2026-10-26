$ErrorActionPreference = 'Stop'

function Get-ProjectRoot {
    return (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
}

function Import-DotEnv([string]$Path) {
    if (-not (Test-Path $Path)) { return }
    Get-Content $Path | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith('#')) { return }
        $idx = $line.IndexOf('=')
        if ($idx -lt 1) { return }
        $name = $line.Substring(0, $idx).Trim()
        $value = $line.Substring($idx + 1).Trim()
        [Environment]::SetEnvironmentVariable($name, $value, 'Process')
    }
}

function Assert-Command([string]$Name, [string]$Help) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Missing prerequisite '$Name'. $Help"
    }
}


function Get-MavenExecutable([string]$ProjectRoot) {
    $globalMaven = Get-Command 'mvn' -ErrorAction SilentlyContinue
    if ($globalMaven) {
        return $globalMaven.Source
    }

    $version = '3.9.9'
    $toolsDir = Join-Path $ProjectRoot '.tools'
    $mavenHome = Join-Path $toolsDir ("apache-maven-$version")
    $mavenExe = Join-Path $mavenHome 'bin\mvn.cmd'

    if (Test-Path $mavenExe) {
        return $mavenExe
    }

    Write-Host "Apache Maven was not found globally. Downloading portable Maven $version into the project..." -ForegroundColor Yellow
    New-Item -ItemType Directory -Path $toolsDir -Force | Out-Null
    $zipPath = Join-Path $toolsDir ("apache-maven-$version-bin.zip")
    $url = "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$version/apache-maven-$version-bin.zip"

    try {
        Invoke-WebRequest -Uri $url -OutFile $zipPath -UseBasicParsing
        Expand-Archive -Path $zipPath -DestinationPath $toolsDir -Force
        Remove-Item $zipPath -Force -ErrorAction SilentlyContinue
    } catch {
        throw "Could not download portable Maven automatically. Check your internet/proxy and retry. URL: $url. Error: $($_.Exception.Message)"
    }

    if (-not (Test-Path $mavenExe)) {
        throw "Portable Maven download completed but mvn.cmd was not found at $mavenExe"
    }
    return $mavenExe
}

function Wait-Http([string]$Url, [int]$Seconds = 45) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        try {
            $r = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 2
            if ($r.StatusCode -ge 200 -and $r.StatusCode -lt 500) { return $true }
        } catch { }
        Start-Sleep -Milliseconds 750
    } while ((Get-Date) -lt $deadline)
    return $false
}

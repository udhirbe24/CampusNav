# CampusNav one-click starter for Windows PowerShell.
# Needs Java 17 or newer. Maven is downloaded automatically by mvnw.cmd the first time.
$Host.UI.RawUI.WindowTitle = "CampusNav - Smart Campus Navigation System"
Set-Location -Path (Join-Path $PSScriptRoot "backend")

try { java -version 2>&1 | Out-Null } catch {
    Write-Host "[ERROR] Java was not found. Install Java 21 from https://adoptium.net and try again." -ForegroundColor Red
    exit 1
}

if (-not $env:PORT) { $env:PORT = "8088" }
$url = "http://localhost:$($env:PORT)/app"

Write-Host "=====================================================================" -ForegroundColor Green
Write-Host "   CampusNav - Smart Campus Navigation System (Java / Spring Boot)" -ForegroundColor Green
Write-Host "   Thapar Institute of Engineering and Technology" -ForegroundColor Green
Write-Host "=====================================================================" -ForegroundColor Green
Write-Host "   Navigation map:  $url"
Write-Host "   Admin portal:    http://localhost:$($env:PORT)/admin"
Write-Host "   Press Ctrl+C to stop."

# Open the browser once the server answers
Start-Job -ArgumentList $env:PORT, $url -ScriptBlock {
    param($port, $url)
    for ($i = 0; $i -lt 60; $i++) {
        try { Invoke-WebRequest "http://localhost:$port/api/health" -UseBasicParsing -TimeoutSec 1 | Out-Null; Start-Process $url; break }
        catch { Start-Sleep -Seconds 1 }
    }
} | Out-Null

& .\mvnw.cmd -q spring-boot:run

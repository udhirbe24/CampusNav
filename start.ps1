# CampusNav One-Click Starter for PowerShell
$Host.UI.RawUI.WindowTitle = "CampusNav - Smart Campus Navigation System"
Set-Location -Path $PSScriptRoot

Write-Host "=====================================================================" -ForegroundColor Green
Write-Host "         CampusNav - Smart Campus Navigation System                  " -ForegroundColor Green
Write-Host "         Thapar Institute of Engineering and Technology              " -ForegroundColor Green
Write-Host "=====================================================================" -ForegroundColor Green
Write-Host ""

# Detect Python
$pythonCmd = "python"
try {
    $ver = & python --version 2>&1
    if ($LASTEXITCODE -ne 0) { throw }
} catch {
    if (Test-Path "$env:LOCALAPPDATA\Python\pythoncore-3.14-64\python.exe") {
        $pythonCmd = "$env:LOCALAPPDATA\Python\pythoncore-3.14-64\python.exe"
    } else {
        $pythonCmd = "py"
    }
}

Write-Host "[*] Using Python: $pythonCmd" -ForegroundColor Cyan

# Check DB
if (-not (Test-Path "backend\instance\campusnav.db")) {
    Write-Host "[*] Initializing database and seeding Thapar campus data..." -ForegroundColor Yellow
    & $pythonCmd backend\init_db.py
}

Write-Host "[*] Starting server on http://localhost:5000 ..." -ForegroundColor Cyan
Write-Host "[*] Opening browser..." -ForegroundColor Cyan

# Open browser in 2 seconds
Start-Job -ScriptBlock { Start-Sleep -Seconds 2; Start-Process "http://localhost:5000/app" } | Out-Null

Write-Host ""
Write-Host "=====================================================================" -ForegroundColor Green
Write-Host "  SERVER ACTIVE! Press Ctrl+C to stop." -ForegroundColor White
Write-Host "  Navigation App:  http://localhost:5000/app" -ForegroundColor White
Write-Host "  Admin Portal:    http://localhost:5000/admin" -ForegroundColor White
Write-Host "  Admin Account:   admin@thapar.edu | AdminPassword123!" -ForegroundColor Yellow
Write-Host "=====================================================================" -ForegroundColor Green
Write-Host ""

& $pythonCmd backend\run.py

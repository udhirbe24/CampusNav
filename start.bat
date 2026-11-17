@echo off
title CampusNav - Smart Campus Navigation System
color 0A
:: CampusNav one-click starter for Windows (Command Prompt).
:: Needs Java 17 or newer. Maven is downloaded automatically by mvnw.cmd the first time.
cd /d "%~dp0backend"

java -version >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Java was not found. Install Java 21 from https://adoptium.net and try again.
    pause
    exit /b 1
)

if "%PORT%"=="" set "PORT=8088"

echo =====================================================================
echo    CampusNav - Smart Campus Navigation System (Java / Spring Boot)
echo    Thapar Institute of Engineering and Technology
echo =====================================================================
echo    Navigation map:  http://localhost:%PORT%/app
echo    Admin portal:    http://localhost:%PORT%/admin
echo    Press Ctrl+C to stop.
echo =====================================================================

:: Open the browser after the server has had time to start
start "" cmd /c "timeout /t 15 /nobreak >nul & start http://localhost:%PORT%/app"

call mvnw.cmd -q spring-boot:run
pause

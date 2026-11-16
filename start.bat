@echo off
title CampusNav - Smart Campus Navigation System
color 0A

:: Change working directory to this script's directory
cd /d "%~dp0"

echo =====================================================================
echo          CampusNav - Smart Campus Navigation System
echo          Thapar Institute of Engineering and Technology
echo =====================================================================
echo.

:: Detect Python executable
set "PYTHON_CMD="
python --version >nul 2>&1
if %ERRORLEVEL% equ 0 (
    set "PYTHON_CMD=python"
) else (
    py --version >nul 2>&1
    if %ERRORLEVEL% equ 0 (
        set "PYTHON_CMD=py"
    ) else (
        if exist "%LOCALAPPDATA%\Python\pythoncore-3.14-64\python.exe" (
            set "PYTHON_CMD=%LOCALAPPDATA%\Python\pythoncore-3.14-64\python.exe"
        ) else (
            echo [ERROR] Python not found on your system!
            echo Please install Python 3.10+ or add it to PATH.
            pause
            exit /b 1
        )
    )
)

echo [*] Using Python: %PYTHON_CMD%

:: Check if database exists, if not initialize it
if not exist "backend\instance\campusnav.db" (
    echo [*] Initializing CampusNav database and seeding Thapar campus data...
    "%PYTHON_CMD%" backend\init_db.py
    echo [*] Database ready.
)

echo.
echo [*] Starting CampusNav server on http://localhost:5000 ...
echo [*] Launching your web browser...
echo.

:: Launch default web browser after 2 seconds in parallel
start "" cmd /c "timeout /t 2 /nobreak >nul & start http://localhost:5000/app"

echo =====================================================================
echo   SERVER RUNNING! Press Ctrl+C in this window to stop the server.
echo.
echo   - Interactive Navigation Map:  http://localhost:5000/app
echo   - Campus Landing Page:         http://localhost:5000/
echo   - Administration Portal:       http://localhost:5000/admin
echo   - Sign In / Account:           http://localhost:5000/auth
echo.
echo   Demo Admin Login: admin@thapar.edu  ^|  Password: AdminPassword123!
echo =====================================================================
echo.

:: Start the Flask application
"%PYTHON_CMD%" backend\run.py

pause

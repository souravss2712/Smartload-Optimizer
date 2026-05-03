@echo off
setlocal
cd /d "%~dp0"

where mvn >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Maven ^(mvn^) was not found. Install Maven and JDK 17, or install Docker Desktop and use:
    echo   docker compose up --build
    exit /b 1
)

echo Starting SmartLoad on http://localhost:8080 ^(Ctrl+C to stop^)...
mvn spring-boot:run
exit /b %ERRORLEVEL%

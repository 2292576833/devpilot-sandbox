@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"
title DevPilot Sandbox v0.2.0

:: Kill old Java process
taskkill /f /im java.exe >nul 2>nul
cls

echo ========================================
echo   DevPilot Sandbox v0.2.0
echo ========================================
echo.

:: Find Java
set JAVA_CMD=java
where java >nul 2>nul
if errorlevel 1 (
  if exist "%ProgramFiles%\Eclipse Adoptium\jdk-8.0.492.9-hotspot\bin\java.exe" set "JAVA_CMD=%ProgramFiles%\Eclipse Adoptium\jdk-8.0.492.9-hotspot\bin\java.exe"
  if exist "%ProgramFiles%\Java\jdk-8\bin\java.exe" set "JAVA_CMD=%ProgramFiles%\Java\jdk-8\bin\java.exe"
  if exist "%ProgramFiles%\Java\jdk-11\bin\java.exe" set "JAVA_CMD=%ProgramFiles%\Java\jdk-11\bin\java.exe"
)
echo [OK] Java: %JAVA_CMD%

:: Build JAR if missing
if not exist "target\devpilot-sandbox-0.2.0.jar" (
  echo [BUILD] Building JAR...
  call .maven\apache-maven-3.9.16\bin\mvn.cmd clean package -DskipTests -q
  if errorlevel 1 ( echo [ERROR] Build failed! & pause & exit /b 1 )
)

:: Find free port
set PORT=9091
:checkport
netstat -ano 2>nul | findstr ":%PORT% " >nul
if !errorlevel! equ 0 (
  set /a PORT=PORT+1
  if !PORT! gtr 9200 ( echo [ERROR] No free port! & pause & exit /b 1 )
  goto checkport
)
echo [OK] Port %PORT%
echo.

:: Start Java server (independent window, persists after closing this console)
echo [*] Starting Java server on port %PORT%...
start "DevPilot Sandbox" "%JAVA_CMD%" -jar "target\devpilot-sandbox-0.2.0.jar" --server.port=%PORT%

:: Wait for server
echo [WAIT] Waiting for server...
set WAIT=0
:waitready
set /a WAIT=%WAIT%+1
if %WAIT% gtr 60 (
  echo [ERROR] Server did not start within 60s
  pause & exit /b 1
)
>nul ping 127.0.0.1 -n 2
powershell -Command "try{$r=Invoke-WebRequest 'http://127.0.0.1:%PORT%/api/v1/guard/health' -UseBasicParsing -TimeoutSec 2;exit 0}catch{exit 1}" 2>nul
if !errorlevel! equ 0 goto ready
goto waitready
:ready

:: Start MCP HTTP server (background, same console)
start /B node extensions\codex-plugin-devpilot-sandbox\mcp-http-server.js
echo [OK] Servers ready!
echo.
echo ========================================
echo   DevPilot Sandbox - RUNNING
echo ========================================
echo   API:  http://127.0.0.1:%PORT%
echo   MCP:  http://127.0.0.1:9092/mcp
echo   Web:  http://127.0.0.1:%PORT%/ui/
echo ========================================
echo.
start "" "http://127.0.0.1:%PORT%/ui/"

echo  Press any key to stop Java server...
pause >nul
taskkill /f /fi "WINDOWTITLE eq DevPilot Sandbox*" >nul 2>nul
echo [OK] Server stopped.
pause

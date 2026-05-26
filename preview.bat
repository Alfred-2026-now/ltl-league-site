@echo off
setlocal

cd /d "%~dp0"

set "PORT=4173"
if not "%~1"=="" set "PORT=%~1"

where node >nul 2>nul
if errorlevel 1 (
  echo Node.js is required to preview this site.
  echo Please install Node.js, then run this file again.
  pause
  exit /b 1
)

echo Starting LTL League Site preview at http://localhost:%PORT%
start "" "http://localhost:%PORT%"
node tools\static-server.mjs %PORT%

echo.
echo Preview server stopped.
pause

@echo off
cd /d "%~dp0"
echo Installing DevPilot Sandbox VSCode Extension...
echo.

:: Try VSIX install
if exist "extensions\devpilot-sandbox-0.2.1.vsix" (
    echo [1/2] Installing from VSIX...
    "D:\Microsoft VS Code\bin\code.cmd" --install-extension "extensions\devpilot-sandbox-0.2.1.vsix" --force >nul 2>&1
    if !errorlevel! equ 0 (
        echo [OK] Extension installed!
        echo.
        echo Open D:\Microsoft VS Code\Code.exe
        echo Press Ctrl+Shift+P -> DevPilot
        pause
        exit /b 0
    )
)

:: Fallback: copy to extensions dir
echo [2/2] Copying to extensions directory...
set EXT_DIR=%USERPROFILE%\.vscode\extensions\devpilot.devpilot-sandbox-0.2.1
if exist "%EXT_DIR%" rmdir /s /q "%EXT_DIR%"
xcopy /e /i /q "extensions\vscode-devpilot-sandbox" "%EXT_DIR%" >nul
echo [OK] Copied to %EXT_DIR%
echo.
echo Restart VSCode to activate the extension.
pause

# DevPilot Sandbox - VSCode Extension

AI Agent security sandbox for VS Code.

## Features
- Status Bar - Connection status + current role
- Role Tree - Browse roles in the sidebar
- Auto-check - Check file permission on open/save
- Decorations - Green/Red markers for file status
- Commands - Check file, command, security tests
- Web Console - One-click browser access

## Commands
- DevPilot: Check file access
- DevPilot: Check command
- DevPilot: Quick test (path traversal)
- DevPilot: Show sandbox status
- DevPilot: Open web console
- DevPilot: Select default role

## Configuration
- devpilot.baseUrl (default: http://127.0.0.1:9091)
- devpilot.defaultRole (default: CODE_ENGINEER)
- devpilot.autoCheck (default: true)
- devpilot.autoCheckOnSave (default: false)

## Usage
1. Start server: double-click run.bat
2. Extension auto-connects (status in bottom bar)
3. Click status bar or use Ctrl+Shift+P -> DevPilot

## Install
code --install-extension extensions/vscode-devpilot-sandbox/

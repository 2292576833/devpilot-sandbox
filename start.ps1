<#
.SYNOPSIS
    DevPilot Sandbox — 一键启动
.DESCRIPTION
    自动检测空闲端口、自动构建（如果需要）、启动服务并打开浏览器
.PARAMETER Port
    指定端口（默认自动检测）
.PARAMETER NoBuild
    跳过 Maven 构建
.PARAMETER NoBrowser
    不自动打开浏览器
.EXAMPLE
    .\start.ps1              # 自动检测端口启动
    .\start.ps1 -Port 9090   # 指定端口
    .\start.ps1 -NoBuild     # 跳过构建，直接启动
#>

param(
    [int]$Port = 0,
    [switch]$NoBuild,
    [switch]$NoBrowser
)
# --- cleanup stale sandbox processes ---
try {
    $stale = Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -like '*devpilot-sandbox*' }
    if ($stale) {
        Write-Host "  Cleaning stale processes..." -ForegroundColor Yellow
        $stale | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
        Start-Sleep 1
        Write-Host "  OK" -ForegroundColor Green
    }
} catch { }



$ErrorActionPreference = "Stop"
$ProjectDir = Split-Path $MyInvocation.MyCommand.Path
$JarFile = Join-Path $ProjectDir "target\devpilot-sandbox-0.1.0.jar"

Write-Host ""
Write-Host "╔══════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║   DevPilot Sandbox — 一键启动       ║" -ForegroundColor Cyan
Write-Host "╚══════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

# ── 1. 检查 Java ──
try {
    $jv = java -version 2>&1
    Write-Host "✅ Java: OK" -ForegroundColor Green
} catch {
    Write-Host "❌ 需要安装 Java 8+" -ForegroundColor Red
    exit 1
}

# ── 2. 构建 ──
if (-not (Test-Path $JarFile)) {
    if ($NoBuild) {
        Write-Host "❌ JAR 不存在，跳过构建 (--NoBuild)" -ForegroundColor Red
        exit 1
    }
    Write-Host "🔨 构建中（首次需要下载依赖，约 2 分钟）..." -ForegroundColor Yellow
    
    # 检查 Maven
    $mvn = Get-Command "mvn.cmd" -ErrorAction SilentlyContinue
    if (-not $mvn) {
        # 检查项目自带的 Maven
        $localMvn = Join-Path $ProjectDir ".maven\apache-maven-3.9.16\bin\mvn.cmd"
        if (Test-Path $localMvn) {
            & $localMvn package -DskipTests -q
        } else {
            Write-Host "❌ 未找到 Maven，请先安装或运行 mvn package" -ForegroundColor Red
            exit 1
        }
    } else {
        mvn package -DskipTests -q
    }
    Write-Host "✅ 构建完成" -ForegroundColor Green
}

# ── 3. 检测空闲端口 ──
if ($Port -eq 0) {
    $Port = 9091
    $found = $false
    for ($i = 0; $i -lt 100; $i++) {
        $inUse = netstat -ano 2>$null | Select-String ":$Port\s"
        if (-not $inUse) { $found = $true; break }
        $Port++
    }
    if (-not $found) {
        Write-Host "❌ 未找到可用端口" -ForegroundColor Red
        exit 1
    }
}
Write-Host "✅ 端口 $Port 空闲" -ForegroundColor Green

# ── 4. 启动 ──
Write-Host ""
Write-Host "╔══════════════════════════════════════╗" -ForegroundColor Green
Write-Host "║   🚀 正在启动..." -ForegroundColor Green
Write-Host "╠══════════════════════════════════════╣" -ForegroundColor Green
Write-Host "║                                      ║" -ForegroundColor Green
Write-Host "║  📡 API:                              ║" -ForegroundColor White
Write-Host "║     http://127.0.0.1:$Port/api/v1    ║" -ForegroundColor Yellow
Write-Host "║                                      ║" -ForegroundColor Green
Write-Host "║  🌐 Web 控制台:                       ║" -ForegroundColor White
Write-Host "║     http://127.0.0.1:$Port/ui/       ║" -ForegroundColor Yellow
Write-Host "║                                      ║" -ForegroundColor Green
Write-Host "║  🔍 健康检查:                         ║" -ForegroundColor White
Write-Host "║     curl http://127.0.0.1:$Port/api/v1/guard/health ║" -ForegroundColor Yellow
Write-Host "║                                      ║" -ForegroundColor Green
Write-Host "║  📂 项目路径:                         ║" -ForegroundColor White
Write-Host "║     $ProjectDir" -ForegroundColor Gray
Write-Host "║                                      ║" -ForegroundColor Green
Write-Host "╚══════════════════════════════════════╝" -ForegroundColor Green
Write-Host ""

# 打开浏览器
if (-not $NoBrowser) {
    Start-Process "http://127.0.0.1:$Port/ui/index.html"
}

# 启动服务（前台运行，Ctrl+C 停止）
java -jar $JarFile --server.port=$Port

Write-Host "`n服务已停止。" -ForegroundColor Cyan

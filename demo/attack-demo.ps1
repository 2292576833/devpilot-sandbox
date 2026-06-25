<#
.SYNOPSIS
    DevPilot Sandbox Attack Demo
    演示合法操作 vs 越界攻击的对比
#>

$BASE = "http://127.0.0.1:9091"
$HEADERS = @{ "Content-Type" = "application/json" }

Write-Host "======================================" -ForegroundColor Cyan
Write-Host "  DevPilot Sandbox - Attack Demo" -ForegroundColor Cyan
Write-Host "  演示 PathGuard + CommandGuard 效果" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host ""

# ---- Health Check ----
Write-Host "[1/5] 健康检查" -ForegroundColor Yellow
try {
    $resp = Invoke-RestMethod -Uri "$BASE/api/v1/guard/health" -Method Get
    Write-Host "  ✅ 状态: $($resp.status)" -ForegroundColor Green
    Write-Host "  可用角色: $($resp.roles -join ', ')"
} catch {
    Write-Host "  ❌ 服务未启动，请先运行: mvn spring-boot:run" -ForegroundColor Red
    exit 1
}

Write-Host ""

# ---- 1. 合法读路径 ----
Write-Host "[2/5] 合法读路径" -ForegroundColor Yellow
$body = @{ roleId = "CODE_ENGINEER"; path = "src/main/java/App.java" } | ConvertTo-Json
$resp = Invoke-RestMethod -Uri "$BASE/api/v1/guard/file-read" -Method Post -Body $body -ContentType "application/json"
if ($resp.allowed) {
    Write-Host "  ✅ 允许访问: $($resp.resolvedPath)" -ForegroundColor Green
} else {
    Write-Host "  ❌ 被拒绝: $($resp.reason)" -ForegroundColor Red
}

Write-Host ""

# ---- 2. 路径穿越攻击 ----
Write-Host "[3/5] 路径穿越攻击" -ForegroundColor Yellow
$body = @{ roleId = "CODE_ENGINEER"; path = "../../etc/passwd" } | ConvertTo-Json
$resp = Invoke-RestMethod -Uri "$BASE/api/v1/guard/file-read" -Method Post -Body $body -ContentType "application/json"
if (-not $resp.allowed) {
    Write-Host "  ✅ 已拦截: $($resp.reason)" -ForegroundColor Green
} else {
    Write-Host "  ❌ 未被拦截!" -ForegroundColor Red
}

Write-Host ""

# ---- 3. 敏感路径拦截 ----
Write-Host "[4/5] 敏感路径拦截 (.git)" -ForegroundColor Yellow
$body = @{ roleId = "CODE_ENGINEER"; path = ".git/config" } | ConvertTo-Json
$resp = Invoke-RestMethod -Uri "$BASE/api/v1/guard/file-read" -Method Post -Body $body -ContentType "application/json"
if (-not $resp.allowed) {
    Write-Host "  ✅ 已拦截: $($resp.reason)" -ForegroundColor Green
} else {
    Write-Host "  ❌ 未被拦截!" -ForegroundColor Red
}

Write-Host ""

# ---- 4. 命令校验 ----
Write-Host "[5/5] 命令校验对比" -ForegroundColor Yellow

# 合法命令
$body = @{ roleId = "CODE_ENGINEER"; command = "git status" } | ConvertTo-Json
$resp = Invoke-RestMethod -Uri "$BASE/api/v1/guard/command-run" -Method Post -Body $body -ContentType "application/json"
if ($resp.allowed) {
    Write-Host "  ✅ git status 允许" -ForegroundColor Green
} else {
    Write-Host "  ❌ git status 被拒绝" -ForegroundColor Red
}

# 越权命令
$body = @{ roleId = "CODE_ENGINEER"; command = "rm -rf /" } | ConvertTo-Json
$resp = Invoke-RestMethod -Uri "$BASE/api/v1/guard/command-run" -Method Post -Body $body -ContentType "application/json"
if (-not $resp.allowed) {
    Write-Host "  ✅ rm -rf / 已拦截: $($resp.reason)" -ForegroundColor Green
} else {
    Write-Host "  ❌ rm -rf / 未被拦截!" -ForegroundColor Red
}

# 禁止参数
$body = @{ roleId = "CODE_ENGINEER"; command = "npm install -g express" } | ConvertTo-Json
$resp = Invoke-RestMethod -Uri "$BASE/api/v1/guard/command-run" -Method Post -Body $body -ContentType "application/json"
if (-not $resp.allowed) {
    Write-Host "  ✅ npm -g 已拦截: $($resp.reason)" -ForegroundColor Green
} else {
    Write-Host "  ❌ npm -g 未被拦截!" -ForegroundColor Red
}

Write-Host ""
Write-Host "======================================" -ForegroundColor Cyan
Write-Host "  Demo 完成! 审计日志见: logs/audit.jsonl" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan

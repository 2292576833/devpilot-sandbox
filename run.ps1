# DevPilot Sandbox - Start Script
param($Port=9091)
Write-Host "Starting DevPilot Sandbox v0.2.0 on port $Port..." -ForegroundColor Cyan

# Kill any existing process on this port
$existing = netstat -ano | Select-String ":${Port} "
if ($existing) {
    Write-Host "Port ${Port} is in use, stopping..." -ForegroundColor Yellow
    $existingPid = ($existing -split "\s+")[-1]
    Stop-Process -Id $existingPid -Force -ErrorAction SilentlyContinue
    Start-Sleep 2
}

# Start HTTP MCP server on port 9092
$mcpProc = Start-Process -FilePath java -ArgumentList "-jar target\devpilot-sandbox-0.2.0.jar --server.port=$Port" -WindowStyle Hidden -PassThru
$httpMcp = Start-Process -WindowStyle Hidden -FilePath "node" -ArgumentList "extensions\codex-plugin-devpilot-sandbox\mcp-http-server.js"
Write-Host "PID: $($proc.Id)" -ForegroundColor Yellow

# Wait for server to start (up to 60 seconds)
$maxWait = 60
for ($i=0; $i -lt $maxWait; $i++) {
    Start-Sleep 1
    try { 
        $r = Invoke-WebRequest "http://127.0.0.1:$Port/api/v1/guard/health" -UseBasicParsing -TimeoutSec 2
        if ($r.StatusCode -eq 200) {
            $h = $r.Content | ConvertFrom-Json
            Write-Host "Server ready!
Write-Host '  HTTP MCP : http://127.0.0.1:9092/mcp' -ForegroundColor Cyan" -ForegroundColor Green
            Write-Host "  Web UI : http://127.0.0.1:$Port/ui/" -ForegroundColor Cyan
            Write-Host "  Status : $($h.status) v$($h.version)" -ForegroundColor Green
            Write-Host "  Mode   : $($h.mode)" -ForegroundColor Yellow
            return
        }
    } catch {
        if ($i % 5 -eq 0) { Write-Host "  Waiting... ($($i+1)s)" -ForegroundColor DarkGray }
    }
}
Write-Host "Startup failed after ${maxWait}s" -ForegroundColor Red
Write-Host "Check logs: logs/devpilot-sandbox.log" -ForegroundColor Yellow

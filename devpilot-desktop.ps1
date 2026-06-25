# DevPilot Sandbox - Graphical Desktop Client
# PowerShell WinForms GUI with dark theme

Add-Type -AssemblyName System.Windows.Forms, System.Drawing
$ErrorActionPreference = "Stop"
$scriptDir = Split-Path $MyInvocation.MyCommand.Path

# ── Globals ──
$port = 9091
$serverProc = $null
$isRunning = $false
$jarFile = Join-Path $scriptDir "target\devpilot-sandbox-0.1.0.jar"
$auditFile = Join-Path $scriptDir "logs\audit.jsonl"
$lastLogPos = 0

# ── Functions ──
function Find-Java {
    $paths = @(if ($env:JAVA_HOME) { "$($env:JAVA_HOME)\bin\java" } else { $null },
        "C:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot\bin\java",
        "C:\Program Files\Java\jdk-8\bin\java",
        "C:\Program Files\Java\jdk-11\bin\java",
        "C:\Program Files\Java\jdk-17\bin\java",
        "java")
    foreach ($p in $paths) { if ($p -and (Get-Command $p -ErrorAction SilentlyContinue)) { return $p } }
    return "java"
}

function Find-Maven {
    $m = Get-Command "mvn.cmd" -ErrorAction SilentlyContinue
    if (-not $m) { $lm = Join-Path $scriptDir ".maven\apache-maven-3.9.16\bin\mvn.cmd"; if (Test-Path $lm) { return $lm } }
    return $m
}

function Find-FreePort {
    $p = 9090
    do { $b = netstat -ano 2>$null | Select-String ":$p\s"; if (-not $b) { return $p }; $p++ } while ($p -lt 9200)
    return 0
}

function Start-Sandbox {
    if ($isRunning) { return }
    $javaPath = Find-Java
    $freePort = Find-FreePort
    if ($freePort -eq 0) { Write-Log "ERROR" "No free port found"; return }
    if (-not (Test-Path $jarFile)) {
        $mvnCmd = Find-Maven
        if (-not $mvnCmd) { Write-Log "ERROR" "JAR not built, no Maven found"; return }
        Write-Log "INFO" "Building JAR..."
        & $mvnCmd package -DskipTests -q
        if ($LASTEXITCODE -ne 0) { Write-Log "ERROR" "Build failed"; return }
    }
    $serverProc = Start-Process -FilePath $javaPath -ArgumentList @("-jar", $jarFile, "--server.port=$freePort") -WindowStyle Hidden -PassThru
    $script:port = $freePort
    $script:isRunning = $true
    $portLabel.Text = "Port: $freePort"
    $startBtn.Enabled = $false
    $stopBtn.Enabled = $true
    Write-Log "INFO" "Server starting on port $freePort..."
}

function Stop-Sandbox {
    if (-not $isRunning) { return }
    if ($serverProc -and -not $serverProc.HasExited) { $serverProc.Kill() }
    taskkill /f /im java.exe 2>$null
    $script:isRunning = $false
    $startBtn.Enabled = $true
    $stopBtn.Enabled = $false
    Update-Status
    Write-Log "INFO" "Server stopped"
}

function Update-Status {
    try {
        $r = Invoke-WebRequest "http://localhost:$port/api/v1/guard/health" -UseBasicParsing -TimeoutSec 2
        if ($r.StatusCode -eq 200) {
            $statusLabel.Text = "Running"
            $statusLabel.ForeColor = [System.Drawing.Color]::Lime
            $dotLabel.ForeColor = [System.Drawing.Color]::Lime
            return
        }
    } catch {}
    $statusLabel.Text = "Stopped"
    $statusLabel.ForeColor = [System.Drawing.Color]::Red
    $dotLabel.ForeColor = [System.Drawing.Color]::Red
}

function Write-Log($level, $msg) {
    $time = Get-Date -Format "HH:mm:ss"
    $logBox.AppendText("[$time] [$level] $msg" + [Environment]::NewLine)
    $logBox.ScrollToCaret()
}

function Read-AuditLog {
    if (-not (Test-Path $auditFile)) { return }
    try {
        $lines = Get-Content $auditFile | Select-Object -Skip $lastLogPos
        $script:lastLogPos += $lines.Count
        foreach ($line in $lines) {
            if ($line.Trim()) { $logBox.AppendText("  $line" + [Environment]::NewLine); $logBox.ScrollToCaret() }
        }
    } catch {}
}

function Open-Console {
    $url = "http://localhost:$port/ui/index.html"
    $browserPath = $null
    foreach ($bp in @("${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe","${env:ProgramFiles}\Microsoft\Edge\Application\msedge.exe","${env:LOCALAPPDATA}\Microsoft\Edge\Application\msedge.exe","${env:ProgramFiles}\Google\Chrome\Application\chrome.exe","${env:LOCALAPPDATA}\Google\Chrome\Application\chrome.exe")) {
        if (Test-Path $bp) { $browserPath = $bp; break }
    }
    if ($browserPath) { Start-Process -FilePath $browserPath -ArgumentList "--app=$url" } else { Start-Process $url }
}

# ── Create Form ──
$form = New-Object System.Windows.Forms.Form
$form.Text = "DevPilot Sandbox"
$form.Size = New-Object System.Drawing.Size(720, 520)
$form.StartPosition = "CenterScreen"
$form.BackColor = [System.Drawing.Color]::FromArgb(30, 30, 30)
$form.ForeColor = [System.Drawing.Color]::White
$form.Font = New-Object System.Drawing.Font("Segoe UI", 10)
$form.MinimumSize = $form.Size

# ── Status Bar ──
$statusPanel = New-Object System.Windows.Forms.Panel
$statusPanel.Location = New-Object System.Drawing.Point(0, 0)
$statusPanel.Size = New-Object System.Drawing.Size(720, 40)
$statusPanel.BackColor = [System.Drawing.Color]::FromArgb(45, 45, 45)

$dotLabel = New-Object System.Windows.Forms.Label
$dotLabel.Text = "●"
$dotLabel.ForeColor = [System.Drawing.Color]::Red
$dotLabel.Location = New-Object System.Drawing.Point(15, 8)
$dotLabel.Size = New-Object System.Drawing.Size(20, 24)
$dotLabel.Font = New-Object System.Drawing.Font("Segoe UI", 14, [System.Drawing.FontStyle]::Bold)
$statusPanel.Controls.Add($dotLabel)

$statusLabel = New-Object System.Windows.Forms.Label
$statusLabel.Text = "Stopped"
$statusLabel.ForeColor = [System.Drawing.Color]::Red
$statusLabel.Location = New-Object System.Drawing.Point(35, 8)
$statusLabel.Size = New-Object System.Drawing.Size(120, 24)
$statusLabel.Font = New-Object System.Drawing.Font("Segoe UI", 12, [System.Drawing.FontStyle]::Bold)
$statusPanel.Controls.Add($statusLabel)

$portLabel = New-Object System.Windows.Forms.Label
$portLabel.Text = "Port: --"
$portLabel.ForeColor = [System.Drawing.Color]::Gray
$portLabel.Location = New-Object System.Drawing.Point(170, 10)
$portLabel.Size = New-Object System.Drawing.Size(100, 20)
$statusPanel.Controls.Add($portLabel)

$form.Controls.Add($statusPanel)

# ── Log Viewer ──
$logBox = New-Object System.Windows.Forms.RichTextBox
$logBox.Location = New-Object System.Drawing.Point(10, 48)
$logBox.Size = New-Object System.Drawing.Size(685, 360)
$logBox.ReadOnly = $true
$logBox.BackColor = [System.Drawing.Color]::FromArgb(12, 12, 12)
$logBox.ForeColor = [System.Drawing.Color]::Lime
$logBox.Font = New-Object System.Drawing.Font("Consolas", 9)
$logBox.WordWrap = $false
$logBox.Text = "DevPilot Sandbox v0.1.0`r`nClick Start to begin..."
$form.Controls.Add($logBox)

# ── Buttons ──
$btnPanel = New-Object System.Windows.Forms.Panel
$btnPanel.Location = New-Object System.Drawing.Point(0, 416)
$btnPanel.Size = New-Object System.Drawing.Size(720, 50)
$btnPanel.BackColor = [System.Drawing.Color]::FromArgb(45, 45, 45)

$startBtn = New-Object System.Windows.Forms.Button
$startBtn.Text = "  Start"
$startBtn.Location = New-Object System.Drawing.Point(15, 10)
$startBtn.Size = New-Object System.Drawing.Size(100, 30)
$startBtn.FlatStyle = [System.Windows.Forms.FlatStyle]::Flat
$startBtn.BackColor = [System.Drawing.Color]::FromArgb(0, 120, 212)
$startBtn.ForeColor = [System.Drawing.Color]::White
$startBtn.FlatAppearance.BorderSize = 0
$startBtn.Add_Click({ Start-Sandbox })
$btnPanel.Controls.Add($startBtn)

$stopBtn = New-Object System.Windows.Forms.Button
$stopBtn.Text = "  Stop"
$stopBtn.Location = New-Object System.Drawing.Point(125, 10)
$stopBtn.Size = New-Object System.Drawing.Size(100, 30)
$stopBtn.FlatStyle = [System.Windows.Forms.FlatStyle]::Flat
$stopBtn.BackColor = [System.Drawing.Color]::FromArgb(200, 50, 50)
$stopBtn.ForeColor = [System.Drawing.Color]::White
$stopBtn.Enabled = $false
$stopBtn.FlatAppearance.BorderSize = 0
$stopBtn.Add_Click({ Stop-Sandbox })
$btnPanel.Controls.Add($stopBtn)

$consoleBtn = New-Object System.Windows.Forms.Button
$consoleBtn.Text = "  Open Console"
$consoleBtn.Location = New-Object System.Drawing.Point(235, 10)
$consoleBtn.Size = New-Object System.Drawing.Size(130, 30)
$consoleBtn.FlatStyle = [System.Windows.Forms.FlatStyle]::Flat
$consoleBtn.BackColor = [System.Drawing.Color]::FromArgb(50, 50, 50)
$consoleBtn.ForeColor = [System.Drawing.Color]::White
$consoleBtn.FlatAppearance.BorderSize = 0
$consoleBtn.Add_Click({ Open-Console })
$btnPanel.Controls.Add($consoleBtn)

$form.Controls.Add($btnPanel)

# ── Timer ──
$timer = New-Object System.Windows.Forms.Timer
$timer.Interval = 5000
$timer.Add_Tick({
    Update-Status
    if ($isRunning) { Read-AuditLog }
    $trayStart.Enabled = -not $isRunning
    $trayStop.Enabled = $isRunning
})
$timer.Start()

# ── Tray ──
$bmp = New-Object System.Drawing.Bitmap(16, 16)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.Clear([System.Drawing.Color]::DodgerBlue)
$f = New-Object System.Drawing.Font("Segoe UI", 8, [System.Drawing.FontStyle]::Bold)
$g.DrawString("S", $f, [System.Drawing.Brushes]::White, 2, 1)
$g.Dispose(); $f.Dispose()
$icon = [System.Drawing.Icon]::FromHandle($bmp.GetHicon())

$tray = New-Object System.Windows.Forms.NotifyIcon
$tray.Icon = $icon
$tray.Text = "DevPilot Sandbox"
$tray.Visible = $true
$tray.add_MouseClick({ if ($_.Button -eq [System.Windows.Forms.MouseButtons]::Left) { $form.WindowState = "Normal"; $form.Activate() } })

$trayMenu = New-Object System.Windows.Forms.ContextMenuStrip
$trayStart = $trayMenu.Items.Add("Start Server", $null, { Start-Sandbox })
$trayStop = $trayMenu.Items.Add("Stop Server", $null, { Stop-Sandbox })
$trayStop.Enabled = $false
$trayMenu.Items.Add("-")
$trayMenu.Items.Add("Open Console", $null, { Open-Console }).Font = New-Object System.Drawing.Font("Segoe UI", 9)
$trayMenu.Items.Add("Show Window", $null, { $form.WindowState = "Normal"; $form.Activate() })
$trayMenu.Items.Add("-")
$trayMenu.Items.Add("Exit", $null, {
    Stop-Sandbox; $tray.Visible = $false; $tray.Dispose(); $icon.Dispose()
    $timer.Stop(); [System.Windows.Forms.Application]::Exit()
})
$tray.ContextMenuStrip = $trayMenu

# ── Events ──
$form.Add_Resize({
    if ($form.WindowState -eq "Minimized") { $form.Hide(); $tray.ShowBalloonTip(2000, "DevPilot Sandbox", "Running in background. Right-click tray icon for options.", [System.Windows.Forms.ToolTipIcon]::Info) }
})

$form.Add_FormClosing({
    Stop-Sandbox; $tray.Visible = $false; $tray.Dispose(); $icon.Dispose()
    $timer.Stop()
}) -SupportEvent

Write-Log "INFO" "GUI started. Ready."

# ── Show ──
[System.Windows.Forms.Application]::Run($form)

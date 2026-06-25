const vscode = require("vscode");
const http = require("http");
const https = require("https");

let statusBarItem;
let treeDataProvider;
let decorationAllowed;
let decorationDenied;
let diagnosticCollection;
let outputChannel;
let wasOnline = false;
let retryCount = 0;
let extensionActive = true;

function log(msg) {
  if (!outputChannel) return;
  outputChannel.appendLine("[" + new Date().toLocaleTimeString() + "] " + msg);
}

function getConfig() {
  const cfg = vscode.workspace.getConfiguration("devpilot");
  return {
    baseUrl: (cfg.baseUrl || "http://127.0.0.1:9091").replace(/\/+$/, ""),
    defaultRole: cfg.defaultRole || "CODE_ENGINEER",
    autoCheck: cfg.autoCheck !== false,
    autoCheckOnSave: cfg.autoCheckOnSave || false
  };
}

function apiRequest(method, path, body) {
  const cfg = getConfig();
  const url = new URL(cfg.baseUrl + path);
  const mod = url.protocol === "https:" ? https : http;
  return new Promise((resolve, reject) => {
    const opts = {
      hostname: url.hostname, port: url.port,
      path: url.pathname + url.search, method: method,
      headers: { "Content-Type": "application/json" },
      timeout: 5000
    };
    const req = mod.request(opts, (res) => {
      let data = "";
      res.on("data", (c) => (data += c));
      res.on("end", () => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          try { return resolve(JSON.parse(data)); }
          catch (e) { return reject(new Error("Invalid response: " + data)); }
        }
        // Handle error status codes (400, 404, 429, 500, etc.)
        try {
          const errBody = JSON.parse(data);
          reject(new Error(errBody.reason || errBody.message || "HTTP " + res.statusCode));
        } catch (e) {
          reject(new Error("Server error (" + res.statusCode + "): " + data.slice(0, 200)));
        }
      });
    });
    req.on("error", (e) => reject(new Error("Connection refused: is the DevPilot server running?")));
    req.on("timeout", () => { req.destroy(); reject(new Error("Timeout")); });
    if (body) req.write(JSON.stringify(body));
    req.end();
  });
}



function rawRequest(method, path) {
  const cfg = getConfig();
  const url = new URL(cfg.baseUrl + path);
  const mod = url.protocol === "https:" ? https : http;
  return new Promise((resolve, reject) => {
    const opts = { hostname: url.hostname, port: url.port, path: url.pathname + url.search, method: method, timeout: 5000 };
    const req = mod.request(opts, (res) => {
      let data = "";
      res.on("data", (c) => data += c);
      res.on("end", () => resolve(data));
    });
    req.on("error", (e) => reject(e));
    req.on("timeout", () => { req.destroy(); reject(new Error("Timeout")); });
    req.end();
  });
}


function findJarPath() {
  var cfg = vscode.workspace.getConfiguration("devpilot").get("serverJarPath");
  if (cfg) { try { if (require("fs").existsSync(cfg)) return cfg; } catch(e) {} }
  try {
    var folders = vscode.workspace.workspaceFolders;
    if (folders) for (var i = 0; i < folders.length; i++) {
      var p = require("path").join(folders[i].uri.fsPath, "target", "devpilot-sandbox-0.2.0.jar");
      if (require("fs").existsSync(p)) return p;
    }
  } catch(e) {}
  var home = require("os").homedir();
  var checks = [
    require("path").join(process.cwd(), "target", "devpilot-sandbox-0.2.0.jar"),
    require("path").join(home, "Documents", "New project 2", "devpilot-sandbox", "target", "devpilot-sandbox-0.2.0.jar"),
    require("path").join(__dirname, "..", "..", "target", "devpilot-sandbox-0.2.0.jar"),
  ];
  for (var i = 0; i < checks.length; i++) { try { if (require("fs").existsSync(checks[i])) return checks[i]; } catch(e) {} }
  return "";
}
function startServer(jarPath) {
  return new Promise(function(resolve, reject) {
    var cp = require("child_process");
    var proc = cp.spawn("java", ["-jar", jarPath, "--server.port=9091"], { detached: true, stdio: "ignore" });
    proc.unref();
    var maxWait = 30000;
    var waited = 0;
    var timer = setInterval(function() {
      waited += 1000;
      apiRequest("GET", "/api/v1/guard/health").then(function() {
        clearInterval(timer); resolve();
      }).catch(function() {
        if (waited >= maxWait) { clearInterval(timer); reject(new Error("Server start timeout")); }
      });
    }, 1000);
  });
}
class DevPilotTreeProvider {
  constructor() {
    this._onDidChangeTreeData = new vscode.EventEmitter();
    this.onDidChangeTreeData = this._onDidChangeTreeData.event;
  }
  refresh() { this._onDidChangeTreeData.fire(); }
  getTreeItem(element) { return element; }
  async getChildren(element) {
    if (!element) {
      try {
        const h = await apiRequest("GET", "/api/v1/guard/health");
        const roles = h.roles || [];
        if (roles.length === 0) return [new vscode.TreeItem("No roles found")];
        return roles.map(r => {
          const item = new vscode.TreeItem(r, vscode.TreeItemCollapsibleState.None);
          item.iconPath = new vscode.ThemeIcon("shield");
          item.contextValue = "role";
          item.command = { command: "devpilot.selectRole", title: "Select Role", arguments: [r] };
          item.tooltip = "Role: " + r;
          return item;
        });
      } catch (e) {
        const item = new vscode.TreeItem("Offline - click to retry", vscode.TreeItemCollapsibleState.None);
        item.iconPath = new vscode.ThemeIcon("warning");
        item.command = { command: "devpilot.showStatus", title: "Retry" };
        return [item];
      }
    }
    return [];
  }
}

async function pickRole() {
  try {
    const h = await apiRequest("GET", "/api/v1/guard/health");
    const roles = h.roles || [getConfig().defaultRole];
    return await vscode.window.showQuickPick(roles, { placeHolder: "Select role" });
  } catch (e) {
    return await vscode.window.showInputBox({ prompt: "Role ID", value: getConfig().defaultRole });
  }
}

function activate(context) {
  extensionActive = true;
  outputChannel = vscode.window.createOutputChannel("DevPilot Sandbox");
  log("Extension activated");
  decorationAllowed = vscode.window.createTextEditorDecorationType({
    backgroundColor: "rgba(0,200,0,0.15)", isWholeLine: true,
    overviewRulerColor: "green", overviewRulerLane: vscode.OverviewRulerLane.Right
  });
  decorationDenied = vscode.window.createTextEditorDecorationType({
    backgroundColor: "rgba(200,0,0,0.15)", isWholeLine: true,
    overviewRulerColor: "red", overviewRulerLane: vscode.OverviewRulerLane.Right
  });
  diagnosticCollection = vscode.languages.createDiagnosticCollection("devpilot");

  treeDataProvider = new DevPilotTreeProvider();
  
  // Auto-start server on VSCode startup
    var sjp = vscode.workspace.getConfiguration("devpilot").get("serverJarPath");
  if (!sjp) sjp = findJarPath();
  if (sjp) {
    setTimeout(function() {
      startServer(sjp).then(function() {
        log("Server auto-started"); updateStatusBar();
      }).catch(function(e) {
        log("Auto-start failed: " + e.message);
      });
    }, 2000);
  }

  context.subscriptions.push(
    vscode.window.registerTreeDataProvider("devpilotRoles", treeDataProvider)
  );

  statusBarItem = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right, 100);
  statusBarItem.command = "devpilot.showStatus";
  statusBarItem.tooltip = "DevPilot Sandbox - Click for details";
  statusBarItem.show();
  context.subscriptions.push(statusBarItem);
  updateStatusBar();

  if (getConfig().autoCheck) {
    context.subscriptions.push(
      vscode.workspace.onDidOpenTextDocument((doc) => {
        if (doc && doc.uri && doc.uri.scheme === "file")
          setTimeout(() => checkFileAccess(doc.uri.fsPath), 1000);
      })
    );
  }
  if (getConfig().autoCheckOnSave) {
    context.subscriptions.push(
      vscode.workspace.onDidSaveTextDocument((doc) => {
        if (doc && doc.uri && doc.uri.scheme === "file")
          checkFileAccess(doc.uri.fsPath);
      })
    );
  }

  context.subscriptions.push(
    vscode.commands.registerCommand("devpilot.checkFile", async (filePath) => {
      const editor = vscode.window.activeTextEditor;
      const path = filePath || (editor ? editor.document.fileName : null);
      if (!path) { vscode.window.showWarningMessage("Open a file first"); return; }
      const role = await pickRole();
      if (!role) return;
      await checkFileAccess(path, role);
    }),

    vscode.commands.registerCommand("devpilot.checkCommand", async () => {
      const cmd = await vscode.window.showInputBox({ prompt: "Command to check", value: "git status" });
      if (!cmd) return;
      const role = await vscode.window.showInputBox({ prompt: "Role ID", value: getConfig().defaultRole });
      if (!role) return;
      try {
        const r = await apiRequest("POST", "/api/v1/guard/command-run", { roleId: role, command: cmd });
        if (r.allowed) {
          vscode.window.showInformationMessage("Allowed: command '" + cmd + "' is permitted");
        } else {
          vscode.window.showErrorMessage("Denied: " + (r.reason || "Not permitted"));
        }
      } catch (e) { vscode.window.showErrorMessage("Error: " + e.message); }
    }),

    vscode.commands.registerCommand("devpilot.quickTest", async () => {
      try {
        // Test with a blocked path
        const r = await apiRequest("POST", "/api/v1/guard/file-read", { roleId: getConfig().defaultRole, path: "../../etc/passwd" });
        if (r.allowed) {
          vscode.window.showErrorMessage("WARNING: Path traversal NOT blocked! Check your policy.");
        } else {
          vscode.window.showInformationMessage("Path traversal blocked: " + (r.reason || ""));
        }
        // Also test a dangerous command
        try {
          const r2 = await apiRequest("POST", "/api/v1/guard/command-run", { roleId: getConfig().defaultRole, command: "rm -rf /" });
          if (r2.allowed) {
            vscode.window.showWarningMessage("Dangerous command 'rm -rf /' was ALLOWED!");
          } else {
            vscode.window.showInformationMessage("Dangerous command blocked: " + (r2.reason || ""));
          }
        } catch (e2) { /* ignore secondary error */ }
      } catch (e) { vscode.window.showErrorMessage("Error: " + e.message); }
    }),

    vscode.commands.registerCommand("devpilot.openConsole", async () => {
      const cfg = getConfig();
      vscode.env.openExternal(vscode.Uri.parse(cfg.baseUrl + "/ui/"));
      vscode.window.showInformationMessage("Opening DevPilot Console at " + cfg.baseUrl + "/ui/");
    }),

    vscode.commands.registerCommand("devpilot.selectRole", async (role) => {
      // If called without arguments (from command palette), show picker
      if (!role) {
        role = await pickRole();
        if (!role) return;
      }
      const cfg = vscode.workspace.getConfiguration("devpilot");
      await cfg.update("defaultRole", role, vscode.ConfigurationTarget.Global);
      vscode.window.showInformationMessage("Default role set to: " + role);
      updateStatusBar();
    }),

    vscode.commands.registerCommand("devpilot.showStatus", showStatusDialog),
    vscode.commands.registerCommand("devpilot.refreshRoles", () => {
      if (treeDataProvider) treeDataProvider.refresh();
    }),
    
    vscode.commands.registerCommand("devpilot.showStats", async () => {
      try {
        const s = await vscode.window.withProgress({ location: vscode.ProgressLocation.Notification, title: "Loading stats" }, () => apiRequest("GET", "/api/v1/guard/stats"));
        if (!s || s.totalRequests === undefined) {
          vscode.window.showInformationMessage("No stats available yet.");
          return;
        }
        const items = [
          { label: "Requests: " + (s.totalRequests || 0), description: "" },
          { label: "Blocked: " + (s.totalBlocked || 0), description: "" },
          { label: "Rate: " + (s.blockedRate || "0%"), description: "" },
        ];
        if (s.blockedCommandsByRole) {
          const roles = Object.keys(s.blockedCommandsByRole);
          items.push({ label: "--- Blocked by role ---", description: "" });
          for (let i = 0; i < roles.length; i++) {
            items.push({ label: "  " + roles[i] + ": " + s.blockedCommandsByRole[roles[i]] + " commands", description: "" });
          }
        }
        items.push({ label: "Refresh", description: "" });
        const pick = await vscode.window.showQuickPick(items, { placeHolder: "Sandbox Statistics" });
        if (pick && pick.label === "Refresh") vscode.commands.executeCommand("devpilot.showStats");
      } catch (e) { vscode.window.showErrorMessage("Error: " + e.message); }
    }),

    vscode.commands.registerCommand("devpilot.batchTest", async () => {
      const role = getConfig().defaultRole;
      const testCmds = ["rm -rf /", "format C: /q", "sudo rm -rf /", "net user admin /add", "del /f /s /q", "shutdown /s"];
      const results = [];
      await vscode.window.withProgress({ location: vscode.ProgressLocation.Notification, title: "Testing dangerous commands..." }, async () => {
        for (let i = 0; i < testCmds.length; i++) {
          try {
            const r = await apiRequest("POST", "/api/v1/guard/command-run", { roleId: role, command: testCmds[i] });
            results.push({ cmd: testCmds[i], allowed: r.allowed, reason: r.reason });
          } catch (e) {
            results.push({ cmd: testCmds[i], allowed: null, reason: e.message });
          }
        }
      });
      const items = results.map(function(r) {
        const icon = r.allowed === false ? "[BLOCK]" : r.allowed === true ? "[OK]" : "[ERR]";
        return { label: icon + " " + r.cmd, description: r.allowed === false ? (r.reason || "") : (r.allowed === true ? "Allowed" : "Error") };
      });
      items.push({ label: "Run again", description: "" });
      const pick = await vscode.window.showQuickPick(items, { placeHolder: "Dangerous Commands Test Results" });
      if (pick && pick.label.includes("Run again")) vscode.commands.executeCommand("devpilot.batchTest");
    }),


    vscode.commands.registerCommand("devpilot.showAuditLog", async () => {
      try {
        const r = await apiRequest("GET", "/api/v1/guard/audit?limit=30");
        const entries = (r.entries || []).map(function(e) {
          try { return JSON.parse(e); } catch (x) { return null; }
        }).filter(Boolean);
        if (entries.length === 0) { vscode.window.showInformationMessage("No audit log entries."); return; }
        const items = entries.map(function(e) {
          const ts = e.timestamp ? new Date(e.timestamp).toLocaleString("zh-CN") : "-";
          const icon = e.allowed ? "[OK]" : "[X]";
          return { label: icon + " " + (e.operation || ""), description: (e.roleId || ""), detail: (e.reason || ts) };
        });
        items.push({ label: "[Refresh]", description: "" });
        const pick = await vscode.window.showQuickPick(items, { placeHolder: "Recent audit log" });
        if (pick && pick.label.includes("Refresh")) vscode.commands.executeCommand("devpilot.showAuditLog");
      } catch (e) { vscode.window.showErrorMessage("Error: " + e.message); }
    }),

    vscode.commands.registerCommand("devpilot.checkFileWrite", async () => {
      const editor = vscode.window.activeTextEditor;
      const defaultPath = editor ? editor.document.fileName : "";
      const path = await vscode.window.showInputBox({ prompt: "File path to write", value: defaultPath });
      if (!path) return;
      const role = await vscode.window.showInputBox({ prompt: "Role ID", value: getConfig().defaultRole });
      if (!role) return;
      try {
        const r = await apiRequest("POST", "/api/v1/guard/file-write", { roleId: role, path: path });
        if (r.allowed) { vscode.window.showInformationMessage("Write allowed: " + path); }
        else { vscode.window.showErrorMessage("Write denied: " + (r.reason || "")); }
      } catch (e) { vscode.window.showErrorMessage("Error: " + e.message); }
    }),

    vscode.commands.registerCommand("devpilot.showRoleDetails", async () => {
      try {
        const roles = await apiRequest("GET", "/api/v1/policy/roles");
        if (!roles || roles.length === 0) { vscode.window.showInformationMessage("No roles found."); return; }
        const pick = await vscode.window.showQuickPick(
          roles.map(function(r) { return { label: r.id, description: r.workDir || "" }; }),
          { placeHolder: "Select a role" }
        );
        if (!pick) return;
        const role = roles.find(function(r) { return r.id === pick.label; });
        if (!role) return;
        const items = [
          { label: "ID: " + role.id, description: "Role ID" },
          { label: "Work Dir: " + (role.workDir || "-"), description: "" },
          { label: "Mode: " + (role.readonly ? "Readonly" : "Read/Write"), description: "" },
          { label: "Max Size: " + (role.maxFileSizeMb || "default") + " MB", description: "" },
          { label: "Commands: " + (role.allowedCommands || []).length, description: "" },
        ];
        (role.allowedCommands || []).forEach(function(c) {
          items.push({ label: "  " + c.command + " (" + (c.subcommands || []).join(", ") + ")", description: "" });
        });
        items.push({ label: "[Refresh]", description: "" });
        const p2 = await vscode.window.showQuickPick(items, { placeHolder: "Role: " + role.id });
        if (p2 && p2.label.includes("Refresh")) vscode.commands.executeCommand("devpilot.showRoleDetails");
      } catch (e) { vscode.window.showErrorMessage("Error: " + e.message); }
    }),

    vscode.commands.registerCommand("devpilot.runDiagnostics", async () => {
      const results = [];
      try { const h = await apiRequest("GET", "/api/v1/guard/health"); results.push({ label: "[OK] Server: " + h.status, description: "v" + (h.version || "?") }); } catch (e) { results.push({ label: "[FAIL] Server unreachable", description: e.message }); }
      try { const s = await apiRequest("GET", "/api/v1/guard/stats"); results.push({ label: "[OK] Stats: " + (s.totalRequests || 0) + " req, " + (s.totalBlocked || 0) + " blocked", description: "" }); } catch (e) { results.push({ label: "[FAIL] Stats API", description: e.message }); }
      try { const r = await apiRequest("POST", "/api/v1/guard/command-run", { roleId: getConfig().defaultRole, command: "rm -rf /" }); results.push({ label: r.allowed === false ? "[OK] rm -rf / blocked" : "[WARN] rm -rf / ALLOWED", description: r.reason || "" }); } catch (e) { results.push({ label: "[FAIL] rm -rf test", description: e.message }); }
      try { const r = await apiRequest("POST", "/api/v1/guard/command-run", { roleId: getConfig().defaultRole, command: "git status" }); results.push({ label: r.allowed ? "[OK] git allowed" : "[WARN] git DENIED", description: r.reason || "" }); } catch (e) { results.push({ label: "[FAIL] git test", description: e.message }); }
      try { const roles = await apiRequest("GET", "/api/v1/policy/roles"); results.push({ label: "[OK] " + (roles.length || 0) + " roles", description: "" }); } catch (e) { results.push({ label: "[FAIL] Roles API", description: e.message }); }
      results.push({ label: "--- Complete ---", description: "" });
      await vscode.window.showQuickPick(results, { placeHolder: "Diagnostics" });
    }),

    vscode.commands.registerCommand("devpilot.exportAudit", async () => {
      try {
        const r = await apiRequest("GET", "/api/v1/guard/audit?limit=500");
        const entries = (r.entries || []).map(function(e) { try { return JSON.parse(e); } catch (x) { return null; } }).filter(Boolean);
        if (entries.length === 0) { vscode.window.showInformationMessage("No entries."); return; }
        const uri = await vscode.window.showSaveDialog({ filters: { "JSON Files": ["json"] }, defaultUri: vscode.Uri.file("devpilot-audit.json") });
        if (!uri) return;
        require("fs").writeFileSync(uri.fsPath, JSON.stringify(entries, null, 2), "utf8");
        vscode.window.showInformationMessage("Exported " + entries.length + " entries");
      } catch (e) { vscode.window.showErrorMessage("Error: " + e.message); }
    }),

    vscode.commands.registerCommand("devpilot.viewPolicy", async () => {
      try {
        const yaml = await rawRequest("GET", "/api/v1/policy/yaml");
        const doc = await vscode.workspace.openTextDocument({ content: yaml, language: "yaml" });
        vscode.window.showTextDocument(doc);
      } catch (e) { vscode.window.showErrorMessage("Error: " + e.message); }
    }),    vscode.commands.registerCommand("devpilot.startServer", async function() {
      var jarPath = vscode.workspace.getConfiguration("devpilot").get("serverJarPath");
      if (!jarPath) jarPath = findJarPath();
      if (!jarPath) {
        jarPath = await vscode.window.showInputBox({ prompt: "Path to sandbox JAR", value: "target/devpilot-sandbox-0.2.0.jar" });
        if (!jarPath) return;
      }
      await vscode.window.withProgress({ location: vscode.ProgressLocation.Notification, title: "Starting DevPilot Sandbox..." }, function() { return startServer(jarPath); });
      vscode.window.showInformationMessage("DevPilot Sandbox started");
      updateStatusBar();
    }),

vscode.commands.registerCommand("devpilot.showOutput", () => {
      if (outputChannel) outputChannel.show();
    })
  );

  // Add all disposables
  context.subscriptions.push(
    decorationAllowed, decorationDenied, diagnosticCollection
  );
}

async function checkFileAccess(filePath, roleOverride) {
  const role = roleOverride || getConfig().defaultRole;
  const editor = vscode.window.activeTextEditor;
  try {
    const r = await apiRequest("POST", "/api/v1/guard/file-read", { roleId: role, path: filePath });
    const uri = vscode.Uri.file(filePath);
    if (editor && editor.document.uri.toString() === uri.toString()) {
      editor.setDecorations(decorationAllowed, []);
      editor.setDecorations(decorationDenied, []);
      const line = editor.selection.active.line;
      if (r.allowed) {
        editor.setDecorations(decorationAllowed, [new vscode.Range(line, 0, line, 0)]);
        // Clear diagnostics for this file
        if (diagnosticCollection) diagnosticCollection.delete(uri);
      } else {
        editor.setDecorations(decorationDenied, [new vscode.Range(line, 0, line, 0)]);
        if (diagnosticCollection) {
          diagnosticCollection.set(uri, [new vscode.Diagnostic(
            new vscode.Range(line, 0, line, 10),
            "Access denied: " + (r.reason || "No permission"),
            vscode.DiagnosticSeverity.Error
          )]);
        }
      }
    }
    return r;
  } catch (e) {
    if (e.message.includes("Connection refused")) {
      vscode.window.showWarningMessage("Cannot connect to DevPilot Sandbox. Is the server running?");
    }
    return null;
  }
}

async function updateStatusBar() {
  if (!extensionActive) return;
  try {
    const h = await apiRequest("GET", "/api/v1/guard/health");
    if (!extensionActive) return;
    const role = getConfig().defaultRole;
    statusBarItem.text = "$(check) DevPilot: UP [" + role + "]";
    statusBarItem.backgroundColor = new vscode.ThemeColor("statusBarItem.prominentForeground");
    statusBarItem.tooltip = "Connected to " + getConfig().baseUrl + " | Version: " + (h.version || "?") + " | Blocked: " + (h.totalBlocked || 0);
    retryCount = 0;
    log("Connected: " + h.status + " (v" + (h.version || "?") + ")");
    if (!wasOnline) {
      wasOnline = true;
      vscode.window.showInformationMessage("DevPilot Sandbox connected");
      if (treeDataProvider) treeDataProvider.refresh();
    }
    if (extensionActive) setTimeout(updateStatusBar, 30000);
  } catch (e) {
    if (!extensionActive) return;
    statusBarItem.text = "$(warning) DevPilot: Offline";
    statusBarItem.backgroundColor = new vscode.ThemeColor("statusBarItem.errorBackground");
    statusBarItem.tooltip = "Server not running. Start with .\\run.ps1";
    wasOnline = false;
    if (retryCount === 0) log("Server not reachable: " + e.message);
    const delay = Math.min(1000 * Math.pow(2, retryCount), 30000);
    retryCount++;
    if (extensionActive) setTimeout(updateStatusBar, delay);
  }
}

async function showStatusDialog() {
  try {
    const h = await apiRequest("GET", "/api/v1/guard/health");
    const items = [
      { label: "$(check) Status: " + h.status, description: "v" + (h.version || "?") },
      { label: "$(package) Mode: " + (h.mode || "multi"), description: "" },
      { label: "$(people) Roles: " + (h.roles || []).length, description: (h.roles || []).join(", ") },
      { label: "$(shield) Blocked: " + (h.totalBlocked || 0) + " / " + (h.totalRequests || 0) + " requests", description: "Rate: " + (h.blockedRate || "0%") },
      { label: "$(globe) Open Web Console", description: "" },
      { label: "$(refresh) Refresh", description: "" }
    ];
    const pick = await vscode.window.showQuickPick(items, { placeHolder: "DevPilot Sandbox" });
    if (!pick) return;
    if (pick.label.includes("Open Web Console")) {
      vscode.commands.executeCommand("devpilot.openConsole");
    } else if (pick.label.includes("Refresh")) {
      updateStatusBar();
      if (treeDataProvider) treeDataProvider.refresh();
    }
  } catch (e) {
    const pick = await vscode.window.showQuickPick([
      { label: "$(warning) Sandbox Offline", description: "Cannot connect to " + getConfig().baseUrl },
      { label: "$(globe) Open Web Console (may fail)", description: "" },
      { label: "$(refresh) Retry", description: "" }
    ], { placeHolder: "DevPilot Sandbox" });
    if (!pick) return;
    if (pick.label.includes("Open Web Console")) {
      vscode.commands.executeCommand("devpilot.openConsole");
    } else if (pick.label.includes("Retry")) {
      updateStatusBar();
    }
  }
}

function deactivate() {
  extensionActive = false;
  if (outputChannel) outputChannel.dispose();
  log("Extension deactivated");
}

module.exports = { activate, deactivate };

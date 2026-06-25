const http = require("http");
const https = require("https");

console.error("[DevPilot Sandbox MCP] Server starting on PID " + process.pid + "...");

const SANDBOX_URL = (process.env.SANDBOX_URL || "http://127.0.0.1:9091").replace(/\/+$/, "");

function api(method, path, body) {
  return new Promise((resolve, reject) => {
    const url = new URL(SANDBOX_URL + path);
    const mod = url.protocol === "https:" ? https : http;
    const options = {
      hostname: url.hostname,
      port: url.port,
      path: url.pathname + (url.search || ""),
      method: method,
      headers: { "Content-Type": "application/json" },
      timeout: 10000
    };
    const req = mod.request(options, (res) => {
      let data = "";
      res.on("data", (chunk) => { data += chunk; });
      res.on("end", () => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          try { resolve(JSON.parse(data)); }
          catch (e) { reject(new Error("Invalid response from sandbox")); }
        } else {
          try { const err = JSON.parse(data); reject(new Error(err.reason || "Error " + res.statusCode)); }
          catch (e) { reject(new Error("HTTP " + res.statusCode)); }
        }
      });
    });
    req.on("error", (e) => reject(new Error("Sandbox unreachable - is the server running? (" + e.message + ")")));
    req.on("timeout", () => { req.destroy(); reject(new Error("Request timeout")); });
    if (body) req.write(JSON.stringify(body));
    req.end();
  });
}

const TOOLS = [
  {
    name: "check_file_read",
    description: "Check if a file can be safely read (prevents path traversal)",
    inputSchema: { type: "object", properties: { path: { type: "string", description: "File path" }, roleId: { type: "string", description: "Role ID (default: CODE_ENGINEER)" } }, required: ["path"] }
  },
  {
    name: "check_file_write",
    description: "Check if a file can be safely written",
    inputSchema: { type: "object", properties: { path: { type: "string", description: "File path" }, roleId: { type: "string", description: "Role ID" } }, required: ["path"] }
  },
  {
    name: "check_command",
    description: "Check if a command is safe to run (blocks dangerous commands)",
    inputSchema: { type: "object", properties: { command: { type: "string", description: "Command to check" }, roleId: { type: "string", description: "Role ID" } }, required: ["command"] }
  },
  {
    name: "check_permission",
    description: "Check ANY operation (file-read, file-write, or command-run) in one call",
    inputSchema: { type: "object", properties: { type: { type: "string", enum: ["read", "write", "command"], description: "Operation type" }, value: { type: "string", description: "File path or command" }, roleId: { type: "string", description: "Role ID" } }, required: ["type", "value"] }
  },
  {
    name: "get_health",
    description: "Get sandbox server health and stats",
    inputSchema: { type: "object", properties: {} }
  },
  {
    name: "get_stats",
    description: "Get interception statistics (total requests, blocked count, by-role breakdown)",
    inputSchema: { type: "object", properties: {} }
  },
  {
    name: "get_dangerous_commands",
    description: "List all built-in dangerous commands that are always blocked",
    inputSchema: { type: "object", properties: {} }
  },
  {
    name: "get_audit_log",
    description: "Query the audit log",
    inputSchema: { type: "object", properties: { role: { type: "string", description: "Filter by role" }, operation: { type: "string", description: "Filter by operation" }, limit: { type: "number", description: "Number of entries (default: 20)" } } }
  },
  {
    name: "get_roles",
    description: "Get all available roles and their configurations",
    inputSchema: { type: "object", properties: {} }
  },
  {
    name: "get_diagnostics",
    description: "Run a self-test: check MCP server status, Node.js, and sandbox connectivity",
    inputSchema: { type: "object", properties: {} }
  },
  {
    name: "get_blocked_summary",
    description: "Get a plain-text summary of blocked requests per role",
    inputSchema: { type: "object", properties: {} }
  }
];

console.error("[DevPilot Sandbox MCP] Loaded " + TOOLS.length + " tools");

async function handleToolCall(name, args) {
  const roleId = args.roleId || "CODE_ENGINEER";
  switch (name) {
    case "check_file_read": {
      const r = await api("POST", "/api/v1/guard/file-read", { roleId, path: args.path });
      return { content: [{ type: "text", text: JSON.stringify(r, null, 2) }] };
    }
    case "check_file_write": {
      const r = await api("POST", "/api/v1/guard/file-write", { roleId, path: args.path });
      return { content: [{ type: "text", text: JSON.stringify(r, null, 2) }] };
    }
    case "check_command": {
      const r = await api("POST", "/api/v1/guard/command-run", { roleId, command: args.command });
      return { content: [{ type: "text", text: JSON.stringify(r, null, 2) }] };
    }
    case "check_permission": {
      const ep = { read: "file-read", write: "file-write", command: "command-run" }[args.type];
      if (!ep) return { content: [{ type: "text", text: "Error: type must be read, write, or command" }], isError: true };
      const body = args.type === "command" ? { roleId, command: args.value } : { roleId, path: args.value };
      const r = await api("POST", "/api/v1/guard/" + ep, body);
      return { content: [{ type: "text", text: JSON.stringify(r, null, 2) }] };
    }
    case "get_health": {
      const r = await api("GET", "/api/v1/guard/health");
      return { content: [{ type: "text", text: JSON.stringify(r, null, 2) }] };
    }
    case "get_stats": {
      const r = await api("GET", "/api/v1/guard/stats");
      return { content: [{ type: "text", text: JSON.stringify(r, null, 2) }] };
    }
    case "get_dangerous_commands": {
      const r = await api("GET", "/api/v1/guard/dangerous-commands");
      return { content: [{ type: "text", text: JSON.stringify(r, null, 2) }] };
    }
    case "get_audit_log": {
      const params = new URLSearchParams();
      if (args.role) params.set("role", args.role);
      if (args.operation) params.set("operation", args.operation);
      params.set("limit", String(args.limit || 20));
      const r = await api("GET", "/api/v1/guard/audit?" + params.toString());
      return { content: [{ type: "text", text: JSON.stringify(r, null, 2) }] };
    }
    case "get_roles": {
      const r = await api("GET", "/api/v1/policy/roles");
      return { content: [{ type: "text", text: JSON.stringify(r, null, 2) }] };
    }
    case "get_blocked_summary": {
      const s = await api("GET", "/api/v1/guard/stats");
      let txt = "Stats:\n  Total: " + (s.totalRequests || 0) + "\n  Blocked: " + (s.totalBlocked || 0) + "\n  Rate: " + (s.blockedRate || "0%");
      if (s.blockedCommandsByRole) {
        const ks = Object.keys(s.blockedCommandsByRole);
        txt += "\n\n  Blocked by role:";
        for (let i = 0; i < ks.length; i++) txt += "\n    " + ks[i] + ": " + s.blockedCommandsByRole[ks[i]] + " commands";
      }
      if (s.blockedFilesByRole) {
        const ks = Object.keys(s.blockedFilesByRole);
        for (let i = 0; i < ks.length; i++) txt += "\n    " + ks[i] + ": " + s.blockedFilesByRole[ks[i]] + " files";
      }
      return { content: [{ type: "text", text: txt }] };
    }
    case "get_diagnostics": {
      const d = { status: "running", name: "devpilot-sandbox", version: "0.2.0", nodeVersion: process.version, platform: process.platform, sandboxUrl: SANDBOX_URL, pid: process.pid, cwd: process.cwd(), toolsCount: TOOLS.length };
      try { const h = await api("GET","/api/v1/guard/health"); d.sandboxStatus = h.status; d.sandboxVersion = h.version; d.sandboxMode = h.mode; d.sandboxRoles = h.roles; } catch(e) { d.sandboxStatus = "unreachable"; d.sandboxError = e.message; }
      return { content: [{ type: "text", text: JSON.stringify(d, null, 2) }] };
    }
    default:
      return { content: [{ type: "text", text: "Unknown tool: " + name }], isError: true };
  }
}

// MCP stdio JSON-RPC server
process.stdin.setEncoding("utf8");
let buffer = "";

process.stdin.on("data", (chunk) => {
  buffer += chunk;
  const lines = buffer.split("\n");
  buffer = lines.pop() || "";
  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed) continue;
    try {
      const msg = JSON.parse(trimmed);
      const id = msg.id;
      const params = msg.params || {};
      if (msg.method === "initialize") {
        send(id, { protocolVersion: "2024-11-05", capabilities: { tools: {} }, serverInfo: { name: "devpilot-sandbox", version: "0.2.0" } });
      } else if (msg.method === "tools/list") {
        send(id, { tools: TOOLS });
      } else if (msg.method === "tools/call") {
        handleToolCall(params.name, params.arguments || {})
          .then(result => send(id, result))
          .catch(err => send(id, { content: [{ type: "text", text: "Error: " + err.message }] }, true));
      } else if (msg.method === "notifications/initialized") {
        // Ignore
      } else {
        send(id, null, true, { code: -32601, message: "Unknown method: " + msg.method });
      }
    } catch (e) {
      // Skip malformed JSON
    }
  }
});

function send(id, result, isError, error) {
  const msg = { jsonrpc: "2.0", id: id };
  if (error) {
    msg.error = error;
  } else if (isError) {
    msg.error = { code: -32000, message: "Execution error", data: result };
  } else {
    msg.result = result;
  }
  process.stdout.write(JSON.stringify(msg) + "\n");
}

process.stdin.on("end", () => process.exit(0));

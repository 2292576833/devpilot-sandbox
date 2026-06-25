const http = require("http");
const https = require("https");

const PORT = parseInt(process.env.MCP_PORT || "9092", 10);
const SANDBOX_URL = (process.env.SANDBOX_URL || "http://127.0.0.1:9091").replace(/\/+$/, "");

function api(method, path, body) {
  return new Promise((resolve, reject) => {
    const url = new URL(SANDBOX_URL + path);
    const mod = url.protocol === "https:" ? https : http;
    const opts = {
      hostname: url.hostname, port: url.port,
      path: url.pathname + (url.search || ""),
      method: method,
      headers: { "Content-Type": "application/json" },
      timeout: 10000
    };
    const req = mod.request(opts, (res) => {
      let data = "";
      res.on("data", (c) => { data += c; });
      res.on("end", () => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          try { resolve(JSON.parse(data)); } catch(e) { reject(new Error("Invalid response")); }
        } else {
          try { const err = JSON.parse(data); reject(new Error(err.reason || "HTTP " + res.statusCode)); }
          catch(e) { reject(new Error("HTTP " + res.statusCode)); }
        }
      });
    });
    req.on("error", (e) => reject(new Error("Sandbox unreachable: " + e.message)));
    req.on("timeout", () => { req.destroy(); reject(new Error("Timeout")); });
    if (body) req.write(JSON.stringify(body));
    req.end();
  });
}

const TOOLS = [
  { name: "check_file_read", description: "Check if a file can be safely read (prevents path traversal)", inputSchema: { type: "object", properties: { path: { type: "string" }, roleId: { type: "string" } }, required: ["path"] } },
  { name: "check_file_write", description: "Check if a file can be safely written", inputSchema: { type: "object", properties: { path: { type: "string" }, roleId: { type: "string" } }, required: ["path"] } },
  { name: "check_command", description: "Check if a command is safe to run (blocks dangerous commands)", inputSchema: { type: "object", properties: { command: { type: "string" }, roleId: { type: "string" } }, required: ["command"] } },
  { name: "check_permission", description: "Check ANY operation in one call", inputSchema: { type: "object", properties: { type: { type: "string", enum: ["read","write","command"] }, value: { type: "string" }, roleId: { type: "string" } }, required: ["type","value"] } },
  { name: "get_health", description: "Get sandbox server health and stats", inputSchema: { type: "object", properties: {} } },
  { name: "get_stats", description: "Get interception statistics", inputSchema: { type: "object", properties: {} } },
  { name: "get_dangerous_commands", description: "List all built-in dangerous commands", inputSchema: { type: "object", properties: {} } },
  { name: "get_audit_log", description: "Query the audit log", inputSchema: { type: "object", properties: { role: { type: "string" }, operation: { type: "string" }, limit: { type: "number" } } } },
  { name: "get_roles", description: "Get all available roles", inputSchema: { type: "object", properties: {} } },
  { name: "get_diagnostics", description: "Run a self-test of MCP server and sandbox connectivity", inputSchema: { type: "object", properties: {} } },
  { name: "get_blocked_summary", description: "Get a summary of blocked requests", inputSchema: { type: "object", properties: {} } }
];

async function handleRequest(method, args) {
  const roleId = args.roleId || "CODE_ENGINEER";
  switch (method) {
    case "check_file_read": return await api("POST", "/api/v1/guard/file-read", { roleId, path: args.path });
    case "check_file_write": return await api("POST", "/api/v1/guard/file-write", { roleId, path: args.path });
    case "check_command": return await api("POST", "/api/v1/guard/command-run", { roleId, command: args.command });
    case "check_permission": {
      const ep = { read: "file-read", write: "file-write", command: "command-run" }[args.type];
      if (!ep) return { error: "type must be read/write/command" };
      const body = args.type === "command" ? { roleId, command: args.value } : { roleId, path: args.value };
      return await api("POST", "/api/v1/guard/" + ep, body);
    }
    case "get_health": return await api("GET", "/api/v1/guard/health");
    case "get_stats": return await api("GET", "/api/v1/guard/stats");
    case "get_dangerous_commands": return await api("GET", "/api/v1/guard/dangerous-commands");
    case "get_audit_log": {
      const p = new URLSearchParams();
      if (args.role) p.set("role", args.role);
      if (args.operation) p.set("operation", args.operation);
      p.set("limit", String(args.limit || 20));
      return await api("GET", "/api/v1/guard/audit?" + p.toString());
    }
    case "get_roles": return await api("GET", "/api/v1/policy/roles");
    case "get_diagnostics": {
      const d = { status: "running", server: "devpilot-sandbox-http", version: "0.2.0", nodeVersion: process.version, platform: process.platform, sandboxUrl: SANDBOX_URL, pid: process.pid, toolsCount: TOOLS.length };
      try { const h = await api("GET", "/api/v1/guard/health"); d.sandboxStatus = h.status; d.sandboxVersion = h.version; d.sandboxRoles = h.roles; } catch(e) { d.sandboxStatus = "unreachable"; d.sandboxError = e.message; }
      return d;
    }
    case "get_blocked_summary": {
      const s = await api("GET", "/api/v1/guard/stats");
      let t = "Stats:\n  Total: " + (s.totalRequests || 0) + "\n  Blocked: " + (s.totalBlocked || 0) + "\n  Rate: " + (s.blockedRate || "0%");
      if (s.blockedCommandsByRole) {
        const ks = Object.keys(s.blockedCommandsByRole);
        t += "\n\n  Blocked by role:";
        for (let i = 0; i < ks.length; i++) t += "\n    " + ks[i] + ": " + s.blockedCommandsByRole[ks[i]] + " commands";
      }
      return { summary: t };
    }
    default: throw new Error("Unknown tool: " + method);
  }
}

const server = http.createServer((req, res) => {
  // CORS headers
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");

  if (req.method === "OPTIONS") {
    res.writeHead(200); res.end();
    return;
  }

  if (req.method === "POST" && req.url === "/mcp") {
    let body = "";
    req.on("data", (c) => body += c);
    req.on("end", async () => {
      try {
        const msg = JSON.parse(body);
        const id = msg.id;
        let result;

        if (msg.method === "initialize") {
          result = { protocolVersion: "2024-11-05", capabilities: { tools: {} }, serverInfo: { name: "devpilot-sandbox-http", version: "0.2.0" } };
          res.writeHead(200, { "Content-Type": "application/json" });
          res.end(JSON.stringify({ jsonrpc: "2.0", id, result }));
        } else if (msg.method === "tools/list") {
          res.writeHead(200, { "Content-Type": "application/json" });
          res.end(JSON.stringify({ jsonrpc: "2.0", id, result: { tools: TOOLS } }));
        } else if (msg.method === "tools/call") {
          try {
            const data = await handleRequest(msg.params.name, msg.params.arguments || {});
            res.writeHead(200, { "Content-Type": "application/json" });
            res.end(JSON.stringify({ jsonrpc: "2.0", id, result: { content: [{ type: "text", text: JSON.stringify(data, null, 2) }] } }));
          } catch (e) {
            res.writeHead(500, { "Content-Type": "application/json" });
            res.end(JSON.stringify({ jsonrpc: "2.0", id, error: { code: -32000, message: e.message } }));
          }
        } else {
          res.writeHead(400, { "Content-Type": "application/json" });
          res.end(JSON.stringify({ jsonrpc: "2.0", id, error: { code: -32601, message: "Unknown method" } }));
        }
      } catch (e) {
        res.writeHead(400);
        res.end(JSON.stringify({ jsonrpc: "2.0", error: { code: -32700, message: "Parse error" } }));
      }
    });
  } else if (req.method === "GET" && (req.url === "/" || req.url === "/health")) {
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ status: "UP", server: "devpilot-sandbox-http", version: "0.2.0", tools: TOOLS.length, sandbox: SANDBOX_URL, port: PORT }));
  } else {
    res.writeHead(404);
    res.end("Not found");
  }
});

server.listen(PORT, "127.0.0.1", () => {
  console.error("[DevPilot Sandbox HTTP MCP] Server running on http://127.0.0.1:" + PORT + "/mcp");
});

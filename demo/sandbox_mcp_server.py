"""
DevPilot Sandbox - MCP Server
使任何 MCP 兼容的 AI Agent 可以直接发现和使用 Sandbox 保护的工具。

启动:
  pip install mcp
  python sandbox_mcp_server.py
"""

import json, os, subprocess, sys
from sandbox_client import DevPilotSandboxClient

SANDBOX_URL = os.environ.get("SANDBOX_URL", "http://127.0.0.1:9091")
DEFAULT_ROLE = os.environ.get("SANDBOX_ROLE", "CODE_ENGINEER")

def client():
    return DevPilotSandboxClient(SANDBOX_URL, DEFAULT_ROLE)

TOOLS = [
    {"name":"read_file","description":"Read file (Sandbox guarded)","inputSchema":{"type":"object","properties":{"path":{"type":"string"},"role":{"type":"string"}},"required":["path"]}},
    {"name":"write_file","description":"Write file (Sandbox guarded)","inputSchema":{"type":"object","properties":{"path":{"type":"string"},"content":{"type":"string"},"role":{"type":"string"}},"required":["path","content"]}},
    {"name":"run_command","description":"Run command (Sandbox guarded)","inputSchema":{"type":"object","properties":{"command":{"type":"string"},"role":{"type":"string"}},"required":["command"]}},
    {"name":"list_directory","description":"List directory (Sandbox guarded)","inputSchema":{"type":"object","properties":{"path":{"type":"string"},"role":{"type":"string"}}}},
    {"name":"read_audit","description":"Query audit log","inputSchema":{"type":"object","properties":{"role":{"type":"string"},"limit":{"type":"integer"}}}}
]

def handle(name, args):
    c = client()
    if name == "read_file":
        r = c.check_file_read(args["path"])
        if not r["allowed"]: return {"content":[{"type":"text","text":"BLOCKED: "+r["reason"]}],"isError":True}
        with open(r["resolvedPath"],encoding="utf-8") as f: return {"content":[{"type":"text","text":f.read()}]}
    elif name == "write_file":
        r = c.check_file_write(args["path"])
        if not r["allowed"]: return {"content":[{"type":"text","text":"BLOCKED: "+r["reason"]}],"isError":True}
        with open(r["resolvedPath"],"w",encoding="utf-8") as f: f.write(args["content"])
        return {"content":[{"type":"text","text":"Written"}]}
    elif name == "run_command":
        r = c.check_command(args["command"])
        if not r["allowed"]: return {"content":[{"type":"text","text":"BLOCKED: "+r["reason"]}],"isError":True}
        p = subprocess.run(args["command"],shell=True,capture_output=True,text=True,timeout=60)
        return {"content":[{"type":"text","text":p.stdout+(p.stderr or "")}]}
    elif name == "list_directory":
        r = c.check_file_read(args.get("path","."))
        if not r["allowed"]: return {"content":[{"type":"text","text":"BLOCKED: "+r["reason"]}],"isError":True}
        return {"content":[{"type":"text","text":"\n".join(os.listdir(r["resolvedPath"]))}]}
    elif name == "read_audit":
        e = c.query_audit(args.get("role"),None,args.get("limit",10),0)
        return {"content":[{"type":"text","text":"\n".join(e) if e else "(empty)"}]}
    return {"content":[{"type":"text","text":"Unknown: "+name}],"isError":True}

def main():
    for line in sys.stdin:
        line=line.strip()
        if not line: continue
        try: msg=json.loads(line)
        except: continue
        m=msg.get("method"); i=msg.get("id"); p=msg.get("params",{})
        if m=="initialize": r={"jsonrpc":"2.0","id":i,"result":{"protocolVersion":"2024-11-05","capabilities":{"tools":{}}}}
        elif m=="tools/list": r={"jsonrpc":"2.0","id":i,"result":{"tools":TOOLS}}
        elif m=="tools/call": r={"jsonrpc":"2.0","id":i,"result":handle(p.get("name"),p.get("arguments",{}))}
        elif m=="notifications/initialized": continue
        else: r={"jsonrpc":"2.0","id":i,"error":{"code":-32601,"message":"Unknown: "+m}}
        sys.stdout.write(json.dumps(r)+"\n"); sys.stdout.flush()

if __name__=="__main__": main()

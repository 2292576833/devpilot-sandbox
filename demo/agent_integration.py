"""
DevPilot Sandbox - AI Agent 集成示例

展示三种集成方式:
1. 手动校验（最通用）
2. 装饰器模式（最简洁）
3. OpenAI Function Calling 集成（最实用）
"""

import json
import os
import subprocess
import sys
from functools import wraps
from pathlib import Path

SANDBOX_URL = os.environ.get("SANDBOX_URL", "http://127.0.0.1:9091")


# ============================================================
# 方式一：手动校验 —— 适合任意 Agent 框架
# ============================================================

def agent_read_file(filepath: str, role: str = "CODE_ENGINEER") -> str:
    from sandbox_client import DevPilotSandboxClient
    client = DevPilotSandboxClient(SANDBOX_URL, role)
    result = client.check_file_read(filepath)
    if not result["allowed"]:
        raise PermissionError(f"[Sandbox] 读操作被拦截: {result['reason']}")
    with open(result["resolvedPath"], "r", encoding="utf-8") as f:
        return f.read()


def agent_run_command(command: str, role: str = "CODE_ENGINEER") -> str:
    from sandbox_client import DevPilotSandboxClient
    client = DevPilotSandboxClient(SANDBOX_URL, role)
    result = client.check_command(command)
    if not result["allowed"]:
        raise PermissionError(f"[Sandbox] 命令被拦截: {result['reason']}")
    proc = subprocess.run(command, shell=True, capture_output=True, text=True, timeout=30)
    return proc.stdout + proc.stderr


# ============================================================
# 方式二：装饰器模式 —— 一键给所有工具加上围栏
# ============================================================

def sandbox_guard(guard_type: str = "command", role: str = "CODE_ENGINEER"):
    from sandbox_client import DevPilotSandboxClient
    client = DevPilotSandboxClient(SANDBOX_URL, role)

    def decorator(func):
        @wraps(func)
        def wrapper(*args, **kwargs):
            target = kwargs.get("path") or kwargs.get("command") or (args[0] if args else None)
            if not target:
                return func(*args, **kwargs)
            if guard_type == "command":
                result = client.check_command(target)
            else:
                result = client.check_file_read(target)
            if not result.get("allowed"):
                raise PermissionError(f"[Sandbox] 拦截: {result['reason']}")
            return func(*args, **kwargs)
        return wrapper
    return decorator


@sandbox_guard(guard_type="command")
def safe_shell(cmd: str) -> str:
    result = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=30)
    return result.stdout


@sandbox_guard(guard_type="file")
def safe_read(path: str) -> str:
    with open(path, "r", encoding="utf-8") as f:
        return f.read()


# ============================================================
# 方式三：OpenAI Function Calling —— 最实用的集成方式
# ============================================================

def build_sandbox_tools():
    return [
        {
            "type": "function",
            "function": {
                "name": "read_file",
                "description": "读取文件内容（受 Sandbox 安全保护）",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "path": {"type": "string", "description": "文件路径"},
                        "role": {"type": "string", "description": "角色ID", "default": "CODE_ENGINEER"}
                    },
                    "required": ["path"]
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "run_command",
                "description": "执行 shell 命令（受 Sandbox 安全保护）",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "command": {"type": "string", "description": "要执行的命令"},
                        "role": {"type": "string", "description": "角色ID", "default": "CODE_ENGINEER"}
                    },
                    "required": ["command"]
                }
            }
        },
        {
            "type": "function",
            "function": {
                "name": "list_directory",
                "description": "列出目录内容（受 Sandbox 路径保护）",
                "parameters": {
                    "type": "object",
                    "properties": {
                        "path": {"type": "string", "description": "目录路径", "default": "."},
                        "role": {"type": "string", "description": "角色ID", "default": "CODE_ENGINEER"}
                    },
                    "required": []
                }
            }
        }
    ]


def execute_sandboxed_tool(name: str, arguments: dict) -> str:
    from sandbox_client import DevPilotSandboxClient
    client = DevPilotSandboxClient(SANDBOX_URL, arguments.get("role", "CODE_ENGINEER"))

    if name == "read_file":
        path = arguments["path"]
        result = client.check_file_read(path)
        if not result["allowed"]:
            return json.dumps({"error": f"安全拦截: {result['reason']}", "allowed": False})
        with open(result["resolvedPath"], "r", encoding="utf-8") as f:
            content = f.read()
        return json.dumps({"content": content, "path": result["resolvedPath"], "allowed": True})

    elif name == "run_command":
        command = arguments["command"]
        result = client.check_command(command)
        if not result["allowed"]:
            return json.dumps({"error": f"安全拦截: {result['reason']}", "allowed": False})
        proc = subprocess.run(command, shell=True, capture_output=True, text=True, timeout=30)
        return json.dumps({
            "stdout": proc.stdout, "stderr": proc.stderr,
            "exit_code": proc.returncode, "allowed": True
        })

    elif name == "list_directory":
        path = arguments.get("path", ".")
        result = client.check_file_read(path)
        if not result["allowed"]:
            return json.dumps({"error": f"安全拦截: {result['reason']}", "allowed": False})
        entries = os.listdir(result["resolvedPath"])
        return json.dumps({"entries": entries, "path": result["resolvedPath"], "allowed": True})

    return json.dumps({"error": f"未知工具: {name}"})


# ============================================================
# 四种主流框架集成方式
# ============================================================

def integrate_with_openai():
    """OpenAI Assistants API / GPTs"""
    tools = build_sandbox_tools()
    tool_map = {"read_file": execute_sandboxed_tool,
                "run_command": execute_sandboxed_tool,
                "list_directory": execute_sandboxed_tool}
    return tools, tool_map


def integrate_with_langchain():
    """
    LangChain Tool 集成
    用法: pip install langchain

    from langchain.tools import tool
    from agent_integration import sandbox_guard

    @tool
    @sandbox_guard(guard_type="command")
    def shell(cmd: str) -> str:
        \"\"\"执行 shell 命令\"\"\"
        import subprocess
        return subprocess.run(cmd, shell=True, capture_output=True, text=True).stdout
    """
    pass


def integrate_with_crewai():
    """
    CrewAI Tool 集成
    用法: pip install crewai

    from crewai.tools import tool
    from agent_integration import sandbox_guard

    @tool("安全Shell")
    @sandbox_guard(guard_type="command")
    def safe_shell_command(cmd: str) -> str:
        return subprocess.run(cmd, shell=True, capture_output=True, text=True).stdout
    """
    pass


def integrate_with_mcp():
    """
    MCP (Model Context Protocol) Server 集成

    把 Sandbox 包装为 MCP Server，Claude Desktop / Codex 可以直接用它。
    详细实现见: https://modelcontextprotocol.io
    """
    pass


# ============================================================
# 启动入口
# ============================================================

if __name__ == "__main__":
    print("=" * 60)
    print("  DevPilot Sandbox - AI Agent 集成示例")
    print("=" * 60)
    print("")
    print("  [方式一] 手动校验（任意框架）")
    print("    content = agent_read_file('README.md')")
    print("    output = agent_run_command('git status')")
    print("")
    print("  [方式二] 装饰器模式（LangChain/CrewAI）")
    print("    @sandbox_guard(guard_type='command')")
    print("    def my_tool(cmd): ...")
    print("")
    print("  [方式三] OpenAI Function Calling")
    print("    tools, handlers = integrate_with_openai()")
    print("    result = handlers['read_file']('read_file', {'path':'README.md'})")
    print("")
    print("  [方式四] MCP Server")
    print("    # 将 Sandbox 包装为 MCP 工具，Claude 可直接发现和使用")
    print("")
    print(f"  SANDBOX_URL = {SANDBOX_URL}")
    print("  先启动服务: java -jar target/devpilot-sandbox-0.1.0.jar")
    print("=" * 60)

# DevPilot Sandbox 🛡️
 看到最后呦

> **给 AI 钥匙，而不是拆掉围墙。**  
> 面向 AI Agent 的本地安全执行沙箱中间件——基于角色的文件、命令权限控制。

[![Version](https://img.shields.io/badge/version-0.2.0-blue)]()
[![Tests](https://img.shields.io/badge/tests-60/60-passing-brightgreen)]()
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)]()

---

## 📋 概述

DevPilot Sandbox 是一个独立的安全沙箱中间件，**插在 AI Agent 和操作系统之间**。每次 Agent 想执行命令或读写文件前，先问沙箱"能不能做"。

```
AI Agent (Cursor/Codex/OpenHands/...)
       │
       │ HTTP API / MCP
       ▼
┌─────────────────────────────┐
│     DevPilot Sandbox        │
│  ├ PathGuard   (文件围栏)   │
│  ├ CommandGuard (命令围栏)  │
│  ├ PolicyEngine (策略引擎)  │
│  └ AuditLogger  (审计日志)  │
└──────────┬──────────────────┘
           │
           ▼
       操作系统
```

## ✨ 核心功能

| 功能 | 说明 |
|------|------|
| 🛡️ **路径穿越防护** | 防止 `../../etc/passwd` 等逃逸攻击 |
| 🚫 **44个内置危险命令** | `rm -rf /`、`format`、`sudo`、`reg delete` 等始终拦截 |
| 📋 **命令白名单** | 按角色配置允许的命令和子命令 |
| 🔒 **参数校验** | 禁止 `-g`、`--global` 等危险标志 |
| 👤 **多角色隔离** | 每个角色独立工作目录和权限 |
| 📝 **审计日志** | 每次请求/拒绝都记录 |
| 🔄 **并发控制** | 每个角色独立并发上限 |
| 📊 **拦截统计** | 实时查看拦截率 |
| 🔥 **热加载策略** | 修改 `policy.yaml` 3 秒生效 |

## 🚀 快速开始

```powershell
git clone <repo>
cd devpilot-sandbox
.\run.bat
```

启动后访问：
- **Web 控制台**: http://127.0.0.1:9091/ui/
- **API**: http://127.0.0.1:9091/api/v1/guard/health
- **MCP**: http://127.0.0.1:9092/mcp

## 🔌 集成方式

| 方式 | 适合场景 | 接入成本 |
|------|---------|---------|
| **REST API** | 任何语言的 AI Agent | 低 |
| **HTTP MCP** | 支持 MCP 的 AI 客户端 | 低 |
| **Python SDK** | Python AI Agent | 极低 |
| **Java SDK** | Java 项目 | 极低 |
| **VSCode 插件** | VSCode 用户 | 一键安装 |

### REST API（通用方式）

```powershell
# 检查命令是否能执行
Invoke-RestMethod http://127.0.0.1:9091/api/v1/guard/command-run `
  -Method Post -ContentType "application/json" `
  -Body '{"roleId":"CODE_ENGINEER","command":"rm -rf /"}'
# → {"allowed":false,"reason":"Dangerous command blocked..."}

# 检查文件是否能读取
Invoke-RestMethod http://127.0.0.1:9091/api/v1/guard/file-read `
  -Method Post -ContentType "application/json" `
  -Body '{"roleId":"CODE_ENGINEER","path":"../../etc/passwd"}'
# → {"allowed":false,"reason":"Access denied..."}
```

### Python SDK

```python
from demo.sandbox_client import DevPilotSandboxClient

sandbox = DevPilotSandboxClient()
result = sandbox.check_command("rm -rf /")
if not result["allowed"]:
    print(f"拒绝: {result['reason']}")
```

### MCP（11个工具）

支持 MCP 协议的 AI Agent 自动发现如下工具：

```
check_file_read     检查文件读取权限
check_file_write    检查文件写入权限
check_command       检查命令执行权限
check_permission    统一检查任意操作
get_health          获取服务状态
get_stats           获取拦截统计
get_dangerous_commands  查看44个危险命令
get_audit_log       查询审计日志
get_roles           获取角色列表
get_diagnostics     运行自检
get_blocked_summary 获取拦截摘要
```

## ⚙️ 策略配置 (`data/policy.yaml`)

```yaml
mode: multi
roles:
  - id: CODE_ENGINEER
    work_dir: "C:/home/user/project"
    allowed_commands:
      - command: git
        subcommands: [status, add, commit, pull, push]
      - command: npm
        subcommands: [install, run, test]
        deny_flags: ["-g", "--global"]
      - command: pip
        subcommands: [install]
        require_env: ["VIRTUAL_ENV"]
```

## 🧪 测试

```powershell
# 启动服务器
.\run.bat

# 测试拦截
Invoke-RestMethod http://127.0.0.1:9091/api/v1/guard/stats
Invoke-RestMethod ... -Body '{"roleId":"CODE_ENGINEER","command":"rm -rf /"}'
Invoke-RestMethod ... -Body '{"roleId":"CODE_ENGINEER","command":"git status"}'
```

运行 Java 单元测试：

```bash
mvn test
# 60/60 ✅
```

## 🏗️ 技术栈

| 组件 | 技术 |
|------|------|
| 后端 | Java 17 + Spring Boot 3 |
| 构建 | Maven |
| Web UI | HTML + Vue3 (Element Plus) |
| VSCode 扩展 | Node.js |
| MCP 服务器 | Node.js |

## 📄 文档

| 文档 | 路径 |
|------|------|
| 使用说明书 | `USAGE.md` |
| 安全白皮书 | `docs/AI-Agent-Security-Whitepaper.md` |
| 更新计划 | `更新计划.md` |

## 📜 许可证

Apache 2.0






























































爱发电













<img width="571" height="572" alt="mm_facetoface_collect_qrcode_1782390475437" src="https://github.com/user-attachments/assets/c1123efa-b42a-48c4-80b2-5ac0c8e394f5" />


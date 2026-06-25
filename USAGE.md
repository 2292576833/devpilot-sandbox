# DevPilot Sandbox 使用指南

## 目录

1. [快速开始](#1-快速开始)
2. [构建项目](#2-构建项目)
3. [启动服务](#3-启动服务)
4. [Web 控制台](#4-web-控制台)
5. [API 使用](#5-api-使用)
6. [策略配置](#6-策略配置)
7. [集成到 AI Agent](#7-集成到-ai-agent)
8. [桌面客户端](#8-桌面客户端)
9. [命令行演示](#9-命令行演示)
10. [故障排除](#10-故障排除)

---

## 1. 快速开始

### 前置条件
- **Java 8+**（推荐 Temurin JDK 8 或 17）
- **Maven 3.6+**（项目自带 `.maven/` 目录下的 Maven 可免安装）
- **Windows 10/11**（当前版本支持 Windows）

### 一键启动

双击项目根目录的 `run.bat`，或执行：

```batch
cd devpilot-sandbox
run.bat
```

脚本会自动：
1. 查找 Java 环境
2. 清理旧进程
3. 自动检测空闲端口（从 9090 开始）
4. 启动服务
5. 等待就绪后打开浏览器

---

## 2. 构建项目

### 标准构建

```powershell
cd devpilot-sandbox

# 使用自带 Maven
.maven\apache-maven-3.9.16\bin\mvn.cmd clean package -DskipTests

# 或使用系统 Maven（如果已安装）
mvn clean package -DskipTests
```

### 构建并运行测试

```powershell
.maven\apache-maven-3.9.16\bin\mvn.cmd clean test
```

构建产物：`target/devpilot-sandbox-0.1.0.jar`

---

## 3. 启动服务

### 方法一：双击 run.bat（推荐）

```batch
run.bat
```

这是最简单的方式，自动处理端口检测和启动。

如果端口 9090 被占用（如 Clash 等代理软件），脚本会自动递增端口。

### 方法二：命令行直接启动

```powershell
java -jar target\devpilot-sandbox-0.1.0.jar --server.port=9091
```

指定端口参数 `--server.port=XXXX` 可以自定义端口。

### 方法三：通过 start.cmd

```batch
start.cmd
```

这个脚本会先执行 Maven 构建，然后启动服务。

### 验证服务是否运行

```powershell
curl.exe http://localhost:9091/api/v1/guard/health
```

预期响应：
```json
{"status":"UP","version":"0.1.0","roles":["CODE_ENGINEER","DESKTOP_OPERATOR","READONLY"],...}
```

---

## 4. Web 控制台

启动服务后，打开浏览器访问：

```
http://localhost:9091/ui/index.html
```

Web 控制台提供以下功能：

### 仪表盘
- 服务状态（UP/DOWN）
- 已配置的角色列表
- 版本信息
- 策略来源

### 策略测试
- **文件读测试**：输入角色 ID 和路径，检查是否能读取
- **文件写测试**：输入角色 ID 和路径，检查是否能写入
- **命令测试**：输入角色 ID 和命令，检查是否允许执行

### 审计日志
- 查看所有操作记录（文件访问、命令执行）
- 按角色筛选
- 按操作类型筛选

示例攻击测试：
| 测试场景 | 角色 | 输入 | 预期结果 |
|---------|------|------|---------|
| 合法读 | CODE_ENGINEER | `src/Main.java` | ✅ 允许 |
| 路径穿越 | CODE_ENGINEER | `../../etc/passwd` | ❌ 拒绝 |
| 禁止参数 | CODE_ENGINEER | `npm install -g express` | ❌ 拒绝 |

---

## 5. API 使用

所有 API 端点：`http://localhost:{PORT}/api/v1/guard/`

### 5.1 健康检查

```powershell
curl.exe http://localhost:9091/api/v1/guard/health
```

### 5.2 文件读取校验

```powershell
# 合法操作
curl.exe -X POST http://localhost:9091/api/v1/guard/file-read ^
  -H "Content-Type: application/json" ^
  -d "{\"roleId\":\"CODE_ENGINEER\",\"path\":\"src/Main.java\"}"

# 路径穿越攻击（会被拦截）
curl.exe -X POST http://localhost:9091/api/v1/guard/file-read ^
  -H "Content-Type: application/json" ^
  -d "{\"roleId\":\"CODE_ENGINEER\",\"path\":\"../../etc/passwd\"}"
```

### 5.3 命令执行校验

```powershell
# 合法命令
curl.exe -X POST http://localhost:9091/api/v1/guard/command-run ^
  -H "Content-Type: application/json" ^
  -d "{\"roleId\":\"CODE_ENGINEER\",\"command\":\"git status\"}"

# 被禁止的参数（会被拦截）
curl.exe -X POST http://localhost:9091/api/v1/guard/command-run ^
  -H "Content-Type: application/json" ^
  -d "{\"roleId\":\"CODE_ENGINEER\",\"command\":\"npm install -g express\"}"
```

### 5.4 查询审计日志

```powershell
# 全部日志
curl.exe http://localhost:9091/api/v1/guard/audit

# 按角色筛选
curl.exe "http://localhost:9091/api/v1/guard/audit?role=CODE_ENGINEER"

# 限制条数
curl.exe "http://localhost:9091/api/v1/guard/audit?limit=10"
```

### API 响应格式

```json
{
  "allowed": true,
  "resolvedPath": "C:/work/dir/src/Main.java",
  "reason": null
}
```

- `allowed`: `true` = 允许, `false` = 拒绝
- `resolvedPath`: 解析后的绝对路径
- `reason`: 拒绝原因（允许时为 null）

---

## 6. 策略配置

配置文件：`src/main/resources/policy.yaml`

### 默认策略

```yaml
roles:
  - id: CODE_ENGINEER
    workDir: "C:/Users/27736/project"
    allowedPaths:
      - "."
    deniedPaths:
      - ".git"
    allowedCommands:
      - command: git
        subcommands: [status, add, commit, pull, push]
      - command: npm
        subcommands: [install, ci, run, test]
        denyFlags: ["-g", "--global"]
    maxFileSizeMb: 10

  - id: READONLY
    workDir: "C:/Users/27736"
    allowedPaths:
      - "."
    allowedCommands: []
    maxFileSizeMb: 5
```

### 策略字段说明

| 字段 | 类型 | 说明 |
|-----|------|------|
| `id` | string | 角色唯一标识 |
| `workDir` | string | 工作目录（绝对路径） |
| `allowedPaths` | string[] | 允许访问的路径（相对 workDir） |
| `deniedPaths` | string[] | 禁止访问的路径 |
| `allowedCommands` | object[] | 允许执行的命令列表 |
| `command` | string | 命令名 |
| `subcommands` | string[] | 允许的子命令 |
| `denyFlags` | string[] | 禁止的参数 |
| `requireEnv` | string[] | 必须设置的环境变量 |
| `maxFileSizeMb` | int | 最大文件大小（MB） |

### 自定义策略示例

```yaml
roles:
  - id: CUSTOM_ROLE
    workDir: "D:/my-work"
    allowedPaths:
      - "."          # 允许整个工作目录
      - "data"       # 允许 data 子目录
    deniedPaths:
      - ".git"
      - "secrets"
    allowedCommands:
      - command: python
        subcommands: [script.py, main.py]
      - command: pip
        requireEnv: ["VIRTUAL_ENV"]
    maxFileSizeMb: 50
```

修改策略后需重启服务生效。

---

## 7. 集成到 AI Agent

### 通用集成方案

DevPilot Sandbox 作为独立的 Sidecar 服务运行，任何 AI Agent 都可以通过 REST API 调用它。

```
┌─────────────────┐         HTTP/JSON          ┌──────────────────┐
│  AI Agent       │ ──────────────────────────► │ DevPilot Sandbox │
│  (编程助手/RPA)  │ ◄────────────────────────── │ (Sidecar 服务)   │
└─────────────────┘                              └──────────────────┘
```

集成步骤：
1. 启动 Sandbox Sidecar（后台运行）
2. Agent 在操作文件或执行命令前，先调用 Sandbox API 校验
3. 根据 API 响应决定是否执行操作

### 集成到 Cursor/Copilot

通过中间层代理，在 Agent 的文件读写和命令执行前进行校验：

```python
import requests
import os

SANDBOX_URL = "http://localhost:9091/api/v1/guard"

def check_file_access(path, mode="read"):
    """检查文件访问是否被允许"""
    try:
        resp = requests.post(f"{SANDBOX_URL}/file-{mode}", json={
            "roleId": "CODE_ENGINEER",
            "path": path
        }, timeout=2)
        result = resp.json()
        if not result["allowed"]:
            raise PermissionError(f"Sandbox拒绝访问: {result['reason']}")
        return result["resolvedPath"]
    except requests.ConnectionError:
        # Sandbox 未运行，回退到正常操作
        return os.path.abspath(path)

def check_command(command):
    """检查命令是否被允许"""
    try:
        resp = requests.post(f"{SANDBOX_URL}/command-run", json={
            "roleId": "CODE_ENGINEER",
            "command": command
        }, timeout=2)
        result = resp.json()
        if not result["allowed"]:
            raise PermissionError(f"Sandbox拒绝命令: {result['reason']}")
        return True
    except requests.ConnectionError:
        return True  # Sandbox 未运行时允许所有命令
```

### 集成到 OpenHands / OpenClaw

在 Agent 的插件或工具层中添加 Sandbox 调用：

```typescript
// TypeScript 集成示例
const sandboxBaseUrl = 'http://localhost:9091/api/v1/guard';

async function validateFileRead(path: string) {
  const res = await fetch(`${sandboxBaseUrl}/file-read`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ roleId: 'CODE_ENGINEER', path })
  });
  const data = await res.json();
  if (!data.allowed) {
    throw new Error(`❌ 安全策略拦截: ${data.reason}`);
  }
  return data.resolvedPath;
}
```

### 集成到企业自研 Agent

```java
// Java SDK 风格调用
public class SandboxClient {
    private final String baseUrl;

    public SandboxClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public GuardResult checkFileRead(String roleId, String path) {
        // POST /api/v1/guard/file-read
        // 返回 { allowed, resolvedPath, reason }
    }

    public GuardResult checkCommand(String roleId, String command) {
        // POST /api/v1/guard/command-run
    }
}
```

---

## 8. 桌面客户端

DevPilot Sandbox 提供一个图形化桌面客户端（系统托盘应用）。

### 启动桌面客户端

```batch
DevPilot Desktop.cmd
```

### 功能

- **启动/停止服务**：通过按钮控制 Sandbox 服务
- **实时日志**：查看服务运行日志
- **状态指示**：服务运行状态在系统托盘显示
- **快速访问**：右键托盘图标打开 Web 控制台

### 要求

- PowerShell 5.1+
- .NET Framework 4.5+（Windows 自带）

---

## 9. 命令行演示

### 演示脚本

```powershell
powershell -ExecutionPolicy Bypass -File demo/attack-demo.ps1
```

### 手动 API 测试

```powershell
# 1. 启动服务
java -jar target\devpilot-sandbox-0.1.0.jar --server.port=9091

# 2. 测试健康检查
curl.exe http://localhost:9091/api/v1/guard/health

# 3. 测试合法文件访问
curl.exe -X POST http://localhost:9091/api/v1/guard/file-read ^
  -H "Content-Type: application/json" ^
  -d "{"""roleId""":"""CODE_ENGINEER""","""path""":"""src/Main.java"""}"

# 4. 测试路径穿越攻击
curl.exe -X POST http://localhost:9091/api/v1/guard/file-read ^
  -H "Content-Type: application/json" ^
  -d "{"""roleId""":"""CODE_ENGINEER""","""path""":"""../../etc/passwd"""}"

# 5. 测试命令校验
curl.exe -X POST http://localhost:9091/api/v1/guard/command-run ^
  -H "Content-Type: application/json" ^
  -d "{"""roleId""":"""CODE_ENGINEER""","""command""":"""git status"""}"

# 6. 测试被禁止的命令
curl.exe -X POST http://localhost:9091/api/v1/guard/command-run ^
  -H "Content-Type: application/json" ^
  -d "{"""roleId""":"""CODE_ENGINEER""","""command""":"""npm install -g express"""}"

# 7. 查看审计日志
curl.exe http://localhost:9091/api/v1/guard/audit
```

> **注意**: PowerShell 5.1 中，推荐使用 JSON 文件传递请求体以避免引号转义问题。

```powershell
# 使用 JSON 文件
Set-Content -Path "$env:TEMP\req.json" -Value '{"roleId":"CODE_ENGINEER","path":"src/Main.java"}'
curl.exe -X POST http://localhost:9091/api/v1/guard/file-read -H "Content-Type: application/json" -d "@$env:TEMP\req.json"
```

---

## 10. 故障排除

### 服务无法启动

**症状**: `run.bat` 执行后提示超时

**解决方法**:
1. 检查 Java 是否安装: `java -version`
2. 检查端口是否被占用: `netstat -ano | findstr ":9091"`
3. 手动指定端口: `java -jar target\devpilot-sandbox-0.1.0.jar --server.port=9093`
4. 查看日志文件: `target\sandbox.log`

### Web 控制台打不开

**症状**: 浏览器访问 `http://127.0.0.1:9091/ui/index.html` 空白或 404

**解决方法**:
1. 确认服务正在运行: `curl.exe http://127.0.0.1:9091/api/v1/guard/health`
2. 如果使用端口 9091: `http://localhost:9091/ui/index.html`
3. 如果端口被 Clash 等代理占用，Sandbox 会自动使用下一可用端口
4. 查看启动日志中打印的 Web 地址

### API 返回 400 错误

**症状**: 调用 API 返回 `{"timestamp":"...","status":400,"error":"Bad Request"}`

**解决方法**:
1. 检查 JSON 格式是否正确
2. 确保 `Content-Type: application/json` 头已设置
3. 确保 `roleId` 和 `path`/`command` 字段存在
4. 使用 JSON 文件方式传递请求体

### "Work directory does not exist" 警告

**症状**: 日志中出现 `Work directory does not exist` 警告

**解决方法**:
1. 创建策略中配置的工作目录
2. 或修改 `policy.yaml` 中的 `workDir` 为实际存在的目录

### 策略修改不生效

**症状**: 修改 `policy.yaml` 后行为未改变

**解决方法**:
1. 当前版本需重启服务使策略生效
2. 确保修改的文件是 `src/main/resources/policy.yaml`（打包前）
3. 如果是已打包的 JAR，使用外部策略文件（即将支持）
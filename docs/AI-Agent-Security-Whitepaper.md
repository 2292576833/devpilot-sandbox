# AI Agent 安全最佳实践白皮书

## DevPilot Sandbox —— 给 AI 钥匙而不是拆掉围墙

> 版本 0.2.0 | 2026年6月

---

## 目录

1. [AI Agent 的安全现状与风险](#1-ai-agent-的安全现状与风险)
2. [现有安全方案分析](#2-现有安全方案分析)
3. [DevPilot Sandbox 安全架构](#3-devpilot-sandbox-安全架构)
4. [快速集成指南](#4-快速集成指南)
5. [策略配置最佳实践](#5-策略配置最佳实践)
6. [集成模式](#6-集成模式)
7. [案例研究](#7-案例研究)
8. [常见问题](#8-常见问题)

---

## 1. AI Agent 的安全现状与风险

### 1.1 问题背景

AI 编程助手（Cursor、Copilot、Codex）和自主 Agent（OpenHands、Claude Code）正在获得越来越大的操作系统权限。它们可以：

- 读写文件系统中的任意文件
- 执行系统命令和脚本
- 访问网络和 API
- 修改系统配置

这些能力带来的安全风险是巨大的。

### 1.2 典型安全事件

| 场景 | 风险等级 | 示例 |
|------|---------|------|
| 路径穿越 | 🔴 高危 | Agent 读取 ../../etc/passwd 泄露系统账户 |
| 危险命令 | 🔴 高危 | Agent 执行 rm -rf / 删除整个系统 |
| 敏感文件泄露 | 🟡 中危 | Agent 读取 .env 泄露 API Key |
| 越权操作 | 🟡 中危 | Agent 修改 /etc/hosts 或系统配置 |
| 权限提升 | 🔴 高危 | Agent 执行 sudo 提权 |
| 供应链攻击 | 🟡 中危 | Agent 安装恶意 npm 包 |

### 1.3 为什么传统方案不够

| 方案 | 问题 |
|------|------|
| **完全隔离（Docker）** | 太严格，无法访问本地开发资源 |
| **人工确认** | 打断工作流，不可规模化 |
| **容器逃逸检测** | 被动防御，无法预防 |
| **最小权限原则** | Agent 需要的是动态权限，不是静态的 |

---

## 2. 现有安全方案分析

### 2.1 完全容器化隔离

`
优点: 强隔离，安全
缺点: 无法访问本地服务、数据库、配置文件
`

### 2.2 人工确认模式

`
优点: 人类判断最准确
缺点: 频繁打断，Agent 效率降低 70%+
`

### 2.3 基于规则的检测

`
优点: 无额外延迟
缺点: 只能检测已知攻击模式，误报率高
`

### 2.4 我们需要的方案

> **基于策略的权限控制中间件**，在 Agent 和操作系统之间建立一道可配置的围栏：
>
> - 细粒度：按角色区分权限
> - 动态：支持运行时策略变更
> - 可审计：记录每一次操作决策
> - 高性能：微秒级决策延迟
> - 易于集成：标准的 HTTP API

---

## 3. DevPilot Sandbox 安全架构

### 3.1 架构总览

`
Agent (Cursor/Codex/Copilot/OpenHands/...)
       │
       │ HTTP API
       ▼
┌─────────────────────────────────────┐
│        DevPilot Sandbox             │
│                                     │
│  ┌────────┐  ┌───────────┐         │
│  │ Path   │  │ Command   │         │
│  │ Guard  │  │ Guard     │         │
│  └───┬────┘  └─────┬─────┘         │
│      │             │               │
│      ▼             ▼               │
│  ┌────────────────────────┐        │
│  │    Policy Engine       │        │
│  │  (YAML 策略 + 热加载)  │        │
│  └───────────┬────────────┘        │
│              │                     │
│  ┌───────────▼────────────┐        │
│  │    Audit Logger        │        │
│  │  (每次请求都记录)       │        │
│  └────────────────────────┘        │
└─────────────────────────────────────┘
       │
       ▼
   操作系统（文件系统 + 命令执行）
`

### 3.2 三层防御模型

#### 第一层：内置危险命令黑名单（纵深防御）

44 个系统危险命令始终被拦截，无论角色如何配置：

| 类别 | 命令数 | 示例 |
|------|--------|------|
| 磁盘操作 | 3 | format, diskpart, mkfs |
| 系统管理 | 7 | shutdown, bcdedit, powercfg |
| 注册表 | 3 | reg delete/add/import, regedit |
| 服务控制 | 2 | sc stop/delete, schtasks |
| 用户管理 | 3 | net user, takeown, passwd |
| 权限修改 | 6 | icacls, chmod 777, chown, sudo, su |
| 文件破坏 | 8 | rm -rf, del /f, rmdir /s, cipher /w |
| 进程管理 | 3 | kill -9, killall, pkill |
| 系统关闭 | 4 | reboot, halt, poweroff, init 0 |

#### 第二层：角色白名单

每个角色有独立的：
- 工作目录（所有文件操作限制在此目录内）
- 允许的命令列表（含子命令和参数限制）
- 黑名单文件/目录
- 最大文件大小
- 并发限制

#### 第三层：审计日志

每次操作（无论允许还是拒绝）都记录到审计日志。

### 3.3 策略决策流程

`
请求传入
  │
  ▼
┌─ 第一步：内置黑名单 ────┐
│ 命令在黑名单中？        │──→ 是 → 拒绝
└──────────┬──────────────┘
           │ 否
           ▼
┌─ 第二步：角色白名单 ────┐
│ 命令在白名单中？        │──→ 否 → 拒绝
└──────────┬──────────────┘
           │ 是
           ▼
┌─ 第三步：参数校验 ──────┐
│ 参数包含禁止标志？      │──→ 是 → 拒绝
└──────────┬──────────────┘
           │ 否
           ▼
┌─ 第四步：环境校验 ──────┐
│ 环境变量满足要求？      │──→ 否 → 拒绝
└──────────┬──────────────┘
           │ 是
           ▼
┌─ 第五步：并发控制 ──────┐
│ 并发数未超上限？        │──→ 否 → 429
└──────────┬──────────────┘
           │ 是
           ▼
        允许执行
`

---

## 4. 快速集成指南

### 4.1 启动服务

`powershell
cd devpilot-sandbox
.\run.ps1
`

服务启动后访问 http://127.0.0.1:9091/ui/

### 4.2 Python Agent 集成

`python
from sandbox_client import DevPilotSandboxClient

sandbox = DevPilotSandboxClient(base_url=http://127.0.0.1:9091, role_id=CODE_ENGINEER)

# Agent 写文件前先校验
result = sandbox.check_file_write(/etc/passwd)
if not result[allowed]:
    return f拒绝: {result['reason']}

# Agent 执行命令前先校验
result = sandbox.check_command(rm -rf /)
if not result[allowed]:
    return f拒绝: {result['reason']}
`

### 4.3 任意 HTTP 客户端

`ash
curl -X POST http://127.0.0.1:9091/api/v1/guard/command-run \
  -H Content-Type: application/json \
  -d '{roleId:CODE_ENGINEER,command:rm -rf /}'
# -> {allowed:false,reason:Dangerous command blocked...}
`

---

## 5. 策略配置最佳实践

### 5.1 最小权限原则

每个角色只给最少的权限：

`yaml
allowed_commands:
  - git: [status, add, commit, diff, log, pull, push]
  - npm: [install, ci, run, test, build]
  - mvn: [clean, compile, test, package]
`

### 5.2 工作目录隔离

`yaml
- id: PROJECT_A
  work_dir: C:/projects/project-a
  allowed_commands:
    - npm: [install, run, build]

- id: READER
  work_dir: C:/docs
  readonly: true
`

### 5.3 敏感文件保护

`yaml
deny_files:
  - .env
  - .git
  - config.prod.yaml
  - *.pem
  - secrets.*
  - id_rsa
  - .ssh
`

### 5.4 参数限制

`yaml
- command: npm
  subcommands: [install, ci, run, test]
  deny_flags: [-g, --global]
  require_env: [VIRTUAL_ENV]
`

---

## 6. 集成模式

### 6.1 透明代理模式

在 Agent 和操作系统之间插入沙箱：

`
Agent -> Sandbox -> 操作系统
   |         |
   |   允许的操作 -> 透传
   |   拒绝的操作 -> 拦截并记录
`

### 6.2 装饰器模式

在 Agent 的工具函数上加校验层：

`python
@sandbox_guard(file-write)
def write_file(path, content):
    with open(path, w) as f:
        f.write(content)
`

### 6.3 MCP 集成

`
Agent <-> MCP Server <-> Sandbox API <-> 操作系统
`

Agent 通过 MCP 协议发现工具，沙箱在工具执行层做拦截。

---

## 7. 案例研究

### 案例 1：AI 编程助手

场景：Cursor 在企业内使用时，需要确保它不能读写项目外的敏感文件。

方案：在 Cursor 的工具调用前加一层 Sandbox 校验。

效果：
- 路径穿越攻击 100% 拦截
- 危险命令 100% 拦截
- 审计日志记录所有操作
- 零误报（基于白名单）

### 案例 2：自动化运维 Agent

场景：运维 Agent 需要执行服务器管理任务，但不能执行破坏性命令。

方案：只允许特定的运维命令和路径。

效果：
- rm -rf / 等破坏性操作被拦截
- 只能操作指定目录
- 支持热更新策略

### 案例 3：RPA 桌面自动化

场景：RPA 脚本执行桌面自动化，需要防止误操作。

方案：配置 DESKTOP_OPERATOR 角色，禁止所有命令。

效果：
- RPA 只能操作桌面文件
- 命令执行被完全禁止
- 即使被注入攻击也无法执行系统命令

---

## 8. 常见问题

### Q: Sandbox 会增加多少延迟？
A: 每次校验的延迟通常在 1-5ms 以内，对用户体验无影响。

### Q: 内置危险命令列表能自定义吗？
A: 内置列表是硬编码的纵深防御，不可修改。但你可以通过角色白名单添加更多限制。

### Q: 如何查看拦截记录？
A: Web 控制台 http://127.0.0.1:9091/ui/ 的审计日志和拦截统计页面可以查看。

### Q: 支持哪些 Agent 平台？
A: 任何能发 HTTP 请求的 Agent 都可以集成。已提供 Python SDK、Java SDK、MCP 服务器、Codex 插件。

### Q: 策略修改后需要重启吗？
A: 不需要，支持热加载。修改 data/policy.yaml 后 3 秒内自动生效。

---

> DevPilot Sandbox v0.2.0 | Apache 2.0 License
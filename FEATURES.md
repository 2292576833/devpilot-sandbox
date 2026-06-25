# DevPilot Sandbox v0.2.0 — 功能清单

> 当前版本已完成的功能汇总

---

## 1. 核心引擎

| 功能 | 状态 | 说明 |
|------|------|------|
| PathGuard 文件围栏 | ✅ 完成 | 路径穿越防护、读写分离、符号链接检测 |
| CommandGuard 命令围栏 | ✅ 完成 | 命令白名单 + 44个内置危险命令黑名单 |
| PolicyEngine 策略引擎 | ✅ 完成 | YAML 策略配置 + 热加载（3秒生效） |
| AuditLogger 审计日志 | ✅ 完成 | JSONL 格式、自动轮转、查询 API |
| 并发控制 | ✅ 完成 | 每个角色独立信号量限制 |
| 拦截统计 | ✅ 完成 | `GET /api/v1/guard/stats` |
| 危险命令列表 API | ✅ 完成 | `GET /api/v1/guard/dangerous-commands` |

## 2. REST API（12+ 端点）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/guard/file-read` | 校验文件读取权限 |
| POST | `/api/v1/guard/file-write` | 校验文件写入权限 |
| POST | `/api/v1/guard/command-run` | 校验命令执行权限 |
| GET | `/api/v1/guard/health` | 服务健康检查（含统计） |
| GET | `/api/v1/guard/stats` | 拦截统计信息 |
| GET | `/api/v1/guard/dangerous-commands` | 内置危险命令列表 |
| GET | `/api/v1/guard/audit` | 查询审计日志 |
| GET | `/api/v1/policy/roles` | 获取角色列表 |
| GET | `/api/v1/policy/yaml` | 获取策略 YAML |
| POST | `/api/v1/policy/reload` | 重载策略 |
| GET | `/api/v1/policy/versions` | 版本历史 |
| POST | `/api/v1/policy/rollback/{id}` | 回滚版本 |

## 3. 内置危险命令黑名单（44 个）

| 类别 | 命令 | 数量 |
|------|------|------|
| 磁盘操作 | `format`, `diskpart`, `mkfs` | 3 |
| 系统管理 | `shutdown`, `bcdedit`, `powercfg` | 3 |
| 注册表 | `reg delete/add/import`, `regedit` | 2 |
| 服务控制 | `sc stop/delete`, `schtasks` | 2 |
| 用户管理 | `net user`, `takeown`, `passwd` | 3 |
| 权限修改 | `icacls`, `chmod 777`, `chown`, `sudo`, `su` | 5 |
| 文件破坏 | `rm -rf`, `del /f`, `rmdir /s`, `cipher /w`, `erase`, `rd`, `move`, `rename`, `ren`, `replace` | 10 |
| 进程管理 | `kill -9`, `killall`, `pkill` | 3 |
| 系统关闭 | `reboot`, `halt`, `poweroff`, `init 0` | 4 |
| 服务管理 | `systemctl stop/disable/mask`, `mount`, `umount` | 5 |
| Windows 特定 | `wevtutil`, `subst`, `msg`, `attrib`, `fsutil` | 5 |
| 其他 | `dd`, `fdisk`, `cacls`, `net1` | 4 |

## 4. Web 控制台（6 个页面）

| 页面 | 功能 |
|------|------|
| 📊 仪表盘 | 服务器状态、角色列表、拦截率 |
| 🧪 安全测试 | 文件读写测试、命令测试、路径穿越测试 |
| 📋 审计日志 | 日志查询、角色过滤、操作过滤、导出 CSV |
| 🛡️ 拦截统计 | 拦截率、各角色拦截明细、危险命令列表 |
| ⚙️ 角色管理 | 角色 CRUD、版本历史、回滚 |
| 🔧 系统配置 | YAML 查看/下载、策略重载 |

## 5. VSCode 扩展（19 个命令）

| 命令 | 说明 |
|------|------|
| Check file access | 检查文件读取权限 |
| Check file write access | 检查文件写入权限 |
| Check command permission | 检查命令执行权限 |
| Quick security test | 快速安全测试（路径穿越 + 危险命令）|
| Show sandbox status | 显示沙箱状态 |
| Show sandbox statistics | 显示拦截统计 |
| Show dangerous commands list | 显示 44 个危险命令 |
| Batch test dangerous commands | 批量测试 6 个危险命令 |
| Show audit log | 查看审计日志 |
| Show role details | 查看角色详情 |
| Run diagnostics | 运行完整诊断 |
| Export audit log | 导出审计日志为 JSON |
| View policy YAML | 查看策略 YAML |
| Start sandbox server | 手动启动服务器 |
| Open web console | 打开 Web 控制台 |
| Select default role | 切换默认角色 |
| Refresh role list | 刷新角色列表 |
| Show output log | 显示扩展日志 |

**其他功能：**
- 自动查找 JAR 路径（无需手动配置）
- 开机启动服务器（添加到注册表）
- VSCode 启动时自动拉起服务器
- 状态栏显示连接状态 + 拦截数
- 侧边栏角色树
- 文件自动检测（打开/保存时）

## 6. Codex 插件（11 个 MCP 工具）

| 工具 | 说明 |
|------|------|
| `check_file_read` | 检查文件读取权限 |
| `check_file_write` | 检查文件写入权限 |
| `check_command` | 检查命令执行权限 |
| `check_permission` | 统一检查任意操作 |
| `get_health` | 获取服务状态 |
| `get_stats` | 获取拦截统计 |
| `get_dangerous_commands` | 查看危险命令列表 |
| `get_audit_log` | 查询审计日志 |
| `get_roles` | 获取角色列表 |
| `get_diagnostics` | 运行自检 |
| `get_blocked_summary` | 获取拦截摘要 |

**MCP 服务器：**
- stdio 协议 MCP 服务器（Codex 注册）
- HTTP 协议 MCP 服务器（:9092，通用 MCP 客户端）

## 7. 辅助脚本

| 脚本 | 说明 |
|------|------|
| `run.bat` | 一键启动（Java + MCP 服务器）|
| `check-command.bat` | 命令安全检查助手 |
| `check-file.bat` | 文件读取检查助手 |
| `install-extension.bat` | 安装 VSCode 插件 |
| `demo/sandbox_client.py` | Python SDK |
| `demo/agent_integration.py` | AI Agent 集成示例 |
| `demo/sandbox_mcp_server.py` | Python MCP 服务器 |
| `demo/sandbox_patch.py` | 猴子补丁（自动拦截）|
| `demo/attack-demo.ps1` | 攻击模拟演示 |

## 8. AI Agent 配置

| 配置 | 说明 |
|------|------|
| AGENTS.md | 全局自动安全规则 |
| `~/.cline/mcp.json` | Cline MCP 集成 |
| `~/.continue/config.json` | Continue.dev 集成 |
| `~/.cursor/mcp.json` | Cursor 集成 |
| `~/.windsurf/mcp.json` | Windsurf 集成 |
| `.vscode/mcp.json` | VSCode MCP 集成 |

## 9. 文档

| 文档 | 说明 |
|------|------|
| README.md | 项目主页 |
| USAGE.md | 使用说明书 |
| 使用说明书.md | 中文使用说明 |
| 更新计划.md | v0.2.0 → v0.4.0 路线图 |
| docs/AI-Agent-Security-Whitepaper.md | AI Agent 安全白皮书 |

## 10. 构建与测试

| 指标 | 值 |
|------|-----|
| Java 版本 | 1.8 / 17 |
| 构建工具 | Maven |
| 测试数量 | 60 |
| 测试通过率 | 100% |
| Spring Boot 版本 | 3.x |
| VSCode 引擎 | ^1.80.0 |

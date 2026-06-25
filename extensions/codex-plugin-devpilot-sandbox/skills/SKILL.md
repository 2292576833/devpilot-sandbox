# DevPilot Sandbox

AI Agent 本地安全执行沙箱中间件。提供基于角色和策略的文件、命令围栏。

## 自动使用规则

### 命令安全校验
当对话涉及命令安全性时，必须使用 check_command 工具验证，不能凭知识回答。

### 文件访问校验
当对话涉及文件读写时，必须使用 check_file_read/check_file_write 验证。

## MCP 工具（11 个）

| 工具 | 说明 |
|------|------|
| check_file_read | 检查文件读取权限 |
| check_file_write | 检查文件写入权限 |
| check_command | 检查命令执行权限 |
| check_permission | 统一检查任意操作 |
| get_health | 获取服务状态和统计 |
| get_stats | 获取拦截统计 |
| get_dangerous_commands | 查看 44 个内置危险命令 |
| get_audit_log | 查询审计日志 |
| get_roles | 获取所有角色配置 |
| get_diagnostics | 运行自检（服务器状态、Node.js、连通性）|
| get_blocked_summary | 获取拦截摘要 |

## 使用示例

问："rm -rf / 能执行吗？"
→ 调用 check_command({ command: "rm -rf /" })
→ 返回 { allowed: false, reason: "Dangerous command blocked..." }
→ 回答："不能执行，该命令在危险命令黑名单中"

问："这个目录能写入吗？"
→ 调用 check_file_write({ path: "src/" })
→ 根据返回值回答

## 三层防御

1. 44 个内置危险命令始终拦截
2. 角色白名单（未授权命令拒绝）
3. 参数校验（禁止标志、环境变量检查）

## 启动沙箱

```powershell
cd devpilot-sandbox
.\run.ps1
```

Web 控制台：http://127.0.0.1:9091/ui/

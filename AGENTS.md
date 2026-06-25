# DevPilot Sandbox Project

This project is the DevPilot Sandbox — a security sandbox middleware for AI Agents.

# DevPilot Sandbox — 自动安全规则

DevPilot Sandbox 是一个本地安全沙箱中间件，通过 MCP 工具提供命令和文件权限校验。

## 强制规则（必须遵守）

### 命令安全校验
当用户询问"这个命令能执行吗"、"这个命令安全吗"、"会不会有风险"等涉及命令安全的问题时：
  → 你**必须**从 `devpilot-sandbox` MCP 服务器调用 `check_command` 工具来验证
  → **不能**仅凭你自己的知识回答
  → 必须等待工具返回结果后再给出答案

示例流程：
1. 用户问："npm install -g express 能执行吗？"
2. 你调用 `check_command({ command: "npm install -g express" })`
3. 工具返回 `{ allowed: false, reason: "Flag denied: -g" }`
4. 你回答："不能，-g 参数被安全策略禁止"

### 文件访问校验
当用户询问文件能否读取或写入时：
  → 必须调用 `check_file_read` 或 `check_file_write` 工具验证
  → 不能仅凭路径判断

### 无需校验的普通对话
如果用户只是问"npm 是什么"或"给我写一段代码"，不需要调用沙箱工具。
只在涉及**执行风险**时使用。


# DevPilot Sandbox - Python SDK 示例
# 展示如何从任何 Python AI Agent 中集成 Sandbox 安全围栏
#
# 使用方法:
#   1. 启动 Sandbox 服务: java -jar target/devpilot-sandbox-0.1.0.jar
#   2. 运行本脚本: python demo/sandbox_client.py

import json
import sys
from urllib.request import Request, urlopen
from urllib.error import URLError


class DevPilotSandboxClient:
    """DevPilot Sandbox 的 Python 客户端封装"""

    def __init__(self, base_url="http://127.0.0.1:9091", role_id="CODE_ENGINEER"):
        self.base_url = base_url.rstrip("/")
        self.role_id = role_id

    def _post(self, endpoint, data):
        """发送 POST 请求的底层方法"""
        url = f"{self.base_url}{endpoint}"
        body = json.dumps(data).encode("utf-8")
        req = Request(url, data=body, headers={"Content-Type": "application/json"},
                      method="POST")
        try:
            with urlopen(req, timeout=5) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except URLError as e:
            return {"allowed": False, "reason": f"连接失败: {e.reason}"}
        except Exception as e:
            return {"allowed": False, "reason": f"请求异常: {str(e)}"}

    def _get(self, endpoint):
        """发送 GET 请求的底层方法"""
        url = f"{self.base_url}{endpoint}"
        try:
            with urlopen(url, timeout=5) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except Exception as e:
            return {"error": str(e)}

    def check_health(self):
        """健康检查"""
        return self._get("/api/v1/guard/health")

    def check_file_read(self, path, role_id=None):
        """校验文件读取路径"""
        return self._post("/api/v1/guard/file-read", {
            "roleId": role_id or self.role_id,
            "path": path,
        })

    def check_file_write(self, path, role_id=None):
        """校验文件写入路径"""
        return self._post("/api/v1/guard/file-write", {
            "roleId": role_id or self.role_id,
            "path": path,
        })

    def check_command(self, command, role_id=None):
        """校验命令执行"""
        return self._post("/api/v1/guard/command-run", {
            "roleId": role_id or self.role_id,
            "command": command,
        })

    # ----- 安全便捷方法 -----

    def safe_read(self, path, role_id=None):
        """安全的文件读取 —— 先校验后读取"""
        result = self.check_file_read(path, role_id)
        if not result.get("allowed"):
            raise PermissionError(
                f"[Sandbox] 读操作被拦截: {result.get('reason')}")
        # 此处执行实际的文件读取操作
        # with open(result["resolvedPath"], "r") as f:
        #     return f.read()
        return result["resolvedPath"]

    def safe_run(self, command, role_id=None):
        """安全的命令执行 —— 先校验后执行"""
        result = self.check_command(command, role_id)
        if not result.get("allowed"):
            raise PermissionError(
                f"[Sandbox] 命令被拦截: {result.get('reason')}")
        # 此处执行实际的命令
        # import subprocess
        # return subprocess.run(command, shell=True, capture_output=True)
        return result.get("resolvedPath")


# ===== Demo =====

def main():
    client = DevPilotSandboxClient(role_id="CODE_ENGINEER")

    print("=" * 50)
    print("  DevPilot Sandbox - Python SDK Demo")
    print("=" * 50)

    # 1. 健康检查
    print("\n[1] 健康检查...")
    health = client.check_health()
    print(f"    状态: {health.get('status')}")
    print(f"    可用角色: {', '.join(health.get('roles', []))}")

    if health.get("status") != "UP":
        print("    ❌ 服务未启动，请先运行: java -jar target/devpilot-sandbox-0.1.0.jar")
        sys.exit(1)

    # 2. 文件读取校验
    print("\n[2] 文件读取校验...")
    tests = [
        ("合法路径", "src/main/java/App.java"),
        ("路径穿越", "../../etc/passwd"),
        ("敏感路径", ".git/config"),
    ]
    for name, path in tests:
        result = client.check_file_read(path)
        status = "✅" if result.get("allowed") else "⛔"
        print(f"    {status} [{name}] {path}")
        if not result.get("allowed"):
            print(f"      ↳ {result.get('reason')}")

    # 3. 命令校验
    print("\n[3] 命令校验...")
    cmd_tests = [
        ("合法命令", "git status"),
        ("越权命令", "rm -rf /"),
        ("禁止参数", "npm install -g express"),
    ]
    for name, cmd in cmd_tests:
        result = client.check_command(cmd)
        status = "✅" if result.get("allowed") else "⛔"
        print(f"    {status} [{name}] {cmd}")
        if not result.get("allowed"):
            print(f"      ↳ {result.get('reason')}")

    # 4. 文件写入校验
    print("\n[4] 文件写入校验 (大小限制)...")
    result = client.check_file_write("target/output.jar")
    status = "✅" if result.get("allowed") else "⛔"
    print(f"    {status} target/output.jar")

    # 5. 异常处理演示
    print("\n[5] 异常处理演示 (safe_read)...")
    try:
        client.safe_read("../../Windows/System32/config")
    except PermissionError as e:
        print(f"    ✅ 安全拦截生效: {e}")

    print("\n" + "=" * 50)
    print("  Demo 完成!")
    print("=" * 50)


if __name__ == "__main__":
    main()

# sandbox_patch.py
#
# 通用 Python 猴子补丁 —— 零代码侵入，import 即用
#
# 用法:
#   import sandbox_patch                  # 自动安装（默认 127.0.0.1:9091）
#   import sandbox_patch; sandbox_patch.init("http://localhost:9091")
#
# 效果:
#   - builtins.open()       -> 先过 PathGuard 再读
#   - subprocess.run()      -> 先过 CommandGuard 再执行
#   - subprocess.Popen()    -> 同上
#   - os.system()           -> 同上
#   - os.popen()            -> 同上
#
# 任何 Python Agent（OpenAI SDK / LangChain / CrewAI / 自研）只需 import 即可生效

import builtins
import os
import subprocess
import sys
from pathlib import Path
from typing import Any, List, Optional, Union

_SANDBOX_URL = os.environ.get("SANDBOX_URL", "http://127.0.0.1:9091")
_DEFAULT_ROLE = os.environ.get("SANDBOX_ROLE", "CODE_ENGINEER")
_ENABLED = True
_client = None


def _get_client():
    global _client
    if _client is None:
        from sandbox_client import DevPilotSandboxClient
        _client = DevPilotSandboxClient(_SANDBOX_URL, _DEFAULT_ROLE)
    return _client


def guard_path(path: str, mode: str = "read") -> str:
    client = _get_client()
    result = client.check_file_read(path) if mode == "read" else client.check_file_write(path)
    if not result.get("allowed"):
        raise PermissionError(f"[Sandbox] path blocked: {result.get('reason')}")
    return result["resolvedPath"]


def guard_command(command: str) -> None:
    client = _get_client()
    result = client.check_command(command)
    if not result.get("allowed"):
        raise PermissionError(f"[Sandbox] command blocked: {result.get('reason')}")


# Save originals
_original_open = builtins.open
_original_run = subprocess.run
_original_popen_cls = subprocess.Popen
_original_system = os.system
_original_popen = os.popen


class _GuardedFileWrapper:
    def __init__(self, path: str, mode: str = "r"):
        access = "write" if ("w" in mode or "a" in mode or "x" in mode or "+" in mode) else "read"
        safe_path = guard_path(path, access)
        self._file = _original_open(safe_path, mode)

    def __enter__(self): return self._file.__enter__()
    def __exit__(self, *a): return self._file.__exit__(*a)
    def __getattr__(self, n): return getattr(self._file, n)


def _guarded_open(path, mode="r", *a, **kw):
    if not _ENABLED: return _original_open(path, mode, *a, **kw)
    return _GuardedFileWrapper(path, mode)


def _guarded_run(*a, **kw):
    if not _ENABLED: return _original_run(*a, **kw)
    cmd = a[0] if a else kw.get("args", "")
    if isinstance(cmd, list): cmd = " ".join(cmd)
    guard_command(str(cmd))
    return _original_run(*a, **kw)


class _GuardedPopen(subprocess.Popen):
    def __init__(self, args, *a, **kw):
        if _ENABLED:
            cmd = args if isinstance(args, str) else " ".join(args) if isinstance(args, list) else str(args)
            guard_command(cmd)
        super().__init__(args, *a, **kw)


def _guarded_system(cmd):
    if _ENABLED: guard_command(cmd)
    return _original_system(cmd)


def _guarded_popen(cmd, *a, **kw):
    if _ENABLED: guard_command(cmd)
    return _original_popen(cmd, *a, **kw)


_installed = False


def install(url=None, role=None):
    global _SANDBOX_URL, _DEFAULT_ROLE, _client, _installed, _ENABLED
    if url: _SANDBOX_URL = url
    if role: _DEFAULT_ROLE = role
    _client = None
    _ENABLED = True
    builtins.open = _guarded_open
    subprocess.run = _guarded_run
    subprocess.Popen = _GuardedPopen
    os.system = _guarded_system
    os.popen = _guarded_popen
    _installed = True
    print(f"[SandboxPatch] installed - all file/cmd ops guarded ({_SANDBOX_URL})")


def uninstall():
    global _installed, _ENABLED
    builtins.open = _original_open
    subprocess.run = _original_run
    subprocess.Popen = _original_popen_cls
    os.system = _original_system
    os.popen = _original_popen
    _installed = False
    _ENABLED = True
    print("[SandboxPatch] uninstalled")


def pause():
    global _ENABLED
    _ENABLED = False


def resume():
    global _ENABLED
    _ENABLED = True


# Auto-install on import
if os.environ.get("SANDBOX_PATCH_DISABLE", "").lower() not in ("1", "true", "yes"):
    install()
else:
    print("[SandboxPatch] auto-install skipped (SANDBOX_PATCH_DISABLE=1)")

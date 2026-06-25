"""DevPilot Sandbox - 猴子补丁现场演示"""
import sys, os
sys.path.insert(0, 'demo')
os.environ['SANDBOX_URL'] = 'http://127.0.0.1:9091'

print("=" * 60)
print("  DevPilot Sandbox - Monkey Patch Demo")
print("=" * 60)

print("\n[1/7] import sandbox_patch ...")
import sandbox_patch
print("  OK installed")

print("\n[2/7] open(README.md) - valid read")
try:
    with open("README.md") as f: c = f.read()
    print(f"  OK read ({len(c)} chars)")
except PermissionError as e:
    print(f"  BLOCKED: {e}")

print("\n[3/7] open(../../Windows/System32/config) - path traversal")
try:
    with open("../../Windows/System32/config") as f: f.read()
    print("  NOT BLOCKED!")
except PermissionError as e:
    print(f"  BLOCKED: {e}")

print("\n[4/7] open(.git/config) - sensitive path")
try:
    with open(".git/config") as f: f.read()
    print("  NOT BLOCKED!")
except PermissionError as e:
    print(f"  BLOCKED: {e}")

print("\n[5/7] subprocess.run(git status) - valid command")
import subprocess
try:
    r = subprocess.run("git status", shell=True, capture_output=True, text=True)
    print(f"  OK: {r.stdout.strip()[:60]}")
except PermissionError as e:
    print(f"  BLOCKED: {e}")

print("\n[6/7] subprocess.run(rm -rf /) - dangerous command")
try:
    subprocess.run("rm -rf /", shell=True, capture_output=True)
    print("  NOT BLOCKED!")
except PermissionError as e:
    print(f"  BLOCKED: {e}")

print("\n[7/7] subprocess.run(npm install -g express) - denied flag")
try:
    subprocess.run("npm install -g express", shell=True, capture_output=True)
    print("  NOT BLOCKED!")
except PermissionError as e:
    print(f"  BLOCKED: {e}")

print("\n" + "=" * 60)
print("  ALL TESTS COMPLETE")
print("=" * 60)

"""
preflight_check.py — Pre-Flight Environment & Toolchain Validator
==================================================================
Runs before Step 01 or Step 04 of any SDLC pipeline to ensure all required
compilers, runtimes, daemons, and environment variables are properly set.

Fail-Fast Contract:
  If any prerequisite is missing, outputs clear actionable human commands
  to configure the environment and exits with code 1, preventing rogue
  file scanning by AI agents.

Zero external dependencies — stdlib only.
"""

from __future__ import annotations

import os
import sys
import re
import subprocess
import argparse
from pathlib import Path
from typing import List, Tuple

# Fix Windows console encoding for UTF-8
if hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass


def _check_command(cmd: str, timeout: int = 10) -> tuple[int, str]:
    try:
        proc = subprocess.run(
            cmd,
            shell=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            timeout=timeout,
        )
        return proc.returncode, proc.stdout.decode("utf-8", errors="replace").strip()
    except Exception as exc:
        return -1, str(exc)


def check_git(repo_root: Path) -> Tuple[bool, str, str]:
    code, out = _check_command("git --version")
    if code != 0:
        return False, "Git not found on PATH.", "Install Git and ensure 'git' is in system PATH."
    return True, f"Git detected: {out}", ""


def check_java(required_version: str = "21") -> Tuple[bool, str, str]:
    # 1. Check JAVA_HOME
    java_home = os.environ.get("JAVA_HOME", "").strip()
    home_msg = f"$env:JAVA_HOME={java_home}" if java_home else "JAVA_HOME is not set"

    # 2. Check java -version
    code, out = _check_command("java -version")
    if code != 0:
        return (
            False,
            f"Java binary not accessible ({home_msg}).",
            f"Ensure JDK {required_version} is installed and set:\n"
            f'      $env:JAVA_HOME = "C:\\Program Files\\Java\\jdk-{required_version}..."\n'
            f'      $env:Path = "$env:JAVA_HOME\\bin;" + $env:Path'
        )

    # 3. Match major version
    match = re.search(r'version\s+"(\d+)', out)
    if not match:
        match = re.search(r'(\d+)\.\d+\.\d+', out)

    if match:
        major = match.group(1)
        if major == required_version:
            return True, f"JDK {major} active ({home_msg}).", ""
        else:
            return (
                False,
                f"JDK version mismatch: detected JDK {major}, but required JDK {required_version} ({home_msg}).",
                f"Switch active JDK to version {required_version}:\n"
                f'      $env:JAVA_HOME = "C:\\Program Files\\Java\\jdk-{required_version}..."\n'
                f'      $env:Path = "$env:JAVA_HOME\\bin;" + $env:Path'
            )

    return True, f"Java detected: {out.splitlines()[0]}", ""


def check_docker() -> Tuple[bool, str, str]:
    code, out = _check_command("docker info")
    if code != 0:
        return (
            False,
            "Docker daemon is not running or 'docker' CLI is not accessible.",
            "Start Docker Desktop or your local container runtime daemon."
        )
    return True, "Docker daemon is active and responsive.", ""


def check_build_wrapper(repo_root: Path, stack_hints: str = "") -> Tuple[bool, str, str]:
    # Check if Gradle project
    is_gradle = any((repo_root / f).exists() for f in ["build.gradle.kts", "build.gradle", "settings.gradle.kts"])
    if is_gradle:
        has_bat = (repo_root / "gradlew.bat").exists()
        has_sh = (repo_root / "gradlew").exists()
        if not has_bat and not has_sh:
            return (
                False,
                "Gradle wrapper (gradlew/gradlew.bat) missing in project root.",
                "Ensure Environment Bootstrap (Step 03) generates the Gradle wrapper wrapper files."
            )
        return True, "Gradle wrapper found (gradlew.bat / gradlew).", ""

    return True, "Build tool check skipped (no Gradle files in root yet).", ""


def parse_manifest_prerequisites(env_file: Path) -> dict:
    result = {"java_version": "21", "docker_required": True}
    if not env_file.exists():
        return result

    text = env_file.read_text(encoding="utf-8")
    if "JDK 17" in text or "JVM 17" in text or "Java 17" in text:
        result["java_version"] = "17"
    elif "JDK 21" in text or "JVM 21" in text or "Java 21" in text:
        result["java_version"] = "21"

    if "Docker" in text or "docker-compose" in text or "Docker Compose" in text:
        result["docker_required"] = True

    return result


def run_preflight(repo_root: Path) -> bool:
    print("=" * 70)
    print("   Anti-Gravity Agents — Pre-Flight Environment Validator")
    print("=" * 70)

    # Search for PROJECT_ENV
    env_file = repo_root / "docs" / "PROJECT_ENV.md"
    if not env_file.exists():
        env_file = repo_root / "PROJECT_ENV.md"

    prereqs = parse_manifest_prerequisites(env_file)
    failures: List[Tuple[str, str]] = []

    # 1. Git Check
    ok, msg, fix = check_git(repo_root)
    if ok:
        print(f"  ✔ {msg}")
    else:
        print(f"  ✖ {msg}")
        failures.append((msg, fix))

    # 2. Java Check
    ok, msg, fix = check_java(prereqs["java_version"])
    if ok:
        print(f"  ✔ {msg}")
    else:
        print(f"  ✖ {msg}")
        failures.append((msg, fix))

    # 3. Docker Check
    if prereqs["docker_required"]:
        ok, msg, fix = check_docker()
        if ok:
            print(f"  ✔ {msg}")
        else:
            print(f"  ✖ {msg}")
            failures.append((msg, fix))

    # 4. Build Wrapper Check
    ok, msg, fix = check_build_wrapper(repo_root)
    if ok:
        print(f"  ✔ {msg}")
    else:
        print(f"  ✖ {msg}")
        failures.append((msg, fix))

    print("-" * 70)

    if failures:
        print("\n" + "!" * 70)
        print("  [PRE-FLIGHT FAILED]: Environment prerequisites not met:")
        print("!" * 70)
        for issue, remediation in failures:
            print(f"\n  • Problem: {issue}")
            if remediation:
                print(f"    Action:  {remediation}")
        print("\n" + "!" * 70)
        print("  Please configure your environment variables and rerun.\n")
        return False

    print("\n[✔] All pre-flight environment checks passed successfully.\n")
    return True


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Pre-Flight Environment Check")
    parser.add_argument("--root", default=".", help="Repository root directory")
    args = parser.parse_args()

    root_path = Path(args.root).resolve()
    success = run_preflight(root_path)
    sys.exit(0 if success else 1)

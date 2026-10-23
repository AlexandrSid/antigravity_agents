# Local Development Environment Slot Implementation

> **Layer**: `environment`  
> **Implements**: `.ai/guidelines/environment/META.md`  
> **Target**: Local Developer Workstation (Windows PowerShell / Linux Bash / macOS)

---

## 1. TOOLCHAIN_WRAPPER_CONVENTIONS
1. **Mandatory Project-Local Wrappers**:
   - For JVM/Gradle: Use `./gradlew` (POSIX) or `.\gradlew.bat` (Windows). Never use global PATH `gradle`.
   - For Node: Use `npm`, `pnpm`, or `yarn` honoring lockfiles.
   - For Python: Use `.venv/bin/python` / `.venv/Scripts/python.exe`.
2. **OS Path & Shell Agnosticism**:
   - Commands specified in `docs/PROJECT_ENV.md` must be executable in the developer's active shell without syntax errors.

## 2. PREFLIGHT_CHECK_SPECIFICATION
Preflight verification script `python python-runtime/preflight_check.py` verifies:
- Runtime version (e.g., JDK 21).
- Key environment variables (e.g., `JAVA_HOME`).
- Docker daemon responsiveness (`docker info`).
- Working directory permissions and tool wrapper existence.

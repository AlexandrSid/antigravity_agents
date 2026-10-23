#!/usr/bin/env python3
"""
control-panel/run.py — Legacy CLI Forwarder
============================================
Forwarder to the dedicated standalone engine in python-runtime/standalone_runner.py.
For In-IDE development (Subscription-First), use your IDE's native orchestrator:
  - Antigravity IDE: .antigravity/orchestrator.md
  - Cursor: .cursor/rules/orchestrator.md
"""

import sys
import subprocess
from pathlib import Path

RUNNER = Path(__file__).resolve().parent.parent / "python-runtime" / "standalone_runner.py"

if not RUNNER.exists():
    print(f"Error: Standalone runner not found at {RUNNER}", file=sys.stderr)
    sys.exit(1)

proc = subprocess.run([sys.executable, str(RUNNER)] + sys.argv[1:])
sys.exit(proc.returncode)

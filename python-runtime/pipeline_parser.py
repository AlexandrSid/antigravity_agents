"""
pipeline_parser.py — Markdown SSOT Pipeline Parser
====================================================
Reads the Greenfield SDLC pipeline table directly from a Markdown file
(e.g. .ai/pipelines/GREENFIELD_SDLC_PIPELINE.md) without any intermediate JSON/YAML.

Zero external dependencies — stdlib only.
"""

from __future__ import annotations

import re
import dataclasses
from pathlib import Path
from typing import List, Optional


# ---------------------------------------------------------------------------
# Data Model
# ---------------------------------------------------------------------------

@dataclasses.dataclass
class PipelineStep:
    """Represents a single row from the SDLC execution matrix."""
    step_id: str           # e.g. "01", "02" …
    role_name: str         # e.g. "Architect"
    rule_file: str         # e.g. ".ai/rules/architect.md"
    inputs: List[str]      # resolved paths (no {feature})
    outputs: List[str]     # resolved paths (no {feature})
    profile: str           # "REASONING_HEAVY" | "STRICT_CODEGEN" | "FAST_EXECUTION"
    gate: str              # "NONE" | "COMPILE_CHECK" | "UNIT_RED_PHASE" | …
    description: str       # free-text purpose column


# ---------------------------------------------------------------------------
# Internal helpers
# ---------------------------------------------------------------------------

_ROW_RE = re.compile(r"^\|\s*\*\*(\d+)\*\*\s*\|(.+)$")
_BR_RE = re.compile(r"<br\s*/?>", re.IGNORECASE)
_BACKTICK_RE = re.compile(r"`([^`]+)`")


def _clean_cell(raw: str) -> str:
    text = _BR_RE.sub("\n", raw)
    text = text.replace("•", "").strip()
    return text


def _extract_paths(cell_text: str) -> List[str]:
    return _BACKTICK_RE.findall(cell_text)


def _extract_role_and_file(cell_text: str) -> tuple[str, str]:
    role_match = re.search(r"\*\*([^*]+)\*\*", cell_text)
    role = role_match.group(1).strip() if role_match else "unknown"
    paths = _extract_paths(cell_text)
    rule_file = paths[0] if paths else ""
    return role, rule_file


def _interpolate(text: str, feature: str) -> str:
    return text.replace("{feature}", feature)


def _split_row_cells(row: str) -> List[str]:
    inner = row.strip().strip("|")
    return [c.strip() for c in inner.split("|")]


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------

def load_pipeline(md_path: str | Path, feature: str) -> List[PipelineStep]:
    md_path = Path(md_path)
    if not md_path.exists():
        raise FileNotFoundError(f"Pipeline file not found: {md_path}")

    content = md_path.read_text(encoding="utf-8")
    lines = content.splitlines()
    header_idx: Optional[int] = None
    for i, line in enumerate(lines):
        has_step_col = bool(re.search(r"\u0428\u0430\u0433|Step", line, re.IGNORECASE))
        has_gate_col = bool(re.search(r"Gate", line, re.IGNORECASE))
        if has_step_col and has_gate_col:
            header_idx = i
            break
        if has_gate_col and line.strip().startswith("|") and "|".join([""]*5) in line:
            header_idx = i
            break

    if header_idx is None:
        raise ValueError(
            f"Could not locate the execution matrix table with a 'Gate' column "
            f"in {md_path}. Ensure the table header contains the 'Gate' column."
        )

    header_cells = _split_row_cells(lines[header_idx])
    col_map: dict[str, int] = {}
    for idx, cell in enumerate(header_cells):
        cell_ascii = cell.encode("ascii", errors="replace").decode("ascii").lower()
        cell_lower = cell.lower()
        if "step" in cell_ascii or "\u0448\u0430\u0433" in cell_lower or (idx == 0 and "step" not in "".join(header_cells).lower() and idx not in col_map.values()):
            if "step" not in col_map or idx == 0:
                col_map.setdefault("step", idx)
        if any(k in cell_lower for k in ("роль", "agent role", "файл правил")) or "role" in cell_ascii:
            col_map.setdefault("role", idx)
        if any(k in cell_lower for k in ("входные",)) or "input" in cell_ascii:
            col_map.setdefault("inputs", idx)
        if any(k in cell_lower for k in ("выходные",)) or "output" in cell_ascii:
            col_map.setdefault("outputs", idx)
        if any(k in cell_lower for k in ("профиль",)) or "model profile" in cell_ascii or "llm" in cell_ascii:
            col_map.setdefault("profile", idx)
        if "gate" in cell_lower:
            col_map["gate"] = idx
        if any(k in cell_lower for k in ("назначение", "зона")) or "responsibility" in cell_ascii or "purpose" in cell_ascii:
            col_map.setdefault("desc", idx)

    _validate_columns(col_map, md_path)

    steps: List[PipelineStep] = []
    for line in lines[header_idx + 2:]:
        line = line.strip()
        if not line.startswith("|"):
            break
        if re.match(r"^\|[-: |]+\|$", line):
            continue

        if not re.search(r"\*\*\d+\*\*", line):
            continue

        cells = _split_row_cells(line)
        if len(cells) < max(col_map.values()) + 1:
            continue

        step_id = re.search(r"\d+", cells[col_map["step"]])
        if not step_id:
            continue
        step_id_str = step_id.group(0).zfill(2)

        role, rule_file = _extract_role_and_file(cells[col_map["role"]])

        raw_inputs = _clean_cell(cells[col_map["inputs"]])
        raw_outputs = _clean_cell(cells[col_map["outputs"]])

        inputs = [_interpolate(p, feature) for p in _extract_paths(raw_inputs)]
        outputs = [_interpolate(p, feature) for p in _extract_paths(raw_outputs)]

        profile_cell = cells[col_map["profile"]].strip().strip("`") if col_map.get("profile") is not None and col_map["profile"] < len(cells) else ""
        gate_cell = cells[col_map["gate"]].strip().strip("`") if col_map.get("gate") is not None and col_map["gate"] < len(cells) else "NONE"
        desc_idx = col_map.get("desc")
        desc = _clean_cell(cells[desc_idx]).replace("\n", " ") if desc_idx is not None and desc_idx < len(cells) else ""

        steps.append(PipelineStep(
            step_id=step_id_str,
            role_name=role,
            rule_file=_interpolate(rule_file, feature),
            inputs=inputs,
            outputs=outputs,
            profile=profile_cell,
            gate=gate_cell,
            description=desc,
        ))

    if not steps:
        raise ValueError(
            f"No valid pipeline steps parsed from {md_path}. "
            "Check that the table rows contain bold step numbers like **01**."
        )

    steps.sort(key=lambda s: s.step_id)
    return steps


def validate_inputs_exist(step: PipelineStep, repo_root: Path) -> List[str]:
    missing: List[str] = []
    for path in step.inputs:
        if "**" in path or "*" in path:
            continue
        if path.startswith("src/"):
            continue
        full = repo_root / path
        if not full.exists():
            missing.append(path)
    return missing


def _validate_columns(col_map: dict[str, int], md_path: Path) -> None:
    required = ["step", "role", "inputs", "outputs", "profile", "gate"]
    missing = [k for k in required if k not in col_map]
    if missing:
        raise ValueError(
            f"Pipeline table in {md_path} is missing required columns: "
            f"{missing}. Found columns map: {col_map}."
        )


if __name__ == "__main__":
    import sys
    import json

    if len(sys.argv) < 3:
        print("Usage: python pipeline_parser.py <pipeline.md> <feature-name>")
        sys.exit(1)

    parsed = load_pipeline(sys.argv[1], sys.argv[2])
    for s in parsed:
        print(json.dumps(dataclasses.asdict(s), indent=2, ensure_ascii=False))

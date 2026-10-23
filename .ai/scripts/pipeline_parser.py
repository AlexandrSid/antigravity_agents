"""
pipeline_parser.py — Markdown SSOT Pipeline Parser
====================================================
Reads the Greenfield SDLC pipeline table directly from a Markdown file
(e.g. .ai/GREENFIELD_SDLC_PIPELINE.md) without any intermediate JSON/YAML.

Responsibilities:
  - Locate the execution matrix table in the Markdown file.
  - Parse each row into a PipelineStep dataclass.
  - Interpolate {feature} placeholders in all path fields.
  - Validate that all input artifacts physically exist on disk.
  - Expose a `load_pipeline(md_path, feature)` function for the orchestrator.

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

# Matches a Markdown table row that starts with | **NN** |
_ROW_RE = re.compile(r"^\|\s*\*\*(\d+)\*\*\s*\|(.+)$")

# Strip HTML <br> and extra whitespace from a cell
_BR_RE = re.compile(r"<br\s*/?>", re.IGNORECASE)
_BACKTICK_RE = re.compile(r"`([^`]+)`")


def _clean_cell(raw: str) -> str:
    """Remove markdown noise; return plain text."""
    text = _BR_RE.sub("\n", raw)
    text = text.replace("•", "").strip()
    return text


def _extract_paths(cell_text: str) -> List[str]:
    """Extract all backtick-quoted paths from a table cell."""
    return _BACKTICK_RE.findall(cell_text)


def _extract_role_and_file(cell_text: str) -> tuple[str, str]:
    """
    Parse a cell like:
      **Architect**<br>`.ai/rules/architect.md`
    Returns ("Architect", ".ai/rules/architect.md").
    """
    # role name: bold text
    role_match = re.search(r"\*\*([^*]+)\*\*", cell_text)
    role = role_match.group(1).strip() if role_match else "unknown"

    # rule file: first backtick path
    paths = _extract_paths(cell_text)
    rule_file = paths[0] if paths else ""
    return role, rule_file


def _interpolate(text: str, feature: str) -> str:
    return text.replace("{feature}", feature)


def _split_row_cells(row: str) -> List[str]:
    """
    Split a Markdown table row into cell strings.
    Handles <br> inside cells (they don't contain pipe characters after
    stripping HTML, so a simple split on | is safe).
    """
    # remove leading/trailing |
    inner = row.strip().strip("|")
    # split on | but NOT inside HTML tags — for our table format a
    # simple split is sufficient as <br> contains no |
    return [c.strip() for c in inner.split("|")]


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------

def load_pipeline(md_path: str | Path, feature: str) -> List[PipelineStep]:
    """
    Parse a Greenfield SDLC pipeline Markdown file and return ordered steps.

    Args:
        md_path: Path to the pipeline Markdown file
                 (e.g. ".ai/GREENFIELD_SDLC_PIPELINE.md").
        feature: Feature name used to resolve {feature} placeholders
                 (e.g. "user-service").

    Returns:
        List[PipelineStep] ordered by step_id ascending.

    Raises:
        FileNotFoundError: if md_path does not exist.
        ValueError: if the expected table / required columns are not found.
    """
    md_path = Path(md_path)
    if not md_path.exists():
        raise FileNotFoundError(f"Pipeline file not found: {md_path}")

    content = md_path.read_text(encoding="utf-8")

    # Locate the execution matrix table: find the header row containing
    # required column names.
    lines = content.splitlines()
    header_idx: Optional[int] = None
    for i, line in enumerate(lines):
        # Accept Cyrillic 'Шаг', ASCII 'Step', or any line that has the Gate column
        has_step_col = bool(re.search(r"\u0428\u0430\u0433|Step", line, re.IGNORECASE))
        has_gate_col = bool(re.search(r"Gate", line, re.IGNORECASE))
        if has_step_col and has_gate_col:
            header_idx = i
            break
        # Fallback: catch tables where encoding mangled Cyrillic — look for Gate + pipe pattern
        if has_gate_col and line.strip().startswith("|") and "|".join([""]*5) in line:
            header_idx = i
            break

    if header_idx is None:
        raise ValueError(
            f"Could not locate the execution matrix table with a 'Gate' column "
            f"in {md_path}. Ensure the table header contains the 'Gate' column."
        )

    # Determine column indices from the header
    header_cells = _split_row_cells(lines[header_idx])
    col_map: dict[str, int] = {}
    for idx, cell in enumerate(header_cells):
        # Encode to ASCII with replacement so Cyrillic that survived UTF-8 still matches
        cell_ascii = cell.encode("ascii", errors="replace").decode("ascii").lower()
        cell_lower = cell.lower()
        # Match by Cyrillic text, ASCII equivalent, or positional fallback keywords
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

    # Parse data rows (skip header + separator line)
    steps: List[PipelineStep] = []
    for line in lines[header_idx + 2:]:  # +2 to skip separator
        line = line.strip()
        if not line.startswith("|"):
            break  # table ended
        if re.match(r"^\|[-: |]+\|$", line):
            continue  # separator row

        # Only parse rows that represent numbered steps (**NN**)
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
    """
    Check that all declared input artifacts physically exist on disk.
    Returns a list of missing paths (empty list = all OK).
    Paths starting with 'src/' or '**' (glob patterns) are skipped —
    they represent directories checked by the gate runner.
    """
    missing: List[str] = []
    for path in step.inputs:
        # Skip glob patterns and broad directory references
        if "**" in path or "*" in path:
            continue
        # Skip paths that refer to the running source tree (checked by gate)
        if path.startswith("src/"):
            continue
        full = repo_root / path
        if not full.exists():
            missing.append(path)
    return missing


# ---------------------------------------------------------------------------
# Internal validation helpers
# ---------------------------------------------------------------------------

def _validate_columns(col_map: dict[str, int], md_path: Path) -> None:
    required = ["step", "role", "inputs", "outputs", "profile", "gate"]
    missing = [k for k in required if k not in col_map]
    if missing:
        raise ValueError(
            f"Pipeline table in {md_path} is missing required columns: "
            f"{missing}. Found columns map: {col_map}. "
            "Ensure the table header contains: Step, Role, Inputs, Outputs, "
            "Model Profile, Gate."
        )


# ---------------------------------------------------------------------------
# CLI helper for quick inspection
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    import sys
    import json

    if len(sys.argv) < 3:
        print("Usage: python pipeline_parser.py <pipeline.md> <feature-name>")
        sys.exit(1)

    parsed = load_pipeline(sys.argv[1], sys.argv[2])
    for s in parsed:
        print(json.dumps(dataclasses.asdict(s), indent=2, ensure_ascii=False))

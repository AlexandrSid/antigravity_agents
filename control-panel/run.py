#!/usr/bin/env python3
"""
control-panel/run.py — Единый интерактивный лаунчер пайплайнов Anti-Gravity Agents
=================================================================================
Точка входа оператора в панели управления (Control Panel).
Позволяет запустить нужный пайплайн через пошаговый диалог либо передачей коротких аргументов.

Использование:
  Интерактивный диалог:
    python control-panel/run.py
    (или просто `run` / `run.ps1` / `run.sh` внутри control-panel)

  Прямой запуск с аргументами:
    python control-panel/run.py greenfield <feature>
    python control-panel/run.py greenfield <feature> --mode auto
    python control-panel/run.py greenfield <feature> --dry-run
    python control-panel/run.py greenfield <feature> --from-step 5

Zero external dependencies — Python 3 stdlib only.
"""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
from pathlib import Path

# Fix Windows console encoding for UTF-8 output
if hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass
if hasattr(sys.stderr, "reconfigure"):
    try:
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

# Resolve repository root (parent of control-panel/)
REPO_ROOT = Path(__file__).resolve().parent.parent
ORCHESTRATOR_PATH = REPO_ROOT / ".ai" / "scripts" / "orchestrator.py"

# Реестр известных пайплайнов (ключ -> (название, путь относительно корня, описание))
KNOWN_PIPELINES: dict[str, tuple[str, str, str]] = {
    "greenfield": (
        "Greenfield SDLC Pipeline",
        ".ai/pipelines/GREENFIELD_SDLC_PIPELINE.md",
        "Создание сервиса/модуля с нуля: Архитектура -> TDD -> Реализация -> Интеграция -> Документация",
    ),
}


def _find_available_pipelines() -> dict[str, tuple[str, str, str]]:
    """Находит все доступные пайплайны в репозитории (в .ai/pipelines/ и .ai/)."""
    pipelines = dict(KNOWN_PIPELINES)

    search_dirs = [
        REPO_ROOT / ".ai" / "pipelines",
        REPO_ROOT / ".ai",
    ]

    for s_dir in search_dirs:
        if s_dir.exists():
            for p in s_dir.glob("*PIPELINE*.md"):
                rel_path = str(p.relative_to(REPO_ROOT)).replace("\\", "/")
                key = p.stem.lower().replace("_pipeline", "").replace("_sdlc", "").replace("_", "-")
                if key not in pipelines:
                    pipelines[key] = (
                        p.stem.replace("_", " ").title(),
                        rel_path,
                        f"Автоматически обнаруженный пайплайн из {rel_path}",
                    )
    return pipelines


def _prompt_user(prompt_text: str, default: str | None = None) -> str:
    """Выводит приглашение к вводу с поддержкой значения по умолчанию."""
    hint = f" [{default}]" if default else ""
    full_prompt = f"{prompt_text}{hint}: "
    try:
        val = input(full_prompt).strip()
        if not val and default is not None:
            return default
        return val
    except (KeyboardInterrupt, EOFError):
        print("\n\nПрервано пользователем.")
        sys.exit(0)


def _interactive_wizard(pipelines: dict[str, tuple[str, str, str]]) -> list[str]:
    """Пошаговый интерактивный диалог выбора пайплайна и параметров."""
    print("=" * 68)
    print("   [Anti-Gravity Control Panel] Мастер запуска пайплайнов")
    print("=" * 68)
    print()

    # Шаг 1: Выбор пайплайна
    pipeline_keys = list(pipelines.keys())
    print("Доступные пайплайны:")
    for idx, key in enumerate(pipeline_keys, 1):
        name, rel_path, desc = pipelines[key]
        print(f"  [{idx}] {name} ({key})")
        print(f"      Файл: {rel_path}")
        print(f"      Описание: {desc}")
        print()

    default_choice = "1"
    chosen_key = pipeline_keys[0]
    if len(pipeline_keys) > 1:
        while True:
            choice = _prompt_user(f"Выберите пайплайн (1-{len(pipeline_keys)})", default=default_choice)
            if choice.isdigit() and 1 <= int(choice) <= len(pipeline_keys):
                chosen_key = pipeline_keys[int(choice) - 1]
                break
            elif choice in pipelines:
                chosen_key = choice
                break
            print("Некорректный выбор. Пожалуйста, укажите номер или ключ.")
    else:
        choice = _prompt_user(f"Выберите пайплайн [1: {chosen_key}]", default="1")
        if choice in pipelines:
            chosen_key = choice

    pipeline_name, pipeline_file, _ = pipelines[chosen_key]
    print(f"-> Выбран пайплайн: {pipeline_name} ({pipeline_file})\n")

    # Шаг 2: Имя фичи
    while True:
        feature = _prompt_user("Введите имя фичи (например, auth, billing, telegram-bot)")
        if feature:
            feature = feature.lower().replace(" ", "-")
            break
        print("Имя фичи обязательно для разрешения путей артефактов.")

    print()

    # Шаг 3: Режим выполнения
    print("Режим выполнения:")
    print("  [1] Интерактивный (рекомендуется): подтверждение оператора перед каждым шагом")
    print("  [2] Автономный (auto): сквозное выполнение без остановок")
    print("  [3] Тестовый (dry-run): валидация пайплайна и контрактов без изменений")
    mode_choice = _prompt_user("Выберите режим", default="1")

    mode = "interactive"
    dry_run = False
    if mode_choice == "2":
        mode = "auto"
    elif mode_choice == "3":
        dry_run = True

    print()

    # Шаг 4: Защита Git
    git_auto_choice = _prompt_user(
        "Автоматически создать ветку agent-feature-<feature> при работе на protected-ветке? (Y/n)",
        default="Y",
    )
    git_auto = git_auto_choice.lower() in ("y", "yes", "д", "да", "")

    cmd_args = [
        "--pipeline", pipeline_file,
        "--feature", feature,
        "--mode", mode,
    ]
    if git_auto:
        cmd_args.append("--git-auto")
    if dry_run:
        cmd_args.append("--dry-run")

    return cmd_args


def main() -> None:
    pipelines = _find_available_pipelines()

    if len(sys.argv) == 1:
        cmd_args = _interactive_wizard(pipelines)
    else:
        first_arg = sys.argv[1]

        pipeline_file: str | None = None
        feature_val: str | None = None
        remaining_args: list[str] = []

        if first_arg in ("-h", "--help"):
            print(__doc__)
            print("Доступные пайплайны:")
            for k, (name, path, desc) in pipelines.items():
                print(f"  {k:12} : {name} ({path})")
            sys.exit(0)

        if first_arg in pipelines:
            _, pipeline_file, _ = pipelines[first_arg]
            if len(sys.argv) > 2 and not sys.argv[2].startswith("-"):
                feature_val = sys.argv[2]
                remaining_args = sys.argv[3:]
            else:
                remaining_args = sys.argv[2:]
        else:
            default_pipeline_key = next(iter(pipelines.keys()))
            _, pipeline_file, _ = pipelines[default_pipeline_key]
            if not first_arg.startswith("-"):
                feature_val = first_arg
                remaining_args = sys.argv[2:]
            else:
                remaining_args = sys.argv[1:]

        cmd_args = []
        if pipeline_file:
            cmd_args.extend(["--pipeline", pipeline_file])
        if feature_val:
            cmd_args.extend(["--feature", feature_val])

        has_git_flag = any(arg.startswith("--git") for arg in remaining_args)
        has_mode_flag = any(arg.startswith("--mode") for arg in remaining_args)

        if not has_git_flag:
            cmd_args.append("--git-auto")
        if not has_mode_flag and "--dry-run" not in remaining_args:
            cmd_args.extend(["--mode", "interactive"])

        cmd_args.extend(remaining_args)

    if "--feature" not in cmd_args and "-h" not in cmd_args and "--help" not in cmd_args:
        feature_name = _prompt_user("Укажите имя фичи (--feature)")
        cmd_args.extend(["--feature", feature_name])

    exec_cmd = [sys.executable, str(ORCHESTRATOR_PATH)] + cmd_args
    print("\n" + "-" * 68)
    print(f"[*] Запуск оркестратора: {' '.join(cmd_args)}")
    print("-" * 68 + "\n")

    try:
        proc = subprocess.run(exec_cmd)
        sys.exit(proc.returncode)
    except KeyboardInterrupt:
        print("\nОркестратор остановлен пользователем.")
        sys.exit(130)


if __name__ == "__main__":
    main()

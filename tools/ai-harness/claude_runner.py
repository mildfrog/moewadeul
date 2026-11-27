from __future__ import annotations

import json
import subprocess
from pathlib import Path


class ClaudeError(RuntimeError):
    pass


def run_claude(
    prompt: str,
    cwd: Path,
    allowed_tools: str,
    permission_mode: str | None = None,
    timeout: int = 1800,
) -> str:
    command = ["claude", "-p", "--output-format", "json", "--allowedTools", allowed_tools]
    if permission_mode:
        command += ["--permission-mode", permission_mode]
    try:
        done = subprocess.run(command, cwd=cwd, input=prompt, capture_output=True, text=True, timeout=timeout)
    except subprocess.TimeoutExpired as e:
        raise ClaudeError(f"claude가 {timeout}초 안에 끝나지 않았다") from e
    if done.returncode != 0:
        raise ClaudeError(f"claude 종료 코드 {done.returncode}: {(done.stderr or done.stdout)[-500:]}")
    try:
        payload = json.loads(done.stdout)
    except json.JSONDecodeError as e:
        raise ClaudeError(f"claude 출력이 JSON이 아니다: {done.stdout[-300:]}") from e
    if payload.get("is_error"):
        raise ClaudeError(f"claude 오류: {str(payload.get('result'))[-500:]}")
    return str(payload.get("result", ""))

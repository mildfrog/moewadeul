from __future__ import annotations

import json
import re
import subprocess
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable

from claude_runner import ClaudeError, run_claude
from github import git

BASE_REF = "origin/develop"
GITLEAKS_IMAGE = "ghcr.io/gitleaks/gitleaks@sha256:c00b6bd0aeb3071cbcb79009cb16a60dd9e0a7c60e2be9ab65d25e6bc8abbb7f"
MODULE_PATH = re.compile(r"^backend/src/main/java/com/moewadeul/([^/]+)/(.*)$")
LOG_TAIL_LINES = 200
LOG_TAIL_CHARS = 12000
REVIEW_TOOLS = "Read,Glob,Grep,Bash(git diff:*)"


@dataclass(frozen=True)
class GateContext:
    worktree: Path
    changed: list[str]


@dataclass(frozen=True)
class Gate:
    name: str
    command: Callable[[GateContext], list[str] | None]
    cwd: str
    when: Callable[[list[str]], bool]
    timeout: int = 1800


@dataclass(frozen=True)
class GateResult:
    name: str
    passed: bool
    seconds: float
    log_tail: str


@dataclass(frozen=True)
class SecurityFinding:
    file: str
    line: int | None
    severity: str
    summary: str
    scenario: str


@dataclass
class GateRun:
    results: list[GateResult] = field(default_factory=list)
    skipped: list[str] = field(default_factory=list)

    @property
    def failed(self) -> GateResult | None:
        return next((r for r in self.results if not r.passed), None)


@dataclass(frozen=True)
class SecurityReview:
    findings: list[SecurityFinding]
    error: str | None = None


def changed(prefix: str) -> Callable[[list[str]], bool]:
    return lambda files: any(f.startswith(prefix) for f in files)


def always(files: list[str]) -> bool:
    return True


def changed_files(worktree: Path) -> list[str]:
    return git(worktree, "diff", "--name-only", f"{BASE_REF}...HEAD").splitlines()


def integration_command(ctx: GateContext) -> list[str]:
    modules: list[str] = []
    api_changed = False
    for path in ctx.changed:
        match = MODULE_PATH.match(path)
        if not match:
            continue
        if match.group(1) not in modules:
            modules.append(match.group(1))
        api_changed = api_changed or match.group(2).startswith("api/")
    command = ["./gradlew", "integrationTest"]
    # 모듈에 속하지 않는 main 변경(마이그레이션, 설정)은 어느 모듈에 영향이 갈지 몰라 통합 테스트 전체를 돈다
    for module in sorted(modules):
        command += ["--tests", f"com.moewadeul.{module}.*"]
    if api_changed:
        command += ["--tests", "com.moewadeul.common.security.EndpointAuthorizationCoverageIntegrationTest"]
    return command + ["--console=plain"]


def gitleaks_command(ctx: GateContext) -> list[str]:
    command = ["docker", "run", "--rm", "-v", f"{ctx.worktree}:/repo"]
    # 워크트리의 .git은 본 저장소 .git을 절대 경로로 가리키므로 같은 경로로 붙여야 컨테이너에서 읽힌다
    common = git(ctx.worktree, "rev-parse", "--path-format=absolute", "--git-common-dir").strip()
    command += ["-v", f"{common}:{common}:ro"]
    return command + [GITLEAKS_IMAGE, "git", "/repo", "--no-banner", "--redact", f"--log-opts={BASE_REF}..HEAD"]


GATES = [
    Gate("backend-unit", lambda ctx: ["./gradlew", "test", "spotlessCheck", "--console=plain"], "backend", changed("backend/")),
    Gate("backend-integration", integration_command, "backend", changed("backend/src/main/")),
    Gate(
        "frontend",
        lambda ctx: ["sh", "-c", "npm ci && npm run build && npm run lint && npm test && npm run format:check"],
        "frontend",
        changed("frontend/"),
    ),
    Gate("secret-scan", gitleaks_command, ".", always),
]


def tail(text: str) -> str:
    lines = text.splitlines()[-LOG_TAIL_LINES:]
    return "\n".join(lines)[-LOG_TAIL_CHARS:]


def run_gate(gate: Gate, ctx: GateContext, command: list[str]) -> GateResult:
    started = time.monotonic()
    try:
        done = subprocess.run(
            command, cwd=ctx.worktree / gate.cwd, capture_output=True, text=True, timeout=gate.timeout, stdin=subprocess.DEVNULL
        )
        passed, log = done.returncode == 0, done.stdout + done.stderr
    except subprocess.TimeoutExpired:
        passed, log = False, f"{gate.timeout}초 안에 끝나지 않아 중단했다"
    except OSError as e:
        passed, log = False, f"명령을 실행하지 못했다: {e}"
    return GateResult(gate.name, passed, round(time.monotonic() - started, 1), tail(log))


def run_gates(worktree: Path, gates: list[Gate] | None = None) -> GateRun:
    ctx = GateContext(worktree, changed_files(worktree))
    outcome = GateRun()
    for gate in gates if gates is not None else GATES:
        command = gate.command(ctx) if gate.when(ctx.changed) else None
        if command is None:
            outcome.skipped.append(gate.name)
            continue
        result = run_gate(gate, ctx, command)
        outcome.results.append(result)
        if not result.passed:
            break
    return outcome


def parse_review(text: str) -> SecurityReview:
    start, end = text.find("{"), text.rfind("}")
    if start < 0 or end < start:
        return SecurityReview([], f"JSON을 찾지 못했다: {text[:200]!r}")
    try:
        raw = json.loads(text[start : end + 1])["findings"]
        findings = [
            SecurityFinding(
                file=str(f["file"]),
                line=int(f["line"]) if f.get("line") is not None else None,
                severity=str(f["severity"]),
                summary=str(f["summary"]),
                scenario=str(f["scenario"]),
            )
            for f in raw
        ]
    except (ValueError, KeyError, TypeError) as e:
        return SecurityReview([], f"JSON 형식이 맞지 않다: {e!r}")
    return SecurityReview(findings)


def security_review(worktree: Path, prompt: str) -> SecurityReview:
    try:
        return parse_review(run_claude(prompt, worktree, REVIEW_TOOLS, timeout=900))
    except ClaudeError as e:
        return SecurityReview([], str(e))


def report_markdown(run: GateRun, review: SecurityReview, fix_rounds: int) -> str:
    rows = ["| 검사 | 결과 | 시간 |", "|---|---|---|"]
    rows += [f"| {r.name} | {'통과' if r.passed else '실패'} | {r.seconds:g}초 |" for r in run.results]
    lines = ["## 검증 보고", "", *rows, ""]
    if run.skipped:
        lines += [f"바뀐 경로에 해당하지 않아 건너뛴 검사: {', '.join(run.skipped)}", ""]
    lines += [f"실패 후 Claude가 고친 횟수: {fix_rounds}", "", "### AI 보안 리뷰 (참고, 머지를 막지 않는다)", ""]
    if review.error:
        lines.append(f"리뷰 결과를 읽지 못했다. 사유: {review.error}")
    elif not review.findings:
        lines.append("지적 없음.")
    else:
        lines += ["| 심각도 | 위치 | 요약 | 시나리오 |", "|---|---|---|---|"]
        for f in review.findings:
            where = f"{f.file}:{f.line}" if f.line else f.file
            lines.append(f"| {f.severity} | `{where}` | {_cell(f.summary)} | {_cell(f.scenario)} |")
    return "\n".join(lines)


def _cell(text: str) -> str:
    return text.replace("|", "\\|").replace("\n", " ")

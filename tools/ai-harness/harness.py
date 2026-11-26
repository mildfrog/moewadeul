#!/usr/bin/env python3
from __future__ import annotations

import argparse
import dataclasses
import logging
import re
import time
import traceback
from dataclasses import dataclass
from datetime import datetime
from enum import Enum
from pathlib import Path
from typing import Callable

from chat import Approval, Chat, chat_from_env
from claude_runner import run_claude
from gates import BASE_REF, report_markdown, run_gates, security_review
from github import Comment, GitHub, Issue, git, push_branch

log = logging.getLogger("ai-harness")

HERE = Path(__file__).resolve().parent
BASE_BRANCH = "develop"

INTAKE = "claude"
AWAITING = "ai:awaiting-approval"
IMPLEMENTING = "ai:implementing"
VERIFYING = "ai:verifying"
PR_OPEN = "ai:pr-open"
BLOCKED = "ai:blocked"
REJECTED = "ai:rejected"
TERMINAL = {PR_OPEN, BLOCKED, REJECTED}

PLAN_TOOLS = "Read,Glob,Grep"
IMPLEMENT_TOOLS = (
    "Read,Edit,Write,Glob,Grep,Bash(./gradlew:*),Bash(npm:*),"
    "Bash(git status:*),Bash(git diff:*),Bash(git add:*),Bash(git commit:*)"
)
PR_BODY_TOOLS = "Read,Glob,Grep,Bash(git diff:*),Bash(git log:*)"

# 같은 실패를 세 번 이상 고치게 하면 테스트를 약하게 만들어 통과시키는 쪽으로 흐르기 쉬워 두 번에서 끊는다
MAX_FIX_ROUNDS = 2

PLAN_MARK = "<!-- ai-harness:plan -->"
PLAN_HEADER = "## 구현 계획"
DISCORD_MESSAGE = re.compile(r"<!-- ai-harness:discord-message=(\S+) -->")
DISCORD_APPROVAL_MARK = "<!-- ai-harness:discord-approval -->"
BLOCKED_MARK = "<!-- ai-harness:blocked -->"
APPROVAL_FOOTER = (
    "승인: 디스코드 ✅ 또는 이 이슈에 `/approve` 댓글, 반려: ❌ 또는 `/reject`. "
    "고를 것이 있으면 `/approve` 다음 줄부터 결정을 적는다. 그 내용이 구현 지시에 들어간다"
)


class Outcome(Enum):
    ADVANCE = "advance"
    WAIT = "wait"
    REJECT = "reject"


class Blocked(Exception):
    def __init__(self, reason: str, log_tail: str = ""):
        super().__init__(reason)
        self.reason = reason
        self.log_tail = log_tail


@dataclass(frozen=True)
class Step:
    run: Callable[["Harness", Issue], Outcome]
    next: str
    rejected: str | None = None


def render(name: str, **values: object) -> str:
    text = (HERE / "prompts" / f"{name}.md").read_text()
    return re.sub(r"\{(\w+)\}", lambda m: str(values[m.group(1)]) if m.group(1) in values else m.group(0), text)


def command_of(comment: Comment) -> str | None:
    lines = comment.body.strip().splitlines()
    first = lines[0].strip().lower() if lines else ""
    return first if first in ("/approve", "/reject") else None


class Harness:
    def __init__(self, repo_dir: Path, gh: GitHub, chat: Chat):
        self.repo_dir = repo_dir
        self.gh = gh
        self.chat = chat
        self.owner = gh.owner
        self.worktree_root = repo_dir.parent / f"{gh.name}-ai"
        self._warned: set[int] = set()

    def run_once(self) -> None:
        for issue in sorted(self.gh.open_issues(), key=lambda i: i.number):
            if issue.labels & set(STATES):
                try:
                    self.process(issue)
                except Exception:
                    log.exception("#%s 처리 중 오류. 다음 이슈로 넘어간다", issue.number)

    def process(self, issue: Issue) -> None:
        while True:
            state = current_state(issue.labels)
            if state is None or not self.accepts(issue, state):
                return
            step = STATES[state]
            try:
                outcome = step.run(self, issue)
            except Blocked as e:
                self.block(issue, e.reason, e.log_tail)
                return
            except Exception as e:
                log.exception("#%s %s 단계 실패", issue.number, state)
                self.block(issue, f"{type(e).__name__}: {e}", traceback.format_exc())
                return
            if outcome is Outcome.WAIT:
                return
            target = step.rejected if outcome is Outcome.REJECT else step.next
            self.transition(issue, target)
            issue = dataclasses.replace(issue, labels=frozenset({target}))
            if target in TERMINAL:
                return

    def accepts(self, issue: Issue, state: str) -> bool:
        if issue.author != self.owner:
            self._skip_once(issue, f"작성자 {issue.author}가 소유자가 아니다")
            return False
        if state == INTAKE:
            labeler = self.gh.last_labeler(issue.number, INTAKE)
            if labeler != self.owner:
                self._skip_once(issue, f"`{INTAKE}` 라벨을 붙인 {labeler}가 소유자가 아니다")
                return False
        return True

    def _skip_once(self, issue: Issue, reason: str) -> None:
        if issue.number not in self._warned:
            self._warned.add(issue.number)
            log.warning("#%s 받지 않는다: %s", issue.number, reason)

    def transition(self, issue: Issue, new: str) -> None:
        self.gh.add_label(issue.number, new)
        for old in sorted((set(STATES) | {INTAKE} | TERMINAL) - {new}):
            if old in issue.labels:
                self.gh.remove_label(issue.number, old)

    def block(self, issue: Issue, reason: str, log_tail: str) -> None:
        detail = log_tail.replace("```", "'''")[-12000:]
        body = f"{BLOCKED_MARK}\n## 하네스가 멈췄다\n\n사유: {reason}\n"
        if detail.strip():
            body += f"\n```\n{detail}\n```\n"
        body += "\n라벨을 `ai:blocked`로 바꿨다. 다시 돌리려면 원인을 해결하고 `claude` 라벨을 새로 붙인다."
        try:
            self.gh.add_comment(issue.number, body)
            self.transition(issue, BLOCKED)
        finally:
            self.notify(f"#{issue.number} {issue.title} 중단: {reason}\n{self.gh.issue_url(issue.number)}")

    def notify(self, text: str) -> str | None:
        try:
            return self.chat.send(text)
        except Exception:
            log.exception("디스코드 알림 실패")
            return None

    def owner_comments(self, number: int) -> list[Comment]:
        return [c for c in self.gh.comments(number) if c.author == self.owner]

    def plan_comment(self, number: int) -> Comment | None:
        plans = [c for c in self.owner_comments(number) if PLAN_MARK in c.body]
        return plans[-1] if plans else None

    def owner_notes(self, number: int) -> str:
        notes = []
        for c in self.owner_comments(number):
            if "<!-- ai-harness:" in c.body:
                continue
            body = c.body.strip()
            if command_of(c) is not None:
                body = body.partition("\n")[2].strip()
            if body:
                notes.append(body)
        return "\n\n---\n\n".join(notes) or "(없음)"

    def ensure_worktree(self, number: int) -> Path:
        path = self.worktree_root / f"issue-{number}"
        if path.exists():
            return path
        branch = f"claude/issue-{number}"
        git(self.repo_dir, "fetch", "origin", BASE_BRANCH)
        self.worktree_root.mkdir(parents=True, exist_ok=True)
        exists = git(self.repo_dir, "branch", "--list", branch).strip()
        if exists:
            git(self.repo_dir, "worktree", "add", str(path), branch)
        else:
            git(self.repo_dir, "worktree", "add", "-b", branch, str(path), BASE_REF)
        return path

    def commits_ahead(self, worktree: Path) -> int:
        return int(git(worktree, "rev-list", "--count", f"{BASE_REF}..HEAD").strip())

    def head(self, worktree: Path) -> str:
        return git(worktree, "rev-parse", "HEAD").strip()

    def plan(self, issue: Issue) -> Outcome:
        worktree = self.ensure_worktree(issue.number)
        if self.plan_comment(issue.number) is not None:
            return Outcome.ADVANCE
        prompt = render(
            "plan",
            issue_number=issue.number,
            issue_title=issue.title,
            issue_body=issue.body,
            issue_comments=self.owner_notes(issue.number),
        )
        plan = run_claude(prompt, worktree, PLAN_TOOLS, timeout=900).strip()
        summary = plan[:700] + ("..." if len(plan) > 700 else "")
        message_id = self.request_approval(f"#{issue.number} {issue.title}\n계획 승인 요청\n{self.gh.issue_url(issue.number)}\n\n{summary}")
        markers = [PLAN_MARK] + ([f"<!-- ai-harness:discord-message={message_id} -->"] if message_id else [])
        self.gh.add_comment(issue.number, f"{PLAN_HEADER}\n\n{plan}\n\n---\n" + "\n".join(markers) + f"\n{APPROVAL_FOOTER}")
        return Outcome.ADVANCE

    def request_approval(self, text: str) -> str | None:
        try:
            return self.chat.request_approval(text)
        except Exception:
            log.exception("디스코드 승인 요청 실패. 이슈 댓글로만 승인할 수 있다")
            return None

    def wait_approval(self, issue: Issue) -> Outcome:
        plan = self.plan_comment(issue.number)
        if plan is None:
            raise Blocked("승인 대기 중인데 계획 댓글이 없다")
        commands = [command_of(c) for c in self.owner_comments(issue.number) if c.id > plan.id]
        commands = [c for c in commands if c]
        if commands:
            return Outcome.REJECT if commands[-1] == "/reject" else Outcome.ADVANCE
        found = DISCORD_MESSAGE.search(plan.body)
        if not found:
            return Outcome.WAIT
        try:
            state = self.chat.approval_state(found.group(1))
        except Exception:
            log.exception("디스코드 반응을 읽지 못했다")
            return Outcome.WAIT
        if state is Approval.PENDING:
            return Outcome.WAIT
        verb = "승인" if state is Approval.APPROVED else "반려"
        if not any(DISCORD_APPROVAL_MARK in c.body for c in self.owner_comments(issue.number)):
            stamp = datetime.now().astimezone().strftime("%Y-%m-%d %H:%M:%S %Z")
            self.gh.add_comment(issue.number, f"{DISCORD_APPROVAL_MARK}\n디스코드에서 소유자가 {verb}했다({stamp}).")
        return Outcome.ADVANCE if state is Approval.APPROVED else Outcome.REJECT

    def implement(self, issue: Issue) -> Outcome:
        worktree = self.ensure_worktree(issue.number)
        if self.commits_ahead(worktree) > 0:
            return Outcome.ADVANCE
        plan = self.plan_comment(issue.number)
        approved = plan.body.rsplit("\n\n---\n", 1)[0].removeprefix(PLAN_HEADER).strip() if plan else ""
        prompt = render(
            "implement",
            issue_number=issue.number,
            issue_title=issue.title,
            issue_body=issue.body,
            plan=approved,
            issue_comments=self.owner_notes(issue.number),
        )
        run_claude(prompt, worktree, IMPLEMENT_TOOLS, permission_mode="acceptEdits", timeout=3600)
        if self.commits_ahead(worktree) == 0:
            raise Blocked("Claude가 구현을 끝냈지만 커밋이 없다")
        return Outcome.ADVANCE

    def verify(self, issue: Issue) -> Outcome:
        worktree = self.ensure_worktree(issue.number)
        branch = f"claude/issue-{issue.number}"
        fix_rounds = 0
        while True:
            outcome = run_gates(worktree)
            failed = outcome.failed
            if failed is None:
                break
            if fix_rounds >= MAX_FIX_ROUNDS:
                raise Blocked(f"{failed.name} 검사가 재수정 {fix_rounds}회 뒤에도 실패한다", failed.log_tail)
            before = self.head(worktree)
            prompt = render(
                "fix",
                issue_number=issue.number,
                issue_title=issue.title,
                failed_gate=failed.name,
                failure_log=failed.log_tail,
            )
            run_claude(prompt, worktree, IMPLEMENT_TOOLS, permission_mode="acceptEdits", timeout=1800)
            if self.head(worktree) == before:
                raise Blocked(f"{failed.name} 검사 실패를 고치라고 했지만 새 커밋이 없다", failed.log_tail)
            fix_rounds += 1

        review = security_review(worktree, render("security_review", issue_number=issue.number, issue_title=issue.title, diff_base=BASE_REF))
        push_branch(worktree, branch)
        pr = self.gh.find_pr(branch) or self.open_pr(issue, worktree, branch)
        sha = self.head(worktree)
        report_mark = f"<!-- ai-harness:report sha={sha} -->"
        if not any(report_mark in c.body for c in self.gh.comments(pr["number"])):
            self.gh.add_comment(pr["number"], f"{report_mark}\n{report_markdown(outcome, review, fix_rounds)}")
        self.gh.set_status(sha, "success", "결정적 검사 통과. AI 보안 리뷰는 참고용", pr["url"])
        self.notify(f"#{issue.number} {issue.title} PR이 열렸다\n{pr['url']}")
        return Outcome.ADVANCE

    def open_pr(self, issue: Issue, worktree: Path, branch: str) -> dict:
        body = run_claude(
            render("pr_body", issue_number=issue.number, issue_title=issue.title, diff_base=BASE_REF),
            worktree,
            PR_BODY_TOOLS,
            timeout=900,
        ).strip()
        if f"Closes #{issue.number}" not in body:
            body = f"Closes #{issue.number}\n\n{body}"
        subjects = git(worktree, "log", f"{BASE_REF}..HEAD", "--reverse", "--format=%s").splitlines()
        return self.gh.create_pr(branch, BASE_BRANCH, subjects[0] if subjects else issue.title, body)


STATES: dict[str, Step] = {
    INTAKE: Step(Harness.plan, next=AWAITING),
    AWAITING: Step(Harness.wait_approval, next=IMPLEMENTING, rejected=REJECTED),
    IMPLEMENTING: Step(Harness.implement, next=VERIFYING),
    VERIFYING: Step(Harness.verify, next=PR_OPEN),
}
ORDER = [INTAKE, AWAITING, IMPLEMENTING, VERIFYING]


def current_state(labels: frozenset[str]) -> str | None:
    # 멈춘 이슈에 소유자가 claude 라벨을 다시 붙이면 재시작이다. 승인된 계획과 커밋은 그대로 이어 쓴다.
    if INTAKE in labels:
        return INTAKE
    if labels & TERMINAL:
        return None
    present = [s for s in ORDER if s in labels]
    return present[-1] if present else None


def main() -> None:
    parser = argparse.ArgumentParser(prog="harness")
    sub = parser.add_subparsers(dest="command", required=True)
    run = sub.add_parser("run")
    run.add_argument("--once", action="store_true")
    run.add_argument("--interval", type=int, default=30)
    run.add_argument("--repo-dir", type=Path, default=HERE.parents[1])
    args = parser.parse_args()

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    harness = Harness(args.repo_dir.resolve(), GitHub.discover(args.repo_dir), chat_from_env(HERE / ".env"))
    while True:
        try:
            harness.run_once()
        except Exception:
            log.exception("이번 주기를 건너뛴다")
        if args.once:
            return
        try:
            time.sleep(args.interval)
        except KeyboardInterrupt:
            return


if __name__ == "__main__":
    main()

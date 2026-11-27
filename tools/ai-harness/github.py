from __future__ import annotations

import json
import subprocess
import urllib.parse
from dataclasses import dataclass
from pathlib import Path
from typing import Any


class CommandError(RuntimeError):
    def __init__(self, command: list[str], returncode: int, output: str):
        super().__init__(f"{' '.join(command[:4])} 실패({returncode}): {output[-500:]}")
        self.command = command
        self.returncode = returncode
        self.output = output


def run(command: list[str], cwd: Path | str | None = None, input: str | None = None, timeout: int | None = 300) -> str:
    done = subprocess.run(command, cwd=cwd, input=input, capture_output=True, text=True, timeout=timeout)
    if done.returncode != 0:
        raise CommandError(command, done.returncode, (done.stdout + done.stderr))
    return done.stdout


def git(cwd: Path | str, *args: str, timeout: int | None = 300) -> str:
    return run(["git", *args], cwd=cwd, timeout=timeout)


@dataclass(frozen=True)
class Comment:
    id: int
    author: str
    body: str


@dataclass(frozen=True)
class Issue:
    number: int
    title: str
    body: str
    author: str
    labels: frozenset[str]


class GitHub:
    def __init__(self, owner: str, name: str):
        self.owner = owner
        self.name = name
        self.slug = f"{owner}/{name}"

    @classmethod
    def discover(cls, cwd: Path | str) -> "GitHub":
        info = json.loads(run(["gh", "repo", "view", "--json", "owner,name"], cwd=cwd))
        return cls(info["owner"]["login"], info["name"])

    def issue_url(self, number: int) -> str:
        return f"https://github.com/{self.slug}/issues/{number}"

    def _api(self, path: str, *fields: str, method: str | None = None, paginate: bool = False) -> Any:
        command = ["gh", "api", f"repos/{self.slug}/{path}"]
        if method:
            command += ["-X", method]
        if paginate:
            command += ["--paginate", "--slurp"]
        for field in fields:
            command += ["-f", field]
        out = run(command)
        if not out.strip():
            return None
        data = json.loads(out)
        if paginate:
            return [item for page in data for item in page]
        return data

    @staticmethod
    def _issue(raw: dict) -> Issue:
        return Issue(
            number=raw["number"],
            title=raw["title"],
            body=raw.get("body") or "",
            author=raw["user"]["login"],
            labels=frozenset(label["name"] for label in raw["labels"]),
        )

    def open_issues(self) -> list[Issue]:
        raw = self._api("issues?state=open&per_page=100", paginate=True)
        return [self._issue(item) for item in raw if "pull_request" not in item]

    def issue(self, number: int) -> Issue:
        return self._issue(self._api(f"issues/{number}"))

    def comments(self, number: int) -> list[Comment]:
        raw = self._api(f"issues/{number}/comments?per_page=100", paginate=True)
        return [Comment(c["id"], c["user"]["login"], c["body"] or "") for c in raw]

    def add_comment(self, number: int, body: str) -> None:
        self._api(f"issues/{number}/comments", f"body={body}")

    def last_labeler(self, number: int, label: str) -> str | None:
        events = self._api(f"issues/{number}/timeline?per_page=100", paginate=True)
        actors = [
            e["actor"]["login"]
            for e in events
            if e.get("event") == "labeled" and e["label"]["name"] == label and e.get("actor")
        ]
        return actors[-1] if actors else None

    def ensure_label(self, label: str, color: str = "6f42c1") -> None:
        run(["gh", "label", "create", label, "-R", self.slug, "--color", color, "--force"])

    def add_label(self, number: int, label: str) -> None:
        self.ensure_label(label)
        self._api(f"issues/{number}/labels", f"labels[]={label}")

    def remove_label(self, number: int, label: str) -> None:
        try:
            self._api(f"issues/{number}/labels/{urllib.parse.quote(label, safe='')}", method="DELETE")
        except CommandError as e:
            if "404" not in e.output and "Label does not exist" not in e.output:
                raise

    def find_pr(self, branch: str) -> dict | None:
        out = run(["gh", "pr", "list", "-R", self.slug, "--head", branch, "--state", "open", "--json", "number,url"])
        prs = json.loads(out)
        return prs[0] if prs else None

    def create_pr(self, branch: str, base: str, title: str, body: str) -> dict:
        run(["gh", "pr", "create", "-R", self.slug, "--head", branch, "--base", base, "--title", title, "--body-file", "-"], input=body)
        pr = self.find_pr(branch)
        if pr is None:
            raise RuntimeError(f"PR을 만들었는데 {branch}로 찾을 수 없다")
        return pr

    def set_status(self, sha: str, state: str, description: str, target_url: str) -> None:
        self._api(
            f"statuses/{sha}",
            f"state={state}",
            "context=ai-gate",
            f"description={description[:140]}",
            f"target_url={target_url}",
        )


def push_branch(worktree: Path, branch: str) -> None:
    git(worktree, "push", "origin", f"{branch}:{branch}", timeout=300)

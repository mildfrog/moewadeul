import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

HARNESS_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(HARNESS_DIR))

from chat import Approval, Chat  # noqa: E402
from github import GitHub  # noqa: E402
from harness import Harness  # noqa: E402

GIT_ENV = {
    "GIT_AUTHOR_NAME": "t", "GIT_AUTHOR_EMAIL": "t@example.com",
    "GIT_COMMITTER_NAME": "t", "GIT_COMMITTER_EMAIL": "t@example.com",
    "GIT_CONFIG_GLOBAL": os.devnull, "GIT_CONFIG_SYSTEM": os.devnull,
}


class FakeChat(Chat):
    def __init__(self):
        self.sent: list[str] = []
        self.approval = Approval.PENDING

    def send(self, text):
        self.sent.append(text)
        return "m-notice"

    def request_approval(self, text):
        self.sent.append(text)
        return "m-approval"

    def approval_state(self, message_id):
        return self.approval


def sh(cwd, *command):
    subprocess.run(command, cwd=cwd, check=True, capture_output=True, text=True)


class WorldTest(unittest.TestCase):
    """가짜 gh·claude·docker를 PATH 앞에 두고, 로컬 bare 저장소를 origin으로 쓰는 실험실."""

    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.root = Path(tmp.name).resolve()
        bin_dir = self.root / "bin"
        bin_dir.mkdir()
        fake = Path(__file__).resolve().parent / "fake_tools.py"
        for tool in ("gh", "claude", "docker"):
            shim = bin_dir / tool
            shim.write_text(f'#!/bin/sh\nexec python3 "{fake}" {tool} "$@"\n')
            shim.chmod(0o755)

        self.calls_file = self.root / "calls.log"
        self.calls_file.touch()
        self.state_file = self.root / "gh-state.json"
        self.state_file.write_text(json.dumps({"issues": {}, "comments": {}, "prs": [], "statuses": [], "labels": [], "next_id": 1000}))

        origin = self.root / "origin.git"
        self.repo = self.root / "moewadeul"
        env = {**os.environ, **GIT_ENV}
        subprocess.run(["git", "init", "-q", "--bare", "-b", "develop", str(origin)], check=True, env=env)
        subprocess.run(["git", "clone", "-q", str(origin), str(self.repo)], check=True, env=env, capture_output=True)
        gradlew = self.repo / "backend" / "gradlew"
        gradlew.parent.mkdir()
        gradlew.write_text(f'#!/bin/sh\nexec python3 "{fake}" gradlew "$@"\n')
        gradlew.chmod(0o755)
        (self.repo / "README.md").write_text("x\n")

        patcher = mock.patch.dict(
            os.environ,
            {
                **GIT_ENV,
                "PATH": f"{bin_dir}{os.pathsep}{os.environ['PATH']}",
                "FAKE_CALLS": str(self.calls_file),
                "FAKE_GH_STATE": str(self.state_file),
            },
        )
        patcher.start()
        self.addCleanup(patcher.stop)
        sh(self.repo, "git", "add", ".")
        sh(self.repo, "git", "commit", "-q", "-m", "init")
        sh(self.repo, "git", "push", "-q", "origin", "develop")

        self.chat = FakeChat()

    def harness(self):
        return Harness(self.repo, GitHub.discover(self.repo), self.chat)

    def state(self):
        return json.loads(self.state_file.read_text())

    def edit_state(self, change):
        state = self.state()
        change(state)
        self.state_file.write_text(json.dumps(state))

    def calls(self, prefix=""):
        return [line for line in self.calls_file.read_text().splitlines() if line.startswith(prefix)]

    def seed_issue(self, number=1, author="mildfrog", labeler="mildfrog", labels=("claude",)):
        def add(state):
            state["issues"][str(number)] = {
                "number": number,
                "title": "장소 목록 개선",
                "body": "본문",
                "user": {"login": author},
                "labels": [{"name": n} for n in labels],
                "timeline": [{"event": "labeled", "label": {"name": n}, "actor": {"login": labeler}} for n in labels],
            }
            state["comments"][str(number)] = []

        self.edit_state(add)

    def comment(self, number, body, user="mildfrog"):
        def add(state):
            state["comments"][str(number)].append({"id": state["next_id"] + 1, "user": {"login": user}, "body": body})
            state["next_id"] += 1

        self.edit_state(add)

    def labels(self, number=1):
        return {label["name"] for label in self.state()["issues"][str(number)]["labels"]}

    def comment_bodies(self, number=1):
        return [c["body"] for c in self.state()["comments"][str(number)]]

    def only_pr(self):
        [pr] = self.state()["prs"]
        return pr, self.comment_bodies(pr["number"])

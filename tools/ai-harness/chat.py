from __future__ import annotations

import json
import logging
import os
import time
import urllib.error
import urllib.parse
import urllib.request
from enum import Enum
from pathlib import Path

log = logging.getLogger("ai-harness.chat")

APPROVE = "✅"
REJECT = "❌"


class Approval(Enum):
    PENDING = "pending"
    APPROVED = "approved"
    REJECTED = "rejected"


class Chat:
    def send(self, text: str) -> str | None:
        raise NotImplementedError

    def request_approval(self, text: str) -> str | None:
        raise NotImplementedError

    def approval_state(self, message_id: str) -> Approval:
        raise NotImplementedError


class NullChat(Chat):
    def send(self, text: str) -> str | None:
        log.info("디스코드 설정이 없어 알림을 보내지 않는다: %s", text.splitlines()[0] if text else "")
        return None

    def request_approval(self, text: str) -> str | None:
        return self.send(text)

    def approval_state(self, message_id: str) -> Approval:
        return Approval.PENDING


class DiscordChat(Chat):
    def __init__(self, token: str, channel_id: str, owner_id: str, base_url: str = "https://discord.com/api/v10"):
        self.token = token
        self.channel_id = channel_id
        self.owner_id = owner_id
        self.base_url = base_url

    def _call(self, method: str, path: str, body: dict | None = None) -> object:
        data = json.dumps(body).encode() if body is not None else None
        for attempt in range(3):
            request = urllib.request.Request(self.base_url + path, data=data, method=method)
            request.add_header("Authorization", f"Bot {self.token}")
            request.add_header("Content-Type", "application/json")
            # 디스코드는 형식이 맞는 User-Agent가 없는 요청을 막는다
            request.add_header("User-Agent", "DiscordBot (https://github.com/mildfrog/moewadeul, 1.0)")
            try:
                with urllib.request.urlopen(request, timeout=20) as response:
                    raw = response.read()
                    return json.loads(raw) if raw else None
            except urllib.error.HTTPError as e:
                if e.code == 429 and attempt < 2:
                    time.sleep(min(float(json.loads(e.read() or b"{}").get("retry_after", 1)), 10))
                    continue
                raise
        return None

    def send(self, text: str) -> str | None:
        message = self._call("POST", f"/channels/{self.channel_id}/messages", {"content": text[:1900]})
        return str(message["id"])

    def request_approval(self, text: str) -> str | None:
        message_id = self.send(text)
        for emoji in (APPROVE, REJECT):
            self._call("PUT", f"/channels/{self.channel_id}/messages/{message_id}/reactions/{urllib.parse.quote(emoji)}/@me")
        return message_id

    def _reactors(self, message_id: str, emoji: str) -> set[str]:
        users = self._call("GET", f"/channels/{self.channel_id}/messages/{message_id}/reactions/{urllib.parse.quote(emoji)}?limit=100")
        return {str(u["id"]) for u in users or []}

    def approval_state(self, message_id: str) -> Approval:
        if self.owner_id in self._reactors(message_id, REJECT):
            return Approval.REJECTED
        if self.owner_id in self._reactors(message_id, APPROVE):
            return Approval.APPROVED
        return Approval.PENDING


def load_env(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    if path.exists():
        for line in path.read_text().splitlines():
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                values[key.strip()] = value.strip().strip("'\"")
    return values


def chat_from_env(env_file: Path) -> Chat:
    values = {**load_env(env_file), **os.environ}
    token, channel, owner = (values.get(k) for k in ("DISCORD_BOT_TOKEN", "DISCORD_CHANNEL_ID", "DISCORD_OWNER_ID"))
    if token and channel and owner:
        return DiscordChat(token, channel, owner)
    return NullChat()

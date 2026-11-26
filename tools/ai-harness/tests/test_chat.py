import json
import threading
import unittest
import urllib.parse
from http.server import BaseHTTPRequestHandler, HTTPServer

from support import HARNESS_DIR  # noqa: F401
from chat import Approval, DiscordChat, NullChat, chat_from_env

OWNER = "111"


class FakeDiscord(BaseHTTPRequestHandler):
    requests: list = []
    reactions: dict = {}

    def _reply(self, payload):
        data = json.dumps(payload).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(data)

    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        FakeDiscord.requests.append(("POST", self.path, self.headers["Authorization"], body))
        self._reply({"id": "42"})

    def do_PUT(self):
        FakeDiscord.requests.append(("PUT", urllib.parse.unquote(self.path), self.headers["Authorization"], None))
        self._reply(None)

    def do_GET(self):
        emoji = urllib.parse.unquote(self.path.split("/reactions/")[1].split("?")[0])
        self._reply([{"id": u} for u in FakeDiscord.reactions.get(emoji, [])])

    def log_message(self, *args):
        pass


class DiscordChatTest(unittest.TestCase):
    def setUp(self):
        FakeDiscord.requests, FakeDiscord.reactions = [], {}
        server = HTTPServer(("127.0.0.1", 0), FakeDiscord)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)
        self.chat = DiscordChat("tok", "chan", OWNER, base_url=f"http://127.0.0.1:{server.server_port}")

    def test_승인_요청은_메시지를_보내고_두_반응을_단다(self):
        self.assertEqual(self.chat.request_approval("계획"), "42")
        methods = [(m, p) for m, p, _, _ in FakeDiscord.requests]
        self.assertEqual(methods[0], ("POST", "/channels/chan/messages"))
        self.assertIn(("PUT", "/channels/chan/messages/42/reactions/✅/@me"), methods)
        self.assertIn(("PUT", "/channels/chan/messages/42/reactions/❌/@me"), methods)
        self.assertTrue(all(auth == "Bot tok" for _, _, auth, _ in FakeDiscord.requests))

    def test_소유자의_체크_반응만_승인으로_본다(self):
        FakeDiscord.reactions = {"✅": ["999", "bot"]}
        self.assertEqual(self.chat.approval_state("42"), Approval.PENDING)
        FakeDiscord.reactions = {"✅": ["999", OWNER]}
        self.assertEqual(self.chat.approval_state("42"), Approval.APPROVED)

    def test_소유자의_엑스_반응은_반려이고_체크보다_우선한다(self):
        FakeDiscord.reactions = {"✅": [OWNER], "❌": [OWNER]}
        self.assertEqual(self.chat.approval_state("42"), Approval.REJECTED)


class ConfigTest(unittest.TestCase):
    def test_설정이_없으면_아무것도_하지_않는_구현으로_바꾼다(self):
        import os, tempfile
        from pathlib import Path
        from unittest import mock

        with tempfile.TemporaryDirectory() as tmp, mock.patch.dict(os.environ, {}, clear=True):
            chat = chat_from_env(Path(tmp) / ".env")
        self.assertIsInstance(chat, NullChat)
        self.assertEqual(chat.approval_state("x"), Approval.PENDING)


if __name__ == "__main__":
    unittest.main()

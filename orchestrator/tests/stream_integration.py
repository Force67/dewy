"""Check SSE keepalives and change notifications against a built orchestrator."""
import json
import os
from pathlib import Path
import re
import socket
import subprocess
import tempfile
import time
import unittest

ORCHESTRATOR = Path(__file__).resolve().parents[1] / "build/orchestrator"
TOKEN = "dewy-stream-test-token"


@unittest.skipUnless(ORCHESTRATOR.exists(), "requires a built orchestrator")
class StreamTest(unittest.TestCase):
    def test_idle_keepalive_and_change_notification(self):
        with tempfile.TemporaryDirectory(prefix="dewy-stream-") as root:
            log_path = Path(root)/"server.log"
            with log_path.open("w+") as log:
                server = subprocess.Popen(
                    [str(ORCHESTRATOR), "--host", "127.0.0.1", "--port", "0",
                     "--worker-port", "0", "--data", str(Path(root)/"state")],
                    env=dict(os.environ, DEWY_TOKEN=TOKEN), stdout=log, stderr=log)
                try:
                    until = time.monotonic()+10
                    match = None
                    while time.monotonic() < until:
                        match = re.search(r"app on 127\.0\.0\.1:(\d+), workers on 127\.0\.0\.1:(\d+)",
                                          log_path.read_text())
                        if match:
                            break
                        self.assertIsNone(server.poll(), log_path.read_text())
                        time.sleep(.03)
                    self.assertIsNotNone(match, log_path.read_text())
                    app_port, worker_port = map(int, match.groups())
                    with socket.create_connection(("127.0.0.1", app_port), timeout=5) as stream:
                        stream.sendall((f"GET /api/stream?since=0 HTTP/1.1\r\nHost: localhost\r\n"
                                        f"Authorization: Bearer {TOKEN}\r\n\r\n").encode())
                        stream.settimeout(25)
                        received = b""
                        while b"\r\n\r\n" not in received:
                            received += stream.recv(65536)
                        self.assertIn(b"200 OK", received)
                        start = time.monotonic()
                        while b": ping\n\n" not in received:
                            chunk = stream.recv(65536)
                            self.assertTrue(chunk, "stream closed before its idle keepalive")
                            received += chunk
                        self.assertLess(time.monotonic()-start, 24)
                        # A box change should wake the same waiting stream immediately.
                        with socket.create_connection(("127.0.0.1", worker_port), timeout=5) as worker:
                            worker.sendall(json.dumps(dict(type="hello", token=TOKEN, box="stream-test",
                                                           agents=["claude"], running=[])).encode()+b"\n")
                            stream.settimeout(3)
                            changed = b""
                            while b"event: box\n" not in changed:
                                chunk = stream.recv(65536)
                                self.assertTrue(chunk, "stream closed before the box notification")
                                changed += chunk
                            self.assertIn(b"stream-test", changed)
                finally:
                    server.kill()
                    server.wait(timeout=5)


if __name__ == "__main__":
    unittest.main()

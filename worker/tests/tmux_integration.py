"""Exercise the real worker and tmux with local fake agents, without credentials.

Run after `akiko build worker`: python3 worker/tests/tmux_integration.py
"""
import base64
import hashlib
import json
import os
from pathlib import Path
import shutil
import signal
import socket
import subprocess
import tempfile
import time
import unittest

WORKER = Path(__file__).resolve().parents[1] / "build/worker"
TMUX = shutil.which("tmux")

FAKE_AGENT = r'''#!/usr/bin/python3
import base64, json, os, pathlib, signal, subprocess, sys, time
agent = pathlib.Path(sys.argv[0]).name
args = sys.argv[1:]
if args and args[0] == 'models':
    print('mock/model'); sys.exit(0)
if args and args[0] == 'db':
    print('[]'); sys.exit(0)
stdin = sys.stdin.read() if '--input-format' in args else ''
text = json.loads(stdin)['message']['content'][-1]['text'] if stdin else (args[args.index('-p')+1] if agent == 'claude' else args[-1])
config = json.loads(text)
root = pathlib.Path(os.environ['TEST_ROOT'])
images = []
for flag in ('-i', '-f'):
    for i, arg in enumerate(args):
        if arg == flag: images.append(base64.b64encode(pathlib.Path(args[i+1]).read_bytes()).decode())
call = dict(agent=agent, args=args, text=text, stdin=stdin, images=images,
            pid=os.getpid(), cwd=os.getcwd(), value=os.environ.get('TEST_VALUE'))
with (root/'calls.jsonl').open('a') as f: f.write(json.dumps(call)+'\n')
def emit(value, final=False): print(json.dumps(value), end='' if final and config.get('no_newline') else '\n', flush=True)
if agent == 'claude':
    emit(dict(type='system', subtype='init', session_id='claude-session'))
    emit(dict(type='assistant', message=dict(content=[dict(type='text', text='started')])))
elif agent == 'codex':
    emit(dict(type='thread.started', thread_id='codex-session'))
    emit(dict(type='item.completed', item=dict(id='start', type='agent_message', text='started')))
else:
    emit(dict(type='text', sessionID='opencode-session', part=dict(id='start', text='started')))
if config.get('exit'):
    print('fake agent failure detail', file=sys.stderr, flush=True)
    sys.exit(config['exit'])
if config.get('child'):
    signal.signal(signal.SIGTERM, signal.SIG_IGN)
    child = subprocess.Popen([sys.executable, '-c', 'import signal,time; signal.signal(signal.SIGTERM,signal.SIG_IGN); time.sleep(60)'])
    (root/'child.pid').write_text(str(child.pid))
gate = config.get('gate')
while gate and not (root/gate).exists(): time.sleep(.03)
for n in range(config.get('burst', 0)):
    emit(dict(type='assistant', message=dict(content=[dict(type='text', text='burst '+str(n)+' x'*50)])))
if config.get('rollout_total'):
    rollout = pathlib.Path((root/'rollout.path').read_text())
    with rollout.open('a') as f:
        f.write(json.dumps(dict(type='event_msg', payload=dict(type='token_count', info=dict(total_token_usage=dict(total_tokens=config['rollout_total'])))))+'\n')
if agent == 'claude':
    emit(dict(type='assistant', message=dict(id='answer', content=[dict(type='text', text='finished')], usage=dict(input_tokens=100))))
    emit(dict(type='result', usage=dict(input_tokens=100), total_cost_usd=.25), final=True)
elif agent == 'codex':
    emit(dict(type='turn.completed', usage=dict(input_tokens=100, output_tokens=10)))
else:
    emit(dict(type='step_finish', sessionID='opencode-session', part=dict(tokens=dict(total=100), cost=.25)))
'''


def wait_for(predicate, timeout=10):
    until = time.monotonic() + timeout
    while time.monotonic() < until:
        value = predicate()
        if value:
            return value
        time.sleep(.03)
    raise AssertionError("timed out waiting for condition")


def alive(pid):
    try:
        return Path(f"/proc/{pid}/stat").read_text().rsplit(")", 1)[1].split()[0] != "Z"
    except FileNotFoundError:
        return False


@unittest.skipUnless(TMUX and WORKER.exists(), "requires tmux and a built worker")
class TmuxWorkerTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="dewy tmux test ")
        self.root = Path(self.temp.name)
        self.bin = self.root / "bin"
        self.bin.mkdir()
        for agent in ("claude", "codex", "opencode"):
            script = self.bin / agent
            script.write_text(FAKE_AGENT)
            script.chmod(0o755)
        self.data = self.root / "state"
        self.env = dict(os.environ, HOME=str(self.root), CODEX_HOME=str(self.root/".codex"),
                        PATH=f"{self.bin}:/usr/bin:/bin", TEST_ROOT=str(self.root),
                        TEST_VALUE="before restart", DEWY_TOKEN="integration-test")
        self.listener = socket.socket()
        self.listener.bind(("127.0.0.1", 0))
        self.listener.listen()
        self.listener.settimeout(10)
        self.args = [str(WORKER), "--orchestrator", f"127.0.0.1:{self.listener.getsockname()[1]}",
                     "--data", str(self.data), "--name", "tmux-test"]
        digest = hashlib.sha256(str(self.data).encode()).hexdigest()[:24]
        self.tmux_socket = Path(tempfile.gettempdir()) / f"dewy-worker-{os.getuid()}" / f"tmux-{digest}"
        self.worker = None
        self.conn = None
        self.log = (self.root/"worker.log").open("w+")
        self.events = []
        self.buffer = b""

    def tearDown(self):
        self.kill_worker()
        subprocess.run([TMUX, "-S", str(self.tmux_socket), "kill-server"],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        for call in self.calls():
            if alive(call['pid']):
                try:
                    os.killpg(call['pid'], signal.SIGKILL)
                except ProcessLookupError:
                    pass
        self.listener.close()
        self.log.close()
        self.temp.cleanup()

    def start_worker(self):
        self.worker = subprocess.Popen(self.args, env=self.env, stdout=self.log, stderr=self.log)
        self.conn, _ = self.listener.accept()
        self.conn.settimeout(.1)
        self.events = []
        self.buffer = b""
        return self.receive(lambda e: e.get("type") == "hello")

    def kill_worker(self):
        if self.worker:
            self.worker.kill()
            self.worker.wait(timeout=5)
            self.worker = None
        if self.conn:
            self.conn.close()
            self.conn = None

    def send(self, value):
        self.conn.sendall(json.dumps(value).encode()+b"\n")

    def turn(self, task="1", turn="t1", agent="claude", **config):
        msg = dict(type="turn", task=task, turn=turn, agent=agent, project=str(self.root),
                   text=json.dumps(config), session="", model="mock/model", yolo=False)
        self.send(msg)
        return msg

    def receive(self, predicate, timeout=10):
        end = time.monotonic()+timeout
        while time.monotonic() < end:
            while b"\n" in self.buffer:
                line, self.buffer = self.buffer.split(b"\n", 1)
                event = json.loads(line)
                self.events.append(event)
                if predicate(event):
                    return event
            try:
                chunk = self.conn.recv(65536)
                if not chunk:
                    self.fail("worker disconnected: "+(self.root/"worker.log").read_text())
                self.buffer += chunk
            except socket.timeout:
                pass
        self.fail("event timed out: "+repr(self.events)+"\n"+(self.root/"worker.log").read_text())

    def started(self, task="1"):
        return self.receive(lambda e: e.get("type") == "entry" and e.get("task") == task
                            and e.get("entry", {}).get("text") == "started")

    def ended(self, task="1", state="idle"):
        return self.receive(lambda e: e.get("type") == "state" and e.get("task") == task
                            and e.get("state") == state)

    def calls(self):
        path = self.root/"calls.jsonl"
        return [json.loads(line) for line in path.read_text().splitlines()] if path.exists() else []

    def test_restart_recovers_running_and_queued(self):
        self.start_worker()
        self.turn(gate="release-first")
        first = self.started()
        self.turn(turn="t2", note="queued")
        wait_for(lambda: len(list(self.data.glob("*.json"))) == 2)
        calls = self.calls()
        self.assertEqual(len(calls), 1)
        sessions = subprocess.check_output([TMUX, "-S", str(self.tmux_socket), "list-sessions"])
        self.assertIn(b"dewy-31-7431", sessions)
        self.kill_worker()
        self.assertTrue(alive(calls[0]['pid']))
        self.env['TEST_VALUE'] = 'after restart'
        hello = self.start_worker()
        self.assertIn("1", hello['running'])
        replay = self.started()
        self.assertEqual(first['entry']['id'], replay['entry']['id'])
        self.assertEqual(len(self.calls()), 1)
        (self.root/"release-first").touch()
        self.ended()
        self.started()
        self.ended()
        wait_for(lambda: not list(self.data.glob("*.json")))
        calls = self.calls()
        self.assertEqual(len(calls), 2)
        self.assertIn("--resume", calls[1]['args'])
        self.assertIn("claude-session", calls[1]['args'])
        self.assertEqual(calls[1]['value'], 'after restart')

    def test_finished_while_worker_is_down_is_replayed(self):
        self.start_worker()
        self.turn(gate="release", burst=2000)
        self.started()
        self.kill_worker()
        (self.root/"release").touch()
        wait_for(lambda: list(self.data.glob("*.run/exit")))
        self.start_worker()
        self.ended()
        self.assertEqual(len(self.calls()), 1)
        reports = [e for e in self.events if e.get('type') == 'spend']
        self.assertEqual(reports[-1]['turn'], 't1')
        self.assertEqual(reports[-1]['tokens'], 100)
        self.assertEqual(reports[-1]['cost'], .25)

    def test_stop_survives_restart_and_kills_tools(self):
        self.start_worker()
        self.turn(gate="never", child=True)
        self.started()
        child = int(wait_for(lambda: (self.root/'child.pid').read_text() if (self.root/'child.pid').exists() else None))
        self.turn(turn="t2", note="must never run")
        wait_for(lambda: len(list(self.data.glob("*.json"))) == 2)
        self.send(dict(type='stop', task='1'))
        wait_for(lambda: list(self.data.glob('*.stop')))
        self.kill_worker()
        self.start_worker()
        self.ended(state='stopped')
        wait_for(lambda: not alive(child))
        self.assertEqual(len(self.calls()), 1)
        self.assertFalse(alive(self.calls()[0]['pid']))

    def test_all_adapters_preserve_literal_arguments_and_images(self):
        self.start_worker()
        payload = "quotes ' \" $(touch INJECTED) `touch ALSO_INJECTED`\n--dash"
        for n, agent in enumerate(('claude', 'codex', 'opencode'), 1):
            task = str(n)
            msg = dict(type='turn', task=task, turn='t1', agent=agent, project=str(self.root),
                       text=json.dumps(dict(note=payload)), session='', model='mock/model', yolo=True,
                       images=[dict(id='image', type='image/png', data='YWJj')])
            self.send(msg)
            self.ended(task)
            call = self.calls()[-1]
            self.assertEqual(call['text'], msg['text'])
            self.assertEqual(call['cwd'], str(self.root))
            if agent == 'claude':
                self.assertEqual(json.loads(call['stdin'])['message']['content'][0]['source']['data'], 'YWJj')
                self.assertIn('--dangerously-skip-permissions', call['args'])
            else:
                self.assertEqual(call['images'], ['YWJj'])
        self.assertFalse((self.root/'INJECTED').exists())
        self.assertFalse((self.root/'ALSO_INJECTED').exists())

    def test_codex_replay_excludes_later_session_tokens(self):
        rollout = self.root / '.codex/sessions/2026/10/22/rollout-2026-10-22-codex-session.jsonl'
        rollout.parent.mkdir(parents=True)
        def token_line(total):
            return json.dumps(dict(type='event_msg', payload=dict(type='token_count',
                              info=dict(total_token_usage=dict(total_tokens=total)))))+'\n'
        rollout.write_text(token_line(50))
        (self.root/'rollout.path').write_text(str(rollout))
        self.start_worker()
        self.send(dict(type='turn', task='1', turn='t1', agent='codex', project=str(self.root),
                       session='codex-session', text=json.dumps(dict(gate='release', rollout_total=150))))
        self.started()
        self.kill_worker()
        (self.root/'release').touch()
        wait_for(lambda: list(self.data.glob('*.run/exit')))
        # Another later turn in this conversation must not be charged to t1.
        with rollout.open('a') as f:
            f.write(token_line(999))
        self.start_worker()
        self.ended()
        reports = [e for e in self.events if e.get('type') == 'spend']
        self.assertEqual(reports[-1]['tokens'], 100)
        self.assertEqual(len(self.calls()), 1)

    def test_agent_exit_preserves_error_detail(self):
        self.start_worker()
        self.turn(exit=7)
        self.ended(state='failed')
        errors = [e['entry']['text'] for e in self.events
                  if e.get('type') == 'entry' and e['entry'].get('kind') == 'error']
        self.assertIn('fake agent failure detail', errors[-1])

    def test_final_event_without_newline(self):
        self.start_worker()
        self.turn(no_newline=True)
        self.ended()
        reports = [e for e in self.events if e.get('type') == 'spend']
        self.assertEqual(reports[-1]['tokens'], 100)
        self.assertEqual(reports[-1]['cost'], .25)

    def test_missing_tmux_session_fails_without_relaunch(self):
        self.start_worker()
        self.turn(gate='never')
        self.started()
        self.kill_worker()
        subprocess.run([TMUX, '-S', str(self.tmux_socket), 'kill-server'], check=True)
        self.start_worker()
        self.ended(state='failed')
        self.assertEqual(len(self.calls()), 1)
        wait_for(lambda: not alive(self.calls()[0]['pid']))

    def test_tmux_required_and_state_directory_locked(self):
        self.start_worker()
        duplicate = subprocess.run(self.args, env=self.env, capture_output=True, timeout=10)
        self.assertEqual(duplicate.returncode, 2)
        self.assertIn(b'cannot lock', duplicate.stderr)
        self.kill_worker()
        missing = subprocess.run(self.args, env=dict(self.env, PATH=str(self.bin)), capture_output=True, timeout=10)
        self.assertEqual(missing.returncode, 2)
        self.assertIn(b'tmux is required', missing.stderr)


if __name__ == '__main__':
    unittest.main()

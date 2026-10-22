# worker

Runs agent turns on one Linux box, using tmux by default. tmux must be installed and on PATH; there is no direct-process fallback.

`crew.aki` saves incoming turns, runs one per task, and queues replies. Each turn starts a detached tmux session on a private Dewy socket. Inside it, the worker's supervisor mode runs the agent with the existing headless JSON interface and writes its output and exit status to files. `turn.aki` follows those files; the agent adapters translate events into thread entries.

A worker restart reconnects to running turns and replays any output it missed, including turns that finished while it was down. Saved entry ids and cumulative usage reports prevent duplicate entries and charges. Queued replies also survive. Stopping a task persists the stop request, discards queued replies, and asks the supervisor to terminate the agent's process group, then kills it after two seconds if needed.

State defaults to `$XDG_STATE_HOME/dewy/workers/<hex box name>`, falling back to `~/.local/state/dewy/workers/<hex box name>`. `--data DIR` overrides it. One worker may hold a directory at a time. Keep the same directory and box name when restarting. Completed turn records, logs, and input images are removed after their final messages are sent. The directory is private to the worker's user.

The worker prints its tmux socket path on startup. `tmux -S <socket> list-sessions` lists active turns, named `dewy-<hex task>-<hex turn>`. These run headless agents; attaching does not open an interactive agent TUI. Existing Claude terminal watching and takeover continue to work. tmux sessions survive the network worker's death, but do not survive a machine reboot or tmux server failure.

Build with Aki 0.6.0 or newer:

```
akiko build worker
akiko test worker
python3 worker/tests/tmux_integration.py
```

The integration tests use real tmux with fake local agents and require Python 3. They do not use agent credentials or make API calls.

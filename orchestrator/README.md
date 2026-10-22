# orchestrator

The steering node. `board.aki` holds every box, task, and thread and decides what each request and worker line changes; it does no I/O and has the tests. `api.aki` and `stream.aki` serve the app, `dock.aki` talks to workers, `snapshot.aki` saves changed tasks to the data directory, and `main.aki` wires them together.

Event streams use akikit's timed condition-variable waits for keepalives and wake on board changes. Snapshot writes share `wire::files::atomic_write` with worker state writes.

```
akiko test orchestrator
akiko build orchestrator
python3 orchestrator/tests/stream_integration.py
```

The stream integration test checks a 20-second idle keepalive and immediate delivery of a worker's box notification.

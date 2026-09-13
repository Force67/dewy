# orchestrator

The steering node. `board.aki` holds every box, task, and thread and decides what each request and worker line changes; it does no I/O and has the tests. `api.aki` and `stream.aki` serve the app, `dock.aki` talks to workers, `snapshot.aki` saves changed tasks to the data directory, and `main.aki` wires them together.

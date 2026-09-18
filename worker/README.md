# worker

Runs agent turns on one box. `crew.aki` keeps one turn per task running and queues the rest; `turn.aki` runs one turn on its own thread and reads its output through a named pipe (`fifo.aki`). `claude_code.aki` and `codex.aki` know each agent's command line and turn its JSON events into thread entries. `inventory.aki` describes the box, and `main.aki` keeps the link to the orchestrator.

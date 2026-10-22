# Protocols

## Worker and orchestrator

A worker opens a TCP connection to the orchestrator's worker port and both sides send one JSON object per line.

The worker first sends its hello. `running` includes tasks recovered from durable tmux turn records, even when their final output still needs replaying, so the orchestrator can fail tasks whose turn died with an earlier worker:

```json
{"type":"hello","token":"…","box":"forge","host":"forge","os":"NixOS 26.11","agents":["claude","codex","opencode"],"poweroff":true,
 "models":{"opencode":["opencode/big-pickle","opencode-go/kimi-k3"]},"projects":[{"name":"dewy","path":"/home/me/Projects/dewy"}],"running":[]}
```

The orchestrator sends turns, stops, and watches. A turn for a task that is busy waits on the worker until the running turn ends. `yolo` runs the turn without permission prompts. `model` is opencode's `provider/model`, empty for its default (see `docs/adr/0004-opencode.md`). `poweroff` turns the box off, which its hello's `"poweroff":true` allows (see `docs/adr/0007-power-off-when-done.md`). `watch` follows a Claude Code session running in a terminal (see `docs/adr/0001-attach-terminal-sessions.md`).

```json
{"type":"turn","task":"7","turn":"t3","agent":"claude","project":"/home/me/Projects/dewy","session":"…","text":"now push it","yolo":false,"model":""}
{"type":"stop","task":"7"}
{"type":"watch","task":"8","session":"d0806beb-…"}
{"type":"poweroff"}
```

The worker reports entries, the agent's session id, a task's title, the end of a turn, the Claude Code sessions running in terminals when that list changes, the usage meters it measured (see `docs/adr/0005-usage-limits.md`), what a turn used as it goes (see `docs/adr/0006-task-usage.md`), and every fifteen seconds its stats:

```json
{"type":"entry","task":"7","entry":{"id":"t3.toolu_01","kind":"tool","tool":"Bash","detail":"npm test","state":"running","at":1790711758562}}
{"type":"entry","task":"7","entry":{"id":"t3.toolu_01","state":"ok","output":"42 passing"}}
{"type":"session","task":"7","session":"ff8c0078-…"}
{"type":"state","task":"7","state":"idle"}
{"type":"title","task":"8","title":"Android app flash test"}
{"type":"sessions","sessions":[{"id":"d0806beb-…","pid":112882,"project":"/home/me/Projects/dewy","name":"dewy-c4","state":"idle","started":1790758900000}]}
{"type":"spend","task":"7","turn":"t3","provider":"anthropic","tokens":21447,"cost":0.093}
{"type":"usage","meters":[{"agent":"codex","provider":"openai","plan":"prolite","at":1790779257882,"limits":[{"name":"7 days","used":0.78,"resets":1791143267000}],"spend":[]}]}
{"type":"stats","stats":{"load":0.8,"cpus":16,"memTotal":67345383424,"memUsed":23318081536,"uptime":42022}}
```

An entry is `agent`, `user`, `tool`, `note`, or `error`. An entry whose id is already in the thread updates that entry, which is how a tool call gets its result. A turn ends `idle` or `failed`, or `stopped` after cancellation.

A `spend` message with `turn` reports cumulative tokens and cost for that turn. The orchestrator adds only increases over its saved totals for the same task and turn, so replay after a worker restart cannot charge twice. Older workers without `turn` continue to report increments. Upgrade the orchestrator before starting workers that use tmux.

## App and orchestrator

HTTP with `Authorization: Bearer <token>`; the routes are listed at the top of `orchestrator/src/api.aki`. `GET /api/state` returns the boxes, the task summaries, and `seq`, the feed position. `GET /api/stream?since=<seq>` then streams every later change as server-sent events named `task`, `box`, and `entry`. When the orchestrator's feed no longer reaches back to `since`, it sends `reset` and the app loads the state again.

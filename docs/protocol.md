# Protocols

## Worker and orchestrator

A worker opens a TCP connection to the orchestrator's worker port and both sides send one JSON object per line.

The worker first sends its hello. `running` lists the tasks it still runs, so the orchestrator can fail tasks whose turn died with an earlier worker:

```json
{"type":"hello","token":"…","box":"forge","host":"forge","os":"NixOS 26.11","agents":["claude","codex"],
 "projects":[{"name":"dewy","path":"/home/me/Projects/dewy"}],"running":[]}
```

The orchestrator sends turns and stops. A turn for a task that is busy waits on the worker until the running turn ends.

```json
{"type":"turn","task":"7","turn":"t3","agent":"claude","project":"/home/me/Projects/dewy","session":"…","text":"now push it"}
{"type":"stop","task":"7"}
```

The worker reports entries, the agent's session id, the end of a turn, and every fifteen seconds its stats:

```json
{"type":"entry","task":"7","entry":{"id":"t3.toolu_01","kind":"tool","tool":"Bash","detail":"npm test","state":"running","at":1790711758562}}
{"type":"entry","task":"7","entry":{"id":"t3.toolu_01","state":"ok","output":"42 passing"}}
{"type":"session","task":"7","session":"ff8c0078-…"}
{"type":"state","task":"7","state":"idle"}
{"type":"stats","stats":{"load":0.8,"cpus":16,"memTotal":67345383424,"memUsed":23318081536,"uptime":42022}}
```

An entry is `agent`, `user`, `tool`, `note`, or `error`. An entry whose id is already in the thread updates that entry, which is how a tool call gets its result. A turn ends `idle` or `failed`.

## App and orchestrator

HTTP with `Authorization: Bearer <token>`; the routes are listed at the top of `orchestrator/src/api.aki`. `GET /api/state` returns the boxes, the task summaries, and `seq`, the feed position. `GET /api/stream?since=<seq>` then streams every later change as server-sent events named `task`, `box`, and `entry`. When the orchestrator's feed no longer reaches back to `since`, it sends `reset` and the app loads the state again.

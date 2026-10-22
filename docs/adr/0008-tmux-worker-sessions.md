# 8. Run worker turns in tmux by default

Status: implemented

## Context

The worker used to own each agent process and read JSON through a named pipe. A worker restart lost its task threads, queued replies, and output reader. Merely putting that process in tmux would still leave the agent writing to a pipe whose reader had disappeared.

Dewy also needed to move from the old akibase APIs to Aki 0.6.0 and akikit before extending the worker.

## Decision

All worker-run agent turns use tmux. A missing tmux executable fails worker startup with an actionable error. There is no opt-in flag or silent fallback.

- Each worker uses a private socket derived from its canonical state directory. Its tmux server ignores user configuration. Each active turn gets a detached session named with its task and turn ids; the agent's own conversation id continues across turns as before.
- Incoming orders, including queued replies and images, are saved through atomic renames in a user-private state directory. The worker locks that directory for its lifetime. The default follows XDG_STATE_HOME, with a directory per box name; `--data` overrides it.
- Before starting a turn, the worker saves its resolved agent session and effective permission mode. This happens before the preceding turn's record can be removed.
- A turn launches the worker binary in an internal supervisor mode inside tmux. It constructs agent argv directly, never through a shell. The private launch record contains the current worker environment. The supervisor restores it before starting the agent, avoiding stale PATH or credentials in a long-lived server without exposing credentials in tmux command arguments.
- The supervisor owns the agent's process group, redirects its JSON and stderr to regular files, saves the process identity and exit status, and handles a persisted stop request. It sends SIGTERM, waits two seconds without reaping the leader, then sends SIGKILL to the group and reaps it.
- The network worker tails output while it runs. On restart it loads saved orders before its hello, follows already launched turns without relaunching them, and replays their output with the same entry ids. A saved launch that no longer has a live tmux session or exit status fails rather than repeating the agent's work.
- Usage messages include the turn id and cumulative tokens and cost. The orchestrator persists per-turn maxima and charges only increases, including when an older completed turn is replayed. Messages without a turn id retain the old delta semantics. Codex rollout totals are bounded when the supervisor finishes so replay cannot charge a later turn's tokens.
- Completed records and input files remain until their final messages have been sent. Then the worker removes them. This retains the existing TCP delivery semantics; it does not add an orchestrator acknowledgement protocol.
- Cancelling a task atomically saves the exact running and queued records covered by the stop before discarding queued replies. Recovery applies that snapshot before loading orders, so a crash during cleanup cannot revive cancelled work or cancel later replies.
- When a tmux supervisor disappears, the worker validates the saved process identity and snapshots its group members before terminating the group. Escalation checks those members' identities so tools are still killed after the leader is reaped, without trusting a reused process group number.

## Consequences

- tmux is a runtime dependency on agent machines. Builds require Aki 0.6.0 or newer; the backend uses akikit's List, Arc, byte buffers, socket constructors, JSON cloning, and binary file helpers.
- Restarting or replacing a network worker does not stop running agent turns. tmux server failure and machine reboot are not recoverable live sessions; saved output is replayed and the turn fails.
- The current headless agent interfaces and existing Claude terminal takeover remain. This change does not create an interactive agent terminal or send prompts with tmux send-keys.
- The orchestrator must be upgraded before the worker, because older orchestrators treat replayed usage as new increments.
- Local records hold prompts, output, and image inputs until delivery and are protected by the state directory's permissions. Private tmux sockets isolate Dewy from personal tmux sessions.

## Validation

Backend unit tests cover the existing protocol and transcript parsers, plus cumulative usage replay across orchestrator restarts. Real tmux integration tests run fake Claude, Codex, and opencode commands to exercise worker crashes, queued replies, completion while disconnected, interrupted cancellation cleanup, orphaned tool processes, argv and image preservation, required tmux, and the state directory lock.

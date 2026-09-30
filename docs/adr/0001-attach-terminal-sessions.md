# 1. Attach to Claude Code sessions started in a terminal

Status: implemented

## Context

People start Claude Code in a terminal, then have to leave. dewy only knows the tasks it started, so such a session can neither be followed nor answered from the phone.

Claude Code leaves two things on disk that make it reachable:

- `~/.claude/sessions/<pid>.json`, one per running session: `pid`, `procStart` (the process start time from `/proc/<pid>/stat`), `sessionId`, `cwd`, `name`, `kind` (`interactive` for a terminal), and `status` (`busy` or `idle`).
- `~/.claude/projects/<dir>/<sessionId>.jsonl`, the transcript, appended as the session runs. Its `assistant` and `user` lines carry the same `message.content` blocks as `claude -p --output-format stream-json`. A prompt the person typed has `origin.kind == "human"`; injected context has `isMeta`; subagent traffic has `isSidechain`; `ai-title` lines carry `aiTitle`.

The terminal process owns its session, so dewy cannot type into it.

## Decision

Attaching mirrors a terminal session into a dewy task. Replying takes the session over: dewy ends the terminal process, then resumes the session headless, as for any dewy task.

**Worker**

- Every 5 seconds it reads `~/.claude/sessions/*.json` and keeps the `interactive` ones whose process is alive with a matching `procStart`. When that list changes, and after each hello, it sends
  `{"type":"sessions","sessions":[{"id","pid","project","name","state":"working"|"idle","started"}]}` (`busy` is `working`).
- `{"type":"watch","task","session"}` starts a watcher thread for the task, unless one already runs for it. It finds `<session>.jsonl` in any folder under `~/.claude/projects` and waits while it does not exist yet (a session before its first prompt). It reads the file from the start, then polls it for new lines every 500 ms. It translates lines as follows and skips everything else:
  - `user` with `origin.kind == "human"` and not `isMeta` or `isSidechain`: a `user` entry.
  - `attachment` of type `queued_command` with `origin.kind == "human"` (a message typed while the session was busy): a `user` entry.
  - `assistant` blocks (not `isSidechain`): `text` is an `agent` entry, `tool_use` a running `tool` entry.
  - `user` `tool_result` blocks: the result of their tool entry.
  - `ai-title`: `{"type":"title","task","title"}`.

  Entry ids are `w.<uuid>` for prompts, `w.<uuid>.<block>` for agent text, and `w.<tool_use id>` for tools, so they never collide with dewy's `t<n>.` ids.
  The first entry is a note: "Following a Claude Code session in a terminal. Replying stops it there and continues here."
- The watcher also polls the session's registry file and sends `state` `working` or `idle` when `status` changes. A registry file it cannot read changes nothing. The session is gone when its process is (pid and `procStart` checked in `/proc`), or when the registry names another session (`/clear` or `/resume` in the terminal). Then the watcher reads the rest of the transcript, marks tool calls that never finished as failed, sends `idle`, and ends. The task is then an ordinary task with a known session.
- A `turn` for a watched task takes over. The watcher sends SIGTERM to the terminal process (after checking `procStart`), sends SIGKILL if it still runs after 10 seconds, marks unfinished tool calls as failed, adds the note "Took over from the terminal.", sends `stopped`, and ends. The waiting turn then runs with `--resume <session>`. A `stop` for a watched task does the same, minus the turn.
- Every turn sends `working` when it starts. Every Claude turn that resumes a session first ends that session's terminal process if one runs, with the same note. So two writers never share a session, even when no watcher runs (after a worker restart, or after `claude --resume` in a terminal).
- `running` in the hello includes watched tasks.

**Orchestrator**

- `Box` keeps the last `sessions` list as JSON text and shows it in the box JSON. The list is cleared on hello and on disconnect, and ignored when boxes are restored from disk.
- `POST /api/sessions/attach {box, session}` returns the task. It refuses an offline box or a session the box does not list. If a task on that box already has that session id, it returns that task and queues `watch` again, which a worker still watching ignores. Otherwise it creates a task (agent `claude`, `project` from the listed session, `title` from its name or "Terminal session", `session` set, state from the listed state, no turns) and queues `{"type":"watch","task","session"}` for the box.
- The worker message `title` sets the task's title.

**App**

- `Box` gains `sessions`. The box screen lists them under "Terminal sessions" (name, project, working or idle, and "following" when a task has it). Tapping one attaches it and opens its thread.

## Alternatives

- Watch only, no replies: does not help when the session waits on you.
- Writing into the live session through its messaging socket (`/run/user/<uid>/cc-socks/<pid>.sock`): an undocumented internal protocol that can break with any Claude Code update.
- Typing into the terminal with `tmux send-keys`: works only inside tmux, and is blind to what the TUI shows.
- Resuming headless while the terminal still runs: two writers on one session diverge.
- inotify instead of polling: akibase has no binding; 500 ms polling of one file is cheap.

## Consequences

- Terminal sessions become visible and answerable from the phone, with notifications through the existing turn-end logic.
- Taking over ends the terminal session. `claude --resume` in the terminal picks the conversation back up later, including what happened on the phone.
- After takeover, turns run with the worker's permission mode, not the terminal's. For example, a terminal started with `--dangerously-skip-permissions` continues under `acceptEdits` unless the worker runs with `--yolo`.
- The worker depends on Claude Code's session registry and transcript formats, which are not public API. A format change breaks attaching, not ordinary tasks.
- A worker restart loses its watchers. A task that was working then fails with the existing "worker restarted" note. Tapping the session again resumes watching; replying without that is safe, since the turn ends the terminal first.
- Claude Code writes a message to the transcript when it is complete, so the mirror shows whole messages, not text as it streams.
- Mirroring reads the whole transcript at attach time, so a long session makes a long thread. Each entry also costs a linear scan of the thread in the orchestrator, so attaching tens of thousands of entries is slow.
- When the watcher posts a busy or idle change just as a takeover starts, the task may briefly show it; the turn's own `working` corrects it.
- A failed attach (box offline, session gone) shows no message in the app.

## Acceptance

1. With a Claude Code session running in a terminal, the box screen lists it with its name, project, and working or idle within 10 seconds, and drops it within 10 seconds after it exits.
2. Attaching shows the session's history (your prompts, agent text, tool calls with results) and its AI title, and new messages appear within 2 seconds of Claude Code writing them while the terminal works.
3. When the terminal session goes from busy to idle, the task shows "Your turn", and the app notifies you when it is in the background.
4. Replying from the phone ends the terminal process, and the agent's answer shows it remembers the conversation from the terminal.
5. When the terminal session exits without a reply, the task goes idle, and a reply resumes the session.
6. Attaching the same session twice opens the same task.
7. `akiko test` passes in `wire/`, `orchestrator/`, and `worker/`, with new tests for the transcript translation. `./gradlew testDebugUnitTest` passes.

# dewy

Steer your coding agents from your phone. Claude Code, Codex, and opencode run on your own machines ("boxes"); dewy shows every task in a mail-style inbox, streams what each agent is doing as it happens, lets you reply to steer it, and pings your phone when a turn finishes, fails, or goes quiet.

```
 phone (apps/android) ──HTTP + SSE──▶ orchestrator ◀──NDJSON over TCP── worker (one per box)
                                                                           │
                                                              claude -p / codex exec / opencode run
```

| Directory | What it is |
|-----------|------------|
| `orchestrator/` | The steering node, in [aki](https://github.com/aki-language/akiv4). Holds the board of boxes and tasks, keeps each task's thread on disk, serves the app, and hands turns to workers. |
| `worker/` | Runs on each box, in aki. Dials the orchestrator, starts agent turns in the right repository, and translates their JSON output into thread entries. |
| `wire/` | The aki library both share: one JSON document per line over TCP, and JSON helpers. |
| `apps/android/` | The app: Kotlin and Jetpack Compose. |
| `docs/` | The wire protocol and the aki gaps this project ran into. |

## Run it

The aki compiler, `akiko`, has no x86-64 release yet, so build it once from source:

```
git clone https://github.com/aki-language/akiv4 && cd akiv4
nix develop -c bash -c 'tools/seed_from_source.sh && tools/bootstrap.sh'
```

Then, inside akiv4's `nix develop` shell (akiko links through `cc` and loads LLVM from it):

```
# the orchestrator, on a machine your phone and boxes can reach
cd orchestrator && akiko build
DEWY_TOKEN=$(openssl rand -hex 24) ./build/orchestrator --data ~/.local/share/dewy

# a worker on every box, pointed at a repository or a directory of them
cd worker && akiko build
DEWY_TOKEN=<same token> ./build/worker --orchestrator orchestrator.tailnet:8788 --projects ~/Projects
```

The orchestrator listens on 8787 for the app and 8788 for workers. Workers dial out, so boxes behind NAT need no open port. `akiko test` in `wire/`, `orchestrator/`, and `worker/` runs the tests.

The app, from this repository's `nix develop` shell (JDK and Android SDK):

```
cd apps/android && ./gradlew installDebug    # or testDebugUnitTest
```

Enter the orchestrator's address and token on first launch.

## Things to know

- Everything is plain HTTP with a shared bearer token. Keep it on a private network such as a tailnet, or put a TLS proxy in front of port 8787.
- Agents run unattended. By default Claude Code runs with `--permission-mode acceptEdits` and Codex in its `workspace-write` sandbox; commands they may not run show up in the thread as notes. opencode runs with its own configured permissions, and the model it uses is picked per task in the app. Yolo mode, switched on per task when you start it, lifts these limits; `worker --yolo` lifts them for every task on a box. Anyone with the token can start a yolo task, so keep the token to yourself.
- A task that says nothing for three minutes while working is marked stalled, and the app offers to nudge or stop it.
- A box's page lists the Claude Code sessions running in its terminals. Tapping one follows it as a task; replying ends it in the terminal and continues it from the phone.
- Notifications need no Google services: while agents work, the app keeps a foreground service with a live notification that holds the event stream open. Tasks that start while the app has been closed are noticed when it next opens.
- The worker reads `/proc` for box stats and uses named pipes, so boxes run Linux.

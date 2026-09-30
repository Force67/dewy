# 4. opencode, with the model chosen from the phone

Status: implemented

## Context

opencode runs any provider's models (`provider/model`, such as `opencode-go/kimi-k3`), and which model does the work matters more there than with Claude Code or Codex, which have one sensible default. `opencode run --format json` prints one JSON event per line, `-s <session>` continues a session, `-m provider/model` picks the model for that run, `-f <file>` attaches files, and `--auto` approves every permission not denied outright. `opencode models` lists the models the box can use, one per line, in about two seconds. dewy has no notion of a model, so from the phone opencode would always run its configured default.

## Decision

**Worker**

- `opencode` joins `claude` and `codex` in the hello's `agents` when it is on the PATH. The hello also carries `"models":{"opencode":["opencode/big-pickle", …]}`: the output of `opencode models`, run with a 20 second limit before every time the worker dials, since the orchestrator waits only ten seconds for the hello. When that fails or times out the list is empty.
- A turn carries `"model"`, empty for the agent's default. An opencode turn runs `opencode run --format json [-s <session>] [-m <model>] [--auto] [-f <file>]... -- <text>`, `--auto` in yolo mode, the images as `-f` files like Codex's `-i`, "See the attached images." when the prompt is empty. Claude and Codex ignore `model`.
- opencode's events map to entries: any event's `sessionID` is the session; `text` is an agent entry under its part id; `tool_use` (sent once the tool ends) is a tool entry started and finished at once, the tool named as opencode names it, the detail `input.command`, else `input.filePath`, else `state.title`, the output `state.output`, failed when `state.status` is `error` (output `state.error`) or `metadata.exit` is non-zero; `error` fails the turn with `error.data.message`, else `error.name`. Other events are dropped.

**Orchestrator**

- A box keeps its `models` from the hello and shows them in its JSON, as it does `agents`.
- A task has `model`, stored and in its summary. A box without `models`, from an older worker or saved before, has `{}`. `POST /api/tasks` takes `model`; `POST /api/tasks/<id>/model` with `{model}` changes it for the turns after. Both refuse a model the box does not list for the task's agent ("the box has no such model"), except empty, the default. Every turn sends the task's model.

**App**

- `Box.models` and `Task.model` join the wire types.
- The new task sheet shows a Model row when the chosen agent has models on the box. It opens a picker: a search field over the list, "Default" first, then the models grouped by provider. The last model picked is remembered and preselected when the box still lists it.
- The thread header shows the task's model as a pill for an agent with models; tapping it opens the same picker, and picking changes the task's model.

## Alternatives

- The model as a free text field: typos cost a failed turn, and the phone keyboard is poor at `provider/model`.
- A model per message instead of per task: most tasks keep one model, and the composer stays simple; the pill changes it when needed.
- Model lists for Claude and Codex: neither CLI lists its models; their defaults are fine for now.
- `opencode serve` and its HTTP API: a server per box to keep alive, for what one process per turn does.

## Consequences

- A worker dials about two seconds later on a box with opencode, and models added to opencode show after the worker reconnects.
- A box saved before this change, or a worker from before it, lists no models, so its opencode tasks run the default.
- opencode reports a tool only once it ended, so a running opencode tool does not show until it is done.
- Without yolo mode opencode runs with its own configured permissions; what it would ask for is refused, as it cannot ask, and the thread shows the refusal as a failed tool. That includes reading files outside the project.
- A provider's failure, such as an endpoint that is down, fails the turn with the provider's message; switching the model and replying continues the same session.

## Acceptance

1. A box with opencode on its PATH lists it as an agent, and its models reach the app.
2. A task started from the phone with opencode and a picked model runs that model (the session in `opencode export` shows it), and its text and tool calls show in the thread.
3. Changing the model from the thread header makes the next reply run on the new model, in the same session.
4. A model the box does not list is refused with a clear message.
5. A photo sent to an opencode task reaches the model.
6. `akiko test` passes in `wire/`, `orchestrator/`, and `worker/`, with tests for opencode's events and for the task's model. `./gradlew testDebugUnitTest` passes.

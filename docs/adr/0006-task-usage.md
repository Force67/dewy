# 6. What a task used

Status: implemented

## Context

The Usage screen (ADR 0005) shows how close each provider is to its limits, but not which task used them up. The agents report their own consumption as they run:

- Claude Code's `assistant` events carry `message.usage` (`input_tokens`, `cache_creation_input_tokens`, `cache_read_input_tokens`, `output_tokens`), repeated for every content block of one message, and the `result` event ends the turn with its whole `usage` and `total_cost_usd`, the price at API rates even on a subscription.
- Codex's `exec --json` output has usage only in `turn.completed`, but its rollout file, named `rollout-<time>-<session>.jsonl`, gets a `token_count` event after every call with `info.last_token_usage.total_tokens` and the account's `rate_limits`.
- opencode's `step_finish` events carry `part.tokens.total` and `part.cost` in dollars, after every step.

No provider says how much of a limit one task used; they report the account's total, which the worker reads every two minutes. How much of a rise in that total a task caused can only be estimated.

## Decision

**Worker**

- A turn reports what it used as it goes, as increments: `{"type":"spend","task":"7","provider":"anthropic","tokens":12345,"cost":0.02}`. `tokens` counts every token the provider processed, cached ones included; `cost` is 0 when unknown.
- Claude: an `assistant` event whose message id the turn has not counted adds its usage's four token counts; messages of subagents may interleave. `result` adds what its `usage` holds beyond what was already sent, and its `total_cost_usd`. Provider `anthropic`.
- Codex: once the turn knows its session, it looks for the rollout whose name ends in `<session>.jsonl` (in the newest three day directories), after each of the first five lines of the agent's output that come with the session known. A new session's rollout is read from its start; a resumed one's from its end, starting from the running total its last `token_count` in the last MiB reported. After every line of output and at the end, each `token_count` the rollout gained adds what `info.total_token_usage.total_tokens` grew by, since Codex repeats a call's line at times, and then its `rate_limits` goes out as the Codex meter, so Codex's limits move live and after the tokens behind them. Without a rollout, `turn.completed` adds `input_tokens + output_tokens`. Provider `openai`.
- opencode: each `step_finish` adds `tokens.total` and `cost`. The provider is the part of the task's model before the `/`, empty for the default model.

**Orchestrator**

- A task keeps `spend`: `{"tokens","cost","share":[{"name","used"}]}`, saved and in its summary. A `spend` line from the box running the task adds to it, and its tokens become pending for every limit window the box's meter for that provider has; with no such meter yet, they count toward no window.
- When a meter for a box and provider arrives and a window's `used` rose since the box's last meter, the rise is split among the tasks with pending tokens for that window, in proportion to them, and added to their `share` under the window's name; the window's pending tokens clear, the other windows' stay. A rise with no pending tokens is left unattributed. A `used` that fell means the window reset; its pending tokens clear. A meter older (`at`) than the box's known one for the same agent and provider is dropped, from the comparison and from the box's meters. After a worker's hello, the first meter per provider is only a baseline, as the rise since the one before happened while the box was away. Pending tokens live in memory only.

**App**

- `Task.spend` joins the wire types. The thread header shows, once a task used anything, a line such as "184K tokens · $0.42 · ≈ 3% of 5 hours · ≈ 1% of 7 days", updating with the task's events. Claude's cost reads "$0.42 at API prices". For an opencode provider with budget caps, each capped window adds the task's whole cost as a share of the cap, "4% of the budget for 30 days".

## Alternatives

- Only exact numbers: nothing could be misattributed, but the question "how much of my 5 hours did this take" stays open.
- Splitting by cost rather than tokens: Codex reports no cost.
- Claude's per-message usage alone: the output count in those events is not final; `result` corrects it.
- A live Codex usage from `exec --json`: it has none until the turn ends.

## Consequences

- The estimated shares blame dewy's running tasks for anything else that used the same account at the same time, such as a Claude session in a terminal. The app says "≈".
- Claude's limits move in whole percents and are read every two minutes, so a short task may show no share, and a share lands up to two minutes late.
- opencode tasks on the default model show tokens and cost but no share of limits or caps.
- Shares from before an orchestrator restart stay; pending tokens from then are lost.
- A task's share of a budget uses its whole cost, which overstates it for a task older than the window.

## Acceptance

1. A Claude task's header shows its tokens rising while it works, and its cost at the end of each turn.
2. A Codex task's tokens rise per call while it works, and the Codex card on the Usage screen moves with them.
3. An opencode task on an opencode Go model shows its cost and, with a 5-hour cap set, its share of it.
4. After a long Claude task, its header shows a share of the 5-hour limit close to the rise the Usage screen showed while it ran alone.
5. `akiko test` passes in `wire/`, `orchestrator/`, and `worker/`, with tests for each agent's spend and for splitting a rise among tasks. `./gradlew testDebugUnitTest` passes.

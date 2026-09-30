# 5. Usage limits and budgets

Status: implemented

## Context

The agents run on subscriptions with rolling limits, and from the phone nothing shows how close a box is to them. What each agent knows about its limits differs:

- Claude Code's `stream-json` output has a `rate_limit_event` per turn: `rate_limit_info.unifiedWindows` holds `{"five_hour":{"utilization":0.03,"resetsAt":<s>},"seven_day":{…}}`. Nothing else Claude writes locally has it. Its `/usage` asks `GET https://api.anthropic.com/api/oauth/usage` (header `anthropic-beta: oauth-2025-04-20`) with the login in `~/.claude/.credentials.json`, which answers `{"five_hour":{"utilization":7.0,"resets_at":"<ISO>"},"seven_day":{…},…}` in percent, among windows with code names. The endpoint is not documented.
- Codex writes every session to `~/.codex/sessions/YYYY/MM/DD/rollout-*.jsonl` (under `$CODEX_HOME` when set), including its own terminal use. Its `token_count` events carry `rate_limits`: `plan_type`, and `primary` and `secondary` as `{used_percent, window_minutes, resets_at}` (seconds). The files grow past 100 MB.
- opencode records every assistant message with its `providerID`, `cost` in dollars and `tokens`, readable through `opencode db --format json "<SQL>"`. It knows no plan limits. Some providers answer their quota over HTTP with the key opencode keeps in `~/.local/share/opencode/auth.json` (by provider) or `account.json` (by `serviceID`): Z.ai's coding plan at `GET https://api.z.ai/api/monitor/usage/quota/limit`, GitHub Copilot at `GET https://api.github.com/copilot_internal/user`. Neither is documented. opencode Go has no such API; its limits are dollars per 5 hours, week, and month.

## Decision

A **meter** is what one box knows about one agent's provider:

```json
{"agent":"opencode","provider":"zai-coding-plan","plan":"pro","at":1790778000000,
 "limits":[{"name":"5 hours","used":0.46,"resets":1790791570699}],
 "spend":[{"name":"5 hours","cost":0.12,"tokens":81234},{"name":"7 days",…},{"name":"30 days",…}]}
```

`used` is a fraction, `resets` and `at` are Unix milliseconds, `resets` is 0 when unknown. `limits` and `spend` may each be empty.

**Worker**

- It sends `{"type":"usage","meters":[…]}`, which replaces the box's meters with the same agent and provider.
- Claude: a turn that reads a `rate_limit_event` sends a meter for `claude`/`anthropic` with a limit per `unifiedWindows` entry, named `five_hour` → "5 hours", `seven_day` → "7 days", other keys with their underscores as spaces.
- A thread started with the worker measures every two minutes and sends what it found, if the box has the agent:
  - Claude: the usage endpoint, asked with `claudeAiOauth.accessToken` from `.credentials.json` (under `$CLAUDE_CONFIG_DIR` when set) unless its `expiresAt` has passed, fetched like the opencode quotas below. `five_hour`, `seven_day`, `seven_day_opus`, and `seven_day_sonnet` become limits when they carry a `utilization`, named "5 hours", "7 days", "7 days, Opus", and "7 days, Sonnet"; the rest are left out.
  - Codex: of the three rollout files with the newest names, the last line carrying `rate_limits` in the last MiB of each; the one with the latest `timestamp` wins. It becomes a meter for `codex`/`openai` with `plan_type`, and a limit for `primary` and `secondary` each, named from `window_minutes` (300 → "5 hours", 10080 → "7 days", else hours or days).
  - opencode: one query sums `cost` and `tokens.total` of assistant messages per provider over the last 5 hours, 7 days, and 30 days, making a meter per provider used in the last 30 days.
  - opencode quotas, only for providers with a key in either file, fetched with `curl -s -m 15 -H @<file>` so the key stays off the command line (the header file is only the user's, and deleted after): Z.ai's `data.limits` become limits, `TOKENS_LIMIT` named "tokens, <n> <unit>" and `TIME_LIMIT` "tool calls, <n> <unit>" (unit 3 hours, 5 months, 6 weeks), `used` from `percentage`, `resets` from `nextResetTime`, `plan` from `data.level`. Copilot's `quota_snapshots.premium_interactions` becomes "premium requests", `used` = 1 − `percent_remaining`/100, `resets` from `quota_reset_date`, `plan` from `copilot_plan`; skipped when `unlimited` or absent. These meters exist even without spend.

**Orchestrator**

- A box keeps its meters, merges each `usage` line into them, shows them in its JSON as `usage`, and saves them with the box, so they outlive a restart and an offline box.

**App**

- `Box.usage` joins the wire types. A Usage row in the sidebar opens the Usage screen: a card per meter, grouped by box when more than one box has meters. A card shows the agent's mark, the provider and plan, its age ("as of 3 min ago"), a bar per limit with its percentage and "resets in 2 h 14 min", and per spend window the dollars and tokens.
- For opencode, a card has "Set budget": dollar caps for 5 hours, 7 days, and 30 days, kept on the phone per provider. A spend window with a cap gets a bar like a limit.

## Alternatives

- Claude's numbers from dewy turns only: first chosen, as the endpoint is undocumented, but the card stayed empty until a Claude turn ran through dewy and went stale while Claude was used in a terminal.
- `opencode stats`: a text table, days only, no 5-hour window.
- Caps kept on the orchestrator: shared across phones, but one phone is the common case.
- Reading whole rollout files: they grow past 100 MB.

## Consequences

- Claude's meter needs the box's Claude login to be current; Claude Code refreshes it when it runs, and until then the card keeps its last numbers, which its age shows. Turns still update it too.
- The quota endpoints, Anthropic's included, are undocumented and may change; a failed fetch leaves that provider with spend only. The Copilot parsing follows its known response shape but was not tried against an account with a Copilot plan.
- Every two minutes a box with opencode runs `opencode db`, a process of about a second, and one HTTPS request per provider with a quota endpoint.
- The worker reads opencode's stored keys and Claude's login for these requests, and nothing else of them leaves the box.
- A provider with a key but neither quota nor spend, such as a Copilot login without a plan, shows no meter.
- akibase reads JSON numbers with small errors (0.03 comes back as 0.030000000000000006); harmless for bars and percentages, but tests compare numbers by prefix.

## Acceptance

1. Within two minutes of a worker starting, the Usage screen shows Claude's 5-hour and 7-day utilization with reset times, as `/usage` does, without a dewy Claude turn.
2. Codex's weekly limit shows as in its terminal, without dewy running Codex.
3. opencode shows spend per provider for 5 hours, 7 days, and 30 days, matching `opencode stats` in total; Z.ai shows its token and tool call quotas.
4. A budget set for opencode-go draws a bar against its spend, and survives an app restart.
5. Meters survive an orchestrator restart and show for an offline box.
6. `akiko test` passes in `wire/`, `orchestrator/`, and `worker/`, with tests for each source's parsing and for merging meters. `./gradlew testDebugUnitTest` passes.

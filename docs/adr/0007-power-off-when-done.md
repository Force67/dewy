# 7. Turning a box off when its tasks are done

Status: implemented

## Context

A box left on for a long task keeps running, and drawing power, after the task ends. From the phone there is no way to say "turn this machine off once the agents are finished". The worker runs as a normal user; systemd-logind lets such a user power off without root when they have an active local session, and answers `CanPowerOff` with `yes`, `challenge`, or `no`. A headless box reached over SSH usually answers `challenge`, and a box that is off cannot be turned on from dewy.

## Decision

**Worker**

- The hello carries `"poweroff": true` when logind's `CanPowerOff`, asked with `busctl call org.freedesktop.login1 /org/freedesktop/login1 org.freedesktop.login1.Manager CanPowerOff`, answers `yes`; otherwise `false`.
- `{"type":"poweroff"}` from the orchestrator runs `systemctl --no-ask-password poweroff` without a terminal and with a time limit, so it never waits on a prompt. When that fails, the worker answers `{"type":"poweroff","error":"…"}`.

**Orchestrator**

- A box keeps whether it can power off, from its hello, and whether it is armed to power off when done, saved with the box. `POST /api/boxes/<name>/poweroff` with `{"armed": true|false}` arms or disarms it; a box that cannot is refused ("the box cannot turn itself off").
- The box is busy while one of its tasks is `working` or `stalled`, or one of the Claude Code sessions its worker lists is working in a terminal. Checked on every sweep: an armed, online box that is not busy gets a moment it turns off, one minute later, which the app shows; one that is busy again loses that moment. When the moment passes, the orchestrator sends `poweroff` and disarms the box, so a box that comes back on stays on. An order still queued when the worker disconnects is dropped, so it cannot fire on the worker's return. Being armed outlasts a worker's reconnect and an orchestrator restart, unless the hello says the box may no longer power off, which disarms it. A worker's error is kept until the box is armed or disarmed again.
- The box's JSON gains `"poweroff":{"can":…,"armed":…,"at":<ms, 0 when not counting down>,"error":"…"}`.

**App**

- `Box.poweroff` joins the wire types. The box page, for a box that can, has a "Turn off when tasks finish" switch. Its subtitle says what happens: "Waiting for 2 tasks and 1 terminal session to finish", or while counting down "Turning off in 42 s", with the switch as the way to cancel, or the error of a power-off that failed. The sidebar row of an armed box says "Turns off when done".

## Alternatives

- The worker deciding on its own: it does not know a task that is waiting for its next message from the phone, nor stalled ones, which the orchestrator does.
- Suspend instead of power off: wakes with a keypress, but draws power and may never wake on a laptop that runs flat.
- A setting per task ("turn off after this one"): the box is what turns off, and other tasks on it matter as much.
- A confirmation dialog: the minute of countdown, cancellable from the phone, is the confirmation.

## Consequences

- A box off stays off until someone at it, or Wake-on-LAN, turns it on; dewy offers no way back.
- When the box runs the orchestrator too, turning it off ends dewy until it is back.
- Anyone with the token can turn off a box that allows it, as they can start yolo tasks.
- A task waiting for your reply counts as done; the box turns off under it.

## Acceptance

1. A box whose user may power off shows the switch; a headless one does not.
2. Armed while a task works, the box turns off one minute after the task ends, and not while a Claude session in its terminal is working.
3. Starting a task or disarming during the countdown keeps the box on.
4. After the box is back on, it is disarmed; a worker that only reconnected stays armed.
5. `akiko test` passes in `wire/`, `orchestrator/`, and `worker/`, with tests for arming, the countdown, busy boxes, and disarming once the box is sent off. `./gradlew testDebugUnitTest` passes.

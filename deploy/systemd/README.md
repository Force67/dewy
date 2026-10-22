# systemd services

Run these user services as the account that owns your projects and is logged into
its agent CLIs. Install the [backend requirements](../../README.md#requirements) first.

## Setup

Run without sudo:

```sh
./deploy/systemd/install.sh all   # or: orchestrator / worker
```

Installs binaries to `~/.local/lib/dewy/`, units to `~/.config/systemd/user/`, and
private configuration to `~/.config/dewy/` (`XDG_CONFIG_HOME` supported).
Existing configuration is preserved; services are not started automatically.

Generate a token with `openssl rand -hex 24`, then edit:

| Configuration file | Required settings |
| --- | --- |
| `~/.config/dewy/orchestrator.env` | `DEWY_TOKEN` |
| `~/.config/dewy/worker.env` | Same `DEWY_TOKEN`, `DEWY_ORCHESTRATOR=host:8788`, `DEWY_PROJECTS=/absolute/project/path` |

`DEWY_PROJECTS` accepts colon-separated directories. The worker's `PATH` is copied
from the installing shell; ensure it includes tmux, agents, and their tools.
Services do not load shell startup files. Use `NAME=value`, quote spaces, and use
absolute paths: `$HOME`, `$PATH`, and `~` are not expanded in these files.

Enable the installed roles:

```sh
systemctl --user enable --now dewy-orchestrator.service
systemctl --user enable --now dewy-worker.service

# Start at boot and keep running after logout:
sudo loginctl enable-linger "$USER"
```

The app uses port 8787; workers connect out to port 8788 and retry automatically.

## Operations

```sh
systemctl --user status dewy-orchestrator.service dewy-worker.service
journalctl --user -u dewy-orchestrator.service -u dewy-worker.service -f

# Apply configuration changes:
systemctl --user restart dewy-worker.service

# Upgrade after updating the checkout; orchestrator first:
./deploy/systemd/install.sh all
systemctl --user restart dewy-orchestrator.service
systemctl --user restart dewy-worker.service
```

State defaults to `~/.local/state/dewy/orchestrator/` and
`~/.local/state/dewy/workers/<hex box name>/`, respecting `XDG_STATE_HOME` in the
user manager. When migrating, stop manual processes first, set `DEWY_DATA` in
`orchestrator.env` to the existing data directory, and keep the worker's box name
and state directory. Use `systemctl --user edit dewy-worker.service` for custom
CLI arguments.

Worker stops and restarts leave tmux turns running for recovery. Cancel tasks
through Dewy to stop agents; rebooting ends running turns.

# wire

What the orchestrator and the worker share: `LineStream` sends and receives one JSON document per line over TCP and keeps partial lines across read timeouts; `fields` reads and updates JSON members; `clip` cuts text without splitting a UTF-8 sequence; `files` shares atomic text writes between the backends.

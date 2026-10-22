# aki gaps

Dewy's backend now targets Aki 0.6.0 and akikit. The migration was checked with the 0.6.0 compiler and upstream akikit at `4a19769`.

## Resolved since the original implementation

- Linux x86-64 releases are available, alongside aarch64; building the compiler from source is optional.
- `Json` and `List` implement `Clone`. Dewy calls `clone()` and akikit's binary `read_bytes` and `write_bytes` directly; the old compatibility wrappers have been removed.
- `File` reads and writes descriptors directly on Linux and macOS. The old stdio buffering workaround is no longer needed there.
- The socket library supports IPv6 as well as IPv4. Dewy currently keeps its existing IPv4 listener and resolver behavior.
- Condition variables support timed waits. Event streams now wait until a change or their next keepalive deadline; the timekeeping thread only notifies them after a board change.
- `??` now binds more tightly than comparisons. Existing explicitly parenthesized expressions retain their meaning.
- Field defaults, enum methods, type aliases, type packs, pattern guards, and `try` keep constructors, argument lists, and file operations concise. Bulk `List` operations handle buffer copies and ordered entry drains.
- Scoped temporary directories clean up command output and quota headers on every return path. Command probes retain deadlines because akikit's `capture::output` waits indefinitely.

## Remaining constraints and choices

- **`Json` is not `Send`.** Shared board and outbox values still keep JSON as text.
- **JSON `set` appends rather than replacing.** `wire::fields::put` and `merge` retain replacement semantics.
- **`Command` redirects streams to paths.** Worker turns now write regular files under tmux; the FIFO workaround has been removed.
- **No file seek method.** `worker::tail` uses `lseek` to skip existing transcript content and efficiently read the last part of large usage rollouts. It also follows growing output files without reopening them.
- **No host name lookup in the socket address parser.** The worker retains its `getaddrinfo` binding.
- **No TLS or HTTP client in akikit.** Dewy still relies on a proxy or tailnet for transport security and curl for provider usage queries.
- **Threads have no detach or try_join.** The orchestrator retains fixed thread pools for long-lived connections.
- **HTTP response heads include Content-Length and Connection: close.** Event streams retain their custom response head.

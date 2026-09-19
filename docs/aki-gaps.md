# aki gaps

What dewy needed from aki and akibase that was missing or surprising, and how it works around each. Checked against akiv4 at `a550a4c`.

## Toolchain

- **No x86-64 Linux release.** Releases ship for aarch64 only, and the flake packages no `akiko`. dewy's README builds one with `tools/seed_from_source.sh` and `tools/bootstrap.sh`.

## akibase

- **`Json` is not `Send`.** The union lists no capabilities, so no struct holding a `Json` can live behind a `Mutex` shared between threads. The orchestrator's board keeps entries and box details as JSON text and parses them when it builds a reply (`orchestrator/src/board.aki`).
- **`Json` has no clone and no way to replace a member.** `set` appends, so a second `set` of a key leaves two. `wire/src/fields.aki` adds `copy`, `put`, and `merge`.
- **`Command` redirects standard streams only to paths.** There is no pipe to read a child's output while it runs. The worker makes a named pipe with `mkfifo` and redirects stdout into it (`worker/src/fifo.aki`).
- **`File` reads through stdio.** `fread` waits until the buffer is full, so a line from a slow writer never arrives. The worker calls `read(2)` on the descriptor.
- **`Command::spawn` waits for the child's exec.** The child opens its redirect targets before exec, and opening a FIFO for writing blocks until a reader exists, so a reader that opens after `spawn` deadlocks. The worker opens the read end with `O_NONBLOCK` first and clears the flag with `fcntl` after the spawn.
- **No name resolution.** `connect` takes numeric IPv4 addresses only. The worker binds `getaddrinfo` itself (`worker/src/resolve.aki`).
- **No TLS and no HTTP client.** Everything speaks plain HTTP or plain TCP; TLS belongs to a proxy or a tailnet.
- **`http::response_head` always writes `Content-Length` and `Connection: close`.** A streamed response such as server-sent events needs its own head (`orchestrator/src/stream.aki`).
- **Threads cannot detach and `Thread` has no `try_join`.** Dropping a `Thread` joins it. Long-lived connections run on fixed `ThreadPool`s, whose size caps how many app streams and workers can be connected at once.
- **`Condvar::wait_change` has no timeout.** The event streams must ping idle clients, so the orchestrator's timekeeping thread notifies every two seconds to wake them.
- **IPv4 only** in `akibase::net`.

## Language

- **`a ?? b == c` parses as `a ?? (b == c)`.** Write `(a ?? b) == c`.
- **Fields and methods share one namespace**, so a struct cannot have a field `seq` and a method `seq()`.
- **Free functions in a struct's own module cannot build it or touch private fields**; they go through a constructor or a method.

# 2. Cache the board and threads on the phone, and gzip replies

Status: implemented

## Context

Opening a thread downloads all of it, every time: 7.6 MB for a 4,000-entry session, uncompressed. Each reconnect downloads every thread opened since the app started again, and a cold start shows an empty list until `/api/state` answers. On a phone network that is seconds to tens of seconds.

The event stream can already replay what a client missed: every change gets a `seq`, frames carry it as the SSE `id`, and `GET /api/stream?since=<seq>` replays the last 4,000 frames. The app ignores this. It loads the state, then streams from the state's `seq`, so it has to download threads again to cover the gap. `seq` also restarts at 0 when the orchestrator restarts, and nothing tells a client, so a client that resumes from a saved position can miss changes silently.

## Decision

**Orchestrator**

- An `epoch`, the start time in milliseconds, is part of `/api/state`. `GET /api/stream?since=<seq>&epoch=<epoch>` answers `reset` at once when the epoch is not the current one, and also when `since` is past the newest `seq`. A request without an epoch counts as current, as clients before this change sent none.
- `GET /api/tasks/<id>` carries `seq`, the feed position the thread was read at.
- A reply of 1 KiB or more to a request whose `Accept-Encoding` lists `gzip` is gzipped, with `Content-Encoding: gzip`. zlib's `compress2` produces the deflate data; the orchestrator wraps it in a gzip header and trailer, with akibase's `crc32`. The orchestrator links `-lz`.

**App**

- A SQLite database (the platform's `SQLiteOpenHelper`, no new dependency) keeps:
  - `meta`: the last board as JSON, the stream position `seq`, and `epoch`;
  - `entries(task, pos, id, data, size)`, keyed by task and entry id: the threads the app has loaded. `data` is the entry's JSON, raw-deflated on its own, so a streamed entry stays a one-row write; `size` is its length;
  - `threads(task, stale, opened)`, with when the thread was last opened.
- The entries may take 50 MiB. Past that, after a thread downloads and after every 200 streamed entries, the threads opened longest ago are dropped, never the one being written. A dropped thread downloads again when opened.
- The database version is part of the layout; a new version drops the cache rather than migrating it.
- At start, the store shows the cached board, its boxes offline until the orchestrator answers.
- On each connect, the store loads `/api/state` as now, then streams from the cached `seq` when the cached `epoch` matches, so the feed replays what the cached threads missed. Otherwise it marks every cached thread stale and streams from the state's `seq`. It no longer downloads cached threads on a reconnect.
- Replayed task and box events at or before the state's `seq` are skipped, since the state holds them; replayed entries apply. Notifications compare live boards only.
- Every entry event is applied to memory and written to the database, and every event's `seq` is saved. The board is saved a second after it stops changing.
- A `reset` forgets the saved position, so the next connect starts from the state and marks the cached threads stale.
- Opening a thread shows its cached entries at once. The store downloads the thread only when it has none cached or they are stale, and then replaces the cached rows. A thread in memory that went stale downloads at once, and again when opened if that failed.
- Entries streamed while their thread downloads are kept with their `seq`, and those after the thread's `seq` are laid over the downloaded entries. A download for an orchestrator the app has since left is dropped.
- Disconnecting (forgetting the orchestrator) deletes the database.

## Alternatives

- Room: generated code and a KSP plugin for three small tables.
- Compressing whole threads (6.1x on the 7.6 MB test thread) or chunks of 32 entries (5.7x): every streamed update would unpack and repack a chunk, where one entry at a time gives 3.2x there (2.1x on a real 0.3 MB session) with single-row writes.
- Fetching thread entries since a `seq`: the orchestrator does not record per entry when it changed, and the feed replay covers the common case, a phone that was away for a while.
- Paging threads: changes the API and the thread screen; the cache and gzip remove most of the cost first.
- Binding zlib's `z_stream` to write gzip directly: a C struct layout to maintain for what `compress2` plus a 10-byte header and 8-byte trailer does.

## Consequences

- A thread opened once reopens instantly, also after the app restarts, and the task list shows before the orchestrator answers.
- A phone away for longer than the feed reaches (4,000 changes) downloads threads again when they are opened.
- Cached threads take about a third of their JSON size on disk, and all of them at most 50 MiB; a thread larger than that alone stays while it is the one being written.
- The orchestrator depends on zlib at build and run time.
- The database runs in WAL mode without a sync per commit: a crash can lose the last few writes, which the next replay restores.
- `Accept-Encoding` is matched by substring, so `gzip;q=0` still gets gzip; no client sends it.

## Acceptance

1. A cold start shows the cached task list before `/api/state` answers.
2. After an app restart, reopening a long thread shows it without a spinner, and without downloading it when nothing changed meanwhile.
3. Entries added while the app was closed (within the feed) appear in a cached thread after reconnecting, without downloading the thread.
4. After an orchestrator restart, the app resyncs, and stale threads are downloaded again when opened.
5. `GET /api/tasks/3` (the 7.6 MB test thread) with `Accept-Encoding: gzip` transfers at most a quarter of the bytes, and `curl --compressed` gets the same body as without.
6. `akiko test` passes in `orchestrator/`, with a test for the gzip wrapper and one for a position past the newest `seq`. The epoch checks live in the HTTP layer and are checked over HTTP. `./gradlew testDebugUnitTest` passes.
7. When the feed overflowed while the app was closed, the app resets once and settles, instead of reconnecting in a loop.
8. Entries streamed while their thread downloads are in the thread and in the cache after the download.
9. Cached entries are stored deflated, and past the limit the thread opened longest ago is dropped and downloads again when opened.

# 3. Image attachments

Status: implemented

## Context

Claude Code and Codex both take images: `claude -p --input-format stream-json` reads a user message whose content holds `{"type":"image","source":{"type":"base64","media_type","data"}}` blocks, and `codex exec` and `codex exec resume` take `-i <file>...`. Agents also look at images themselves: Claude's tool results carry the same image blocks (a screenshot read with Read), and so do terminal transcripts, including images pasted into a prompt. dewy passes text only, so from the phone nothing can show an agent a screenshot, and images the agent looked at are dropped from the thread.

## Decision

Images are immutable and named by the SHA-256 of their bytes (hex), so each is stored, sent, and cached once.

**App to orchestrator**

- `POST /api/images` takes the raw bytes of a JPEG, PNG, WebP, or GIF with its `Content-Type`, at most 4 MiB, and answers `{"id"}`. The orchestrator stores it as `data/images/<id>`, with its media type in `data/images/<id>.type`.
- `GET /api/images/<id>` answers the bytes with their type and `Cache-Control: private, max-age=31536000, immutable`.
- `POST /api/tasks` and `POST /api/tasks/<id>/messages` take `images: [id]`, at most 4 and at most 4.5 MiB together (413 otherwise), all stored. The user entry lists them: `"images":[{"id","type"}]`. A message may be images alone; a task started that way is titled "Image".

**Orchestrator to worker, and back**

- A turn with images carries them inline: `"images":[{"id","type","data"}]`, `data` in base64. The 4.5 MiB cap keeps that under 6 MiB, and the line under its 8 MiB limit.
- The worker writes them to files for the turn, in a temporary directory only its user can read, and deletes them after. A turn whose images cannot be written fails with an error entry instead of running without them. Claude gets its prompt as a stream-json user message on standard input, the images first, instead of `-p <text>`. Codex gets `-i <file>` for each and "See the attached images." when the prompt is empty. It always gets `--` before the prompt, since `-i` takes several files and a prompt may start with a dash.
- An image block in a Claude tool result, or in a terminal transcript's tool result or prompt (typed at once or while the session was busy), goes into an entry as `"images":[{"type","data"}]`, `data` in base64 as the agent gave it. Images pasted into a prompt make their own user entry before the prompt's text. The orchestrator's worker connection stores each and leaves `{"id","type"}` in the entry before the board sees it, so the board stays free of I/O. Images that would take an entry's base64 past 6 MiB are left out, to stay under the line limit.

**App**

- The composer and the new task sheet get an attach button: the system photo picker (up to 4, no storage permission) or the camera (`TakePicture` into the app's cache through a `FileProvider`). A picked image is staged at once: scaled so its longest side is at most 2048 px, encoded as JPEG at quality 90, and written to the app's files, so a draft survives the end of the process and of the picker's permission. Staged images show as thumbnails that can be removed; they are deleted once sent or removed, and at start when older than a day. The camera writes to the app's cache through a `FileProvider`, and that file goes once staged.
- User bubbles and tool rows show the images of their entry as thumbnails; tapping one shows it full screen.
- Images load through a small loader: a file cache in the app's cache directory, named by id, at most 100 MiB, the files used longest ago removed first, one download per image at a time; a memory cache of decoded thumbnails, sampled by their shorter side as they are cropped square; the full view decoded to fit the screen and not kept. An uploaded image goes into the file cache at once, and the cache is emptied when the app forgets the orchestrator.
- Sending your own message scrolls the thread to it, since the attachments above the composer can push the last entry out of view. A message that went clears the draft through an event, so it also clears after the screen was rebuilt; images picked while it was sending stay for the next one.

## Alternatives

- Inline base64 images in the task and message requests: the orchestrator's request limit, and every thread download would carry them.
- Coil for loading: a dependency with its own cache, for what content-addressed files make simple.
- Images as paths in the prompt for the agent to Read: works for Claude only, and costs a tool call each.

## Consequences

- The orchestrator's data directory grows with every image; nothing removes them, as tasks are never deleted either.
- A reply with images makes the Claude turn read its prompt from standard input; turns without images are unchanged.
- Images Codex looks at on its own do not show; its JSON events do not carry them.
- The orchestrator reads a request body before it checks the token, so an unauthenticated client can make it hold up to 4 MiB per connection, instead of 1 MiB before.

## Acceptance

1. A task started from the phone with a photo from the gallery gets an answer from Claude that describes the photo, and the same from Codex.
2. A reply with a camera photo reaches the agent, in a task started without images.
3. The thread shows the attached images as thumbnails, and full screen on tap.
4. A screenshot Claude reads with its Read tool shows in the thread under that tool call; so does one in an attached terminal session.
5. An image shown before shows again without downloading it, also after an app restart.
6. `akiko test` passes in `wire/`, `orchestrator/`, and `worker/`, with tests for base64 and for image blocks in Claude and terminal lines. `./gradlew testDebugUnitTest` passes.

# dewy for Android

Kotlin and Jetpack Compose. `tasks/TaskStore` is the app's copy of the orchestrator's board, fed by the event stream; screens live by feature (`sidebar`, `tasks`, `thread`, `boxes`, `newtask`, `connect`, `settings`), and `shell` lays them out as one pane on phones and three panes on wide screens from a single back stack. `alerts` posts notifications and runs the service that keeps the stream open while agents work. `DewyApp` builds and wires everything.

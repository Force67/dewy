package dev.dewy.shell

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.dewy.images.discardStaged
import dev.dewy.images.uploadImages
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.dewy.R
import dev.dewy.alerts.Alerts
import dev.dewy.boxes.BoxScreen
import dev.dewy.connect.ConnectScreen
import dev.dewy.net.DewyClient
import dev.dewy.net.Task
import dev.dewy.newtask.Draft
import dev.dewy.newtask.NewTaskSheet
import dev.dewy.settings.Connection
import dev.dewy.settings.ConnectionSettings
import dev.dewy.settings.SettingsScreen
import dev.dewy.usage.UsageScreen
import dev.dewy.sidebar.FoldersScreen
import dev.dewy.tasks.Board
import dev.dewy.tasks.Folder
import dev.dewy.tasks.Link
import dev.dewy.tasks.ReadMarks
import dev.dewy.tasks.TaskListScreen
import dev.dewy.tasks.TaskStore
import dev.dewy.tasks.inFolder
import dev.dewy.thread.ThreadScreen
import dev.dewy.thread.ThreadViewModel
import dev.dewy.ui.LocalPalette
import dev.dewy.ui.Type
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Phones show one pane; from this width on, folders, list and thread sit side by side. */
private val WideFrom = 900.dp

@Composable
fun Shell(
    store: TaskStore,
    settings: ConnectionSettings,
    readMarks: ReadMarks,
    alerts: Alerts,
    openRequests: Flow<String>,
) {
    val boardState = store.board.collectAsStateWithLifecycle()
    val board by boardState
    if (board.link == Link.NotSetUp) {
        ConnectScreen { url, token ->
            DewyClient(url, token).state()
            settings.save(Connection(url, token))
        }
        return
    }
    AskForNotifications()

    val seen = readMarks.seen.collectAsStateWithLifecycle(emptyMap())
    val yolo by settings.yolo.collectAsStateWithLifecycle(false)
    val lastModel by settings.model.collectAsStateWithLifecycle("")
    val appContext = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val stack = rememberNavBackStack(Route.Folders, Route.Tasks(Folder.All))
    var composing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf(Draft()) }

    fun openDetail(route: Route) {
        while (stack.size > 1 && (stack.last() as Route).isDetail) stack.removeAt(stack.lastIndex)
        stack.add(route)
    }

    LaunchedEffect(Unit) { openRequests.collect { openDetail(Route.Thread(it)) } }

    val screens = Screens(
        boardState = boardState,
        seenState = seen,
        stack = stack,
        store = store,
        settings = settings,
        readMarks = readMarks,
        alerts = alerts,
        openDetail = ::openDetail,
        compose = { d ->
            draft = d
            composing = true
        },
        archive = { task, archived -> scope.launch { runCatching { store.archive(task.id, archived) } } },
        attach = { box, session ->
            scope.launch { runCatching { store.attachSession(box, session, yolo) }.onSuccess { openDetail(Route.Thread(it.id)) } }
        },
    )

    BoxWithConstraints(Modifier.fillMaxSize().background(LocalPalette.current.ground)) {
        if (maxWidth >= WideFrom) WideLayout(screens) else CompactLayout(screens)
    }

    if (composing) {
        NewTaskSheet(
            boxes = board.boxes,
            draft = draft,
            yolo = yolo,
            onYolo = { on -> scope.launch { settings.setYolo(on) } },
            lastModel = lastModel,
            onModel = { m -> scope.launch { settings.setModel(m) } },
            onStart = { box, project, agent, model, text, images, yolo ->
                store.startTask(box, project, agent, model, text, uploadImages(appContext, store, images), yolo)
                    .also { discardStaged(images) }
            },
            onStarted = { task ->
                composing = false
                openDetail(Route.Thread(task.id))
            },
            onDismiss = { composing = false },
        )
    }
}

/**
 * Everything a route needs to draw itself, in one place for both layouts.
 * NavDisplay keeps each entry's content from its first composition, so the
 * board and read marks are read through State to stay current.
 */
private class Screens(
    private val boardState: State<Board>,
    private val seenState: State<Map<String, Long>>,
    val stack: NavBackStack<NavKey>,
    val store: TaskStore,
    val settings: ConnectionSettings,
    val readMarks: ReadMarks,
    val alerts: Alerts,
    val openDetail: (Route) -> Unit,
    val compose: (Draft) -> Unit,
    val archive: (Task, Boolean) -> Unit,
    val attach: (box: String, session: String) -> Unit,
) {
    val board: Board get() = boardState.value
    val seen: Map<String, Long> get() = seenState.value
    val folder: Folder get() = stack.filterIsInstance<Route.Tasks>().lastOrNull()?.folder ?: Folder.All
    val selectedTask: String? get() = (stack.lastOrNull() as? Route.Thread)?.taskId

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun openFolder(folder: Folder) {
        stack.clear()
        stack.add(Route.Folders)
        stack.add(Route.Tasks(folder))
    }

    @Composable
    fun Content(route: Route, wide: Boolean) {
        val onBack: (() -> Unit)? = if (wide) null else ::back
        val context = LocalContext.current.applicationContext
        when (route) {
            Route.Folders -> FoldersScreen(
                board = board,
                selected = if (wide) folder else null,
                onFolder = { f -> if (wide) openFolder(f) else stack.add(Route.Tasks(f)) },
                onBox = { openDetail(Route.BoxInfo(it)) },
                onSettings = { openDetail(Route.Settings) },
                onUsage = { openDetail(Route.Usage) },
                onCompose = { compose(Draft()) },
            )
            is Route.Tasks -> TaskListScreen(
                folder = route.folder,
                tasks = board.tasks.inFolder(route.folder),
                seen = seen,
                link = board.link,
                selectedId = if (wide) selectedTask else null,
                onBack = onBack,
                onOpen = { openDetail(Route.Thread(it.id)) },
                onArchive = archive,
                onCompose = {
                    val f = route.folder
                    compose(
                        when (f) {
                            is Folder.OnBox -> Draft(box = f.name)
                            is Folder.InProject -> Draft(
                                box = board.tasks.firstOrNull { it.project == f.path }?.box,
                                project = f.path,
                            )
                            else -> Draft()
                        },
                    )
                },
            )
            is Route.Thread -> {
                val model = viewModel(key = route.taskId) { ThreadViewModel(route.taskId, store, readMarks, context) }
                val budgets by settings.budgets.collectAsStateWithLifecycle(emptyMap())
                ThreadScreen(model, alerts, budgets, onBack)
            }
            is Route.BoxInfo -> BoxScreen(
                box = board.boxes.find { it.name == route.name },
                tasks = board.tasks,
                onBack = onBack,
                onOpenTask = { openDetail(Route.Thread(it.id)) },
                onStartIn = { box, project -> compose(Draft(box, project)) },
                onAttach = attach,
            )
            Route.Settings -> SettingsScreen(settings, board, onBack)
            Route.Usage -> UsageScreen(board.boxes, settings, onBack)
        }
    }
}

@Composable
private fun CompactLayout(s: Screens) {
    NavDisplay(
        backStack = s.stack,
        onBack = { s.back() },
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        transitionSpec = { push() },
        popTransitionSpec = { pop() },
        predictivePopTransitionSpec = { pop() },
        entryProvider = { key -> NavEntry(key) { s.Content(key as Route, wide = false) } },
    )
}

@Composable
private fun WideLayout(s: Screens) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.width(300.dp).fillMaxHeight()) { s.Content(Route.Folders, wide = true) }
        Box(Modifier.width(0.5.dp).fillMaxHeight().background(p.hairline))
        Box(Modifier.width(380.dp).fillMaxHeight()) { s.Content(Route.Tasks(s.folder), wide = true) }
        Box(Modifier.width(0.5.dp).fillMaxHeight().background(p.hairline))
        Box(Modifier.weight(1f).fillMaxHeight()) {
            val details = s.stack.filter { (it as Route).isDetail }
            if (details.isEmpty()) {
                NothingOpen()
            } else {
                NavDisplay(
                    backStack = details,
                    onBack = { s.back() },
                    entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                    popTransitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                    entryProvider = { key -> NavEntry(key) { s.Content(key as Route, wide = true) } },
                )
            }
        }
    }
}

@Composable
private fun NothingOpen() {
    val p = LocalPalette.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(R.drawable.ic_drop), null, tint = p.dew.copy(alpha = 0.35f), modifier = Modifier.size(64.dp))
            Text("Pick a task to follow it live", style = Type.callout, color = p.inkFaint)
        }
    }
}

/** iOS-style: the new screen slides over, the old one drifts back a little. */
private fun push(): ContentTransform =
    slideInHorizontally(tween(320)) { it } togetherWith slideOutHorizontally(tween(320)) { -it / 4 } + fadeOut(tween(320), 0.6f)

private fun pop(): ContentTransform =
    slideInHorizontally(tween(320)) { -it / 4 } + fadeIn(tween(320), 0.6f) togetherWith slideOutHorizontally(tween(320)) { it }

@Composable
private fun AskForNotifications() {
    if (Build.VERSION.SDK_INT < 33) return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}

private val DraftSaver = listSaver<Draft, String?>(
    save = { listOf(it.box, it.project) },
    restore = { Draft(it[0], it[1]) },
)

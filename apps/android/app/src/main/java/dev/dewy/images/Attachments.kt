package dev.dewy.images

import android.content.ActivityNotFoundException
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import dev.dewy.DewyApp
import dev.dewy.net.ImageRef
import dev.dewy.tasks.TaskStore
import dev.dewy.ui.LocalPalette
import dev.dewy.ui.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** The most images one message takes, as the orchestrator does. */
const val MaxImages = 4

private const val LongestSide = 2048

val LocalImageStore = staticCompositionLocalOf<ImageStore?> { null }

// Staged attachments wait here until sent; the picker's permission to read
// the original ends with the process, a file of our own does not.
private fun stageDir(context: Context) = File(context.filesDir, "attach")

private fun sourceOf(context: Context, uri: Uri): ImageDecoder.Source =
    if (uri.scheme == "file") ImageDecoder.createSource(File(uri.path!!)) else ImageDecoder.createSource(context.contentResolver, uri)

/**
 * Copies a picked image into the app, ready to upload: scaled so its longer
 * side is at most 2048 px, which is what the models look at, upright, as JPEG.
 */
suspend fun stageImage(context: Context, uri: Uri): Uri = withContext(Dispatchers.IO) {
    val bitmap = ImageDecoder.decodeBitmap(sourceOf(context, uri)) { decoder, info, _ ->
        val w = info.size.width
        val h = info.size.height
        val scale = minOf(1f, LongestSide.toFloat() / maxOf(w, h))
        decoder.setTargetSize(maxOf(1, (w * scale).toInt()), maxOf(1, (h * scale).toInt()))
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
    val bytes = ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        bitmap.recycle()
        out.toByteArray()
    }
    val file = File(stageDir(context).apply { mkdirs() }, "${UUID.randomUUID()}.jpg")
    file.writeBytes(bytes)
    Uri.fromFile(file)
}

/** Uploads staged images; the answer is their ids, in order. */
suspend fun uploadImages(context: Context, store: TaskStore, uris: List<Uri>): List<String> =
    uris.map { uri ->
        val bytes = withContext(Dispatchers.IO) { File(uri.path!!).readBytes() }
        val id = store.uploadImage(bytes, "image/jpeg")
        // What was just sent needs no download to show.
        (context.applicationContext as? DewyApp)?.images?.put(id, bytes)
        id
    }

/** Staged images that were sent or taken out. */
suspend fun discardStaged(uris: List<Uri>) = withContext(Dispatchers.IO) {
    uris.forEach { uri -> uri.path?.let { File(it).delete() } }
}

/** Staged images older than a day belong to drafts that were given up. */
fun sweepStaged(context: Context) {
    val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
    stageDir(context).listFiles().orEmpty().filter { it.lastModified() < cutoff }.forEach { it.delete() }
    File(context.cacheDir, "camera").listFiles().orEmpty().forEach { it.delete() }
}

/** Opens the photo picker or the camera; picked images go to [onPicked]. */
@Composable
fun AttachButton(room: Int, onPicked: (List<Uri>) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var shot by rememberSaveable { mutableStateOf<String?>(null) }
    fun stage(uris: List<Uri>, then: () -> Unit = {}) = scope.launch {
        val staged = uris.take(room).mapNotNull { runCatching { stageImage(context, it) }.getOrNull() }
        then()
        if (staged.isNotEmpty()) onPicked(staged)
    }
    val pickMany = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MaxImages)) { uris ->
        stage(uris)
    }
    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) stage(listOf(uri))
    }
    // The camera's file is only a step on the way to the staged copy.
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val file = shot?.let(::File) ?: return@rememberLauncherForActivityResult
        shot = null
        if (taken) stage(listOf(Uri.fromFile(file))) { file.delete() } else file.delete()
    }
    Box(modifier) {
        IconButton(onClick = { menu = true }, enabled = room > 0, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Rounded.AddPhotoAlternate, "Attach images", tint = if (room > 0) p.dew else p.inkFaint)
        }
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Photos") },
                leadingIcon = { Icon(Icons.Rounded.PhotoLibrary, null) },
                onClick = {
                    menu = false
                    val images = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    // The picker for several wants room for at least two.
                    if (room > 1) pickMany.launch(images) else pickOne.launch(images)
                },
            )
            DropdownMenuItem(
                text = { Text("Camera") },
                leadingIcon = { Icon(Icons.Rounded.PhotoCamera, null) },
                onClick = {
                    menu = false
                    val file = File(File(context.cacheDir, "camera").apply { mkdirs() }, "${System.currentTimeMillis()}.jpg")
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                    shot = file.path
                    try {
                        camera.launch(uri)
                    } catch (e: ActivityNotFoundException) {
                        shot = null
                    }
                },
            )
        }
    }
}

/** The images about to be sent, each with a way to take it out again. */
@Composable
fun AttachmentRow(uris: List<Uri>, onRemove: (Uri) -> Unit, modifier: Modifier = Modifier) {
    if (uris.isEmpty()) return
    val p = LocalPalette.current
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        uris.forEach { uri ->
            Box {
                LocalThumbnail(uri, 64.dp)
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(p.ink.copy(alpha = 0.7f))
                        .clickable { onRemove(uri) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Close, "Remove", tint = p.surface, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun LocalThumbnail(uri: Uri, size: Dp) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                ImageDecoder.decodeBitmap(sourceOf(context, uri)) { decoder, info, _ ->
                    val scale = px.toFloat() / minOf(info.size.width, info.size.height)
                    if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                }.asImageBitmap()
            }.getOrNull()
        }
    }
    val shape = RoundedCornerShape(10.dp)
    Box(Modifier.size(size).clip(shape).background(p.surface)) {
        bitmap?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

/** An image from the orchestrator, loaded through the cache, cropped to [size]. */
@Composable
fun RemoteImage(ref: ImageRef, size: Dp, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val p = LocalPalette.current
    val store = LocalImageStore.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, ref.id, px) { value = store?.bitmap(ref.id, px, fit = false) }
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(p.surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        bitmap?.let { Image(it, "Image", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

/** A message's images as thumbnails that open full screen. */
@Composable
fun ImageStrip(images: List<ImageRef>, size: Dp, modifier: Modifier = Modifier, alignEnd: Boolean = false) {
    if (images.isEmpty()) return
    var open by remember { mutableStateOf<ImageRef?>(null) }
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp, if (alignEnd) Alignment.End else Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        images.forEach { ref -> RemoteImage(ref, size, onClick = { open = ref }) }
    }
    open?.let { ref -> ImageViewer(ref, onDismiss = { open = null }) }
}

@Composable
private fun ImageViewer(ref: ImageRef, onDismiss: () -> Unit) {
    val store = LocalImageStore.current
    val screen = LocalConfiguration.current.let { maxOf(it.screenWidthDp, it.screenHeightDp) }.dp
    val px = with(LocalDensity.current) { screen.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, ref.id) { value = store?.bitmap(ref.id, px, fit = true) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            bitmap?.let { Image(it, "Image", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
                ?: Text("Loading…", style = Type.callout, color = Color.White)
        }
    }
}

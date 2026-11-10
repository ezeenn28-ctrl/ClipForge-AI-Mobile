package com.clipforge.mobile

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SoundLibraryDialog(onDismiss: () -> Unit, onPreview: (SoundKind?, SoundAsset?) -> Unit,
                       onChoose: (SoundKind, SoundAsset?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember { MyinstantsClient() }
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<OnlineSound>()) }
    var next by remember { mutableStateOf<Int?>(null) }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var library by remember { mutableStateOf(emptyList<SoundAsset>()) }
    val preferences = remember { context.getSharedPreferences("sound-library", 0) }
    var favorites by remember { mutableStateOf(preferences.getStringSet("favorites", emptySet()).orEmpty().toSet()) }
    var onlyFavorites by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { library = withContext(Dispatchers.IO) { SoundLibrary.list(context.filesDir) } }
    DisposableEffect(Unit) { onDispose { onPreview(null, null) } }
    fun favorite(id: String) {
        favorites = if (id in favorites) favorites - id else favorites + id
        preferences.edit().putStringSet("favorites", favorites).apply()
    }
    fun runTask(task: suspend () -> Unit) {
        working = true; message = ""
        scope.launch {
            try { task() }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) { message = error.message ?: "Could not load sound" }
            finally { working = false }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !working) runTask {
            val asset = withContext(Dispatchers.IO) { SoundLibrary.import(context, uri) }
            library = listOf(asset) + library.filterNot { it.id == asset.id }
            tab = 1; query = ""; message = "Imported: " + asset.name
        }
    }
    Dialog(onDismissRequest = { if (!working) onDismiss() }) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 8.dp) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(.92f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sound library", style = MaterialTheme.typography.titleLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Built-in", "My sounds", "Myinstants").forEachIndexed { index, label ->
                        FilterChip(selected = tab == index, enabled = !working, onClick = { tab = index; query = ""; onlyFavorites = false; onPreview(null,null) }, label = { Text(label) })
                    }
                }
                OutlinedTextField(query, { query = it.take(80) }, enabled = !working, singleLine = true,
                    label = { Text(if (tab == 2) "Search Myinstants" else "Find a sound") }, modifier = Modifier.fillMaxWidth())
                if (tab != 2) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = onlyFavorites, onClick = { onlyFavorites = !onlyFavorites }, label = { Text("Favorites") })
                    TextButton(enabled = !working, onClick = { picker.launch(arrayOf("audio/*")) }) { Text("Import MP3 / audio") }
                }
                if (tab == 2) {
                    Text("Community sounds. Check permission before using in paid posts.", style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        listOf("Vine boom", "Bruh", "Punch", "Laugh", "Whoosh").forEach { term ->
                            TextButton(enabled = !working, onClick = { query = term }) { Text(term) }
                        }
                    }
                    Button(enabled = !working && query.trim().length >= 2, onClick = {
                        val requested = query.trim()
                        runTask {
                            val page = withContext(Dispatchers.IO) { client.search(requested) }
                            results = page.sounds; next = page.nextPage; searchQuery = requested
                            if (results.isEmpty()) message = "No sounds found. Try another search."
                        }
                    }) { Text("Search sounds") }
                }
                if (working) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (tab == 0) items(SoundKind.entries.filter { kind ->
                        kind.label.contains(query, true) && (!onlyFavorites || kind.name in favorites)
                    }, key = { "builtin-" + it.name }) { kind ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp)) {
                                Text(kind.label, style = MaterialTheme.typography.titleMedium)
                                Text("Original · ${kind.durationMs} ms", style = MaterialTheme.typography.bodySmall)
                                Row(Modifier.horizontalScroll(rememberScrollState())) {
                                    TextButton(enabled = !working, onClick = { onPreview(kind, null) }) { Text("Preview") }
                                    TextButton(enabled = !working, onClick = { onChoose(kind, null); onDismiss() }) { Text("Use sound") }
                                    TextButton(onClick = { favorite(kind.name) }) { Text(if (kind.name in favorites) "Unfavorite" else "Favorite") }
                                }
                            }
                        }
                    }
                    if (tab == 1) {
                        val shown = library.filter { it.name.contains(query, true) && (!onlyFavorites || it.id in favorites) }
                        if (shown.isEmpty()) item { Text("Import an audio file or download a sound from Myinstants.") }
                        items(shown, key = { "asset-" + it.id }) { asset ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(asset.name, style = MaterialTheme.typography.titleMedium)
                                    Text(asset.origin + " · " + Timecode.precise(asset.durationMs), style = MaterialTheme.typography.bodySmall)
                                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                                        TextButton(enabled = !working, onClick = { onPreview(null, asset) }) { Text("Preview") }
                                        TextButton(enabled = !working, onClick = { onChoose(SoundKind.POP, asset); onDismiss() }) { Text("Use sound") }
                                        TextButton(onClick = { favorite(asset.id) }) { Text(if (asset.id in favorites) "Unfavorite" else "Favorite") }
                                        if (asset.page.isNotBlank()) TextButton(onClick = {
                                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(asset.page))) }
                                                .onFailure { message = "No browser available" }
                                        }) { Text("Source") }
                                    }
                                }
                            }
                        }
                    }
                    if (tab == 2) {
                        items(results, key = { "online-" + it.slug }) { sound ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(sound.name, style = MaterialTheme.typography.titleMedium)
                                    Button(enabled = !working, onClick = {
                                        runTask {
                                            val asset = withContext(Dispatchers.IO) {
                                                val input = File(context.cacheDir, "myinstants-download.mp3")
                                                try {
                                                    input.writeBytes(client.download(sound))
                                                    SoundLibrary.import(context, Uri.fromFile(input), sound.name, "Myinstants", sound.page)
                                                } finally { input.delete() }
                                            }
                                            library = listOf(asset) + library
                                            tab = 1; query = ""; message = "Downloaded. Preview, then choose Use sound."
                                        }
                                    }) { Text("Download") }
                                }
                            }
                        }
                        if (next != null) item {
                            TextButton(enabled = !working && query.trim() == searchQuery, onClick = {
                                val pageNumber = next ?: return@TextButton
                                runTask {
                                    val page = withContext(Dispatchers.IO) { client.search(searchQuery, pageNumber) }
                                    results = (results + page.sounds).distinctBy { it.slug }; next = page.nextPage
                                }
                            }) { Text("More results") }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { onPreview(null,null) }) { Text("Stop preview") }
                    TextButton(enabled = !working, onClick = onDismiss) { Text("Done") }
                }
            }
        }
    }
}

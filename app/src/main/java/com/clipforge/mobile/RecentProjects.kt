package com.clipforge.mobile

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun RecentProjects(listing: ProjectCatalog.Listing, currentId: String, enabled: Boolean, onOpen: (String) -> Unit) {
    var showAll by remember { mutableStateOf(false) }
    if (listing.entries.isEmpty() && listing.unreadable == 0) return
    StudioCard("Recent projects", "Edits save on this phone. Keep the original videos available.") {
        (if (showAll) listing.entries else listing.entries.take(6)).forEach { entry ->
            val thumbnail = rememberClipThumbnail(LocalContext.current, Uri.parse(entry.source), entry.startMs, enabled)
            Surface(onClick = { onOpen(entry.id) }, enabled = enabled,
                shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(Modifier.width(56.dp).height(64.dp), shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.background) {
                        if (thumbnail != null) Image(thumbnail.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Box(contentAlignment = Alignment.Center) { StudioGlyph(StudioGlyph.PLAY, color = MaterialTheme.colorScheme.primary) }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(Timecode.format(entry.durationMs) + " · " +
                            java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
                                .format(java.util.Date(entry.updatedMs)), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(if (entry.id == currentId) "Current" else "Open", color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (listing.entries.size > 6) TextButton(onClick = { showAll = !showAll }) {
            Text(if (showAll) "Show recent" else "See all projects (" + listing.entries.size + ")")
        }
        if (listing.unreadable > 0) Text("Could not read " + listing.unreadable +
            " saved project(s). Their files have been kept.", style = MaterialTheme.typography.bodySmall)
    }
}

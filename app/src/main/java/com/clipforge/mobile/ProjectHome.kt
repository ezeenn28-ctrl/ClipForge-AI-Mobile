package com.clipforge.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProjectHome(enabled: Boolean, modelReady: Boolean, hasProject: Boolean, needsRelink: Boolean,
                projectName: String, durationMs: Long, recent: ProjectCatalog.Listing, currentId: String,
                onNewProject: () -> Unit, onFindClips: () -> Unit, onContinue: () -> Unit,
                onOpen: (String) -> Unit, onImportLink: () -> Unit, onProjectTools: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("YOUR CREATIVE SPACE", fontSize = 10.sp, letterSpacing = 1.6.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            Text("Your videos,\nfor greater impact.", style = MaterialTheme.typography.headlineMedium)
        }
        Button(enabled = enabled, onClick = onNewProject,
            modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(18.dp)) {
            Text("+", fontSize = 27.sp, fontWeight = FontWeight.Normal)
            Spacer(Modifier.width(12.dp))
            Text(if (needsRelink) "Relink original video" else "New project", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        if (hasProject) Surface(onClick = onContinue, enabled = enabled, shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(Modifier.size(42.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) { StudioGlyph(StudioGlyph.PLAY, color = MaterialTheme.colorScheme.primary) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Continue editing", fontWeight = FontWeight.SemiBold)
                    Text(projectName.ifBlank { "Untitled project" } + " · " + Timecode.format(durationMs),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Surface(shape = RoundedCornerShape(22.dp), color = Color.Transparent,
            border = BorderStroke(1.dp, Color(0xFF20525A))) {
            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF0B3B43), Color(0xFF132632))))
                .padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StudioGlyph(StudioGlyph.SPARKLES, color = MaterialTheme.colorScheme.primary)
                    Text("AI Highlights", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    StudioBadge(if (modelReady) "READY" else "SETUP", emphasized = modelReady)
                }
                Text("Find up to 15 speech-based clip suggestions. Review and shape each moment.",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFFD1E4E8))
                OutlinedButton(enabled = enabled, onClick = onFindClips,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)) { Text("Find clips") }
            }
        }
        RecentProjects(recent, currentId, enabled, onOpen)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(enabled = enabled, onClick = onImportLink, modifier = Modifier.weight(1f)) { Text("Import a link") }
            TextButton(enabled = enabled, onClick = onProjectTools, modifier = Modifier.weight(1f)) {
                StudioGlyph(StudioGlyph.SETTINGS, Modifier.size(17.dp), MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp)); Text("Project tools")
            }
        }
    }
}

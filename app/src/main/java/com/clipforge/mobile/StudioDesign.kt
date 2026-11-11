package com.clipforge.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class StudioTab(val label: String, val title: String, val description: String) {
    STUDIO("Studio", "Find your next clip.", "Import footage, discover moments, shape your clip."),
    CAPTIONS("Captions", "Every word, in place.", "Transcribe on your phone. Refine the words and timing."),
    CAMPAIGN("Campaign", "Create with a clear brief.", "Check requirements and track your submissions."),
    EXPORT("Export", "One final look.", "Review your clip. Export only when you approve.")
}

private val studioColors = darkColorScheme(
    primary = Color(0xFF00DDE5), onPrimary = Color(0xFF002E31),
    primaryContainer = Color(0xFF07383D), onPrimaryContainer = Color(0xFFA6F8FB),
    secondary = Color(0xFF9AC9CE), onSecondary = Color(0xFF112F34),
    secondaryContainer = Color(0xFF223148), onSecondaryContainer = Color(0xFFD5DFFF),
    background = Color(0xFF0B0F15), onBackground = Color(0xFFEDF2F8),
    surface = Color(0xFF151C25), onSurface = Color(0xFFEDF2F8),
    surfaceVariant = Color(0xFF202B38), onSurfaceVariant = Color(0xFFAAB9CB),
    outline = Color(0xFF42516A), outlineVariant = Color(0xFF303740),
    error = Color(0xFFFFB4B0)
)

@Composable
fun ClipForgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = studioColors,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp)),
        typography = Typography(
            headlineMedium = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
            titleLarge = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            titleMedium = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            bodySmall = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 19.sp)
        ), content = content)
}

@Composable
fun StudioHeader(version: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(44.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Image(painterResource(R.drawable.ic_clipforge_mark), contentDescription = null, Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("ClipForge AI", fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = (-.5).sp)
            Text("PERSONAL STUDIO  /  $version", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
        }
        StudioBadge("ON DEVICE")
    }
}

@Composable
fun StudioBadge(text: String, emphasized: Boolean = true) {
    Surface(shape = RoundedCornerShape(8.dp), color = if (emphasized) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
        Text(text, Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            color = if (emphasized) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .5.sp)
    }
}

@Composable
fun StudioNavigation(selected: StudioTab, onSelect: (StudioTab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp, windowInsets = WindowInsets(0, 0, 0, 0)) {
        StudioTab.entries.forEach { tab ->
            NavigationBarItem(selected = selected == tab, onClick = { onSelect(tab) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                icon = { StudioNavIcon(tab, selected == tab) },
                label = { Text(tab.label, fontSize = 11.sp, fontWeight = FontWeight.Medium) })
        }
    }
}

@Composable
private fun StudioNavIcon(tab: StudioTab, selected: Boolean) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(22.dp)) {
        val w = size.width; val h = size.height; val stroke = Stroke(1.7.dp.toPx())
        when (tab) {
            StudioTab.STUDIO -> {
                drawRoundRect(color, Offset(w * .08f, h * .16f), Size(w * .84f, h * .68f), CornerRadius(3.dp.toPx()), style = stroke)
                drawPath(Path().apply { moveTo(w * .42f, h * .33f); lineTo(w * .68f, h * .5f); lineTo(w * .42f, h * .67f); close() }, color)
            }
            StudioTab.CAPTIONS -> {
                drawRoundRect(color, Offset(w * .06f, h * .16f), Size(w * .88f, h * .68f), CornerRadius(3.dp.toPx()), style = stroke)
                drawLine(color, Offset(w * .23f, h * .43f), Offset(w * .77f, h * .43f), 1.7.dp.toPx())
                drawLine(color, Offset(w * .23f, h * .62f), Offset(w * .60f, h * .62f), 1.7.dp.toPx())
            }
            StudioTab.CAMPAIGN -> {
                listOf(.5f, .85f, .65f).forEachIndexed { i, value ->
                    drawRoundRect(color, Offset(w * (.12f + i * .29f), h * (1 - value)), Size(w * .18f, h * (value - .1f)), CornerRadius(2.dp.toPx()))
                }
            }
            StudioTab.EXPORT -> {
                drawPath(Path().apply { moveTo(w * .22f, h * .42f); lineTo(w * .5f, h * .14f); lineTo(w * .78f, h * .42f) }, color, style = stroke)
                drawLine(color, Offset(w * .5f, h * .14f), Offset(w * .5f, h * .69f), 1.7.dp.toPx())
                drawPath(Path().apply { moveTo(w * .12f, h * .64f); lineTo(w * .12f, h * .88f); lineTo(w * .88f, h * .88f); lineTo(w * .88f, h * .64f) }, color, style = stroke)
            }
        }
    }
}

@Composable
fun StudioIntro(tab: StudioTab) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tab.label.uppercase(), color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.8.sp)
        Text(tab.title, style = MaterialTheme.typography.headlineMedium)
        Text(tab.description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun StudioCard(title: String, subtitle: String = "", content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (title.isNotEmpty()) Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
fun StudioEmptyPreview() {
    Box(Modifier.fillMaxWidth().height(190.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.offset(y = (-20).dp)) {
            StudioNavIcon(StudioTab.STUDIO, true)
            Text("Your next great clip starts here", fontWeight = FontWeight.SemiBold)
            Text("Import a video from your phone", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun StudioActivity(message: String, active: Boolean, percent: Int = -1) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (active) {
            if (percent < 0) LinearProgressIndicator(Modifier.fillMaxWidth())
            else LinearProgressIndicator(progress = { percent.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth())
        }
        Text(message, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ClipReviewCard(index: Int, draft: HighlightDraft, active: Boolean, score: Int, enabled: Boolean, thumbnail: android.graphics.Bitmap? = null, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (active) 1.5.dp else 1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.width(52.dp).height(66.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.background) {
                Box(contentAlignment = Alignment.Center) {
                    if (thumbnail != null) Image(thumbnail.asImageBitmap(), contentDescription = "Clip ${index + 1} source frame", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Text((index + 1).toString().padStart(2, '0'), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("${Timecode.format(draft.startMs)} — ${Timecode.format(draft.endMs)}", fontWeight = FontWeight.SemiBold)
                Text(draft.hook.ifBlank { "${(draft.endMs - draft.startMs) / 1000}s moment · Coverage $score/100" }, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (draft.reasons.isNotEmpty()) Text(draft.reasons.joinToString(" · "), maxLines = 3,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
            Text(if (draft.approved) "Approved" else if (active) "Editing" else "Review", fontSize = 11.sp,
                color = if (active || draft.approved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Keep navigation fixed while allowing independent preview/tools scrolling in landscape. */
@Composable
fun StudioWorkspaceLayout(
    modifier: Modifier = Modifier,
    showPreview: Boolean,
    wide: Boolean,
    preview: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    if (wide && showPreview) {
        Row(modifier) {
            Box(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) { preview() }
            Box(Modifier.weight(1f).fillMaxHeight()) { content() }
        }
    } else {
        Column(modifier) {
            if (showPreview) preview()
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.fillMaxHeight().widthIn(max = 720.dp)) { content() }
            }
        }
    }
}

@Composable
fun StudioWorkflowSteps() {
    listOf(
        "Choose your source" to "A downloaded video from your phone.",
        "Shape the moment" to "Highlights, captions and framing.",
        "Review, then export" to "Your approval always comes first."
    ).forEachIndexed { index, (title, detail) ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(28.dp), shape = RoundedCornerShape(9.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Box(contentAlignment = Alignment.Center) { Text((index + 1).toString(), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}


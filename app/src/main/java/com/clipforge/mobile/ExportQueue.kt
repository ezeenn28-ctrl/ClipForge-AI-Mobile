package com.clipforge.mobile

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.media3.transformer.*
import kotlinx.coroutines.*
import java.io.File
import java.util.Properties
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class QueueStatus { REVIEW, READY, RENDERING, COMPLETE, FAILED }
data class QueuedExport(val id: String, val project: EditorProject, val status: QueueStatus = QueueStatus.REVIEW,
                        val message: String = "Review and approve again", val uri: String = "")

/** Immutable snapshots. Disk never carries authorization to export after process death. */
object ExportQueue {
    const val LIMIT = 10
    private fun file(root: File, id: String): File {
        require(id.matches(Regex("[a-f0-9]{32}")))
        return File(root, "export-queue/$id.properties")
    }
    fun clips(p: EditorProject): List<TimelineClip> = if (p.timelineMode) p.timelineClips else listOf(
        TimelineClip("selection", p.startMs, p.endMs, hook = p.hook, caption = p.caption,
            captionStartMs = p.drafts.getOrNull(p.selectedDraft)?.captionStartMs ?: p.startMs,
            captionEndMs = p.drafts.getOrNull(p.selectedDraft)?.captionEndMs ?: p.endMs, effect = p.effect,
            subtitles = if (p.selectedDraft >= 0 && p.subtitleDraftIndex == p.selectedDraft) p.currentSegments else emptyList(),
            source = p.source, sourceDurationMs = p.durationMs))
    fun forReview(item: QueuedExport): EditorProject = item.project.reviewedAgain().copy(
        projectId = ProjectCatalog.newId(), projectName = (item.project.projectName.take(60) + " queue review").trim()).also { it.validate() }
    @Synchronized fun add(root: File, project: EditorProject, approved: Boolean): QueuedExport {
        require(approved) { "Review and approve before adding to the queue" }
        require(list(root).size < LIMIT) { "Queue holds up to $LIMIT snapshots" }
        project.validate(); require(clips(project).isNotEmpty()) { "Add a clip first" }
        val item = QueuedExport(ProjectCatalog.newId(), project.reviewedAgain(), QueueStatus.READY, "Approved snapshot; waiting for Start")
        ProjectStore.write(file(root, item.id), item.project)
        record(root, item)
        return item
    }
    @Synchronized fun record(root: File, item: QueuedExport) {
        val state = Properties().apply {
            setProperty("status", item.status.name); setProperty("message", item.message.take(500)); setProperty("uri", item.uri)
        }
        val target = File(file(root, item.id).path + ".state")
        val temp = File(target.path + ".partial")
        try {
            temp.outputStream().use { stream -> state.store(stream, "Export progress; no persistent approval"); stream.fd.sync() }
            check(temp.renameTo(target)) { "Could not save queue progress" }
        } finally { temp.delete() }
    }
    @Synchronized fun list(root: File): List<QueuedExport> = File(root, "export-queue").listFiles().orEmpty()
        .filter { it.name.matches(Regex("[a-f0-9]{32}\\.properties")) }.sortedBy { it.lastModified() }.take(LIMIT)
        .mapNotNull { f -> runCatching {
            val p = ProjectStore.read(f) ?: return@runCatching null
            val state = Properties().apply { File(f.path + ".state").takeIf(File::isFile)?.inputStream()?.use { load(it) } }
            val complete = state.getProperty("status") == QueueStatus.COMPLETE.name
            QueuedExport(f.nameWithoutExtension, p, if (complete) QueueStatus.COMPLETE else QueueStatus.REVIEW,
                if (complete) "Export completed" else "Interrupted or restored: review and approve again", state.getProperty("uri", ""))
        }.getOrNull() }
    @Synchronized fun remove(root: File, id: String) {
        val f = file(root, id)
        f.delete(); ProjectStore.previousFile(f).delete(); File(f.path + ".state").delete()
    }
}

/** Runs on Main, processes one approved immutable snapshot, and cancels its Transformer on interruption. */
object QueueExporter {
    suspend fun render(context: Context, project: EditorProject, previewOnly:Boolean=false,onProgress: (Int) -> Unit): ExportPublisher.Published {
        project.validate()
        val clips = ExportQueue.clips(project)
        val music = project.music.takeIf { project.timelineMode }
        val sounds = withContext(Dispatchers.IO) {
            MediaAssets.checkTimeline(context, project.source, project.durationMs, clips, music)
            if (SoundTimeline.events(clips).any { it.asset == null }) SoundAssets.ensure(context.filesDir) else emptyMap()
        }
        val folder = (context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: File(context.filesDir, "exports")).apply { mkdirs() }
        val output = File(folder, "clipforge_queue_${ProjectCatalog.newId()}.mp4")
        var transformer: Transformer? = null
        val ticker = CoroutineScope(currentCoroutineContext()).launch {
            while (isActive) {
                val holder = ProgressHolder(); transformer?.getProgress(holder); onProgress(holder.progress.coerceIn(0, 99)); delay(400)
            }
        }
        try {
            suspendCancellableCoroutine<Unit> { continuation ->
                val instance = Transformer.Builder(context).setEncoderFactory(DefaultEncoderFactory.Builder(context).setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(project.exportQuality.bitrate).build()).build()).setVideoFrameProcessorFactory(TimelineExport.frameProcessorFactory())
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            if (continuation.isActive) continuation.resumeWithException(exportException)
                        }
                    }).build()
                transformer = instance
                continuation.invokeOnCancellation { android.os.Handler(android.os.Looper.getMainLooper()).post { instance.cancel() } }
                try {
                    instance.start(TimelineExport.composition(Uri.parse(project.source), clips, project.vertical, project.fillFrame,
                        project.mute, project.subtitleSize, if (project.subtitleYellow) android.graphics.Color.YELLOW else android.graphics.Color.WHITE,
                        music, CaptionPreset.valueOf(project.captionPreset), project.captionAppearance, sounds,project.exportQuality), output.path)
                } catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) }
            }
            val published = if(previewOnly) ExportPublisher.Published(ExportPublisher.privateUri(context,output),false) else withContext(Dispatchers.IO) { ExportPublisher.publish(context, output) }
            if (published.inGallery) output.delete()
            onProgress(100)
            return published
        } catch (error: Exception) { output.delete(); throw error }
        finally { ticker.cancel(); transformer?.cancel() }
    }
}


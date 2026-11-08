package com.clipforge.mobile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Application-level state: Activity recreation does not own or stop an inference job. */
object SpeechJobs {
    data class State(val job: SpeechJob? = null, val percent: Int = -1, val phase: String = "")
    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    private var initialized = false
    val workerActive = AtomicBoolean(false)
    val pauseRequested = AtomicBoolean(false)
    @Volatile private var began = 0L
    @Volatile private var elapsedAtStart = 0L
    fun file(context: Context) = File(context.filesDir, "speech/last-job.properties")

    @Synchronized fun initialize(context: Context) {
        if (initialized) return
        val saved = SpeechJobStore.read(file(context))?.recovered()
        if (saved != null) SpeechJobStore.write(file(context), saved)
        mutable.value = State(saved, phase = saved?.message.orEmpty())
        initialized = true
    }
    @Synchronized fun reloadAfterRestore(context: Context) {
        check(!workerActive.get()) { "Pause processing before restoring a backup" }
        initialized = false
        initialize(context)
    }
    @Synchronized fun relink(context: Context, previousSource: String, newSource: String, durationMs: Long) {
        check(!workerActive.get()) { "Pause processing before relinking" }
        val job = mutable.value.job ?: return
        if (job.source == previousSource && job.durationMs == durationMs) {
            val changed = job.copy(source = newSource).recovered()
            SpeechJobStore.write(file(context), changed)
            mutable.value = State(changed, phase = changed.message)
        }
    }
    @Synchronized fun submit(context: Context, request: SpeechJob) {
        initialize(context)
        check(!workerActive.get() && mutable.value.job?.active != true) { "A speech job is already active" }
        val previous = mutable.value.job
        if (previous != null && previous.id != request.id) SpeechJobStore.write(File(context.filesDir, "speech/archive/${previous.id}.properties"), previous)
        SpeechJobStore.write(file(context), request)
        mutable.value = State(request, phase = "Starting offline processing")
    }
    fun startService(context: Context) {
        try { ContextCompat.startForegroundService(context, Intent(context, SpeechProcessingService::class.java)) }
        catch (error: Exception) { fail(context, error.message ?: "Android could not start background processing") }
    }
    fun pause() { pauseRequested.set(true); mutable.value = mutable.value.copy(phase = "Pausing after the current audio window…") }
    fun elapsedMs(): Long = if (workerActive.get() && began > 0) elapsedAtStart + SystemClock.elapsedRealtime() - began else mutable.value.job?.elapsedMs ?: 0
    fun progress(phase: String, percent: Int) { mutable.value = mutable.value.copy(phase = phase, percent = percent) }
    fun fail(context: Context, message: String) {
        mutable.value.job?.copy(status = SpeechJobStatus.FAILED, elapsedMs = elapsedMs(), message = message)?.let {
            SpeechJobStore.write(file(context), it); mutable.value = State(it, phase = message)
        }
    }
    fun run(context: Context, updateNotification: (State) -> Unit, shouldPause: () -> Boolean = { false }): SpeechJob? {
        initialize(context)
        val initial = mutable.value.job ?: return null
        if (!workerActive.compareAndSet(false, true)) return null
        pauseRequested.set(false); began = SystemClock.elapsedRealtime(); elapsedAtStart = initial.elapsedMs
        var finished: SpeechJob? = null
        try {
            val running = initial.copy(status = SpeechJobStatus.RUNNING)
            SpeechJobStore.write(file(context), running); mutable.value = State(running, phase = "Preparing audio")
            val model = File(context.filesDir, "models/ggml-tiny.bin")
            finished = WhisperNative.withSession {
                SpeechJobRunner.run(running, { pauseRequested.get() || shouldPause() }, { unit ->
                    progress("Clip ${unit.clipIndex + 1} · preparing audio", -1); updateNotification(mutable.value)
                    OfflineTranscriber.range(context, Uri.parse(initial.source), model, unit.startMs, unit.endMs) { phase, percent ->
                        progress("Clip ${unit.clipIndex + 1} · $phase", percent); updateNotification(mutable.value)
                    }
                }, { checkpoint ->
                    val timed = checkpoint.copy(elapsedMs = elapsedMs())
                    SpeechJobStore.write(file(context), timed)
                    // Keep the job active until the model and wake lock can be released.
                    mutable.value = State(timed.copy(status = SpeechJobStatus.RUNNING), phase = timed.message)
                    updateNotification(mutable.value)
                    finished = timed
                })
            }.copy(elapsedMs = elapsedMs())
            SpeechJobStore.write(file(context), finished!!)
            return finished
        } catch (error: Throwable) {
            val failed = (finished ?: mutable.value.job ?: initial).copy(status = SpeechJobStatus.FAILED, elapsedMs = elapsedMs(), message = error.message ?: "Offline processing failed")
            SpeechJobStore.write(file(context), failed); finished = failed
            return failed
        } finally {
            workerActive.set(false); began = 0
            finished?.let { mutable.value = State(it, if (it.status == SpeechJobStatus.COMPLETED) 100 else -1, it.message) }
        }
    }
}

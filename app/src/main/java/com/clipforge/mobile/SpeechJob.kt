package com.clipforge.mobile

enum class SpeechJobMode { FIRST_30, SELECTED, HIGHLIGHTS, FULL_VIDEO, TIMELINE }
enum class SpeechJobStatus { QUEUED, RUNNING, PAUSED, COMPLETED, FAILED }
data class SpeechRange(val index: Int, val startMs: Long, val endMs: Long)
data class SpeechUnit(val clipIndex: Int, val startMs: Long, val endMs: Long) {
    val key: String get() = "$clipIndex:$startMs:$endMs"
}
data class SpeechJob(
    val id: String,
    val source: String,
    val durationMs: Long,
    val mode: SpeechJobMode,
    val ranges: List<SpeechRange>,
    val targetId: String = "",
    val status: SpeechJobStatus = SpeechJobStatus.QUEUED,
    val results: Map<String, List<WhisperNative.Segment>> = emptyMap(),
    val elapsedMs: Long = 0,
    val message: String = "Ready to transcribe",
    val language: String = "auto"
) {
    val active: Boolean get() = status == SpeechJobStatus.QUEUED || status == SpeechJobStatus.RUNNING
    fun validate() {
        require(language in listOf("auto", "en", "tl")) { "Unsupported speech language" }
        require(id.matches(Regex("[A-Za-z0-9-]{1,80}"))) { "Invalid speech job ID" }
        require(source.startsWith("content://") || source.startsWith("file://")) { "Speech source must be local" }
        require(durationMs in 1..86_400_000 && elapsedMs >= 0) { "Invalid speech duration" }
        require(ranges.size in 1..60 && ranges.map { it.index }.distinct().size == ranges.size) { "Invalid speech ranges" }
        ranges.forEach { require(it.index >= 0 && it.startMs >= 0 && it.endMs > it.startMs && it.endMs <= durationMs) { "Invalid speech interval" } }
        val units = units().associateBy { it.key }
        require(units.size <= 2880) { "Speech schedule is too large" }
        require(results.size <= units.size && results.keys.all { it in units }) { "Invalid speech checkpoint" }
        require(results.values.sumOf { it.size } <= 100_000) { "Speech checkpoint too large" }
        results.forEach { (key, segments) ->
            val unit = units.getValue(key)
            segments.forEach { require(it.startMs >= unit.startMs && it.endMs > it.startMs && it.endMs <= unit.endMs) { "Subtitle outside its speech window" } }
        }
    }
    fun units(): List<SpeechUnit> = ranges.flatMap { range ->
        val output = mutableListOf<SpeechUnit>()
        var cursor = range.startMs
        while (cursor < range.endMs) {
            val end = minOf(range.endMs, cursor + 30_000)
            output.add(SpeechUnit(range.index, cursor, end)); cursor = end
        }
        output
    }
    fun groupedResults(completeRangesOnly: Boolean = false): Map<Int, List<WhisperNative.Segment>> = ranges.mapNotNull { range ->
        val units = units().filter { it.clipIndex == range.index }
        if (completeRangesOnly && units.any { it.key !in results }) null
        else range.index to units.flatMap { results[it.key].orEmpty() }.sortedBy { it.startMs }
    }.toMap()
    fun recovered(): SpeechJob = if (active) copy(status = SpeechJobStatus.PAUSED, message = "Interrupted. Resume from completed audio windows.") else this
}

/** Pure checkpoint runner, independent of Android service lifecycle and inference implementation. */
object SpeechJobRunner {
    fun run(
        initial: SpeechJob,
        stopRequested: () -> Boolean,
        transcribe: (SpeechUnit) -> List<WhisperNative.Segment>,
        checkpoint: (SpeechJob) -> Unit
    ): SpeechJob {
        initial.validate()
        var job = initial.copy(status = SpeechJobStatus.RUNNING)
        try {
            for (unit in job.units()) {
                if (stopRequested()) return job.copy(status = SpeechJobStatus.PAUSED, message = "Paused. Completed windows are saved.").also(checkpoint)
                if (unit.key in job.results) continue
                val segments = transcribe(unit)
                job = job.copy(results = job.results + (unit.key to segments), message = "Saved ${job.results.size + 1}/${job.units().size} audio windows")
                job.validate()
                checkpoint(job)
            }
            return job.copy(status = SpeechJobStatus.COMPLETED, message = "Offline transcription complete").also(checkpoint)
        } catch (error: Exception) {
            return job.copy(status = SpeechJobStatus.FAILED, message = error.message ?: "Speech processing failed").also(checkpoint)
        }
    }
}

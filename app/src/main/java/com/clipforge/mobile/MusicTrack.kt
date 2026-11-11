package com.clipforge.mobile

/** One local music bed with optional fit/loop and dialogue ducking. */
data class MusicTrack(
    val source: String, val name: String, val durationMs: Long,
    val startMs: Long = 0, val endMs: Long = durationMs, val timelineStartMs: Long = 0,
    val volume: Float = .25f, val fadeInMs: Long = 500, val fadeOutMs: Long = 500,
    val loop: Boolean = false, val ducking: Boolean = false, val duckFloor: Float = .25f,
    val bpm: Int = 0, val origin: String = "Imported local music · verify reuse permission", val mood: String = "",
    val beats: List<Long> = emptyList(), val libraryId: String = "", val muted:Boolean=false, val locked:Boolean=false
) {
    fun validate() {
        require(source.startsWith("content://") || source.startsWith("file://")) { "Music must be a local file" }
        require(durationMs in 1..86_400_000 && Timecode.validTrim(startMs, endMs, durationMs)) { "Invalid music trim" }
        require(timelineStartMs in 0..21_600_000 && volume.isFinite() && volume in 0f..1f) { "Invalid music placement or volume" }
        require(duckFloor.isFinite() && duckFloor in .05f..1f && bpm in 0..300 && origin.length<=500 && mood.length<=40)
        require(libraryId.length<=80 && libraryId.all { it.isLetterOrDigit() || it=='-' })
        require(beats.size<=500 && beats.all { it in 0..durationMs })
        require(!loop || endMs-startMs>=1000) { "Loop at least one second of music" }
        require(fadeInMs in 0..10_000 && fadeOutMs in 0..10_000) { "Invalid music fades" }
    }
    fun activeDuration(sequenceMs: Long): Long = if(loop) (sequenceMs-timelineStartMs).coerceAtLeast(0) else minOf(endMs-startMs,(sequenceMs-timelineStartMs).coerceAtLeast(0))
    fun sourcePosition(ms:Long):Long=if(loop)ms.coerceAtLeast(0)%(endMs-startMs) else ms.coerceIn(0,endMs-startMs)
    fun beatMarkers(sequenceMs:Long):List<Long>{
        val base=if(bpm>0) generateSequence(0){it+1}.take(5000).map{(it*60000.0/bpm).toLong()}.takeWhile{it<endMs}.toList() else beats
        val out=mutableListOf<Long>();var cursor=0L
        while(cursor<activeDuration(sequenceMs)&&out.size<300){base.filter{it in startMs until endMs}.forEach{t->val at=timelineStartMs+cursor+t-startMs;if(at<timelineStartMs+activeDuration(sequenceMs)&&out.size<300)out.add(at)};if(!loop)break;cursor+=endMs-startMs};return out
    }
    fun envelope(sequenceMs: Long): TimelineClip? = activeDuration(sequenceMs).takeIf { it > 0 }?.let {
        TimelineClip("music", 0, it, volume = if(muted)0f else volume, fadeInMs = minOf(fadeInMs, it), fadeOutMs = minOf(fadeOutMs, it))
    }
    fun gain(sequencePositionMs: Long, sequenceMs: Long, activity: List<AudioRegion> = emptyList()): Float {
        if(muted)return 0f
        val active = activeDuration(sequenceMs)
        val local = sequencePositionMs - timelineStartMs
        if (local < 0 || local >= active) return 0f
        val fadeIn = minOf(fadeInMs, active)
        val fadeOut = minOf(fadeOutMs, active)
        val attack = if (fadeIn > 0) (local.toDouble() / fadeIn).coerceIn(0.0, 1.0) else 1.0
        val release = if (fadeOut > 0) ((active-local).toDouble() / fadeOut).coerceIn(0.0, 1.0) else 1.0
        return (volume * minOf(attack, release)).toFloat() * if(ducking) AudioPlanning.duck(sequencePositionMs,activity,duckFloor) else 1f
    }
}


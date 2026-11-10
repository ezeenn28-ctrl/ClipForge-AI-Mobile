package com.clipforge.mobile

import java.io.File
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID
import kotlin.math.*

enum class SoundKind(val label: String, val durationMs: Long) {
    WHOOSH("Whoosh", 650), POP("Pop", 220), IMPACT("Impact", 550),
    CHIME("Metal chime", 700), BOING("Boing", 650), SCRATCH("Scratch", 450)
}
data class ClipSound(val id: String, val sourceMs: Long, val kind: SoundKind, val volume: Float = .3f) {
    fun validate(sourceDuration: Long) {
        require(id.matches(Regex("[A-Za-z0-9-]{1,80}"))) { "Invalid sound effect ID" }
        require(sourceMs in 0 until sourceDuration && volume.isFinite() && volume in 0f..1f) { "Invalid sound effect timing or volume" }
    }
}
data class TimedSound(val id: String, val startMs: Long, val durationMs: Long, val kind: SoundKind, val volume: Float) {
    val endMs: Long get() = startMs + durationMs
}
object SoundTimeline {
    fun events(clips: List<TimelineClip>): List<TimedSound> {
        val raw = clips.flatMapIndexed { index, clip ->
            val offset = TimelineModel.offset(clips, index)
            clip.sounds.filter { it.sourceMs in clip.startMs until clip.endMs && it.volume > 0 }.mapNotNull { cue ->
                val local = clip.outputOffsetMs(cue.sourceMs - clip.startMs)
                val available = clip.durationMs - local
                if (available <= 0) null else TimedSound(clip.id + ":" + cue.id, offset + local,
                    minOf(cue.kind.durationMs, available), cue.kind, cue.volume)
            }
        }.sortedWith(compareBy<TimedSound> { it.startMs }.thenBy { it.id })
        // One sparse effects lane: nearby effects are shortened, never stacked uncontrollably.
        return raw.mapIndexedNotNull { index, sound ->
            val next = raw.getOrNull(index + 1)?.startMs ?: Long.MAX_VALUE
            val length = minOf(sound.durationMs, next - sound.startMs)
            if (length < 40) null else sound.copy(durationMs = length)
        }
    }
    fun active(events: List<TimedSound>, positionMs: Long): TimedSound? =
        events.lastOrNull { positionMs >= it.startMs && positionMs < it.endMs }

    fun suggest(clip: TimelineClip): List<ClipSound> {
        val result = mutableListOf(ClipSound(UUID.randomUUID().toString(), clip.startMs, SoundKind.WHOOSH, .22f))
        clip.subtitles.filter { it.endMs > clip.startMs && it.startMs < clip.endMs }.forEach { cue ->
            if (result.size >= 4) return@forEach
            val text = cue.text.lowercase(Locale.ROOT)
            val kind = when {
                Regex("\\b(no way|fight|sword|grabe|what the)\\b").containsMatchIn(text) -> SoundKind.IMPACT
                Regex("\\b(oh|wow|really|hala|teka|wait)\\b").containsMatchIn(text) -> SoundKind.POP
                else -> null
            }
            val at = cue.startMs.coerceAtLeast(clip.startMs)
            if (kind != null && at - result.last().sourceMs >= (900 * clip.speed).toLong())
                result.add(ClipSound(UUID.randomUUID().toString(), at, kind, .3f))
        }
        return result
    }
}

/** Original procedural PCM effects. No samples are copied from reference footage. */
object SoundAssets {
    const val RATE = 48000
    @Synchronized fun ensure(directory: File): Map<SoundKind, String> {
        val root = File(directory, "sound-effects").apply { mkdirs() }
        return SoundKind.entries.associateWith { kind ->
            val target = File(root, kind.name.lowercase(Locale.ROOT) + "-v1.wav")
            val expected = 44 + (kind.durationMs * RATE / 1000).toInt() * 2
            if (!target.isFile || target.length() != expected.toLong()) {
                val temporary = File(root, target.name + ".partial")
                try {
                    temporary.outputStream().use { stream -> stream.write(wave(kind)) }
                    check(temporary.renameTo(target)) { "Could not prepare sound effect" }
                } finally { temporary.delete() }
            }
            URI("file", "", target.absolutePath, null).toASCIIString()
        }
    }
    fun wave(kind: SoundKind): ByteArray {
        val count = (kind.durationMs * RATE / 1000).toInt()
        val out = ByteBuffer.allocate(44 + count * 2).order(ByteOrder.LITTLE_ENDIAN)
        out.put("RIFF".toByteArray()).putInt(36 + count * 2).put("WAVEfmt ".toByteArray())
        out.putInt(16).putShort(1.toShort()).putShort(1.toShort()).putInt(RATE).putInt(RATE * 2)
        out.putShort(2.toShort()).putShort(16.toShort()).put("data".toByteArray()).putInt(count * 2)
        var noise = 173L + kind.ordinal
        repeat(count) { index ->
            val t = index.toDouble() / RATE
            val x = index.toDouble() / (count - 1)
            noise = (noise * 1664525 + 1013904223) and 0xffffffffL
            val random = ((noise ushr 8).toDouble() / 0xffffff) * 2 - 1
            val envelope = sin(PI * x).pow(2)
            val sample = when (kind) {
                SoundKind.WHOOSH -> random * envelope * (.6 + .4 * sin(2 * PI * (550 * t + 1400 * t * t)))
                SoundKind.POP -> sin(2 * PI * (850 * t - 500 * t * t)) * exp(-14 * x) * minOf(1.0, x * 100)
                SoundKind.IMPACT -> (sin(2 * PI * (75 * t + 30 * t * t)) * .8 + random * .2) * exp(-7 * x) * minOf(1.0, x * 80)
                SoundKind.CHIME -> (sin(2 * PI * 880 * t) + .45 * sin(2 * PI * 1423 * t) + .2 * sin(2 * PI * 2341 * t)) / 1.65 * exp(-5 * x) * minOf(1.0, x * 90)
                SoundKind.BOING -> sin(2 * PI * (220 * t + 30 * sin(2 * PI * 5 * t))) * exp(-4 * x) * minOf(1.0, x * 80)
                SoundKind.SCRATCH -> (sin(2 * PI * (1800 * t - 1400 * t * t)) * .65 + random * .35) * envelope
            }
            out.putShort((sample * .42 * Short.MAX_VALUE).roundToInt().coerceIn(-32767, 32767).toShort())
        }
        return out.array()
    }
}

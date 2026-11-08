package com.clipforge.mobile

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WhisperNative {
    init { System.loadLibrary("clipforge_whisper") }
    external fun transcribeSegmentsNative(modelPath: String, audio: FloatArray): String
    data class Segment(val startMs: Long, val endMs: Long, val text: String)
    fun transcribeSegments(model: File, wav: File, startMs: Long, endMs: Long): List<Segment> {
        val raw = transcribeWav(model, wav, startMs, endMs)
        if (raw.startsWith("ERROR:")) error(raw)
        return raw.lineSequence().mapNotNull { line ->
            val fields = line.split('\t', limit = 3)
            if (fields.size != 3) null else {
                val from = fields[0].toLongOrNull()
                val to = fields[1].toLongOrNull()
                if (from == null || to == null) null
                else Segment(startMs + from, startMs + to, fields[2].trim())
            }
        }.filter { it.text.isNotBlank() }.toList()
    }

    /** Reads up to 30 seconds of 16-bit mono WAV and resamples to Whisper's 16 kHz input. */
    fun transcribeWav(model: File, wav: File, startMs: Long = 0L, endMs: Long = 30_000L): String {
        require(model.isFile) { "Download Whisper model first" }
        RandomAccessFile(wav, "r").use { file ->
            val header = ByteArray(44)
            file.readFully(header)
            val h = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            require(String(header, 0, 4) == "RIFF" && String(header, 8, 4) == "WAVE") { "Invalid WAV" }
            require(h.getShort(22).toInt() == 1 && h.getShort(34).toInt() == 16) { "Expected mono PCM16 WAV" }
            val sampleRate = h.getInt(24)
            require(sampleRate in 8000..192000) { "Unsupported sample rate" }
            val totalSamples = ((file.length() - 44) / 2).coerceAtLeast(0)
            val first = (startMs * sampleRate / 1000).coerceIn(0, totalSamples)
            val last = (endMs * sampleRate / 1000).coerceIn(first, totalSamples)
            val count = (last - first).toInt()
            require(count > 0 && count <= sampleRate * 31) { "Select a nonempty 30-second segment" }
            val bytes = ByteArray(count * 2)
            file.seek(44 + first * 2)
            file.readFully(bytes)
            val input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val targetCount = (count.toLong() * 16000 / sampleRate).toInt()
            val samples = FloatArray(targetCount) { i ->
                val position = i.toDouble() * sampleRate / 16000.0
                val a = position.toInt().coerceIn(0, count - 1)
                val b = (a + 1).coerceAtMost(count - 1)
                val fraction = (position - a).toFloat()
                (input.get(a) * (1f - fraction) + input.get(b) * fraction) / 32768f
            }
            return transcribeSegmentsNative(model.absolutePath, samples)
        }
    }
}

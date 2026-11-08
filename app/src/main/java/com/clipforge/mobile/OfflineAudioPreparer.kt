package com.clipforge.mobile

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Extracts decoded audio from an imported video into a local mono 16-bit PCM WAV.
 * This prepares audio for a future native Whisper inference bridge; it does NOT transcribe. */
object OfflineAudioPreparer {
    fun extractWav(context: Context, uri: Uri, destination: File): File {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("Video has no audio track")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("Unknown audio format")
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
            destination.parentFile?.mkdirs()
            val wav = RandomAccessFile(destination, "rw")
            wav.setLength(0)
            wav.write(ByteArray(44))
            var writtenBytes = 0L
            var pendingSampleBytes = ByteArray(0)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = android.media.AudioFormat.ENCODING_PCM_16BIT
            try {
            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(10000)
                    if (inputIndex >= 0) {
                        val buffer = codec.getInputBuffer(inputIndex) ?: error("Missing input buffer")
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                when (val index = codec.dequeueOutputBuffer(info, 10000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val actual = codec.outputFormat
                        sampleRate = actual.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = actual.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        if (actual.containsKey(MediaFormat.KEY_PCM_ENCODING))
                            encoding = actual.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        require(encoding == android.media.AudioFormat.ENCODING_PCM_16BIT) {
                            "Unsupported PCM encoding: $encoding"
                        }
                    }
                    else -> if (index >= 0) {
                        val buffer = codec.getOutputBuffer(index) ?: error("Missing output buffer")
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        val bytes = ByteArray(info.size)
                        buffer.get(bytes)
                        require(channels > 0) { "Invalid channel count" }
                        require(encoding == android.media.AudioFormat.ENCODING_PCM_16BIT) { "Unsupported PCM encoding: $encoding" }
                        val merged = pendingSampleBytes + bytes
                        val completeBytes = (merged.size / (channels * 2)) * (channels * 2)
                        pendingSampleBytes = merged.copyOfRange(completeBytes, merged.size)
                        val samples = ByteBuffer.wrap(merged, 0, completeBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val frames = samples.remaining() / channels
                        val mono = ByteArray(frames * 2)
                        for (frame in 0 until frames) {
                            var sum = 0
                            for (channel in 0 until channels) sum += samples.get(frame * channels + channel).toInt()
                            val value = sum / channels
                            mono[frame * 2] = value.toByte()
                            mono[frame * 2 + 1] = (value ushr 8).toByte()
                        }
                        wav.write(mono)
                        writtenBytes += mono.size
                        codec.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
            require(pendingSampleBytes.isEmpty()) { "Incomplete final audio sample" }
            require(sampleRate > 0 && channels > 0) { "Invalid decoded audio format" }
            require(writtenBytes <= Int.MAX_VALUE - 36L) { "Audio too long for standard WAV format" }
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt((36L + writtenBytes).toInt())
            header.put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16); header.putShort(1); header.putShort(1)
            header.putInt(sampleRate); header.putInt(sampleRate * 2)
            header.putShort(2); header.putShort(16)
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(writtenBytes.toInt())
            wav.seek(0)
            wav.write(header.array())
            } finally {
                wav.close()
            }
            return destination
        } finally {
            codec?.stop()
            codec?.release()
            extractor.release()
        }
    }
}

package com.clipforge.mobile

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.Properties
import java.util.UUID
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

data class SoundAsset(val id: String, val name: String, val source: String, val durationMs: Long,
                      val origin: String = "Imported", val page: String = "") {
    fun validate() {
        require(id.matches(Regex("[A-Za-z0-9-]{1,80}")) && name.isNotBlank() && name.length <= 100) { "Invalid library sound" }
        require(source.startsWith("file://") && durationMs in 40..15_000) { "Sound must be a local file, 0.04–15 seconds" }
        require(origin in listOf("Imported", "Myinstants") && (page.isEmpty() || MyinstantsUrls.isPage(page))) { "Invalid sound source" }
    }
}

object SoundLibrary {
    const val MAX_BYTES = 10 * 1024 * 1024
    private fun root(files: File) = File(files, "sound-library").apply { mkdirs() }
    @Synchronized fun list(files: File): List<SoundAsset> =
        root(files).listFiles().orEmpty().filter { it.extension == "properties" }.sortedByDescending { it.lastModified() }
            .take(200).mapNotNull { file -> runCatching {
                val p = Properties().apply { file.reader(Charsets.UTF_8).use { load(it) } }
                SoundAsset(p.getProperty("id"), p.getProperty("name"), p.getProperty("source"), p.getProperty("duration").toLong(),
                    p.getProperty("origin", "Imported"), p.getProperty("page", "")).also { it.validate() }
            }.getOrNull() }
    @Synchronized fun save(files: File, asset: SoundAsset) {
        asset.validate()
        val p = Properties().apply {
            setProperty("id", asset.id); setProperty("name", asset.name); setProperty("source", asset.source)
            setProperty("duration", asset.durationMs.toString()); setProperty("origin", asset.origin); setProperty("page", asset.page)
        }
        val target = File(root(files), asset.id + ".properties")
        val temp = File(target.parentFile, target.name + ".partial")
        try {
            temp.outputStream().use { stream ->
                val writer = java.io.OutputStreamWriter(stream, Charsets.UTF_8)
                p.store(writer, "ClipForge sound"); writer.flush(); stream.fd.sync()
            }
            check(temp.renameTo(target)) { "Could not save sound library" }
        } finally { temp.delete() }
    }
    fun importSound(context: Context, uri: Uri, name: String? = null, origin: String = "Imported", page: String = ""): SoundAsset {
        val directory = root(context.filesDir)
        require(list(context.filesDir).size < 200) { "Sound library is full (200 sounds)" }
        val id = UUID.randomUUID().toString()
        val input = File(directory, "$id.input")
        val output = File(directory, "$id.wav")
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                input.outputStream().use { destination ->
                    val buffer = ByteArray(32 * 1024); var total = 0
                    while (true) {
                        val count = stream.read(buffer); if (count < 0) break
                        total += count; require(total <= MAX_BYTES) { "Choose a sound smaller than 10 MB" }
                        destination.write(buffer, 0, count)
                    }
                }
            } ?: error("Could not open sound")
            val info = MediaAssets.inspect(context, Uri.fromFile(input), false)
            require(info.durationMs in 40..15_000) { "Choose a sound between 0.04 and 15 seconds" }
            OfflineAudioPreparer.extractWav(context, Uri.fromFile(input), output, 0, 15_000)
            tameWave(output)
            val duration = MediaAssets.inspect(context, Uri.fromFile(output), false).durationMs
            val display = (name ?: MediaAssets.inspect(context, uri, false).name).replace(Regex("[\\p{Cntrl}]"), " ").trim().take(100).ifBlank { "Imported sound" }
            return SoundAsset(id, display, Uri.fromFile(output).toString(), duration, origin, page).also { save(context.filesDir, it) }
        } catch (error: Throwable) { output.delete(); throw error }
        finally { input.delete() }
    }
    /** Tame boosted uploads without raising quiet sounds. Keep user volume independent. */
    fun tameWave(file: File) {
        require(file.length() in 46..2_000_000) { "Invalid decoded sound size" }
        val bytes = file.readBytes()
        require(String(bytes, 0, 4) == "RIFF" && String(bytes, 36, 4) == "data") { "Invalid PCM sound" }
        val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(data.getShort(22).toInt() == 1 && data.getShort(34).toInt() == 16) { "Mono PCM sound required" }
        var peak = 0; var energy = 0.0
        val samples = (bytes.size - 44) / 2
        repeat(samples) { i -> val value = data.getShort(44 + i * 2).toInt(); peak = maxOf(peak, abs(value)); energy += value.toDouble() * value }
        val rms = kotlin.math.sqrt(energy / samples)
        val gain = minOf(1.0, 26_000.0 / maxOf(1, peak), 7_000.0 / maxOf(1.0, rms))
        val edge = maxOf(1, data.getInt(24) / 200)
        repeat(samples) { i ->
            val envelope = minOf(1.0, i.toDouble() / edge, (samples - 1 - i).toDouble() / edge)
            data.putShort(44 + i * 2, (data.getShort(44 + i * 2) * gain * envelope).toInt().toShort())
        }
        file.writeBytes(bytes)
    }
}

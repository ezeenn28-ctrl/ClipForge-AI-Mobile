package com.clipforge.mobile

import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object LocalVideoDownload {
    /** Never overwrite the currently selected video with a partial/failed download. */
    fun fetch(address: String, directory: File): File {
        require(URL(address).protocol == "https") { "Use an HTTPS video link" }
        directory.mkdirs()
        val temporary = File.createTempFile("clipforge_source_", ".partial", directory)
        val destination = File(directory, temporary.name.removeSuffix(".partial") + ".mp4")
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            connection.connect()
            require(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
            val type = connection.contentType.orEmpty()
            require(type.startsWith("video/") || type.startsWith("application/octet-stream")) {
                "Link must return a video file. Download the video first, then import it."
            }
            val expected = connection.contentLengthLong
            require(expected <= 1_000_000_000) { "Video exceeds the 1 GB limit" }
            var total = 0L
            connection.inputStream.use { input ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(65_536)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 1_000_000_000) { "Video exceeds the 1 GB limit" }
                        require(directory.usableSpace > 16L * 1024 * 1024) { "Insufficient storage for download" }
                        output.write(buffer, 0, count)
                    }
                }
            }
            require(total > 0 && (expected < 0 || total == expected)) { "Incomplete video download" }
            check(temporary.renameTo(destination)) { "Could not finalize downloaded video" }
            return destination
        } finally {
            connection.disconnect()
            temporary.delete()
        }
    }
}

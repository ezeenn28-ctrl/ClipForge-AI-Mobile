package com.clipforge.mobile

import org.json.JSONObject
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.net.HttpURLConnection
import java.io.ByteArrayOutputStream

object MyinstantsUrls {
    private fun valid(value: String, prefix: String): Boolean = runCatching {
        val u = URI(value)
        u.scheme == "https" && u.host in listOf("www.myinstants.com", "myinstants.com") &&
            u.userInfo == null && u.port in listOf(-1,443) && u.fragment == null &&
            u.path.startsWith(prefix) && !u.path.contains("..") && u.normalize().path == u.path
    }.getOrDefault(false)
    fun isAudio(value: String) = valid(value, "/media/sounds/") && runCatching { URI(value).path.endsWith(".mp3", true) }.getOrDefault(false)
    fun isPage(value: String) = valid(value, "/en/instant/")
    fun isApi(value: String) = valid(value, "/api/v1/instants/")
}
data class OnlineSound(val name: String, val slug: String, val audio: String) {
    val page: String get() = "https://www.myinstants.com/en/instant/$slug/"
}
data class SoundSearchPage(val sounds: List<OnlineSound>, val nextPage: Int?)
data class SoundHttpResponse(val status: Int, val bytes: ByteArray, val retryAfterSeconds: Int = 60)
fun interface SoundTransport { fun get(url: String, maxBytes: Int): SoundHttpResponse }
class SoundHttpTransport : SoundTransport {
    override fun get(url: String, maxBytes: Int): SoundHttpResponse {
        require(MyinstantsUrls.isApi(url) || MyinstantsUrls.isAudio(url)) { "Unsupported sound URL" }
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000; connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", if (MyinstantsUrls.isApi(url)) "application/json" else "audio/mpeg")
            connection.setRequestProperty("User-Agent", "ClipForgeAI/1.2 (personal sound library)")
            val status = connection.responseCode
            if (status != 200) return SoundHttpResponse(status, byteArrayOf(), connection.getHeaderField("Retry-After")?.toIntOrNull()?.coerceIn(1,3600) ?: 60)
            require(connection.contentLengthLong <= maxBytes || connection.contentLengthLong == -1L) { "Sound response is too large" }
            val data = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val count = input.read(buffer); if (count < 0) break
                    require(data.size() + count <= maxBytes) { "Sound response is too large" }
                    data.write(buffer, 0, count)
                }
            }
            return SoundHttpResponse(status, data.toByteArray())
        } finally { connection.disconnect() }
    }
}
class SoundRequestGate {
    private val requests = java.util.ArrayDeque<Long>()
    private var blockedUntil = 0L
    @Synchronized fun acquire(now: Long) {
        while (requests.isNotEmpty() && now - requests.first > 60_000) requests.removeFirst()
        require(now >= blockedUntil) { "Myinstants asks you to wait ${(blockedUntil - now + 999) / 1000} seconds" }
        require(requests.size < 8) { "Please wait a minute before requesting more sounds" }
        requests.addLast(now)
    }
    @Synchronized fun blocked(now: Long, seconds: Int) { blockedUntil = maxOf(blockedUntil, now + seconds.coerceIn(1,3600) * 1000L) }
}
class MyinstantsClient(private val transport: SoundTransport = SoundHttpTransport(),
                       private val gate: SoundRequestGate = sharedGate,
                       private val now: () -> Long = System::currentTimeMillis) {
    companion object { private val sharedGate = SoundRequestGate() }
    fun search(query: String, page: Int = 1): SoundSearchPage {
        val clean = query.trim()
        require(clean.length in 2..80 && page in 1..100) { "Search using 2–80 characters" }
        val url = "https://www.myinstants.com/api/v1/instants/?format=json&name=" + URLEncoder.encode(clean, "UTF-8") + "&page=$page"
        val response = request(url, 1024 * 1024)
        return parse(response.bytes.toString(Charsets.UTF_8), page)
    }
    fun download(sound: OnlineSound): ByteArray {
        require(MyinstantsUrls.isAudio(sound.audio)) { "Invalid Myinstants sound link" }
        val bytes = request(sound.audio, SoundLibrary.MAX_BYTES).bytes
        require(bytes.size >= 16 && (String(bytes, 0, 3) == "ID3" ||
            ((bytes[0].toInt() and 255) == 255 && (bytes[1].toInt() and 224) == 224))) { "The response is not an MP3 sound" }
        return bytes
    }
    private fun request(url: String, max: Int): SoundHttpResponse {
        gate.acquire(now())
        val response = transport.get(url, max)
        if (response.status == 429) { gate.blocked(now(), response.retryAfterSeconds); error("Myinstants rate limit. Wait ${response.retryAfterSeconds} seconds and try again.") }
        require(response.status == 200) { "Myinstants unavailable (HTTP ${response.status}). Try again later or import an MP3." }
        require(response.bytes.size <= max) { "Sound response is too large" }
        return response
    }
    fun parse(json: String, page: Int): SoundSearchPage {
        val root = JSONObject(json); val results = root.getJSONArray("results")
        val sounds = (0 until minOf(10, results.length())).mapNotNull { i ->
            val item = results.getJSONObject(i)
            val slug = item.optString("slug")
            val audio = item.optString("sound")
            val name = item.optString("name").replace(Regex("[\\p{Cntrl}]"), " ").trim().take(100)
            if (name.isBlank() || !slug.matches(Regex("[a-z0-9-]{1,160}")) || !MyinstantsUrls.isAudio(audio)) null
            else OnlineSound(name, slug, audio)
        }
        return SoundSearchPage(sounds.distinctBy { it.slug }, if (!root.isNull("next") && page < 100 && sounds.isNotEmpty()) page + 1 else null)
    }
}

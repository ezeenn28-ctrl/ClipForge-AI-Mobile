package com.clipforge.mobile

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SoundLibraryTest {
    @Test fun customEffectPreviewUsesTheLocalAssetWithoutBuiltinFiles() {
        var loaded = ""; var playing = false
        val player = java.lang.reflect.Proxy.newProxyInstance(androidx.media3.common.Player::class.java.classLoader,
            arrayOf(androidx.media3.common.Player::class.java)) { _, method, args ->
            when (method.name) {
                "setMediaItem" -> { loaded = (args!![0] as androidx.media3.common.MediaItem).localConfiguration!!.uri.toString(); null }
                "getCurrentPosition" -> 0L
                "setPlayWhenReady" -> { playing = args!![0] as Boolean; null }
                "pause" -> { playing = false; null }
                else -> null
            }
        } as androidx.media3.common.Player
        val asset = SoundAsset("asset", "Local effect", "file:///private/effect.wav", 400)
        val event = TimedSound("cue", 1000, 400, SoundKind.POP, .4f, asset)
        SoundPreview(player).tick(listOf(event), 1100, true, emptyMap())
        assertEquals(asset.source, loaded); assertTrue(playing)
    }
    private val json = """{"next":"https://www.myinstants.com/api/v1/instants/?name=boom&page=2","results":[{"name":"VINE BOOM SOUND","slug":"vine-boom-sound-70972","sound":"https://www.myinstants.com/media/sounds/vine-boom.mp3"}]}"""
    private fun client(body: String = json, capture: (String) -> Unit = {}) =
        MyinstantsClient(SoundTransport { url, _ -> capture(url); SoundHttpResponse(200, body.toByteArray()) }, SoundRequestGate()) { 1000L }
    @Test fun officialSearchSchemaRetainsSoundNameAudioAndSourcePage() {
        val result = client().search("vine boom")
        assertEquals(2, result.nextPage)
        assertEquals("VINE BOOM SOUND", result.sounds.single().name)
        assertEquals("https://www.myinstants.com/en/instant/vine-boom-sound-70972/", result.sounds.single().page)
    }
    @Test fun queryIsEncodedAndPaginationDoesNotFollowRemoteNextUrls() {
        var requested = ""
        val c = client(json.replace("https://www.myinstants.com/api/v1/instants/?name=boom&page=2", "https://evil.example/steal")) { requested = it }
        val result = c.search("boom & pop", 3)
        assertTrue(requested.contains("name=boom+%26+pop")); assertTrue(requested.endsWith("page=3")); assertEquals(4, result.nextPage)
    }
    @Test fun rejectsOffsiteCredentialsInsecureAndTraversalAudio() {
        for (url in listOf("http://www.myinstants.com/media/sounds/a.mp3", "https://www.myinstants.com.evil.example/media/sounds/a.mp3",
            "https://name@www.myinstants.com/media/sounds/a.mp3", "https://www.myinstants.com/media/sounds/../a.mp3",
            "https://www.myinstants.com/media/sounds/%2e%2e/a.mp3", "https://www.myinstants.com:8080/media/sounds/a.mp3")) assertFalse(url, MyinstantsUrls.isAudio(url))
        assertTrue(MyinstantsUrls.isAudio("https://www.myinstants.com/media/sounds/vine-boom.mp3"))
    }
    @Test fun parserFiltersInvalidSoundLinksAndSlugs() {
        val bad = json.replace("vine-boom-sound-70972", "../broken").replace("https://www.myinstants.com/media/sounds/", "https://evil.example/")
        assertTrue(client(bad).search("boom").sounds.isEmpty())
    }
    @Test fun localRequestGateBoundsAnonymousRequests() {
        val gate = SoundRequestGate()
        repeat(8) { gate.acquire(1000) }
        try { gate.acquire(1001); fail("Limit ignored") } catch (_: IllegalArgumentException) {}
        gate.acquire(61_001)
    }
    @Test fun honorsServerRetryAfterWithoutAutomaticRetries() {
        var clock = 1000L; var calls = 0
        val c = MyinstantsClient(SoundTransport { _, _ -> calls++; SoundHttpResponse(429, byteArrayOf(), 120) }, SoundRequestGate()) { clock }
        repeat(2) { try { c.search("boom"); fail("Throttle ignored") } catch (_: Exception) {} }
        assertEquals(1, calls)
        clock += 120_001
        try { c.search("boom") } catch (_: Exception) {}
        assertEquals(2, calls)
    }
    @Test fun rejectsHtmlDownloadsRedirectsAndOversizedResponses() {
        val sound = OnlineSound("test", "test", "https://www.myinstants.com/media/sounds/test.mp3")
        val html = MyinstantsClient(SoundTransport { _,_ -> SoundHttpResponse(200, "<html>not an audio file</html>".toByteArray()) }, SoundRequestGate())
        try { html.download(sound); fail("HTML treated as MP3") } catch (_: IllegalArgumentException) {}
        val redirect = MyinstantsClient(SoundTransport { _,_ -> SoundHttpResponse(302, byteArrayOf()) }, SoundRequestGate())
        try { redirect.search("boom"); fail("Redirect followed") } catch (_: IllegalArgumentException) {}
        val huge = MyinstantsClient(SoundTransport { _,max -> SoundHttpResponse(200, ByteArray(max + 1)) }, SoundRequestGate())
        try { huge.search("boom"); fail("Oversize accepted") } catch (_: IllegalArgumentException) {}
    }
    @Test fun importedSoundAnchorsSaveSplitAndRetainTheirOwnDurationThroughSpeed() {
        val directory = Files.createTempDirectory("sound-project").toFile()
        try {
            val asset = SoundAsset("asset", "My sound", "file:///private/sound.wav", 1300, "Myinstants", "https://www.myinstants.com/en/instant/test/")
            val clip = TimelineClip("clip", 0, 5000, sounds = listOf(ClipSound("cue", 2000, SoundKind.POP, .4f, asset)))
            val project = EditorProject("content://videos/1", 5000, 0, 5000, timelineMode = true, timelineClips = listOf(clip))
            val file = directory.resolve("project.properties")
            ProjectStore.write(file, project)
            assertEquals(asset, ProjectStore.read(file)!!.timelineClips.single().sounds.single().asset)
            val event = SoundTimeline.events(listOf(clip.withSpeed(2f))).single()
            assertEquals(1000L, event.startMs); assertEquals(1300L, event.durationMs); assertEquals(asset, event.asset)
            val split = TimelineModel.split(listOf(clip), 2000, "right")
            assertEquals(asset, split[1].sounds.single().asset); assertTrue(split[0].sounds.isEmpty())
        } finally { directory.deleteRecursively() }
    }
    @Test fun libraryCatalogIsAtomicAndIgnoresBrokenMetadata() {
        val directory = Files.createTempDirectory("sound-library").toFile()
        try {
            val asset = SoundAsset("sound", "Test", "file:///private/sound.wav", 1000)
            SoundLibrary.save(directory, asset)
            assertEquals(listOf(asset), SoundLibrary.list(directory))
            directory.resolve("sound-library/broken.properties").writeText("broken")
            assertEquals(listOf(asset), SoundLibrary.list(directory))
            try { SoundLibrary.save(directory, asset.copy(source = "https://example.com/audio")); fail("Remote audio persisted") } catch (_: IllegalArgumentException) {}
            assertEquals(listOf(asset), SoundLibrary.list(directory))
        } finally { directory.deleteRecursively() }
    }
    @Test fun boostedImportsAreReducedAndQuietSoundsAreNotAmplified() {
        val directory = Files.createTempDirectory("sound-wave").toFile()
        try {
            val file = directory.resolve("sound.wav")
            val bytes = SoundAssets.wave(SoundKind.WHOOSH)
            val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 44 until bytes.size step 2) data.putShort(i, 32000.toShort())
            file.writeBytes(bytes); SoundLibrary.tameWave(file)
            val result = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            assertTrue(kotlin.math.abs(result.getShort(1000).toInt()) <= 7001)
            assertEquals(0, result.getShort(44).toInt())
            data.putShort(1000, 10.toShort())
            for (i in 44 until bytes.size step 2) data.putShort(i, 10.toShort())
            file.writeBytes(bytes); SoundLibrary.tameWave(file)
            assertEquals(10, ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN).getShort(1000).toInt())
        } finally { directory.deleteRecursively() }
    }
}

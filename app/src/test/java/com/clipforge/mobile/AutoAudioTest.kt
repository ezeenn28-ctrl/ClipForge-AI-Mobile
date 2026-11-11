package com.clipforge.mobile

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AutoAudioTest {
    @Test fun moodUsesWordsAndNeutralFallback() {
        assertEquals(MusicMood.CHILL,AudioPlanning.mood(listOf(TimelineClip("a",0,5000,caption="glove box"))))
        assertEquals(MusicMood.ROMANCE,AudioPlanning.mood(listOf(TimelineClip("a",0,5000,caption="Mahal kita"))))
    }
    @Test fun duckingIsSeekIndependentAndMapsTrimAndSpeed() {
        val clips=listOf(TimelineClip("a",1000,5000,speed=2f,audioProfile=ClipAudioProfile(true,.1f,.5f,listOf(AudioRegion(2000,3000)))))
        val regions=AudioPlanning.activity(clips)
        assertEquals(listOf(AudioRegion(500,1000)),regions)
        assertEquals(.25f,AudioPlanning.duck(750,regions,.25f),.001f)
        assertEquals(1f,AudioPlanning.duck(1500,regions,.25f),.001f)
        assertEquals(.25f,AudioPlanning.duck(750,regions,.25f),.001f)
    }
    @Test fun sampledAnalysisNeverSuggestsSilenceDeletion() {
        val c=TimelineClip("a",0,10000,audioProfile=ClipAudioProfile(true,.1f,.5f,listOf(AudioRegion(2000,4000)),sampled=true))
        assertTrue(AudioPlanning.silence(c).isEmpty())
        assertEquals(listOf(AudioRegion(0,10000)),AudioPlanning.activity(listOf(c)))
    }
    @Test fun suggestionsAreSparseAndKeepManualSounds() {
        val c=TimelineClip("a",0,20000,audioProfile=ClipAudioProfile(true,.1f,.8f,listOf(AudioRegion(0,20000))))
        val cues=AudioPlanning.sounds(c,AudioIntensity.ENERGETIC,MusicMood.FUNNY,false)
        assertTrue(cues.size in 1..5);assertTrue(cues.all{it.volume<=.1f})
        assertEquals(cues,AudioPlanning.sounds(c.copy(sounds=cues),AudioIntensity.MINIMAL,MusicMood.CHILL,true))
    }
    @Test fun loopMusicEndsAtVideoAndDucksAcrossRepeats() {
        val m=MusicTrack("file:///music.wav","Music",4000,loop=true,ducking=true,fadeInMs=0,fadeOutMs=0,volume=.4f,bpm=120)
        m.validate();assertEquals(10000,m.activeDuration(10000));assertEquals(500,m.sourcePosition(4500))
        assertEquals(.1f,m.gain(4500,10000,listOf(AudioRegion(4000,5000))),.001f)
        assertEquals(0f,m.gain(10000,10000),.001f)
        assertTrue(m.beatMarkers(10000).all{it<10000})
    }
    @Test fun levelingPreservesUnityAndBoundsBoost() {
        assertEquals(.98f,VoiceDsp.sample(.98f,1f),0f)
        assertEquals(0f,VoiceDsp.sample(Float.NaN,2f),0f)
        assertTrue(VoiceDsp.sample(.9f,4f)<=.981f)
        assertEquals(1f,ClipAudioProfile(true,0f,0f).suggestedGain,0f)
        assertEquals(2f,ClipAudioProfile(true,.01f,.1f,sampled=true).suggestedGain,0f)
    }
    @Test fun audioUndoKeepsLaterManualChanges() {
        val before=TimelineClip("a",0,5000);val after=before.copy(voiceGain=2f)
        val undo=AudioUndo(listOf(before),listOf(after),null,null)
        assertEquals(before.copy(caption="later"),undo.restore(listOf(after.copy(caption="later")),null).first.single())
        assertEquals(3f,undo.restore(listOf(after.copy(voiceGain=3f)),null).first.single().voiceGain,0f)
    }
    @Test fun metadataRoundTripRetainsAudioAndDefaultsOldProjectsToManual() {
        val dir=kotlin.io.path.createTempDirectory().toFile()
        try {
            val clip=TimelineClip("a",0,5000,voiceGain=1.5f,audioProfile=ClipAudioProfile(true,.1f,.5f,listOf(AudioRegion(1000,2000))))
            val p=EditorProject("file:///video",5000,0,5000,timelineMode=true,timelineClips=listOf(clip),autoAudioEnabled=true,autoAudioHandled=listOf("a"))
            val f=File(dir,"p.properties");ProjectStore.write(f,p);assertEquals(p,ProjectStore.read(f))
            val legacy=EditorProject("file:///video",5000,0,5000);assertFalse(legacy.autoAudioEnabled)
        }finally{dir.deleteRecursively()}
    }
    @Test fun originalLibraryProducesValidBoundedWaveAndSevenDistinctBeds() {
        val dir=kotlin.io.path.createTempDirectory().toFile()
        try {
            val digests=MusicMood.entries.map { mood ->
                val track=MusicLibrary.ensure(dir,mood);track.validate()
                val file=File(java.net.URI(track.source));val p=AudioInspection.wave(file)
                assertTrue(p.peak>.01f&&p.peak<.99f);assertTrue(track.durationMs>10000)
                java.security.MessageDigest.getInstance("SHA-256").digest(file.readBytes()).toList()
            };assertEquals(7,digests.distinct().size)
        }finally{dir.deleteRecursively()}
    }
}

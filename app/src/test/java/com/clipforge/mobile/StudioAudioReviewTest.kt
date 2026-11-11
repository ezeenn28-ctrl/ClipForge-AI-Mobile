package com.clipforge.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-mdpi")
class StudioAudioReviewTest {
    @get:Rule val compose = createComposeRule()
    @Test fun reviewTabsKeepReplaceAndRemoveOnlySelectedCue() {
        val first = TimelineClip("first",0,1000,sounds=listOf(ClipSound("cue",100,SoundKind.POP)))
        val other = TimelineClip("other",0,1000,sounds=listOf(ClipSound("other-cue",200,SoundKind.POP)))
        var clips by mutableStateOf(listOf(first,other))
        var page by mutableStateOf(AudioPage.MUSIC)
        var message = ""
        compose.setContent { ClipForgeTheme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                AudioPageTabs(page) { page=it }
                if(page==AudioPage.REVIEW) AudioReviewControls(clips,null,true,{}, {clips=it}, {}, {message=it})
            }
        } }
        compose.onNodeWithText("Review").performClick()
        compose.onNodeWithText("Clip 1 · 1 sound cues").performClick()
        compose.onNodeWithText("Keep cue").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(first,other),clips); assertTrue(message.isNotBlank()) }
        compose.onNodeWithText("Replace cue").performScrollTo().performClick()
        compose.runOnIdle { assertNotEquals(SoundKind.POP,clips.first().sounds.single().kind); assertEquals(other,clips[1]) }
        compose.onNodeWithText("Remove cue").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(clips.first().sounds.isEmpty()); assertEquals(other,clips[1]) }
    }
}

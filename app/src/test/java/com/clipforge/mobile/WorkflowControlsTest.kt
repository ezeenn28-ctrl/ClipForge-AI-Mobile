package com.clipforge.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], qualifiers="w412dp-h915dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WorkflowControlsTest {
    @get:Rule val compose=createComposeRule()
    @Test fun focusedToolsHideTracksAndAllowExplicitTimelineExpansion() {
        compose.setContent { ClipForgeTheme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                TimelineEditor(LocalContext.current,null,20000,TimelineHistory(listOf(TimelineClip("a",0,20000))),"a",5000,true,
                    {},{},{},{},{throw AssertionError(it)},compact=true,tool=EditorTool.SPEED,showTracks=false)
            }
        } }
        compose.onNodeWithText("Timeline").assertDoesNotExist()
        compose.onNodeWithText("0.5x").assertIsDisplayed()
        compose.onNodeWithText("Show timeline").performClick()
        compose.onNodeWithText("Timeline").assertIsDisplayed()
        compose.onNodeWithText("Hide timeline").performClick()
        compose.onNodeWithText("Timeline").assertDoesNotExist()
        compose.onRoot().captureRoboImage(File("build/outputs/studio-previews/focused-speed-tools.png").apply{parentFile.mkdirs()}.path)
    }
    @Test fun timingControlsCommitAValidCorrectionAndSplitIntoEditableWords() {
        var cues by mutableStateOf(listOf(WhisperNative.Segment(1000,4000,"Hello world")))
        compose.setContent { ClipForgeTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            CaptionTimingEditor(cues,10000,true,{cues=it},{},{throw AssertionError(it)})
        } } }
        compose.onNodeWithText("Caption in").performTextReplacement("00:01.500")
        compose.onNodeWithText("Apply caption timing").performScrollTo().performClick()
        compose.runOnIdle{assertEquals(1500L,cues.single().startMs)}
        compose.onNodeWithText("Split line into editable words").performScrollTo().performClick()
        compose.runOnIdle{assertEquals(listOf("Hello","world"),cues.map{it.text});assertEquals(4000L,cues.last().endMs)}
    }
    @Test fun wholeReviewRowRequiresAndAcceptsAnExplicitApprovalTap() {
        var approved by mutableStateOf(false)
        compose.setContent { ClipForgeTheme { ExportApproval(approved,true){approved=it} } }
        compose.onNode(isToggleable()).assertIsOff().performClick().assertIsOn()
        compose.runOnIdle{assertTrue(approved)}
    }
    @Test fun queueButtonsRequireReviewedSnapshotAndNeverStartOnComposition() {
        var starts=0;var adds=0
        compose.setContent { ClipForgeTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ExportQueueControls(emptyList(),true,false,false,0,{adds++},{starts++},{},{},{},{})
        } } }
        compose.onNodeWithText("Add approved edit to queue").assertIsNotEnabled()
        compose.onNodeWithText("Start approved queue").assertIsNotEnabled()
        compose.runOnIdle{assertEquals(0,starts);assertEquals(0,adds)}
    }
    @Test fun wordFollowRenderingChangesWithSourceTimeAndMatchesExportOverlay() {
        val cue=WhisperNative.Segment(1000,4000,"Hello world")
        val appearance=CaptionAppearance(wordHighlight=true)
        val overlay=segmentSubtitleOverlay("",listOf(cue),0,39f,android.graphics.Color.WHITE,appearance=appearance)
        fun pixels(bitmap:android.graphics.Bitmap):IntArray=IntArray(bitmap.width*bitmap.height).also{bitmap.getPixels(it,0,bitmap.width,0,0,bitmap.width,bitmap.height)}
        val first=makeCaptionOverlay("",cue.text,appearance=appearance,activeWord=CaptionTiming.activeWord(cue,1100))
        val second=makeCaptionOverlay("",cue.text,appearance=appearance,activeWord=CaptionTiming.activeWord(cue,3500))
        try {
            assertFalse(pixels(first).contentEquals(pixels(second)))
            assertTrue(pixels(first).contentEquals(pixels(overlay.getBitmap(1100000))))
            assertTrue(pixels(second).contentEquals(pixels(overlay.getBitmap(3500000))))
        }finally{first.recycle();second.recycle();overlay.release()}
    }
}

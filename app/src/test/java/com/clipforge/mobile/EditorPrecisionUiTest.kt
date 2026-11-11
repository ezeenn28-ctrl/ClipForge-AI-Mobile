package com.clipforge.mobile

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w412dp-h915dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditorPrecisionUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun trackControlsMuteAndLockWithoutRemovingContent(){
        var clip by mutableStateOf(TimelineClip("cut",0,5000,caption="Keep me"))
        compose.setContent{ClipForgeTheme{Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){EditorTrackControls(clip,null,true,{clip=it},{})}}}
        compose.onNodeWithText("Track mute / lock").performClick()
        compose.onNodeWithText("Lock Voice").performScrollTo().performClick()
        compose.onNodeWithText("Mute Voice").performScrollTo().performClick()
        compose.runOnIdle{assertTrue(clip.editor.voiceLocked);assertTrue(clip.editor.voiceMuted);assertEquals("Keep me",clip.caption)}
        compose.onNodeWithText("Unlock Voice").performScrollTo().performClick()
        compose.runOnIdle{assertFalse(clip.editor.voiceLocked)}
    }
    @Test fun directCaptionDragCommitsOneUndoablePlacement(){
        var placement by mutableStateOf(TextPlacement());var commits=0
        compose.setContent{ClipForgeTheme{Box(Modifier.height(600.dp).width(337.dp)){DirectTextHandles(placement,CaptionAppearance(),false,true,{}, {placement=it;commits++})}}}
        compose.onNodeWithContentDescription("Drag caption text").performTouchInput{down(center);moveBy(Offset(30f,-25f));moveBy(Offset(30f,-25f));up()}
        compose.runOnIdle{assertEquals(1,commits);assertTrue(placement.x>0f);assertTrue(placement.y<0f)}
        compose.onNodeWithContentDescription("Resize caption text").performTouchInput{down(center);moveBy(Offset(25f,15f));moveBy(Offset(20f,15f));up()}
        compose.runOnIdle{assertEquals(2,commits);assertTrue(placement.scale>1f)}
    }
    @Test fun tappingCaptionLaneSelectsContextualCaptionTools(){
        val context=ApplicationProvider.getApplicationContext<Context>();var tool:EditorTool?=null;var selected=""
        val clip=TimelineClip("cut",0,5000,subtitles=listOf(WhisperNative.Segment(0,5000,"A caption")))
        compose.setContent{ClipForgeTheme{Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){TimelineEditor(context,null,5000,TimelineHistory(listOf(clip)),"cut",0,true,{}, {selected=it},{},{},{},compact=true,showDetails=false,onTool={value,_->tool=value})}}}
        compose.onNodeWithContentDescription("Select captions track").performTouchInput{click(center)}
        compose.runOnIdle{assertEquals("cut",selected);assertEquals(EditorTool.CAPTIONS,tool)}
    }
    @Test fun rendererMovesCaptionPixelsAndRetainsTheSharedHook(){
        val original=makeCaptionOverlay("Hook","Caption",appearance=CaptionAppearance())
        val moved=makeCaptionOverlay("Hook","Caption",appearance=CaptionAppearance(),placement=TextPlacement(x=.15f,y=-.15f,scale=1.4f))
        fun center(bitmap:android.graphics.Bitmap):Pair<Double,Double>{var x=0.0;var y=0.0;var n=0;for(yy in 400 until 1200)for(xx in 0 until 720)if(android.graphics.Color.alpha(bitmap.getPixel(xx,yy))>100){x+=xx;y+=yy;n++};assertTrue(n>0);return x/n to y/n}
        val a=center(original);val b=center(moved);assertTrue(b.first>a.first+30);assertTrue(b.second<a.second-100)
        original.recycle();moved.recycle()
    }
    @Test fun twoFingerGestureZoomsWhileSingleFingerTapDoesNot(){
        var zoom by mutableFloatStateOf(1f)
        compose.setContent{ClipForgeTheme{Box(Modifier.size(300.dp).semantics{contentDescription="Pinch surface"}.precisionPinch(true){factor,_->zoom*=factor})}}
        compose.onNodeWithContentDescription("Pinch surface").performTouchInput{down(0,Offset(100f,100f));down(1,Offset(200f,100f));moveTo(0,Offset(70f,100f));moveTo(1,Offset(230f,100f));up(0);up(1)}
        compose.runOnIdle{assertTrue(zoom>1.3f)}
        val previous=zoom
        compose.onNodeWithContentDescription("Pinch surface").performTouchInput{click(center)}
        compose.runOnIdle{assertEquals(previous,zoom,0f)}
    }

}

package com.clipforge.mobile
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
@RunWith(RobolectricTestRunner::class) @Config(sdk=[35],qualifiers="w412dp-h915dp-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
class HighlightBatchControlsTest {
 @get:Rule val compose=createComposeRule()
 @Test fun selectAllNeedsExplicitReviewBeforeQueue(){var selected by mutableStateOf(emptySet<Int>());var reviewed by mutableStateOf(false);var queued=0
  compose.setContent{ClipForgeTheme{Column(Modifier.fillMaxSize()){HighlightBatchControls(15,selected,reviewed,true,{selected=it;reviewed=false},{reviewed=it},{queued++})}}}
  compose.onNodeWithText("Select all highlights").performClick();compose.onNodeWithText("Approve 15 highlights and add to queue").assertIsNotEnabled()
  compose.onNode(isToggleable()).performClick();compose.onNodeWithText("Approve 15 highlights and add to queue").performClick();compose.runOnIdle{assertEquals(15,selected.size);assertEquals(1,queued)}
 }
 @Test fun clearingSelectionRevokesBatchConfirmation(){var selected by mutableStateOf((0 until 15).toSet());var reviewed by mutableStateOf(true)
  compose.setContent{ClipForgeTheme{Column(Modifier.fillMaxSize()){HighlightBatchControls(15,selected,reviewed,true,{selected=it;reviewed=false},{reviewed=it},{})}}}
  compose.onNodeWithText("Clear selection").performClick();compose.onNodeWithText("Approve 0 highlights and add to queue").assertIsNotEnabled();compose.runOnIdle{assertFalse(reviewed)}
 }
}

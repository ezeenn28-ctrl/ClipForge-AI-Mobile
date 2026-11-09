package com.clipforge.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-mdpi")
class ProjectHomeTest {
    @get:Rule val compose = createComposeRule()
    @Test fun homeActionsDispatchTheirOwnWorkflows() {
        var action = ""
        compose.setContent {
            ClipForgeTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                        ProjectHome(true, true, true, false, "Saved project", 20_000,
                            ProjectCatalog.Listing(), "current",
                            { action = "new" }, { action = "find" }, { action = "continue" }, {},
                            { action = "link" }, { action = "tools" })
                    }
                }
            }
        }
        compose.onNodeWithText("New project").performClick()
        compose.runOnIdle { assertEquals("new", action) }
        compose.onNodeWithText("Continue editing").performClick()
        compose.runOnIdle { assertEquals("continue", action) }
        compose.onNodeWithText("Find clips").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("find", action) }
        compose.onNodeWithText("Import a link").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("link", action) }
        compose.onNodeWithText("Project tools").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("tools", action) }
    }
}

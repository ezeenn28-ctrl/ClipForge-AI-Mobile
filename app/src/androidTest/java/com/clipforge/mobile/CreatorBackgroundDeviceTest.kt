package com.clipforge.mobile

import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CreatorBackgroundDeviceTest {
    private fun await(label:String,condition:()->Boolean){val until=System.nanoTime()+30_000_000_000L;while(System.nanoTime()<until){if(condition())return;Thread.sleep(50)};fail("Timed out: $label")}
    @Test fun approvedForegroundExportCancelsAndRetainsOnlyUnapprovedRecovery(){
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        val source=File(context.cacheDir,"cancel-landscape.mp4");instrumentation.context.assets.open("landscape.mp4").use{input->source.outputStream().use{input.copyTo(it)}}
        val uri=Uri.fromFile(source).toString();val project=EditorProject(uri,6000,0,6000,mode="Manual Editor",exportQuality=ExportQuality(1080,60,12000000))
        ActivityScenario.launch(MediaHarnessActivity::class.java).use{scenario->
            scenario.onActivity{BackgroundExports.start(it,listOf(QueuedExport("direct",project,QueueStatus.READY)),true)}
            await("persisted in-progress snapshot"){BackgroundExports.state.value.active&&File(context.filesDir,"background-export.properties").isFile&&BackgroundExports.state.value.project!=null}
            scenario.onActivity{BackgroundExports.cancel(it)}
            await("cancelled export"){!BackgroundExports.state.value.active}
            assertTrue(BackgroundExports.state.value.message.contains("cancelled",ignoreCase=true));assertTrue(BackgroundExports.state.value.output.isEmpty())
            val saved=BackgroundExports.recovery(context.filesDir);assertNotNull(saved);assertEquals(project,saved);assertTrue(saved!!.drafts.none{it.approved})
            // Restoring metadata is read-only and cannot reissue the consumed capability.
            Thread.sleep(500);assertFalse(BackgroundExports.state.value.active)
            try{BackgroundExports.start(context,listOf(QueuedExport("direct",saved)),false);fail("Recovery bypassed approval")}catch(expected:IllegalArgumentException){}
        }
        File(context.filesDir,"background-export.properties").let{it.delete();ProjectStore.previousFile(it).delete()};source.delete()
    }
}

package com.clipforge.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrandAssetsTest {
    @Test fun applicationHasAMaskedLauncherIconAndThemedMonochrome() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(R.mipmap.ic_launcher, context.applicationInfo.icon)
        // Load the manifest-selected resource directly; this host's package-manager
        // icon registry is not populated. The real package-manager path is covered
        // separately by the Android device integration suite.
        val icon = context.getDrawable(context.applicationInfo.icon)!!
        assertTrue("Adaptive launcher icon missing", icon is AdaptiveIconDrawable)
        assertNotNull((icon as AdaptiveIconDrawable).monochrome)
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        try {
            icon.setBounds(0, 0, 256, 256); icon.draw(Canvas(bitmap))
            var cyan = 0
            for (y in 0 until 256) for (x in 0 until 256) {
                val c = bitmap.getPixel(x, y)
                if (Color.green(c) > 150 && Color.blue(c) > 120 && Color.red(c) < 100) cyan++
            }
            assertTrue("Brand mark was clipped or missing", cyan > 200)
            val file = File("build/outputs/studio-previews/launcher-icon.png").apply { parentFile.mkdirs() }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }
}

package com.clipforge.mobile

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

object ExportPublisher {
    data class Published(val uri: Uri, val inGallery: Boolean)

    fun privateUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.exports", file)

    /** Called only after an explicitly approved render completes. */
    fun publish(context: Context, file: File): Published {
        require(file.isFile && file.length() > 0) { "Rendered MP4 is empty or missing" }
        if (Build.VERSION.SDK_INT < 29) return Published(privateUri(context, file), false)
        val resolver = context.contentResolver
        val details = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/ClipForge")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), details)
            ?: error("Gallery could not create the video entry")
        try {
            val destination = resolver.openOutputStream(uri, "w") ?: error("Gallery destination could not be opened")
            destination.use { output -> file.inputStream().use { input -> input.copyTo(output, 65_536) } }
            val complete = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            check(resolver.update(uri, complete, null, null) == 1) { "Gallery video could not be finalized" }
            return Published(uri, true)
        } catch (error: Exception) {
            try { resolver.delete(uri, null, null) } catch (_: Exception) { }
            throw error // Original render is retained for recovery/sharing.
        }
    }
}

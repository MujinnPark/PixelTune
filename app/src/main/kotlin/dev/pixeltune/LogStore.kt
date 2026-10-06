package dev.pixeltune

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore

object LogStore {
    /** Saves text to Download/PixelTune/<name>. Returns the path, or null on failure. */
    fun save(ctx: Context, name: String, text: String): String? = runCatching {
        val v = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/PixelTune")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)!!
        ctx.contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) }
        "Download/PixelTune/$name"
    }.getOrNull()
}

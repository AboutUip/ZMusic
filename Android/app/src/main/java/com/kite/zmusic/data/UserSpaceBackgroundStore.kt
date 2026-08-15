package com.kite.zmusic.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 用户空间自定义背景：复制到应用私有目录，按账号持久化路径。
 */
class UserSpaceBackgroundStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val dir = File(app.filesDir, DIR).also { it.mkdirs() }

    fun pathFor(userId: Long): String? {
        if (userId <= 0L) return null
        val stored = prefs.getString(key(userId), null)?.trim().orEmpty()
        if (stored.isEmpty()) return null
        val file = File(stored)
        return if (file.isFile && file.length() > 0L) file.absolutePath else null
    }

    suspend fun import(userId: Long, uri: Uri): String? = withContext(Dispatchers.IO) {
        if (userId <= 0L) return@withContext null
        runCatching {
            val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@runCatching null
            val bmp = decodeSampled(bytes, MAX_PX) ?: return@runCatching null
            dir.mkdirs()
            val out = File(dir, "u_${userId}_${System.currentTimeMillis()}.jpg")
            out.outputStream().use { stream ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 88, stream)
            }
            if (!bmp.isRecycled) bmp.recycle()
            if (!out.isFile || out.length() <= 0L) {
                runCatching { out.delete() }
                return@runCatching null
            }
            clearFilesFor(userId, keep = out)
            prefs.edit().putString(key(userId), out.absolutePath).apply()
            out.absolutePath
        }.onFailure {
            Log.w(TAG, "import user space background failed")
        }.getOrNull()
    }

    fun clear(userId: Long) {
        if (userId <= 0L) return
        prefs.edit().remove(key(userId)).apply()
        clearFilesFor(userId, keep = null)
    }

    private fun clearFilesFor(userId: Long, keep: File?) {
        val prefix = "u_${userId}_"
        dir.listFiles()?.forEach { file ->
            if (keep != null && file.absolutePath == keep.absolutePath) return@forEach
            if (file.name.startsWith(prefix)) {
                runCatching { file.delete() }
            }
        }
    }

    private fun key(userId: Long) = "bg_$userId"

    companion object {
        private const val TAG = "UserSpaceBg"
        private const val PREFS = "zmusic_user_space"
        private const val DIR = "user_space_bg"
        private const val MAX_PX = 2048

        private fun decodeSampled(bytes: ByteArray, maxPx: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0) return null
            var sample = 1
            while (w / sample > maxPx || h / sample > maxPx) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        }
    }
}

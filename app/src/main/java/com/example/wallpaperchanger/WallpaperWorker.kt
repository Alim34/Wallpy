package com.example.wallpaperchanger

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class WallpaperWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val folderUri = prefs.getString(MainActivity.KEY_FOLDER, null)
            ?: return Result.failure()

        val dir = DocumentFile.fromTreeUri(ctx, Uri.parse(folderUri))
            ?: return Result.failure()

        val images = dir.listFiles().filter { file ->
            file.isFile && (file.type?.startsWith("image/") == true)
        }
        if (images.isEmpty()) return Result.success()

        // Не повторяем последнюю картинку, если файлов больше одного
        val lastUri = prefs.getString(KEY_LAST, null)
        val candidates = if (images.size > 1) {
            images.filter { it.uri.toString() != lastUri }
        } else images

        val picked = candidates.random()

        return try {
            val bitmap = decodeSampled(ctx, picked.uri) ?: return Result.retry()
            WallpaperManager.getInstance(ctx).setBitmap(bitmap)
            prefs.edit().putString(KEY_LAST, picked.uri.toString()).apply()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    /** Декодирует картинку с даунсэмплингом, чтобы не поймать OutOfMemory на больших фото. */
    private fun decodeSampled(ctx: Context, uri: Uri, maxSide: Int = 2560): Bitmap? {
        val resolver = ctx.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSide ||
            bounds.outHeight / (sample * 2) >= maxSide
        ) {
            sample *= 2
        }

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }

    companion object {
        const val KEY_LAST = "last_image_uri"
    }
}

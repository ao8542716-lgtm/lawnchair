package app.lawnchair.glass

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Holds the launcher's own copy of the wallpaper.
 *
 * Liquid Glass refracts a View, and an app cannot sample the system wallpaper layer, so the
 * launcher draws the wallpaper itself ([GlassWallpaperView]) and the glass reads that view.
 */
object GlassWallpaperStore {
    private const val FILE_NAME = "glass_wallpaper.jpg"
    private const val MAX_EDGE = 2560

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    /** Copies the picked image into app storage, downsampled and EXIF-rotated. */
    suspend fun importFrom(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val tmp = File(context.cacheDir, "glass_wallpaper_src")
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext false
            input.use { src -> tmp.outputStream().use { dst -> src.copyTo(dst) } }
            var bitmap = decodeSampled(tmp) ?: return@withContext false
            val orientation = runCatching {
                ExifInterface(tmp.absolutePath)
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees != 0f) {
                val m = Matrix().apply { postRotate(degrees) }
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
            }
            FileOutputStream(file(context)).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            true
        } catch (e: Exception) {
            false
        } finally {
            tmp.delete()
        }
    }

    /** The picked wallpaper, or the system wallpaper if readable, or null. Call off the main thread. */
    fun load(context: Context): Bitmap? {
        val own = file(context)
        if (own.exists()) decodeSampled(own)?.let { return it }
        return runCatching {
            (WallpaperManager.getInstance(context).drawable as? BitmapDrawable)?.bitmap
        }.getOrNull()
    }

    private fun decodeSampled(f: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > MAX_EDGE || bounds.outHeight / sample > MAX_EDGE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(f.absolutePath, opts)
    }
}

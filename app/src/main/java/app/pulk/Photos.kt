package app.pulk

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.InputStream

/**
 * The family's photos: any picture from the gallery or camera, however big, becomes a small square (240 px)
 * in the app's own storage, never shared. A picture that cannot be read gives null, never a crash.
 */
object Photos {
    const val SIDE = 240

    /** How much to shrink while reading, so a 50-megapixel photo never fills the memory. */
    fun sample(w: Int, h: Int): Int { var s = 1; while (minOf(w, h) / (s * 2) >= SIDE) s *= 2; return s }

    /** [open] gives the picture afresh each time (it is read twice: its size, then the picture). */
    fun square(open: () -> InputStream?, dir: File): File? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        (open() ?: return null).use { BitmapFactory.decodeStream(it, null, bounds) }   // returns no picture, only its size
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val bmp = open()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample(bounds.outWidth, bounds.outHeight) }) } ?: return null
        val side = minOf(bmp.width, bmp.height)
        val crop = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
        val sq = Bitmap.createScaledBitmap(crop, SIDE, SIDE, true)
        dir.mkdirs()
        val out = File(dir, "${System.currentTimeMillis()}.jpg")
        val ok = out.outputStream().use { sq.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        if (crop !== bmp) crop.recycle(); if (sq !== crop) sq.recycle(); bmp.recycle()
        return if (ok && out.length() > 0) out else { out.delete(); null }
    }

    /** A saved photo for a button; null if it is gone or unreadable. */
    fun load(path: String?): Bitmap? = try {
        path?.let { File(it) }?.takeIf { it.isFile }?.let { f ->
            val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(f.path, b)
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample(b.outWidth, b.outHeight) })
        }
    } catch (_: Throwable) { null }
}

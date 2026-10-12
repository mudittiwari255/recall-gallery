package app.recall

import android.content.ContentResolver
import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import app.recall.core.ArgbImage
import kotlin.math.max
import kotlin.math.roundToInt

data class Photo(
    val id: Long,
    val uri: Uri,
    val name: String,
    val dateTaken: Long,
    val modified: Long,
    val width: Int,
    val height: Int,
    val bucket: String,
)

/** Reads the device photo library through MediaStore. */
class MediaRepo(private val resolver: ContentResolver) {

    fun loadAll(): List<Photo> {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        )
        val out = ArrayList<Photo>()
        resolver.query(collection, projection, null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val iTaken = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val iAdded = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val iMod = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val iW = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val iH = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val iB = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val taken = c.getLong(iTaken).takeIf { it > 0 } ?: (c.getLong(iAdded) * 1000)
                out.add(
                    Photo(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        name = c.getString(iName) ?: "",
                        dateTaken = taken,
                        modified = c.getLong(iMod),
                        width = c.getInt(iW),
                        height = c.getInt(iH),
                        bucket = c.getString(iB) ?: "",
                    ),
                )
            }
        }
        return out
    }

    /**
     * Decodes a photo at most [maxSide] pixels on its long side, upright (EXIF orientation applied), as ARGB pixels.
     * Falls back to the system thumbnail for formats the decoder rejects.
     */
    fun decodeForModel(uri: Uri, maxSide: Int = 1024): ArgbImage {
        val bitmap = try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                val w = info.size.width; val h = info.size.height
                val scale = maxSide.toFloat() / max(w, h)
                if (scale < 1f) decoder.setTargetSize(max(1, (w * scale).roundToInt()), max(1, (h * scale).roundToInt()))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
        } catch (e: Exception) {
            resolver.loadThumbnail(uri, Size(maxSide, maxSide), null)
        }
        val soft = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val px = IntArray(soft.width * soft.height)
        soft.getPixels(px, 0, soft.width, 0, 0, soft.width, soft.height)
        val img = ArgbImage(soft.width, soft.height, px)
        if (soft !== bitmap) soft.recycle()
        bitmap.recycle()
        return img
    }
}

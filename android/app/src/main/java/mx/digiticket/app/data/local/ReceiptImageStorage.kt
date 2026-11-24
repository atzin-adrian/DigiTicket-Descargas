package mx.digiticket.app.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max

data class StoredReceiptImage(val id: String, val fileName: String, val absolutePath: String, val sizeInBytes: Long)

class ReceiptImageStorage(private val context: Context) {
    suspend fun save(sourceUri: Uri): StoredReceiptImage = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "receipts").also { check(it.isDirectory || it.mkdirs()) }
        val id = UUID.randomUUID().toString()
        val dest = File(dir, "$id.jpg")
        val source = File.createTempFile("lectura-", ".img", context.cacheDir)
        val converted = File.createTempFile("foto-", ".tmp", dir)
        try {
            val maxBytes = 25L * 1024 * 1024
            val input = context.contentResolver.openInputStream(sourceUri) ?: error("No se pudo abrir la imagen.")
            input.use { stream -> source.outputStream().use { out ->
                val buffer = ByteArray(32768)
                var count = 0L
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    count += n
                    require(count <= maxBytes) { "Elige una imagen de hasta 25 MB." }
                    out.write(buffer, 0, n)
                }
            } }
            val info = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.path, info)
            require(info.outWidth > 0 && info.outHeight > 0 && info.outWidth.toLong() * info.outHeight <= 100_000_000) {
                "Elige una foto JPEG, PNG o una imagen compatible de hasta 100 megapíxeles."
            }
            var sample = 1
            while (max(info.outWidth, info.outHeight) / sample > 3000) sample *= 2
            val bitmap = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: error("No se pudo leer la fotografía.")
            val orientation = runCatching { ExifInterface(source).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
            val matrix = Matrix().apply {
                when (orientation) {
                    2 -> setScale(-1f, 1f)
                    3 -> setRotate(180f)
                    4 -> setScale(1f, -1f)
                    5 -> { setRotate(90f); postScale(-1f, 1f) }
                    6 -> setRotate(90f)
                    7 -> { setRotate(270f); postScale(-1f, 1f) }
                    8 -> setRotate(270f)
                }
            }
            val rotated = if (matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            try {
                converted.outputStream().use { out -> check(rotated.compress(Bitmap.CompressFormat.JPEG, 92, out)) }
            } finally {
                if (rotated !== bitmap) rotated.recycle()
                bitmap.recycle()
            }
            check(converted.renameTo(dest)) { "No se pudo guardar la foto." }
            StoredReceiptImage(id, dest.name, dest.absolutePath, dest.length())
        } finally {
            source.delete()
            converted.delete()
        }
    }
}

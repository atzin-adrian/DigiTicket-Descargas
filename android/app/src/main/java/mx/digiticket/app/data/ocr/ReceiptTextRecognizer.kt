package mx.digiticket.app.data.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.Closeable
import java.io.File
import java.io.IOException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ReceiptTextRecognizer(
    private val context: Context
) : Closeable {

    private val recognizer = TextRecognition.getClient(
        TextRecognizerOptions.DEFAULT_OPTIONS
    )

    fun recognize(
        imageFile: File,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val inputImage = try {
            InputImage.fromFilePath(
                context,
                Uri.fromFile(imageFile)
            )
        } catch (error: IOException) {
            onFailure(error)
            return
        }

        recognizer
            .process(inputImage)
            .addOnSuccessListener { result ->
                onSuccess(result.text)
            }
            .addOnFailureListener(onFailure)
    }

    suspend fun readLines(file: File): List<ReadLine> {
        val image = InputImage.fromFilePath(context, Uri.fromFile(file))
        return suspendCancellableCoroutine { continuation ->
            recognizer.process(image).addOnSuccessListener { result ->
                val lines = result.textBlocks.flatMap { block -> block.lines.map { line ->
                    val box = line.boundingBox
                    val position = if (box == null) "" else
                        listOf(box.left, box.top, box.right, box.bottom).joinToString(",")
                    ReadLine(line.text, position)
                } }
                if (continuation.isActive) continuation.resume(lines)
            }.addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
        }
    }

    override fun close() {
        recognizer.close()
    }
}

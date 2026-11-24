package mx.digiticket.app.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class StoredReceiptOcrText(
    val fileName: String,
    val absolutePath: String,
    val characterCount: Int
)

data class ReceiptOcrDocument(
    val receiptId: String,
    val text: String
)

class ReceiptOcrTextStorage(
    private val context: Context
) {

    suspend fun save(
        receiptId: String,
        text: String
    ): StoredReceiptOcrText = withContext(Dispatchers.IO) {
        require(text.isNotBlank()) {
            "OCR text must not be blank"
        }

        val ocrDirectory = File(
            context.filesDir,
            "ocr"
        )

        check(
            ocrDirectory.exists() || ocrDirectory.mkdirs()
        ) {
            "Could not create OCR directory"
        }

        val destinationFile = File(
            ocrDirectory,
            "$receiptId.txt"
        )

        destinationFile.writeText(
            text = text,
            charset = Charsets.UTF_8
        )

        check(destinationFile.length() > 0L) {
            "Stored OCR text is empty"
        }

        StoredReceiptOcrText(
            fileName = destinationFile.name,
            absolutePath = destinationFile.absolutePath,
            characterCount = text.length
        )
    }

    suspend fun readAll(): List<ReceiptOcrDocument> = withContext(Dispatchers.IO) {
        val ocrDirectory = File(context.filesDir, "ocr")

        ocrDirectory
            .listFiles { file -> file.isFile && file.extension == "txt" }
            .orEmpty()
            .sortedBy(File::getName)
            .map { file ->
                ReceiptOcrDocument(
                    receiptId = file.nameWithoutExtension,
                    text = file.readText(Charsets.UTF_8)
                )
            }
            .filter { document -> document.text.isNotBlank() }
    }
}

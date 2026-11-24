package mx.digiticket.app.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.digiticket.app.domain.ocr.ReceiptFieldExtraction
import org.json.JSONObject
import java.io.File

data class StoredReceiptExtraction(
    val fileName: String,
    val absolutePath: String
)

class ReceiptExtractionStorage(
    private val context: Context
) {

    suspend fun save(
        receiptId: String,
        extraction: ReceiptFieldExtraction,
        processingTimeMs: Long?
    ): StoredReceiptExtraction = withContext(Dispatchers.IO) {
        val extractionDirectory = File(context.filesDir, "extractions")

        check(
            extractionDirectory.exists() || extractionDirectory.mkdirs()
        ) {
            "Could not create receipt extraction directory"
        }

        val destinationFile = File(extractionDirectory, "$receiptId.json")
        val json = JSONObject()
            .put("receipt_id", receiptId)
            .put("provider", "ML_KIT_ON_DEVICE")
            .put("model", "TEXT_RECOGNITION_19_0_1_PLUS_RULES")
            .put("extractor_version", "RULES_V1")
            .put("merchant", JSONObject.NULL)
            .put("date", extraction.date?.toString() ?: JSONObject.NULL)
            .put("total_cents", extraction.total?.amountInCents ?: JSONObject.NULL)
            .put("description", JSONObject.NULL)
            .put("processing_ms", processingTimeMs ?: JSONObject.NULL)
            .put("total_strategy", extraction.total?.strategy?.name ?: JSONObject.NULL)

        destinationFile.writeText(
            text = json.toString(2),
            charset = Charsets.UTF_8
        )

        check(destinationFile.length() > 0L) {
            "Stored receipt extraction is empty"
        }

        StoredReceiptExtraction(
            fileName = destinationFile.name,
            absolutePath = destinationFile.absolutePath
        )
    }

    suspend fun exists(receiptId: String): Boolean = withContext(Dispatchers.IO) {
        File(
            File(context.filesDir, "extractions"),
            "$receiptId.json"
        ).isFile
    }
}

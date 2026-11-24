package mx.digiticket.app.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import mx.digiticket.app.domain.*
import mx.digiticket.app.domain.ocr.ReceiptFieldExtractor
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

object EntryJson {
    fun encode(entry: Entry): String = with(entry) {
        JSONObject().put("id", id).put("type", type.name).put("source", source.name)
            .put("created", created.toString()).put("date", date).put("time", time)
            .put("description", description).put("merchant", merchant).put("category", category)
            .put("subtotal", subtotal).put("tax", tax).put("total", total).put("payment", payment)
            .put("paymentStatus", paymentStatus).put("notes", notes).put("review", review.name)
            .put("receipt", receiptId).put("items", JSONArray().apply {
                items.forEach { put(JSONArray(listOf(it.description, it.quantity, it.unitPrice, it.amount))) }
            }).toString()
    }

    fun decode(raw: String): Entry = with(JSONObject(raw)) {
        val lines = optJSONArray("items") ?: JSONArray()
        Entry(id = getString("id"), type = MovementType.valueOf(getString("type")),
            source = CaptureSource.valueOf(getString("source")), created = LocalDate.parse(getString("created")),
            date = optString("date"), time = optString("time"), description = optString("description"),
            merchant = optString("merchant"), category = optString("category"), subtotal = optString("subtotal"),
            tax = optString("tax"), total = optString("total"), payment = optString("payment"),
            paymentStatus = optString("paymentStatus"), notes = optString("notes"),
            review = Review.valueOf(getString("review")), receiptId = optString("receipt").ifEmpty { null },
            items = List(lines.length()) { index ->
                val row = lines.getJSONArray(index)
                LineItem(row.getString(0), row.optString(1), row.optString(2), row.optString(3))
            })
    }
}

class LedgerStore(private val context: Context) : SQLiteOpenHelper(context, "digiticket.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE entries(id TEXT PRIMARY KEY NOT NULL, payload TEXT NOT NULL)")
        // Early scanner builds kept private photographs and OCR without a transaction table.
        val images = File(context.filesDir, "receipts").listFiles().orEmpty()
        for (image in images.filter { it.extension == "jpg" }) {
            val id = image.nameWithoutExtension
            if (runCatching { UUID.fromString(id) }.isFailure) continue
            val raw = File(context.filesDir, "ocr/$id.txt").takeIf { it.isFile }?.readText().orEmpty()
            val fields = ReceiptFieldExtractor.extract(raw)
            val entry = Entry(id = id, source = CaptureSource.PRINTED_RECEIPT, receiptId = id,
                created = Instant.ofEpochMilli(image.lastModified()).atZone(ZoneId.systemDefault()).toLocalDate(),
                date = fields.date?.toString().orEmpty(), total = decimal(fields.total?.amountInCents))
            put(db, entry)
        }
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun all(): List<Entry> = readableDatabase.rawQuery("SELECT payload FROM entries ORDER BY rowid DESC", null).use { c ->
        buildList { while (c.moveToNext()) add(EntryJson.decode(c.getString(0))) }
    }
    fun save(entry: Entry) {
        require(!entry.confirmed || entry.errorForConfirmation() == null)
        put(writableDatabase, entry)
    }

    private fun put(db: SQLiteDatabase, entry: Entry) {
        val row = ContentValues().apply { put("id", entry.id); put("payload", EntryJson.encode(entry)) }
        if (db.update("entries", row, "id=?", arrayOf(entry.id)) == 0) db.insertOrThrow("entries", null, row)
    }

    fun delete(entry: Entry) {
        writableDatabase.delete("entries", "id=?", arrayOf(entry.id))
        entry.receiptId?.let { id ->
            listOf("receipts/$id.jpg", "ocr/$id.txt", "extractions/$id.json", "evidence/$id.json").forEach {
                File(context.filesDir, it).delete()
            }
        }
    }
}

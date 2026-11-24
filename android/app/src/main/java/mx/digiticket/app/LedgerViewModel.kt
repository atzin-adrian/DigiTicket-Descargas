package mx.digiticket.app

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import mx.digiticket.app.data.local.*
import mx.digiticket.app.data.ocr.*
import mx.digiticket.app.domain.*
import mx.digiticket.app.domain.ocr.ReceiptFieldExtractor
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

class LedgerViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val store = LedgerStore(application)
    private val imageStore = ReceiptImageStorage(application)
    private val recognizer = ReceiptTextRecognizer(application)
    private val ocr = ReceiptOcrTextStorage(application)
    private val extractions = ReceiptExtractionStorage(application)
    private val files = application.filesDir
    var entries by mutableStateOf(emptyList<Entry>())
        private set
    var draft by mutableStateOf(saved.get<String>("draft")?.let(EntryJson::decode))
        private set
    var month by mutableStateOf(saved.get<String>("month")?.let(YearMonth::parse) ?: YearMonth.now())
        private set
    var tab by mutableStateOf(saved.get<String>("tab") ?: "Inicio")
        private set
    var busy by mutableStateOf(false)
        private set
    var ready by mutableStateOf(false)
        private set
    var message by mutableStateOf("")
    var historyType by mutableStateOf("")
    var historyCategory by mutableStateOf("")
    var reading by mutableStateOf(false)
        private set
    private var readJob: Job? = null
    var evidence by mutableStateOf("")
        private set
    private var evidenceJob: Job? = null

    init { reload() }
    fun reload() = perform {
        entries = withContext(Dispatchers.IO) { store.all() }
        ready = true
        loadEvidence(draft)
    }
    fun navigate(to: String) { tab = to; saved["tab"] = to }
    fun selectMonth(value: YearMonth) { month = value; saved["month"] = value.toString() }
    fun start(type: MovementType) = edit(Entry(type = type, date = LocalDate.now().toString()))
    fun edit(entry: Entry) { change(entry); message = ""; loadEvidence(entry) }
    fun change(entry: Entry) { draft = entry; saved["draft"] = EntryJson.encode(entry) }
    fun closeEditor() { stopReading(); draft = null; saved.remove<String>("draft"); evidence = "" }
    fun isDirty(): Boolean = draft?.let { e -> entries.firstOrNull { it.id == e.id }?.let { it != e }
        ?: (e.description.isNotBlank() || e.total.isNotBlank() || e.notes.isNotBlank() || e.items.isNotEmpty() ||
            e.category.isNotBlank() || e.merchant.isNotBlank() || e.payment.isNotBlank() ||
            e.tax.isNotBlank() || e.subtotal.isNotBlank() || e.paymentStatus.isNotBlank() || e.time.isNotBlank() ||
            e.date != LocalDate.now().toString()) } ?: false

    private fun perform(work: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try { work() }
            catch (c: CancellationException) { throw c }
            catch (e: Exception) { message = e.localizedMessage?.takeIf { it.length < 150 } ?: "No se pudo completar la operación. Vuelve a intentarlo." }
            finally { busy = false }
        }
    }
    private suspend fun persist(e: Entry) {
        entries = withContext(Dispatchers.IO) { store.save(e); store.all() }
    }
    fun save(status: Review) {
        val current = draft ?: return
        if (status == Review.CONFIRMED) current.errorForConfirmation()?.let { message = it; return }
        stopReading()
        perform {
            val entry = current.copy(review = status, description = current.description.trim())
            persist(entry)
            closeEditor()
            selectMonth(entry.month)
            navigate(if (entry.confirmed) "Inicio" else "Pendientes")
            message = if (entry.confirmed) "Movimiento confirmado." else "Guardado para revisión."
        }
    }
    fun delete() {
        val entry = draft ?: return
        stopReading()
        perform {
            entries = withContext(Dispatchers.IO) { store.delete(entry); store.all() }
            closeEditor()
            message = "Movimiento eliminado."
        }
    }
    fun photo(uri: Uri, source: CaptureSource, finished: () -> Unit = {}) {
        val current = draft ?: return
        if (current.receiptId != null || busy) return
        perform {
            try {
                val image = imageStore.save(uri)
                val entry = current.copy(receiptId = image.id, source = source, review = Review.PENDING,
                    date = if (entries.none { it.id == current.id } && current.date == current.created.toString())
                        "" else current.date)
                persist(entry)
                edit(entry)
                readReceipt()
            } finally { finished() }
        }
    }
    fun stopReading() { readJob?.cancel(); readJob = null; reading = false }
    fun readReceipt() {
        val initial = draft ?: return
        val id = initial.receiptId ?: return
        if (initial.confirmed || reading) return
        reading = true
        readJob = viewModelScope.launch {
            try {
                val started = android.os.SystemClock.elapsedRealtime()
                val lines = withTimeoutOrNull(18_000L) { recognizer.readLines(File(files, "receipts/$id.jpg")) }.orEmpty()
                if (lines.isEmpty()) {
                    message = "La foto está guardada. Completa los campos a mano o vuelve a intentar la lectura."
                    return@launch
                }
                val raw = lines.joinToString("\n") { it.text }
                val result = readFields(initial, lines)
                val details = JSONArray().apply { result.evidence.forEach { put(JSONObject().put("field", it.field)
                    .put("detected", it.detected).put("position", it.position).put("certainty", it.certainty)) } }.toString()
                withContext(Dispatchers.IO) {
                    ocr.save(id, raw)
                    extractions.save(id, ReceiptFieldExtractor.extract(raw), android.os.SystemClock.elapsedRealtime() - started)
                    File(files, "evidence").mkdirs()
                    File(files, "evidence/$id.json").writeText(details)
                }
                val cur = draft?.takeIf { it.id == initial.id && !it.confirmed } ?: return@launch
                // Keep every user edit. A retry fills only fields that are still blank.
                val s = result.entry
                val merged = cur.copy(date = cur.date.ifBlank { s.date }, total = cur.total.ifBlank { s.total },
                    merchant = cur.merchant.ifBlank { s.merchant }, description = cur.description.ifBlank { s.description },
                    subtotal = cur.subtotal.ifBlank { s.subtotal }, tax = cur.tax.ifBlank { s.tax },
                    payment = cur.payment.ifBlank { s.payment }, items = cur.items.ifEmpty { s.items })
                change(merged)
                persist(merged)
                loadEvidence(merged)
                message = "Revisa los datos sugeridos con la foto."
            } catch (c: CancellationException) { throw c }
            catch (_: Exception) { message = "Foto guardada. Puedes completar el movimiento a mano o reintentar la lectura." }
            finally { reading = false }
        }
    }
    private fun loadEvidence(entry: Entry?) {
        evidenceJob?.cancel()
        evidence = ""
        val id = entry?.receiptId ?: return
        evidenceJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val raw = File(files, "ocr/$id.txt").takeIf { it.isFile }?.readText().orEmpty()
                val notes = runCatching {
                    val list = JSONArray(File(files, "evidence/$id.json").readText())
                    List(list.length()) { i -> with(list.getJSONObject(i)) {
                        "${getString("field")}: ${getString("detected")} · ${getString("certainty")}"
                    } }.joinToString("\n")
                }.getOrDefault("")
                listOf(notes, raw).filter(String::isNotBlank).joinToString("\n\n")
            }
            if (draft?.receiptId == id) evidence = result
        }
    }
    override fun onCleared() { recognizer.close(); store.close() }
}

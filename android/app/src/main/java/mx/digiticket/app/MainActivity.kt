package mx.digiticket.app

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.*
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.documentscanner.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mx.digiticket.app.data.export.ExcelReport
import mx.digiticket.app.data.local.LedgerStore
import mx.digiticket.app.domain.CaptureSource
import mx.digiticket.app.ui.LedgerApp
import mx.digiticket.app.ui.theme.DigiTicketTheme
import java.io.File
import java.time.YearMonth
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val model by lazy { ViewModelProvider(this)[LedgerViewModel::class.java] }
    private var captureSource = CaptureSource.PRINTED_RECEIPT
    private var cameraName: String? = null
    private var exportPeriod: String? = null
    private val scanner by lazy {
        val options = GmsDocumentScannerOptions.Builder().setGalleryImportAllowed(true).setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL).build()
        GmsDocumentScanning.getClient(options)
    }
    private val scan = registerForActivityResult(StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages?.firstOrNull()?.imageUri
            if (uri != null) model.photo(uri, captureSource)
            else model.message = "No se recibió la foto. Vuelve a intentarlo."
        }
    }
    private val gallery = registerForActivityResult(OpenDocument()) { uri -> if (uri != null) model.photo(uri, captureSource) }
    private val camera = registerForActivityResult(TakePicture()) { taken ->
        val file = cameraName?.let { File(cacheDir, "camera/$it") }
        cameraName = null
        if (file != null) {
            if (taken) model.photo(providerUri(file), captureSource) { file.delete() } else file.delete()
        }
    }
    private val saveFile = registerForActivityResult(CreateDocument(ExcelReport.MIME)) { uri ->
        val pending = exportPeriod
        exportPeriod = null
        if (uri != null && pending != null) export(YearMonth.parse(pending), uri)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        captureSource = savedInstanceState?.getString("source")?.let(CaptureSource::valueOf) ?: captureSource
        cameraName = savedInstanceState?.getString("camera")
        exportPeriod = savedInstanceState?.getString("exportPeriod")
        enableEdgeToEdge()
        setContent {
            DigiTicketTheme {
                LedgerApp(model, ::capture, onExport = { share ->
                    if (share) export(model.month, null)
                    else {
                        exportPeriod = model.month.toString()
                        runCatching { saveFile.launch("DigiTicket-${model.month}.xlsx") }
                            .onFailure { model.message = "No se pudo abrir el selector de archivos." }
                    }
                })
            }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("source", captureSource.name)
        outState.putString("camera", cameraName)
        outState.putString("exportPeriod", exportPeriod)
        super.onSaveInstanceState(outState)
    }
    private fun providerUri(file: File): Uri = FileProvider.getUriForFile(this, "$packageName.files", file)
    private fun capture(source: CaptureSource, action: String) {
        captureSource = source
        runCatching {
            when (action) {
                "gallery" -> gallery.launch(arrayOf("image/*"))
                "camera" -> {
                    val dir = File(cacheDir, "camera").also { it.mkdirs() }
                    val file = File.createTempFile("ticket-", ".jpg", dir)
                    cameraName = file.name
                    camera.launch(providerUri(file))
                }
                else -> scanner.getStartScanIntent(this)
                    .addOnSuccessListener { sender ->
                        runCatching { scan.launch(IntentSenderRequest.Builder(sender).build()) }
                            .onFailure { model.message = "Puedes usar Cámara o Galería para añadir la foto." }
                    }.addOnFailureListener {
                        model.message = "El escáner necesita Servicios de Google Play. Usa Cámara o Galería."
                    }
            }
        }.onFailure { model.message = "No se pudo abrir esa opción. Prueba con Galería o captura manual." }
    }
    private fun export(period: YearMonth, target: Uri?) {
        lifecycleScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val entries = LedgerStore(applicationContext).use { it.all() }
                    val dir = File(cacheDir, "exports").also { it.mkdirs() }
                    val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
                    dir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.deleteRecursively() }
                    val folder = File(dir, UUID.randomUUID().toString()).also { check(it.mkdir()) }
                    val tmp = File(folder, "DigiTicket-$period.xlsx")
                    try {
                        tmp.outputStream().use { ExcelReport.write(it, period, entries) }
                        if (target != null) {
                            val out = contentResolver.openOutputStream(target, "wt") ?: error("No se pudo abrir el archivo.")
                            out.use { dest -> tmp.inputStream().use { it.copyTo(dest) } }
                        }
                        tmp
                    } finally { if (target != null) folder.deleteRecursively() }
                }
                if (target == null) {
                    val uri = providerUri(file)
                    val intent = Intent(Intent.ACTION_SEND).setType(ExcelReport.MIME).putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData = ClipData.newRawUri("Excel", uri) }
                    startActivity(Intent.createChooser(intent, "Compartir Excel"))
                } else model.message = "Archivo de Excel guardado."
            } catch (_: Exception) { model.message = "No se pudo exportar. Comprueba el espacio disponible o elige otra carpeta." }
        }
    }
}

package mx.digiticket.app.data.ocr

import mx.digiticket.app.domain.*
import mx.digiticket.app.domain.ocr.ReceiptFieldExtractor

data class ReadLine(val text: String, val position: String = "")
data class Suggestion(val field: String, val detected: String, val position: String, val certainty: String)

data class ReceiptReading(val entry: Entry, val evidence: List<Suggestion>)

fun readFields(original: Entry, lines: List<ReadLine>): ReceiptReading {
    val raw = lines.joinToString("\n") { it.text }
    val basic = ReceiptFieldExtractor.extract(raw)
    val foreignCurrency = Regex("""\b(USD|EUR|CAD|GBP|d[oó]lares|euros)\b""", RegexOption.IGNORE_CASE).containsMatchIn(raw)
    val signedAmount = Regex("""(?<![\d.,])(?:\d{1,3}(?:,\d{3})+|\d+)\.\d{2}(?!\d)""")
    val evidence = mutableListOf<Suggestion>()
    val handwritten = original.source == CaptureSource.HANDWRITTEN_RECEIPT
    fun note(field: String, line: ReadLine?, detected: String, uncertain: Boolean = false): String {
        if (detected.isNotBlank()) evidence.add(Suggestion(field, detected, line?.position.orEmpty(),
            if (handwritten || uncertain) "Lectura dudosa" else "Por verificar"))
        return detected
    }
    fun amount(label: Regex, title: String): String {
        if (foreignCurrency) return ""
        val line = lines.firstOrNull { label.containsMatchIn(it.text) } ?: return ""
        val value = signedAmount.findAll(line.text).lastOrNull()?.value.orEmpty()
        return note(title, line, decimal(parseCents(value)))
    }
    val date = basic.date?.toString().orEmpty()
    val dateLine = lines.firstOrNull { date.isNotEmpty() && ReceiptFieldExtractor.extractDate(it.text) == basic.date }
    val total = if (foreignCurrency) "" else decimal(basic.total?.amountInCents)
    val totalLine = lines.lastOrNull { line ->
        basic.total != null && signedAmount.findAll(line.text).any { parseCents(it.value) == basic.total.amountInCents }
    }
    val merchantLine = lines.take(3).firstOrNull { line ->
        line.text.length in 3..75 && line.text.count(Char::isLetter) >= 3 && line.text.none(Char::isDigit) &&
            !Regex("ticket|factura|recibo|fecha|total|gracias|bienvenid|nota de", RegexOption.IGNORE_CASE).containsMatchIn(line.text)
    }
    val paymentLine = lines.firstOrNull { Regex("""\b(efectivo|tarjeta|transferencia)\b""", RegexOption.IGNORE_CASE).containsMatchIn(it.text) }
    val method = paymentLine?.text?.let { Regex("efectivo|tarjeta|transferencia", RegexOption.IGNORE_CASE).find(it)?.value }
        ?.lowercase()?.replaceFirstChar { it.uppercase() }.orEmpty()
    val itemPattern = Regex("""^\s*(\d{1,4}(?:[.,]\d{1,3})?)\s+(.{2,65}?)\s+\$?([\d,]+\.\d{2})\s+\$?([\d,]+\.\d{2})\s*$""")
    val items = if (foreignCurrency) emptyList() else lines.mapNotNull { line ->
        val match = itemPattern.matchEntire(line.text) ?: return@mapNotNull null
        val (quantity, description, unit, totalLineAmount) = match.destructured
        if (description.none(Char::isLetter)) return@mapNotNull null
        val item = LineItem(description, quantity, decimal(parseCents(unit)), decimal(parseCents(totalLineAmount)))
        if (item.validated()) { note("Producto", line, line.text, true); item } else null
    }.take(80)
    val suggested = original.copy(
        date = note("Fecha", dateLine, date), total = note("Total", totalLine, total, true),
        merchant = note("Comercio", merchantLine, merchantLine?.text.orEmpty(), true),
        description = note("Descripción", lines.firstOrNull { items.firstOrNull()?.description?.let(it.text::contains) == true },
            items.firstOrNull()?.description.orEmpty(), true),
        subtotal = amount(Regex("^\\s*sub\\s*total", RegexOption.IGNORE_CASE), "Subtotal"),
        tax = amount(Regex("^\\s*(IVA|impuestos?)\\b", RegexOption.IGNORE_CASE), "Impuestos"),
        payment = note("Forma de pago", paymentLine, method), items = items
    )
    return ReceiptReading(suggested, evidence)
}

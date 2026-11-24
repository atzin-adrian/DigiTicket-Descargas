package mx.digiticket.app.domain

import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import java.util.UUID

val mexicanLocale = Locale.forLanguageTag("es-MX")
fun pesos(cents: Long): String = NumberFormat.getCurrencyInstance(mexicanLocale)
    .format(BigDecimal.valueOf(cents, 2))
fun decimal(cents: Long?): String = cents?.let { BigDecimal.valueOf(it, 2).toPlainString() }.orEmpty()

// Accept either decimal mark and Mexican thousands groups without rounding input.
fun parseCents(input: String): Long? {
    val value = input.trim()
    val normalized = when {
        Regex("""\d{1,3}(,\d{3})+(\.\d{1,2})?""").matches(value) -> value.replace(",", "")
        Regex("""\d{1,10}([.,]\d{1,2})?""").matches(value) -> value.replace(',', '.')
        else -> return null
    }
    return normalized.toBigDecimalOrNull()?.takeIf { it <= BigDecimal("9999999999.99") }
        ?.movePointRight(2)?.longValueExact()
}

enum class Review(val label: String) {
    PENDING("Pendiente"), REVIEWED("Revisado"), ILLEGIBLE("Ilegible"), CONFIRMED("Confirmado")
}

val MovementType.label: String get() = when (this) {
    MovementType.SALE -> "Venta"
    MovementType.EXPENSE -> "Gasto"
    MovementType.CONTRIBUTION -> "Aportación personal"
    MovementType.WITHDRAWAL -> "Retiro personal"
    MovementType.LOAN -> "Préstamo recibido"
    MovementType.REPAYMENT -> "Pago de capital"
}
val CaptureSource.label: String get() = when (this) {
    CaptureSource.MANUAL -> "Captura manual"
    CaptureSource.PRINTED_RECEIPT -> "Ticket impreso"
    CaptureSource.HANDWRITTEN_RECEIPT -> "Nota a mano"
}
fun categories(type: MovementType) = when (type) {
    MovementType.SALE -> listOf("Venta de productos", "Servicios", "Otros ingresos")
    MovementType.EXPENSE -> listOf("Mercancía e insumos", "Renta", "Servicios", "Transporte",
        "Sueldos", "Mantenimiento", "Impuestos", "Equipo", "Otros gastos")
    else -> listOf(type.label)
}

data class LineItem(val description: String, val quantity: String = "", val unitPrice: String = "", val amount: String = "") {
    fun validated(): Boolean {
        val qty = quantity.replace(',', '.')
        return description.isNotBlank() && (qty.isBlank() ||
            (Regex("""\d{1,7}(\.\d{1,3})?""").matches(qty) && BigDecimal(qty).signum() > 0)) &&
            listOf(unitPrice, amount).all { it.isBlank() || parseCents(it) != null }
    }
}
data class Entry(
    val id: String = UUID.randomUUID().toString(),
    val type: MovementType = MovementType.EXPENSE,
    val source: CaptureSource = CaptureSource.MANUAL,
    val created: LocalDate = LocalDate.now(),
    val date: String = "",
    val time: String = "",
    val description: String = "",
    val merchant: String = "",
    val category: String = "",
    val subtotal: String = "",
    val tax: String = "",
    val total: String = "",
    val payment: String = "",
    val paymentStatus: String = "",
    val notes: String = "",
    val review: Review = Review.PENDING,
    val receiptId: String? = null,
    val items: List<LineItem> = emptyList()
) {
    val parsedDate: LocalDate? get() = runCatching { LocalDate.parse(date) }.getOrNull()
    val month: YearMonth get() = YearMonth.from(parsedDate ?: created)
    val confirmed: Boolean get() = review == Review.CONFIRMED
    fun errorForConfirmation(): String? = when {
        parsedDate == null || parsedDate!!.year !in 1901..9999 -> "Selecciona una fecha válida."
        time.isNotBlank() && !Regex("""([01]\d|2[0-3]):[0-5]\d""").matches(time) -> "Usa una hora de 00:00 a 23:59."
        description.isBlank() -> "Escribe qué vendiste o en qué gastaste."
        (parseCents(total) ?: 0L) <= 0L -> "Escribe un total mayor a cero, con hasta dos decimales."
        listOf(subtotal, tax).any { it.isNotBlank() && parseCents(it) == null } -> "Revisa el subtotal y los impuestos."
        items.any { !it.validated() } -> "Revisa la descripción y los importes de cada producto."
        else -> null
    }
    fun possibleDuplicate(other: Entry) = id != other.id && other.confirmed && type == other.type &&
        date == other.date && parseCents(total) == parseCents(other.total) &&
        description.trim().equals(other.description.trim(), ignoreCase = true)
}

fun monthlySummary(entries: List<Entry>, month: YearMonth): FinancialSummary = calculateFinancialSummary(
    entries.filter { it.confirmed && it.month == month }.map { entry ->
        Movement(entry.description, requireNotNull(parseCents(entry.total)), entry.type, entry.source)
    }
)

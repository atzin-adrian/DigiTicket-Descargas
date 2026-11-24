package mx.digiticket.app.domain.ocr

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptFieldExtractorTest {

    @Test
    fun `extracts a numeric Mexican date with a two-digit year`() {
        val rawText = "MUESTRA 14/04/25 08:40"

        val date = ReceiptFieldExtractor.extractDate(rawText)

        assertEquals(LocalDate.of(2025, 4, 14), date)
    }

    @Test
    fun `extracts a Spanish textual date even when weekday and day touch`() {
        val rawText = "jueves12 mar. 2026 09:00"

        val date = ReceiptFieldExtractor.extractDate(rawText)

        assertEquals(LocalDate.of(2026, 3, 12), date)
    }

    @Test
    fun `does not return an impossible date`() {
        val date = ReceiptFieldExtractor.extractDate("31/02/26")

        assertNull(date)
    }

    @Test
    fun `continues looking after an impossible date candidate`() {
        val date = ReceiptFieldExtractor.extractDate("99/99/99 emitido 08 SEP 2026")

        assertEquals(LocalDate.of(2026, 9, 8), date)
    }

    @Test
    fun `selects the repeated total when OCR emits dot and hyphen variants`() {
        val rawText = """
            TotaTM.N
            32.40 32.40
            11.20 11.20
            Subtotal:
            IVA AL16:
            Pagos Tarjeta Credito:
            Cambio:
            43.60
            6.98
            50-58
            50.58
            0.00
        """.trimIndent()

        val total = ReceiptFieldExtractor.extractTotal(rawText)

        assertEquals(5_058L, total?.amountInCents)
        assertEquals(TotalExtractionStrategy.REPEATED_AMOUNT, total?.strategy)
    }

    @Test
    fun `selects the first monetary amount near an explicit total label`() {
        val rawText = """
            Total
            Gracias por su visita
            Or ${'$'}
            Cp ${'$'}
            168.00
            42.50
            64.00
        """.trimIndent()

        val total = ReceiptFieldExtractor.extractTotal(rawText)

        assertEquals(16_800L, total?.amountInCents)
        assertEquals(TotalExtractionStrategy.TOTAL_LABEL_PROXIMITY, total?.strategy)
    }

    @Test
    fun `uses the last monetary amount when a printed total label is missing`() {
        val rawText = """
            DESCUENTO 1,250.00-
            5,000.00
            3,750.00
        """.trimIndent()

        val total = ReceiptFieldExtractor.extractTotal(rawText)

        assertEquals(375_000L, total?.amountInCents)
        assertEquals(TotalExtractionStrategy.LAST_MONETARY_AMOUNT, total?.strategy)
    }

    @Test
    fun `recognizes a handwritten arithmetic result without decimal digits`() {
        val rawText = """
            125. >X
            5
            625• +
        """.trimIndent()

        val total = ReceiptFieldExtractor.extractTotal(rawText)

        assertEquals(62_500L, total?.amountInCents)
        assertEquals(TotalExtractionStrategy.HANDWRITTEN_ARITHMETIC_RESULT, total?.strategy)
    }

    @Test
    fun `does not invent a total from unrelated handwritten digits`() {
        val rawText = """
            2 Tacos 45
            3 Aguas 38
            2 Quesadillas 44
        """.trimIndent()

        assertNull(ReceiptFieldExtractor.extractTotal(rawText))
    }
}

package mx.digiticket.app.domain

import mx.digiticket.app.data.export.ExcelReport
import mx.digiticket.app.data.ocr.ReadLine
import mx.digiticket.app.data.ocr.readFields
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class LedgerTest {
    private val month = YearMonth.of(2026, 11)
    private val sale = Entry(type=MovementType.SALE, date="2026-11-20", description="Pan", total="125.05",
        review=Review.CONFIRMED)
    @Test fun validatesMoneyWithoutRounding() {
        assertEquals(123450L, parseCents("1,234.50")); assertEquals(12505L, parseCents("125,05"))
        for (s in listOf("-2", "1e3", "2.345", "1,22,4", "10000000000.00")) assertNull(parseCents(s))
        assertNotNull(sale.copy(total="0").errorForConfirmation())
        assertNotNull(sale.copy(date="2026-02-30").errorForConfirmation())
    }
    @Test fun countsOnlyConfirmedOperatingTransactionsInTheirMonth() {
        val rows = listOf(sale, sale.copy(type=MovementType.EXPENSE,total="25.01"),
            sale.copy(review=Review.PENDING), sale.copy(review=Review.ILLEGIBLE),
            sale.copy(type=MovementType.LOAN), sale.copy(date="2026-12-01"))
        assertEquals(FinancialSummary(12505,2501),monthlySummary(rows,month))
        assertEquals(10004L,monthlySummary(rows,month).resultInCents)
        assertEquals(month, sale.copy(date="",created=LocalDate.of(2026,11,4)).month)
    }
    @Test fun flagsDuplicateAcrossDistinctConfirmedEntries() {
        assertTrue(sale.copy(id="another",description=" pan ",total="125,05").possibleDuplicate(sale))
        assertFalse(sale.possibleDuplicate(sale))
        assertFalse(sale.copy(id="another").possibleDuplicate(sale.copy(review=Review.PENDING)))
    }
    @Test fun marksOcrAsSuggestionsAndProtectsAgainstCurrencyMixups() {
        val raw=listOf("La Tiendita","20/11/2026","2 Pan integral 20.00 40.00","IVA 6.40","TOTAL 46.40","Efectivo")
            .map { ReadLine(it,"1,2,3,4") }
        val reading=readFields(Entry(source=CaptureSource.HANDWRITTEN_RECEIPT),raw)
        assertEquals("46.40", reading.entry.total);assertEquals("2026-11-20",reading.entry.date)
        assertEquals("Pan integral",reading.entry.items.single().description)
        assertEquals(Review.PENDING,reading.entry.review)
        assertTrue(reading.evidence.all { it.certainty=="Lectura dudosa" })
        val foreign=readFields(Entry(),raw+ReadLine("Moneda: USD")).entry
        assertEquals("",foreign.total);assertTrue(foreign.items.isEmpty())
    }
    @Test fun exportsTypedDatesMoneyLiteralTextProductsAndSameSummary() {
        val out=ByteArrayOutputStream()
        val product=LineItem("Pan & café","2","62.50","125.00")
        val rows=listOf(sale.copy(items=listOf(product),description="=SUM(A1) <&>"),sale.copy(type=MovementType.EXPENSE,total="5.01"),
            sale.copy(review=Review.PENDING), sale.copy(date="2026-12-04",description="Fuera"))
        ExcelReport.write(out,month,rows)
        val parts=mutableMapOf<String,ByteArray>()
        ZipInputStream(out.toByteArray().inputStream()).use { zip ->
            while(true) { val name=zip.nextEntry?.name ?: break; parts[name]=zip.readBytes() }
        }
        val parser=DocumentBuilderFactory.newInstance().newDocumentBuilder()
        parts.values.forEach { parser.parse(it.inputStream()) }
        val summary=parts.getValue("xl/worksheets/sheet1.xml").toString(Charsets.UTF_8)
        val tx=parts.getValue("xl/worksheets/sheet2.xml").toString(Charsets.UTF_8)
        assertTrue(summary.contains("<v>120.04</v>"));assertTrue(tx.contains("=SUM(A1) &lt;&amp;&gt;"));assertFalse(tx.contains("<f>"))
        assertTrue(tx.contains("s=\"3\"><v>"));assertTrue(tx.contains("autoFilter"));assertFalse(tx.contains("Fuera"))
        assertTrue(parts.getValue("xl/worksheets/sheet3.xml").toString(Charsets.UTF_8).contains("Pan &amp; café"))
    }
}

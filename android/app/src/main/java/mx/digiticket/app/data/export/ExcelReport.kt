package mx.digiticket.app.data.export

import mx.digiticket.app.domain.*
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A standard Office Open XML workbook. User text always goes into inline string cells. */
object ExcelReport {
    const val MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    private const val sheetNs = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val docRel = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val relNs = "http://schemas.openxmlformats.org/package/2006/relationships"
    private val sheets = listOf("Resumen", "Movimientos", "Productos")
    private val excelEpoch = LocalDate.of(1899, 12, 30)

    fun write(out: OutputStream, period: YearMonth, entries: List<Entry>) {
        val rows = entries.filter { it.month == period }.sortedBy { it.date }
        val summary = monthlySummary(rows, period)
        fun outstanding(type: MovementType) = rows.filter {
            it.confirmed && it.type == type && it.paymentStatus == "Pendiente de pago"
        }.sumOf { requireNotNull(parseCents(it.total)) }

        ZipOutputStream(out.buffered()).use { zip ->
            fun part(name: String, body: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(body.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            part("[Content_Types].xml", """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>${sheets.indices.joinToString("") { """<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""" }}</Types>""")
            part("_rels/.rels", """<Relationships xmlns="$relNs"><Relationship Id="rId1" Type="$docRel/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            part("xl/workbook.xml", """<workbook xmlns="$sheetNs" xmlns:r="$docRel"><sheets>${sheets.mapIndexed { i, name -> """<sheet name="$name" sheetId="${i + 1}" r:id="rId${i + 1}"/>""" }.joinToString("")}</sheets></workbook>""")
            part("xl/_rels/workbook.xml.rels", """<Relationships xmlns="$relNs">${sheets.indices.joinToString("") { """<Relationship Id="rId${it + 1}" Type="$docRel/worksheet" Target="worksheets/sheet${it + 1}.xml"/>""" }}<Relationship Id="rId4" Type="$docRel/styles" Target="styles.xml"/></Relationships>""")
            part("xl/styles.xml", styles)
            sheet(zip, 1, listOf("Concepto", "Valor"), listOf(33, 25), sequenceOf(
                listOf(str("Periodo"), str(period.toString())),
                listOf(str("Ventas confirmadas"), money(summary.salesInCents)),
                listOf(str("Gastos confirmados"), money(summary.expensesInCents)),
                listOf(str("Resultado estimado"), money(summary.resultInCents)),
                listOf(str("Por cobrar"), money(outstanding(MovementType.SALE))),
                listOf(str("Por pagar"), money(outstanding(MovementType.EXPENSE))),
                listOf(str("Movimientos sin confirmar"), number(rows.count { !it.confirmed }.toString()))
            ))
            val headings = listOf("Fecha", "Tipo", "Descripción", "Categoría", "Subtotal", "Impuestos", "Total",
                "Forma de pago", "Estado del pago", "Revisión", "Notas", "Comercio", "Origen", "Hora", "ID")
            sheet(zip, 2, headings, listOf(15,24,36,25,16,16,16,20,23,17,40,26,23,12,40), rows.asSequence().map { e ->
                listOf(date(e), str(e.type.label), str(e.description), str(e.category), money(parseCents(e.subtotal)),
                    money(parseCents(e.tax)), money(parseCents(e.total)), str(e.payment), str(e.paymentStatus),
                    str(e.review.label), str(e.notes), str(e.merchant), str(e.source.label), str(e.time), str(e.id))
            })
            sheet(zip, 3, listOf("Movimiento", "Fecha", "Producto o servicio", "Cantidad", "Precio unitario", "Importe"),
                listOf(40,15,38,15,19,18), rows.asSequence().flatMap { e ->
                    e.items.asSequence().map { item -> listOf(str(e.id), date(e), str(item.description),
                        item.quantity.replace(',', '.').toBigDecimalOrNull()?.let { number(it.toPlainString()) } ?: blank,
                        money(parseCents(item.unitPrice)), money(parseCents(item.amount))) }
                })
        }
    }

    private data class Cell(val value: String, val numeric: Boolean = false, val style: Int = 0)
    private val blank = Cell("")
    private fun str(value: String) = Cell(value)
    private fun number(value: String) = Cell(value, true)
    private fun money(value: Long?) = value?.let { Cell(BigDecimal.valueOf(it, 2).toPlainString(), true, 2) } ?: blank
    private fun date(e: Entry) = e.parsedDate?.let {
        Cell((it.toEpochDay() - excelEpoch.toEpochDay()).toString(), true, 3)
    } ?: blank

    private fun escape(text: String) = buildString {
        text.take(32767).codePoints().forEach { cp ->
            when (cp) {
                38 -> append("&amp;")
                60 -> append("&lt;")
                62 -> append("&gt;")
                9, 10, 13, in 32..0xD7FF, in 0xE000..0xFFFD, in 0x10000..0x10FFFF -> appendCodePoint(cp)
            }
        }
    }

    private fun sheet(zip: ZipOutputStream, index: Int, headers: List<String>, widths: List<Int>, rows: Sequence<List<Cell>>) {
        zip.putNextEntry(ZipEntry("xl/worksheets/sheet$index.xml"))
        val writer = OutputStreamWriter(zip, Charsets.UTF_8)
        writer.write("""<worksheet xmlns="$sheetNs"><sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews><cols>""")
        widths.forEachIndexed { i, width -> writer.write("""<col min="${i + 1}" max="${i + 1}" width="$width" customWidth="1"/>""") }
        writer.write("</cols><sheetData>")
        var row = 0
        fun writeRow(cells: List<Cell>) {
            row++
            writer.write("""<row r="$row">""")
            cells.forEachIndexed { column, c ->
                val ref = "${'A' + column}$row"
                if (c.numeric) writer.write("""<c r="$ref" s="${c.style}"><v>${c.value}</v></c>""")
                else writer.write("""<c r="$ref" s="${c.style}" t="inlineStr"><is><t xml:space="preserve">${escape(c.value)}</t></is></c>""")
            }
            writer.write("</row>")
        }
        writeRow(headers.map { Cell(it, style = 1) })
        rows.forEach(::writeRow)
        writer.write("""</sheetData><autoFilter ref="A1:${'A' + headers.lastIndex}$row"/></worksheet>""")
        writer.flush()
        zip.closeEntry()
    }

    private val styles = """<styleSheet xmlns="$sheetNs"><numFmts count="2"><numFmt numFmtId="164" formatCode="&quot;$&quot;#,##0.00;[Red](&quot;$&quot;#,##0.00)"/><numFmt numFmtId="165" formatCode="dd/mm/yyyy"/></numFmts><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><color rgb="FFFFFFFF"/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF124E45"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="4"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""
}

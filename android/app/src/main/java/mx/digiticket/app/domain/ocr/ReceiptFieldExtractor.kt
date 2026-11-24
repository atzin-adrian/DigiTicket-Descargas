package mx.digiticket.app.domain.ocr

import java.time.DateTimeException
import java.time.LocalDate
import java.text.Normalizer

data class ReceiptFieldExtraction(
    val date: LocalDate?,
    val total: ExtractedTotal?
)

data class ExtractedTotal(
    val amountInCents: Long,
    val strategy: TotalExtractionStrategy
)

enum class TotalExtractionStrategy {
    REPEATED_AMOUNT,
    TOTAL_LABEL_PROXIMITY,
    LAST_MONETARY_AMOUNT,
    HANDWRITTEN_ARITHMETIC_RESULT
}

object ReceiptFieldExtractor {

    private val numericDatePattern = Regex(
        pattern = "(?<!\\d)(\\d{1,2})[/-](\\d{1,2})[/-](\\d{2}|\\d{4})(?!\\d)"
    )
    private val spanishDatePattern = Regex(
        pattern = "(?i)(?<!\\d)(\\d{1,2})\\s*" +
            "(ene(?:ro)?|feb(?:rero)?|mar(?:zo)?|abr(?:il)?|may(?:o)?|" +
            "jun(?:io)?|jul(?:io)?|ago(?:sto)?|sep(?:t(?:iembre)?)?|" +
            "oct(?:ubre)?|nov(?:iembre)?|dic(?:iembre)?)\\.?\\s*(\\d{4})(?!\\d)"
    )
    private val monetaryAmountPattern = Regex(
        pattern = "(?<![\\d/])\\$?\\s*(\\d{1,3}(?:,\\d{3})+|\\d+)[.-](\\d{2})(?!\\d)"
    )
    private val handwrittenResultPattern = Regex(
        pattern = "^\\s*(\\d{2,7})\\D*[+=]\\s*$"
    )

    fun extract(rawText: String): ReceiptFieldExtraction = ReceiptFieldExtraction(
        date = extractDate(rawText),
        total = extractTotal(rawText)
    )

    fun extractDate(rawText: String): LocalDate? {
        numericDatePattern.findAll(rawText).forEach { match ->
            val day = match.groupValues[1].toIntOrNull() ?: return@forEach
            val month = match.groupValues[2].toIntOrNull() ?: return@forEach
            val rawYear = match.groupValues[3].toIntOrNull() ?: return@forEach
            val year = if (rawYear < 100) 2000 + rawYear else rawYear

            createDateOrNull(year, month, day)?.let { return it }
        }

        spanishDatePattern.findAll(normalizeForMatching(rawText)).forEach { match ->
            val day = match.groupValues[1].toIntOrNull() ?: return@forEach
            val month = spanishMonthNumber(match.groupValues[2]) ?: return@forEach
            val year = match.groupValues[3].toIntOrNull() ?: return@forEach

            createDateOrNull(year, month, day)?.let { return it }
        }

        return null
    }

    fun extractTotal(rawText: String): ExtractedTotal? {
        val lines = rawText.lines()
        val candidates = lines.flatMapIndexed { lineIndex, line ->
            monetaryAmountPattern.findAll(line).mapNotNull { match ->
                parseDecimalAmount(match.groupValues[1], match.groupValues[2])
                    ?.takeIf { amountInCents -> amountInCents > 0 }
                    ?.let { amountInCents ->
                        AmountCandidate(
                            amountInCents = amountInCents,
                            lineIndex = lineIndex
                        )
                    }
            }.toList()
        }

        if (candidates.isNotEmpty()) {
            val repeatedAmount = candidates
                .groupingBy { it.amountInCents }
                .eachCount()
                .filterValues { count -> count > 1 }
                .keys
                .maxOrNull()

            if (repeatedAmount != null && lines.any(::containsTotalLabel)) {
                return ExtractedTotal(
                    amountInCents = repeatedAmount,
                    strategy = TotalExtractionStrategy.REPEATED_AMOUNT
                )
            }

            val totalLineIndex = lines.indexOfFirst(::containsTotalLabel)
            if (totalLineIndex >= 0) {
                candidates
                    .firstOrNull { candidate ->
                        candidate.lineIndex in totalLineIndex..(totalLineIndex + 8)
                    }
                    ?.let { candidate ->
                        return ExtractedTotal(
                            amountInCents = candidate.amountInCents,
                            strategy = TotalExtractionStrategy.TOTAL_LABEL_PROXIMITY
                        )
                    }
            }

            return ExtractedTotal(
                amountInCents = candidates.last().amountInCents,
                strategy = TotalExtractionStrategy.LAST_MONETARY_AMOUNT
            )
        }

        lines.asReversed().forEach { line ->
            handwrittenResultPattern.matchEntire(line)?.let { match ->
                return ExtractedTotal(
                    amountInCents = match.groupValues[1].toLong() * 100,
                    strategy = TotalExtractionStrategy.HANDWRITTEN_ARITHMETIC_RESULT
                )
            }
        }

        return null
    }

    private fun containsTotalLabel(line: String): Boolean {
        val compactLine = normalizeForMatching(line)
            .lowercase()
            .filter(Char::isLetterOrDigit)

        return compactLine.startsWith("tota")
    }

    private fun normalizeForMatching(value: String): String = Normalizer
        .normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")

    private fun parseDecimalAmount(wholePart: String, decimalPart: String): Long? {
        val wholePesos = wholePart.replace(",", "").toLongOrNull() ?: return null
        val cents = decimalPart.toLongOrNull() ?: return null

        if (wholePesos > (Long.MAX_VALUE - cents) / 100) {
            return null
        }

        return wholePesos * 100 + cents
    }

    private fun spanishMonthNumber(monthText: String): Int? = when {
        monthText.lowercase().startsWith("ene") -> 1
        monthText.lowercase().startsWith("feb") -> 2
        monthText.lowercase().startsWith("mar") -> 3
        monthText.lowercase().startsWith("abr") -> 4
        monthText.lowercase().startsWith("may") -> 5
        monthText.lowercase().startsWith("jun") -> 6
        monthText.lowercase().startsWith("jul") -> 7
        monthText.lowercase().startsWith("ago") -> 8
        monthText.lowercase().startsWith("sep") -> 9
        monthText.lowercase().startsWith("oct") -> 10
        monthText.lowercase().startsWith("nov") -> 11
        monthText.lowercase().startsWith("dic") -> 12
        else -> null
    }

    private fun createDateOrNull(year: Int, month: Int, day: Int): LocalDate? = try {
        LocalDate.of(year, month, day)
    } catch (_: DateTimeException) {
        null
    }

    private data class AmountCandidate(
        val amountInCents: Long,
        val lineIndex: Int
    )
}

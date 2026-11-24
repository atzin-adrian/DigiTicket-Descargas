package mx.digiticket.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FinancialSummaryTest {

    @Test
    fun `calculates sales expenses and estimated result`() {
        val movements = listOf(
            Movement(
                description = "Venta de abarrotes",
                amountInCents = 150_000L,
                type = MovementType.SALE,
                source = CaptureSource.MANUAL
            ),
            Movement(
                description = "Venta de bebidas",
                amountInCents = 75_050L,
                type = MovementType.SALE,
                source = CaptureSource.PRINTED_RECEIPT
            ),
            Movement(
                description = "Compra de mercancía",
                amountInCents = 90_000L,
                type = MovementType.EXPENSE,
                source = CaptureSource.HANDWRITTEN_RECEIPT
            )
        )

        val summary = calculateFinancialSummary(movements)

        assertEquals(225_050L, summary.salesInCents)
        assertEquals(90_000L, summary.expensesInCents)
        assertEquals(135_050L, summary.resultInCents)
    }

    @Test
    fun `returns zeros when there are no movements`() {
        val summary = calculateFinancialSummary(emptyList())

        assertEquals(0L, summary.salesInCents)
        assertEquals(0L, summary.expensesInCents)
        assertEquals(0L, summary.resultInCents)
    }
}
package mx.digiticket.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MovementTest {

    @Test
    fun `creates a valid expense`() {
        val movement = Movement(
            description = "Compra de mercancía",
            amountInCents = 12_550L,
            type = MovementType.EXPENSE,
            source = CaptureSource.PRINTED_RECEIPT
        )

        assertEquals(12_550L, movement.amountInCents)
        assertEquals(MovementType.EXPENSE, movement.type)
    }

    @Test
    fun `rejects an amount equal to zero`() {
        assertThrows(IllegalArgumentException::class.java) {
            Movement(
                description = "Importe inválido",
                amountInCents = 0L,
                type = MovementType.EXPENSE,
                source = CaptureSource.MANUAL
            )
        }
    }
}
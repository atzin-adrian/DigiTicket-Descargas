package mx.digiticket.app.domain

enum class MovementType {
    SALE,
    EXPENSE,
    CONTRIBUTION,
    WITHDRAWAL,
    LOAN,
    REPAYMENT
}

enum class CaptureSource {
    MANUAL,
    PRINTED_RECEIPT,
    HANDWRITTEN_RECEIPT
}

data class Movement(
    val description: String?,
    val amountInCents: Long,
    val type: MovementType,
    val source: CaptureSource
) {
    init {
        require(amountInCents > 0) {
            "Movement amount must be greater than zero"
        }
    }
}
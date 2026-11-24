package mx.digiticket.app.domain

data class FinancialSummary(
    val salesInCents: Long,
    val expensesInCents: Long
) {
    val resultInCents: Long
        get() = salesInCents - expensesInCents
}

fun calculateFinancialSummary(
    movements: List<Movement>
): FinancialSummary {
    val sales = movements
        .filter { it.type == MovementType.SALE }
        .sumOf { it.amountInCents }

    val expenses = movements
        .filter { it.type == MovementType.EXPENSE }
        .sumOf { it.amountInCents }

    return FinancialSummary(
        salesInCents = sales,
        expensesInCents = expenses
    )
}
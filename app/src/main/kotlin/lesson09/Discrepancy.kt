package lesson09

data class Discrepancy (
    val path : String,
    val expected : String,
    val actual : String
)

sealed interface ValidateResult{
    val discrepancies : List<Discrepancy>

    data object OkValid : ValidateResult {
        override val discrepancies : List<Discrepancy> = emptyList()
    }
    data class BadValid (override val discrepancies: List<Discrepancy>) : ValidateResult
}

class DiscrepancyBuilder {
    private val discrepancies: MutableList<Discrepancy> = mutableListOf()

    fun discrepancy(path: String, expected: String, actual: String) {
        discrepancies += Discrepancy(path, expected, actual)
    }
    fun result(): ValidateResult {
        return if (discrepancies.isEmpty()) {
            ValidateResult.OkValid
        } else {
            ValidateResult.BadValid(discrepancies.toList())
        }
    }
}
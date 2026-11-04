package lesson09

data class Discrepancy (
    val path : String,
    val expected : String,
    val actual : String
    val b : MutableList
)

sealed interface ValidateResult{
    data object OkValid : ValidateResult
    data class BadValid (val discrepancies: MutableList<Discrepancy>) : ValidateResult
}


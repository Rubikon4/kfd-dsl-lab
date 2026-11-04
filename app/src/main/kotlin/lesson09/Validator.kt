package lesson09

@DslMarker
annotation class ValidationDsl

@ValidationDsl
class Validator(
    private val response: HttpResponse,
    private val errors: DiscrepancyBuilder
) {
    fun status(block: StatusValidator.() -> Unit) : Unit {
        val status = StatusValidator(response.status, errors)
    }
    fun header(headerName: String, block: HeaderValidator.() -> Unit) : Unit {
        val headerValues = headerValues(response, headerName)
        val header = HeaderValidator(headerName, headerValues, errors)
    }
    fun body(block: BodyValidator.() -> Unit) : Unit {
        val body = BodyValidator(response.body, errors)
    }
}

@ValidationDsl
class StatusValidator(
    private val actualStatus: Int,
    private val errors: DiscrepancyBuilder
) {
    fun equalTo(expectedStatus: Int) {
        if (expectedStatus != actualStatus)
            errors.discrepancy(
                "status",
                expectedStatus.toString(),
                actualStatus.toString())

    }
}

@ValidationDsl
class HeaderValidator(
    private val headerName: String,
    private val headerValues: List<String>,
    private val errors: DiscrepancyBuilder
) {
    fun equalTo() {}
    fun startsWith(expectedValue: String) {
        val firstValue = headerValues.first()
        if (firstValue != expectedValue)
            errors.discrepancy(
                "headers.$headerName",
                expectedValue,
                firstValue
            )
    }
    fun satisfies() {}
}

@ValidationDsl
class BodyValidator(
    private val actualBody: String?,
    private val errors: DiscrepancyBuilder
) {
    fun absentOrEmpty() {
        if (actualBody.isNullOrEmpty())
            errors.discrepancy(
                "body",
                "non-null or empty body",
                "body is <null> or empty")
    }
    fun nonEmpty() {
        if (actualBody.isNullOrEmpty())
            errors.discrepancy(
                "body",
                "non-empty body",
                if (actualBody == null) "body is <null>" else "body is empty(\"\")")
    }
    fun json() {}
}

@ValidationDsl
class JsonValidator {
    fun field() {}
}

@ValidationDsl
class FieldValidator {
    fun present() {}
//    fun equalTo() {}
//    fun equalTo() {}
//    fun equalTo() {}
}

fun validate(response: HttpResponse, build: Validator.() -> Unit) : ValidateResult {
    val errors = DiscrepancyBuilder()
    val v = Validator(response, errors)
    v.build()
    return errors.result()
}

private fun headerValues(response: HttpResponse, name: String): List<String> =
    response.headers.entries
        .filter { it.key.equals(name, ignoreCase = true) }
        .flatMap { it.value }
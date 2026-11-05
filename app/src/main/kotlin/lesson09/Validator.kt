package lesson09

import kotlin.math.exp

@DslMarker
annotation class ValidationDsl

@ValidationDsl
class Validator(
    private val response: HttpResponse,
    private val errors: DiscrepancyBuilder
) {
    fun status(block: StatusValidator.() -> Unit) : Unit {
        val status = StatusValidator(response.status, errors)
        status.block()
    }
    fun header(headerName: String, block: HeaderValidator.() -> Unit) : Unit {
        val headerValues = headerValues(response, headerName)
        val header = HeaderValidator(headerName, headerValues, errors)
        header.block()
    }
    fun body(block: BodyValidator.() -> Unit) : Unit {
        val body = BodyValidator(response.body, errors)
        body.block()
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
    fun equalTo(expectedValue: String) {
        if (headerValues.none { it == expectedValue })
            errors.discrepancy(
                "header.$headerName",
                "value was expected: $expectedValue",
                "there is no expected value in current header"
            )
    }
    fun startsWith(expectedValue: String) {
        val firstValue = headerValues.first()
        if (firstValue != expectedValue)
            errors.discrepancy(
                "headers.$headerName",
                "first value was expected to be $expectedValue",
                "first value is actual $firstValue"
            )
    }
    fun satisfies(expectation: String, check: (String) -> Boolean) {
        if (headerValues.none(check)) {
            errors.discrepancy(
                "headers.$headerName",
                "expectation: $expectation",
                headerValues.toString(),
            )
        }
    }
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

// entry-point DSL
fun validate(response: HttpResponse, build: Validator.() -> Unit) : ValidateResult {
    val errors = DiscrepancyBuilder()
    val v = Validator(response, errors)
    v.build()
    return errors.result()
}

// parse headers with key to Map of header[values]
private fun headerValues(response: HttpResponse, name: String): List<String> =
    response.headers.entries
        .filter { it.key.equals(name, ignoreCase = true) }
        .flatMap { it.value }
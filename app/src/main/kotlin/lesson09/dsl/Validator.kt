package lesson09.dsl

import lesson09.HttpResponse
import lesson09.support.json.JsonParseResult
import lesson09.support.json.parseJsonObject

private fun renderString(value: String): String {
    val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
    return "\"" + escaped + "\""
}

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
        val matchingHeaders = response.headers.entries
            .filter { it.key.equals(headerName, ignoreCase = true) }
        val headerValues = matchingHeaders.flatMap { it.value }
        val header = HeaderValidator(headerName, matchingHeaders.isNotEmpty(), headerValues, errors)
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
                "Expected status: ${expectedStatus.toString()}",
                "Actual status is ${actualStatus.toString()}")

    }
}

@ValidationDsl
class HeaderValidator(
    private val headerName: String,
    private val headerExists: Boolean,
    private val headerValues: List<String>,
    private val errors: DiscrepancyBuilder
) {
    private val actualValues: String
        get() = if (headerExists)
            headerValues.joinToString(prefix = "[", postfix = "]") { renderString(it) }
        else "<missing>"

    fun equalTo(expectedValue: String) {
        if (headerValues.none { it == expectedValue })
            errors.discrepancy(
                "headers.$headerName",
                "value was expected: $expectedValue",
                actualValues
            )
    }
    fun startsWith(expectedValue: String) {
        if (headerValues.none { it.startsWith(expectedValue) })
            errors.discrepancy(
                "headers.$headerName",
                "prefix was expected: $expectedValue",
                actualValues
            )
    }
    fun satisfies(expectation: String, check: (String) -> Boolean) {
        if (headerValues.none(check)) {
            errors.discrepancy(
                "headers.$headerName",
                "expectation: $expectation",
                actualValues,
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
        if (!actualBody.isNullOrEmpty())
            errors.discrepancy(
                "body",
                "absent or empty body",
                "body is " + renderString(actualBody))
    }
    fun nonEmpty() {
        if (actualBody.isNullOrEmpty())
            errors.discrepancy(
                "body",
                "non-empty body",
                if (actualBody == null) "body is <null>" else "body is empty(\"\")")
    }
    fun json(block: JsonValidator.() -> Unit) : Unit {
        if (actualBody == null) {
            errors.discrepancy(
                "body.json",
                "existing json object",
                "json is <null>"
            )
            return
        }
        when (val result = parseJsonObject(actualBody)) {
            is JsonParseResult.Success -> {
                val json = JsonValidator(result.fields, errors)
                json.block()
            }
             is JsonParseResult.Failure -> {
                 errors.discrepancy(
                     "body.json",
                     "success parse of json",
                     "failure, reason: ${result.reason}"
                 )
             }


        }
    }
}

@ValidationDsl
class JsonValidator(
    private val fields: Map<String, Any?>,
    private val errors: DiscrepancyBuilder
) {
    fun field(name: String, block: FieldValidator.() -> Unit) : Unit {
        val field = FieldValidator(
            name,
            fields.containsKey(name),
            fields[name],
            errors)
        field.block()
    }
}

@ValidationDsl
class FieldValidator(
    private val name: String,
    private val isPresent: Boolean,
    private val actualValue: Any?,
    private val errors: DiscrepancyBuilder
) {
    private val path: String = "body.json.$name"
    private val actual: String
        get() = when {
            !isPresent -> "<missing>"
            actualValue is String -> renderString(actualValue)
            else -> actualValue.toString()
        }

    fun present() {
        if (!isPresent)
            errors.discrepancy(path, "field $name exists", actual)
    }
    fun equalTo(expectedValue: String) {
        if (!isPresent || actualValue != expectedValue)
            errors.discrepancy(path, "value was expected: " + renderString(expectedValue), actual)
    }
    fun equalTo(expectedValue: Long) {
        if (!isPresent || actualValue != expectedValue)
            errors.discrepancy(path, "value was expected: $expectedValue", actual)
    }
    fun equalTo(expectedValue: Boolean) {
        if (!isPresent || actualValue != expectedValue)
            errors.discrepancy(path, "value was expected: $expectedValue", actual)
    }
}

// entry-point DSL
fun validate(response: HttpResponse, build: Validator.() -> Unit) : ValidateResult {
    val errors = DiscrepancyBuilder()
    val v = Validator(response, errors)
    v.build()
    return errors.result()
}


package lesson09

import lesson09.dsl.Discrepancy
import lesson09.dsl.ValidateResult
import lesson09.dsl.Validator
import lesson09.dsl.check01Dsl
import lesson09.dsl.check02Dsl
import lesson09.dsl.check03Dsl
import lesson09.dsl.check04Dsl
import lesson09.dsl.check05Dsl
import lesson09.dsl.check06Dsl
import lesson09.dsl.validate
import lesson09.legacy.check01
import lesson09.legacy.check02
import lesson09.legacy.check03
import lesson09.legacy.check04
import lesson09.legacy.check05
import lesson09.legacy.check06
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DslValidationTest {
    private data class Case(val response: HttpResponse, val expected: List<Discrepancy>)

    private fun compare(
        legacy: (HttpResponse) -> Boolean,
        current: (HttpResponse) -> ValidateResult,
        cases: List<Case>
    ) {
        cases.forEach { (response, expected) ->
            val result = current(response)
            assertEquals(legacy(response), result.discrepancies.isEmpty())
            assertEquals(expected, result.discrepancies)
        }
    }

    @Test
    fun check01KeepsStatusAndBodyRules() {
        val good = HttpResponse(204, emptyMap(), null)
        compare(::check01, ::check01Dsl, listOf(
            Case(good, emptyList()),
            Case(good.copy(body = ""), emptyList()),
            Case(good.copy(status = 200), listOf(
                Discrepancy("status", "Expected status: 204", "Actual status is 200")
            )),
            Case(good.copy(body = " "), listOf(
                Discrepancy("body", "absent or empty body", "body is \" \"")
            )),
            Case(good.copy(body = "hello"), listOf(
                Discrepancy("body", "absent or empty body", "body is \"hello\"")
            ))
        ))
    }

    @Test
    fun check02DoesNotParseBody() {
        val good = HttpResponse(200, mapOf("content-type" to listOf("application/jsonp")), "invalid")
        compare(::check02, ::check02Dsl, listOf(
            Case(good, emptyList()),
            Case(good.copy(status = 500), listOf(
                Discrepancy("status", "Expected status: 200", "Actual status is 500")
            )),
            Case(good.copy(headers = mapOf("Content-Type" to listOf("text/plain"))), listOf(
                Discrepancy("headers.Content-Type", "prefix was expected: application/json", "[\"text/plain\"]")
            )),
            Case(good.copy(body = null), listOf(
                Discrepancy("body", "non-empty body", "body is <null>")
            )),
            Case(good.copy(body = ""), listOf(
                Discrepancy("body", "non-empty body", "body is empty(\"\")")
            )),
            Case(good.copy(body = " "), emptyList())
        ))
    }

    @Test
    fun check03RequiresTwoSeparateValues() {
        val good = HttpResponse(200, mapOf("Accept" to listOf("application/json", "text/html")), null)
        compare(::check03, ::check03Dsl, listOf(
            Case(good, emptyList()),
            Case(good.copy(status = 500), listOf(
                Discrepancy("status", "Expected status: 200", "Actual status is 500")
            )),
            Case(good.copy(headers = mapOf("Accept" to listOf("application/json"))), listOf(
                Discrepancy("headers.Accept", "value was expected: text/html", "[\"application/json\"]")
            )),
            Case(good.copy(headers = mapOf("Accept" to listOf("text/html"))), listOf(
                Discrepancy("headers.Accept", "value was expected: application/json", "[\"text/html\"]")
            )),
            Case(good.copy(headers = mapOf("Accept" to listOf("application/json,text/html"))), listOf(
                Discrepancy("headers.Accept", "value was expected: application/json", "[\"application/json,text/html\"]"),
                Discrepancy("headers.Accept", "value was expected: text/html", "[\"application/json,text/html\"]")
            ))
        ))
    }

    @Test
    fun check04DistinguishesMissingIdFromJsonNull() {
        val good = HttpResponse(200, emptyMap(), """{"id":null,"name":"Ivanov","extra":1}""")
        compare(::check04, ::check04Dsl, listOf(
            Case(good, emptyList()),
            Case(good.copy(status = 500), listOf(
                Discrepancy("status", "Expected status: 200", "Actual status is 500")
            )),
            Case(good.copy(body = """{"name":"Ivanov"}"""), listOf(
                Discrepancy("body.json.id", "field id exists", "<missing>")
            )),
            Case(good.copy(body = """{"id":null,"name":"Petrov"}"""), listOf(
                Discrepancy("body.json.name", "value was expected: \"Ivanov\"", "\"Petrov\"")
            )),
            Case(good.copy(body = "invalid"), listOf(
                Discrepancy("body.json", "success parse of json", "failure, reason: Invalid token at offset 0")
            ))
        ))
    }

    @Test
    fun check05RequiresNumberAndBooleanTypes() {
        val good = HttpResponse(200, emptyMap(), """{"id":42,"active":true}""")
        compare(::check05, ::check05Dsl, listOf(
            Case(good, emptyList()),
            Case(good.copy(status = 500), listOf(
                Discrepancy("status", "Expected status: 200", "Actual status is 500")
            )),
            Case(good.copy(body = """{"id":"42","active":true}"""), listOf(
                Discrepancy("body.json.id", "value was expected: 42", "\"42\"")
            )),
            Case(good.copy(body = """{"id":42,"active":false}"""), listOf(
                Discrepancy("body.json.active", "value was expected: true", "false")
            ))
        ))
    }

    @Test
    fun check06RequiresStatusHeaderAndJsonFields() {
        val good = HttpResponse(422, mapOf("Content-Type" to listOf("application/json")),
            """{"error":"invalid","retry":false}""")
        compare(::check06, ::check06Dsl, listOf(
            Case(good, emptyList()),
            Case(good.copy(status = 200), listOf(
                Discrepancy("status", "Expected status: 422", "Actual status is 200")
            )),
            Case(good.copy(headers = emptyMap()), listOf(
                Discrepancy("headers.Content-Type", "prefix was expected: application/json", "<missing>")
            )),
            Case(good.copy(body = """{"error":"other","retry":false}"""), listOf(
                Discrepancy("body.json.error", "value was expected: \"invalid\"", "\"other\"")
            )),
            Case(good.copy(body = """{"error":"invalid","retry":true}"""), listOf(
                Discrepancy("body.json.retry", "value was expected: false", "true")
            ))
        ))
    }

    @Test
    fun headerValuesKeepCaseRulesAndMissingState() {
        val response = HttpResponse(200, linkedMapOf(
            "Accept" to listOf("application/json"),
            "accept" to listOf("text/html"),
            "X-Extra" to listOf("ignored")
        ), null)
        assertTrue(check03Dsl(response).discrepancies.isEmpty())
        assertEquals(listOf(
            Discrepancy("headers.Accept", "value was expected: TEXT/HTML", "[\"application/json\", \"text/html\"]")
        ), validate(response) { header("Accept") { equalTo("TEXT/HTML") } }.discrepancies)

        val expected = "prefix was expected: application/json"
        assertEquals(listOf(Discrepancy("headers.Content-Type", expected, "<missing>")),
            validate(response) { header("Content-Type") { startsWith("application/json") } }.discrepancies)
        assertEquals(listOf(Discrepancy("headers.Content-Type", expected, "[]")),
            validate(response.copy(headers = mapOf("Content-Type" to emptyList()))) {
                header("Content-Type") { startsWith("application/json") }
            }.discrepancies)
        assertEquals(listOf(Discrepancy("headers.Content-Type", expected, "[\"\"]")),
            validate(response.copy(headers = mapOf("Content-Type" to listOf("")))) {
                header("Content-Type") { startsWith("application/json") }
            }.discrepancies)
        assertEquals(listOf(Discrepancy("headers.Content-Type", expected, "[\" application/json\"]")),
            validate(response.copy(headers = mapOf("Content-Type" to listOf(" application/json")))) {
                header("Content-Type") { startsWith("application/json") }
            }.discrepancies)
        assertTrue(validate(response.copy(headers = mapOf("Content-Type" to listOf("text/plain", "application/json; charset=utf-8")))) {
            header("Content-Type") { startsWith("application/json") }
        }.discrepancies.isEmpty())
        assertEquals(listOf(Discrepancy("headers.Accept", "value was expected: text/html", "[\"text/html \"]")),
            validate(response.copy(headers = mapOf("Accept" to listOf("text/html ")))) {
                header("Accept") { equalTo("text/html") }
            }.discrepancies)
    }

    @Test
    fun missingFieldsRunEachOperationAndParseFailureSkipsThem() {
        val block: Validator.() -> Unit = {
            status { equalTo(200) }
            body {
                json {
                    field("id") {
                        present()
                        equalTo(42L)
                        equalTo(42L)
                    }
                    field("name") { equalTo("Ivanov") }
                }
            }
        }
        assertEquals(listOf(
            Discrepancy("status", "Expected status: 200", "Actual status is 500"),
            Discrepancy("body.json.id", "field id exists", "<missing>"),
            Discrepancy("body.json.id", "value was expected: 42", "<missing>"),
            Discrepancy("body.json.id", "value was expected: 42", "<missing>"),
            Discrepancy("body.json.name", "value was expected: \"Ivanov\"", "<missing>")
        ), validate(HttpResponse(500, emptyMap(), "{}"), block).discrepancies)
        assertEquals(listOf(
            Discrepancy("status", "Expected status: 200", "Actual status is 500"),
            Discrepancy("body.json", "success parse of json", "failure, reason: Invalid token at offset 0")
        ), validate(HttpResponse(500, emptyMap(), "invalid"), block).discrepancies)
        assertEquals(listOf(
            Discrepancy("body.json", "existing json object", "json is <null>")
        ), validate(HttpResponse(200, emptyMap(), null), block).discrepancies)
    }

    @Test
    fun choosingAFieldDoesNotCheckItsPresence() {
        val missing = validate(HttpResponse(200, emptyMap(), "{}")) {
            body { json { field("id") {} } }
        }
        assertEquals(ValidateResult.OkValid, missing)

        val presentNull = validate(HttpResponse(200, emptyMap(), """{"id":null}""")) {
            body {
                json {
                    field("id") {
                        present()
                        equalTo(42L)
                    }
                }
            }
        }
        assertEquals(listOf(
            Discrepancy("body.json.id", "value was expected: 42", "null")
        ), presentNull.discrepancies)
    }

    @Test
    fun independentFailuresKeepTheOrderOfDslCalls() {
        val response = HttpResponse(500, mapOf("Content-Type" to listOf("text/plain")), "invalid")
        assertEquals(listOf(
            Discrepancy("status", "Expected status: 422", "Actual status is 500"),
            Discrepancy("headers.Content-Type", "prefix was expected: application/json", "[\"text/plain\"]"),
            Discrepancy("body.json", "success parse of json", "failure, reason: Invalid token at offset 0")
        ), check06Dsl(response).discrepancies)
    }

    @Test
    fun outerReceiverCanBeCalledWithExplicitLabel() {
        val result = validate(HttpResponse(500, emptyMap(), null)) {
            body {
                this@validate.status { equalTo(200) }
            }
        }
        assertEquals(listOf(
            Discrepancy("status", "Expected status: 200", "Actual status is 500")
        ), result.discrepancies)
    }

    @Test
    fun predicateFormsAndRunIndependence() {
        val response = HttpResponse(200, mapOf("X-Trace" to listOf("", "trace")), null)
        val predicate: (String) -> Boolean = ::nonBlankValue
        val literal = validate(response) { header("X-Trace") { satisfies("non-blank value") { it.isNotBlank() } } }
        val variable = validate(response) { header("X-Trace") { satisfies("non-blank value", predicate) } }
        val reference = validate(response) { header("X-Trace") { satisfies("non-blank value", ::nonBlankValue) } }
        assertEquals(literal, variable)
        assertEquals(literal, reference)
        assertTrue(literal.discrepancies.isEmpty())
        val badResponse = response.copy(headers = mapOf("X-Trace" to listOf("")))
        val badLiteral = validate(badResponse) { header("X-Trace") { satisfies("non-blank value") { it.isNotBlank() } } }
        val badVariable = validate(badResponse) { header("X-Trace") { satisfies("non-blank value", predicate) } }
        val badReference = validate(badResponse) { header("X-Trace") { satisfies("non-blank value", ::nonBlankValue) } }
        assertEquals(badLiteral, badVariable)
        assertEquals(badLiteral, badReference)

        var calls = 0
        val missing = validate(response.copy(headers = emptyMap())) {
            header("X-Trace") { satisfies("non-blank value") { calls++; true } }
        }
        assertEquals(0, calls)
        assertEquals(listOf(Discrepancy("headers.X-Trace", "expectation: non-blank value", "<missing>")),
            missing.discrepancies)
        val emptyValues = validate(response.copy(headers = mapOf("X-Trace" to emptyList()))) {
            header("X-Trace") { satisfies("non-blank value") { calls++; true } }
        }
        assertEquals(0, calls)
        assertEquals(listOf(Discrepancy("headers.X-Trace", "expectation: non-blank value", "[]")),
            emptyValues.discrepancies)

        val block: Validator.() -> Unit = {
            status { equalTo(200) }
            header("X-Trace") { satisfies("non-blank value", predicate) }
        }
        val originalHeaders = response.headers.toMap()
        val first = validate(response, block)
        val second = validate(response.copy(status = 500, headers = mapOf("X-Trace" to listOf(""))), block)
        val third = validate(response, block)
        assertEquals(ValidateResult.OkValid, first)
        assertEquals(listOf(
            Discrepancy("status", "Expected status: 200", "Actual status is 500"),
            Discrepancy("headers.X-Trace", "expectation: non-blank value", "[\"\"]")
        ), second.discrepancies)
        assertEquals(ValidateResult.OkValid, third)
        assertEquals(originalHeaders, response.headers)
        assertEquals(200, response.status)
    }

    @Test
    fun inputCollectionsAndEarlierFailureStayUnchanged() {
        val values = mutableListOf("bad")
        val headers = linkedMapOf("X-Trace" to values)
        val response = HttpResponse(500, headers, " ")
        val block: Validator.() -> Unit = {
            status { equalTo(200) }
            header("X-Trace") { equalTo("ok") }
            body { absentOrEmpty() }
        }
        val first = validate(response, block)
        val expected = listOf(
            Discrepancy("status", "Expected status: 200", "Actual status is 500"),
            Discrepancy("headers.X-Trace", "value was expected: ok", "[\"bad\"]"),
            Discrepancy("body", "absent or empty body", "body is \" \"")
        )
        assertEquals(expected, first.discrepancies)

        val second = validate(HttpResponse(200, mapOf("X-Trace" to listOf("ok")), null), block)
        assertEquals(ValidateResult.OkValid, second)
        assertEquals(expected, first.discrepancies)
        assertEquals(mapOf("X-Trace" to listOf("bad")), headers)
        assertEquals(listOf("bad"), values)
        assertEquals(" ", response.body)
    }

    @Test
    fun headerActualEscapesQuotesAndBackslashes() {
        val response = HttpResponse(200, mapOf("X-Trace" to listOf("a\"b\\c")), null)
        assertEquals(listOf(
            Discrepancy("headers.X-Trace", "value was expected: ok", """["a\"b\\c"]""")
        ), validate(response) { header("X-Trace") { equalTo("ok") } }.discrepancies)
    }

    private fun nonBlankValue(value: String): Boolean = value.isNotBlank()
}

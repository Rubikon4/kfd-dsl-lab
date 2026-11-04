package lesson09

@DslMarker
annotation class ValidationDsl

@ValidationDsl
class Validator {
    fun status() {}
    fun header() {}
    fun body() {}
}

@ValidationDsl
class StatusValidator {
    fun equalTo() {}
}

@ValidationDsl
class HeaderValidator {
    fun equalTo() {}
    fun startsWith() {}
    fun satisfies() {}
}

@ValidationDsl
class BodyValidator {
    fun absentOrEmpty() {}
    fun nonEmpty() {}
    fun json() {}
}

@ValidationDsl
class JsonValidator {
    fun field() {}
}

@ValidationDsl
class FieldValidator {
    fun present() {}
    fun equalTo() {}
    fun equalTo() {}
    fun equalTo() {}
}

fun validate(response: HttpResponse, build: Validator.() -> Discrepancy) : Validator {
    val r = Validator()
    r.build()
    return r
}
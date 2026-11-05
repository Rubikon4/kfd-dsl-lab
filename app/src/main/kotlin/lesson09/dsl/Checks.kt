package lesson09.dsl

import lesson09.HttpResponse

fun check01Dsl(response: HttpResponse): ValidateResult = validate(response) {
    status { equalTo(204) }
    body { absentOrEmpty() }
}

fun check02Dsl(response: HttpResponse): ValidateResult = validate(response) {
    status { equalTo(200) }
    header("Content-Type") { startsWith("application/json") }
    body { nonEmpty() }
}

fun check03Dsl(response: HttpResponse): ValidateResult = validate(response) {
    status { equalTo(200) }
    header("Accept") {
        equalTo("application/json")
        equalTo("text/html")
    }
}

fun check04Dsl(response: HttpResponse): ValidateResult = validate(response) {
    status { equalTo(200) }
    body {
        json {
            field("id") { present() }
            field("name") { equalTo("Ivanov") }
        }
    }
}

fun check05Dsl(response: HttpResponse): ValidateResult = validate(response) {
    status { equalTo(200) }
    body {
        json {
            field("id") { equalTo(42L) }
            field("active") { equalTo(true) }
        }
    }
}

fun check06Dsl(response: HttpResponse): ValidateResult = validate(response) {
    status { equalTo(422) }
    header("Content-Type") { startsWith("application/json") }
    body {
        json {
            field("error") { equalTo("invalid") }
            field("retry") { equalTo(false) }
        }
    }
}

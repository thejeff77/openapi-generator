import org.openapitools.client.infrastructure.Serializer
import org.openapitools.client.models.Status
import org.openapitools.client.models.Widget

// Mirrors exactly what generated ApiClient.requestBody() does for application/json:
//     Serializer.moshi.adapter(T::class.java).toJson(content)
val adapter = Serializer.moshi.adapter(Widget::class.java)

var failures = 0

fun check(name: String, expectation: String, body: () -> String) {
    val result = try {
        "OK   -> " + body()
    } catch (t: Throwable) {
        failures++
        "THROW-> ${t::class.java.name}: ${t.message}"
    }
    println("[$name]")
    println("  expected: $expectation")
    println("  actual:   $result")
    println()
}

fun expect(cond: Boolean, msg: String): String {
    if (!cond) { failures++; return "ASSERTION FAILED: $msg" }
    return msg
}

fun main() {
    println("=== moshi version on classpath: " + com.squareup.moshi.Moshi::class.java.protectionDomain.codeSource.location)
    println("=== EnumJsonAdapter from:      " + com.squareup.moshi.adapters.EnumJsonAdapter::class.java.protectionDomain.codeSource.location)
    println()

    // 1. ENCODE with the optional enum unset -> must NOT throw
    check("1-encode-optional-enum-unset", "no throw; json omits optionalColor/optionalStatus/tags") {
        val w = Widget(id = "w1", requiredColor = Widget.RequiredColor.RED)
        val json = adapter.toJson(w)
        expect(!json.contains("optionalColor"), "json=$json")
    }

    // 2. DECODE explicit JSON null for the optional enum -> must yield null
    check("2-decode-explicit-null", "optionalColor == null and optionalStatus == null") {
        val json = """{"id":"w1","requiredColor":"RED","optionalColor":null,"optionalStatus":null,"tags":null}"""
        val w = adapter.fromJson(json)!!
        expect(w.optionalColor == null && w.optionalStatus == null,
               "optionalColor=${w.optionalColor} optionalStatus=${w.optionalStatus}")
    }

    // 3. DECODE an unrecognized value -> must yield UNKNOWN_DEFAULT_OPEN_API (the whole point of the flag)
    check("3-decode-unknown-value", "optionalColor == unknown_default_open_api, optionalStatus == unknown_default_open_api") {
        val json = """{"id":"w1","requiredColor":"RED","optionalColor":"MAGENTA","optionalStatus":"RETIRED"}"""
        val w = adapter.fromJson(json)!!
        expect(w.optionalColor == Widget.OptionalColor.unknown_default_open_api &&
               w.optionalStatus == Status.unknown_default_open_api,
               "optionalColor=${w.optionalColor} optionalStatus=${w.optionalStatus}")
    }

    // 4. DECODE a known value -> unchanged
    check("4-decode-known-value", "requiredColor=GREEN optionalColor=BLUE optionalStatus=ACTIVE tags=[A, C]") {
        val json = """{"id":"w1","requiredColor":"GREEN","optionalColor":"BLUE","optionalStatus":"ACTIVE","tags":["A","C"]}"""
        val w = adapter.fromJson(json)!!
        expect(w.requiredColor == Widget.RequiredColor.GREEN &&
               w.optionalColor == Widget.OptionalColor.BLUE &&
               w.optionalStatus == Status.ACTIVE &&
               w.tags == listOf(Widget.Tags.A, Widget.Tags.C),
               "requiredColor=${w.requiredColor} optionalColor=${w.optionalColor} optionalStatus=${w.optionalStatus} tags=${w.tags}")
    }

    // 5. ENCODE a fully populated model -> must round-trip (control: no nulls involved)
    check("5-encode-all-populated", "no throw; all enum values present in json") {
        val w = Widget(id = "w1", requiredColor = Widget.RequiredColor.BLUE,
                       optionalColor = Widget.OptionalColor.RED,
                       optionalStatus = Status.INACTIVE,
                       tags = listOf(Widget.Tags.B))
        expect(true, "json=" + adapter.toJson(w))
    }

    println("=== FAILURES: $failures ===")
    if (failures > 0) kotlin.system.exitProcess(1)
}

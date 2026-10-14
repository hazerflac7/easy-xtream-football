import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

fun parseTimeNew(value: String?): Long {
    if (value.isNullOrBlank()) return 0L
    val v = value.trim()
    val digits = v.takeWhile { it.isDigit() }
    if (digits.length < 14) return 0L
    val offset = v.drop(digits.length).replace(" ", "")
    return runCatching {
        val f = if (offset.isNotEmpty()) {
            SimpleDateFormat("yyyyMMddHHmmssZ", Locale.US)
        } else {
            SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
        }
        f.timeZone = TimeZone.getTimeZone("UTC")
        val d = if (offset.isNotEmpty()) digits + offset else digits
        f.parse(d)?.time ?: 0L
    }.getOrDefault(0L)
}

fun main() {
    val times = listOf("20231012200000 +0200", "20231012200000", "20231012200000 -0400")
    for (t in times) {
        println("New  $t: ${parseTimeNew(t)}")
    }
}

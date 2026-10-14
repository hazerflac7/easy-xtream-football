import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

fun main() {
    val d1 = "20231012200000+0200"
    val f1 = SimpleDateFormat("yyyyMMddHHmmssZ", Locale.US)
    println(f1.parse(d1).time)

    val f2 = SimpleDateFormat("yyyyMMddHHmmssZ", Locale.US)
    f2.timeZone = TimeZone.getTimeZone("UTC")
    println(f2.parse(d1).time)

    val d2 = "20231012200000"
    val f3 = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
    f3.timeZone = TimeZone.getTimeZone("UTC")
    println(f3.parse(d2).time)
}

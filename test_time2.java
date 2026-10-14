import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

public class test_time2 {
    public static void main(String[] args) throws Exception {
        String d1 = "20231012200000+0200";
        SimpleDateFormat f1 = new SimpleDateFormat("yyyyMMddHHmmssZ", Locale.US);
        f1.setTimeZone(TimeZone.getTimeZone("UTC"));
        System.out.println(f1.parse(d1).getTime());

        SimpleDateFormat f2 = new SimpleDateFormat("yyyyMMddHHmmssZ", Locale.US);
        System.out.println(f2.parse(d1).getTime());
    }
}

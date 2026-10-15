import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Date;

public class test_time {
    public static void main(String[] args) throws Exception {
        String d1 = "20231012200000+0200";
        SimpleDateFormat f1 = new SimpleDateFormat("yyyyMMddHHmmssZ", Locale.US);
        System.out.println(f1.parse(d1).getTime());

        SimpleDateFormat f2 = new SimpleDateFormat("yyyyMMddHHmmssZ", Locale.US);
        f2.setTimeZone(TimeZone.getTimeZone("UTC"));
        System.out.println(f2.parse(d1).getTime());

        String d2 = "20231012200000";
        SimpleDateFormat f3 = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
        f3.setTimeZone(TimeZone.getTimeZone("UTC"));
        System.out.println(f3.parse(d2).getTime());
    }
}

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

public class test_time3 {
    public static long parseTimeNew(String value) {
        if (value == null || value.trim().isEmpty()) return 0L;
        String v = value.trim();
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < v.length(); i++) {
            if (Character.isDigit(v.charAt(i))) {
                digits.append(v.charAt(i));
            } else {
                break;
            }
        }
        if (digits.length() < 14) return 0L;
        String offset = v.substring(digits.length()).replace(" ", "");
        try {
            SimpleDateFormat f;
            if (!offset.isEmpty()) {
                f = new SimpleDateFormat("yyyyMMddHHmmssZ", Locale.US);
            } else {
                f = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
            }
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            String toParse = offset.isEmpty() ? digits.toString() : digits.toString() + offset;
            return f.parse(toParse).getTime();
        } catch (Exception e) {
            return 0L;
        }
    }

    public static void main(String[] args) {
        String[] times = {"20231012200000 +0200", "20231012200000", "20231012200000 -0400"};
        for (String t : times) {
            System.out.println("New  " + t + ": " + parseTimeNew(t));
        }
    }
}

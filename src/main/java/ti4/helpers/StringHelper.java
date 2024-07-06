package ti4.helpers;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

public final class StringHelper {

    public static String ordinal(int i) {
        String[] suffixes = { "th", "st", "nd", "rd", "th", "th", "th", "th", "th", "th" };
        return switch (i % 100) {
            case 11, 12, 13 -> i + "th";
            default -> i + suffixes[i % 10];
        };
    }

    private static Map<String, String> escapables() {
        Map<String, String> escape = new LinkedHashMap<>();
        escape.put("-", "{dsh}");
        escape.put("_", "{usc}");
        escape.put(":", "{cln}");
        escape.put(";", "{smc}");
        escape.put("|", "{pip}");
        escape.put(",", "{cma}");
        escape.put("\n", "{nl}");
        return escape;
    }

    public static String escape(String input) {
        String output = input;
        for (Entry<String, String> entry : escapables().entrySet())
            output = output.replace(entry.getKey(), entry.getValue());
        return output;
    }

    public static String unescape(String input) {
        String output = input;
        for (Entry<String, String> entry : escapables().entrySet())
            output = output.replace(entry.getValue(), entry.getKey());
        output = output.replace("666fin", ":");
        output = output.replace("667fin", ",");
        return output;
    }
}

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

    public static String numberToWords(int i) {
        String[] first20 = { "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
            "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty" };
        if (i >= 0 && i <= 20) return first20[i];
        return Integer.toString(i);
    }

    private static Map<String, String> escapables() {
        Map<String, String> escape = new LinkedHashMap<>();
        // Do not simply change these values.
        // If you need to change any value, add a line in escape to handle the old value
        escape.put("-", "{dsh}");
        escape.put("_", "{usc}");
        escape.put(":", "{cln}");
        escape.put(";", "{smc}");
        escape.put("|", "{pip}");
        escape.put(",", "{cma}");
        escape.put("\n", "{nl}");
        escape.put(" ", "{sp}");
        return escape;
    }

    public static String escape(String input) {
        String output = input;
        for (Entry<String, String> entry : escapables().entrySet())
            output = output.replace(entry.getKey(), entry.getValue());
        output.replace("\r", "");
        return output;
    }

    public static String unescape(String input) {
        String output = input;
        for (Entry<String, String> entry : escapables().entrySet())
            output = output.replace(entry.getValue(), entry.getKey());
        output = output.replace("666fin", ":");
        output = output.replace("667fin", ",");
        output.replace("\r", "");
        return output;
    }
}

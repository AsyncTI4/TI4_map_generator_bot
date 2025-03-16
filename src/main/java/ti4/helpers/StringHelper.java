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
        // DO NOT add either { or }. Escaping an already escaped string should ideally do nothing
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

    // Calling .replace over and over like this is actually pretty slow, probably better to iterate character by character
    public static String escape(String input) {
        if (input == null) return null;
        String output = input;
        for (Entry<String, String> entry : escapables().entrySet())
            output = output.replace(entry.getKey(), entry.getValue());
        return output.replace("\r", "");
    }

    public static String unescape(String input) {
        if (input == null) return null;
        String output = input;
        for (Entry<String, String> entry : escapables().entrySet())
            output = output.replace(entry.getValue(), entry.getKey());
        output = output.replace("666fin", ":");
        output = output.replace("667fin", ",");
        return output.replace("\r", "");
    }

    public static String nextId(String id) {
        if (id.isBlank()) return "a";

        int index = id.length() - 1;
        char currentChar = id.charAt(index);
        id = (index == 0) ? "" : id.substring(0, index);

        if (currentChar >= 'a' && currentChar < 'z')
            return id + (char) (currentChar + 1);
        if (currentChar == 'z') {
            return nextId(id) + "a";
        }
        return "a".repeat(id.length() + 1);
    }
}

package ti4.website; 

import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AlphanumericComparator implements Comparator<String> {
    private static final Pattern PATTERN = Pattern.compile("(\\d+)|(\\D+)");

    @Override
    public int compare(String s1, String s2) {
        Matcher m1 = PATTERN.matcher(s1);
        Matcher m2 = PATTERN.matcher(s2);

        while (m1.find() && m2.find()) {
            String part1 = m1.group();
            String part2 = m2.group();

            // Check if both parts are numeric
            if (isNumeric(part1) && isNumeric(part2)) {
                int num1 = Integer.parseInt(part1);
                int num2 = Integer.parseInt(part2);
                int cmp = Integer.compare(num1, num2);
                if (cmp != 0) {
                    return cmp;
                }
            } else { // Compare as strings
                int cmp = part1.compareTo(part2);
                if (cmp != 0) {
                    return cmp;
                }
            }
        }

        // Handle cases where one string is a prefix of another
        if (m1.find()) {
            return 1; // s1 is longer
        } else if (m2.find()) {
            return -1; // s2 is longer
        } else {
            return 0; // Strings are equal
        }
    }

    private boolean isNumeric(String str) {
        return str.matches("\\d+");
    }
}
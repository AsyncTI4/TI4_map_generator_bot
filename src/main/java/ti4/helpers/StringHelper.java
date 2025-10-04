package ti4.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.collections4.ListUtils;
import ti4.service.emoji.TI4Emoji;

public final class StringHelper {

    public static String ordinal(int i) {
        String[] suffixes = {"th", "st", "nd", "rd", "th", "th", "th", "th", "th", "th"};
        return switch (i % 100) {
            case 11, 12, 13 -> i + "th";
            default -> i + suffixes[i % 10];
        };
    }

    public static String numberToWords(int i) {
        String[] first20 = {
            "zero",
            "one",
            "two",
            "three",
            "four",
            "five",
            "six",
            "seven",
            "eight",
            "nine",
            "ten",
            "eleven",
            "twelve",
            "thirteen",
            "fourteen",
            "fifteen",
            "sixteen",
            "seventeen",
            "eighteen",
            "nineteen",
            "twenty"
        };
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

    // Calling .replace over and over like this is actually pretty slow, probably better to iterate character by
    // character
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

        if (currentChar >= 'a' && currentChar < 'z') return id + (char) (currentChar + 1);
        if (currentChar == 'z') {
            return nextId(id) + "a";
        }
        return "a".repeat(id.length() + 1);
    }

    private static final char ESCAPE_CHARACTER = '\\';

    /**
     * For use in conjunction with safeSplit. First escapes any instance of the separator or
     * escape character in the strings, then joins them with the separator.
     */
    public static String safeJoin(List<String> lines, char separator) {
        if (separator == ESCAPE_CHARACTER) {
            throw new IllegalArgumentException("Separator cannot be the escape character");
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String line : lines) {
            if (first) {
                first = false;
            } else {
                sb.append(separator);
            }
            line = line.replace(
                    String.valueOf(ESCAPE_CHARACTER),
                    String.valueOf(ESCAPE_CHARACTER) + String.valueOf(ESCAPE_CHARACTER));
            line = line.replace(
                    String.valueOf(separator), String.valueOf(ESCAPE_CHARACTER) + String.valueOf(separator));
            sb.append(line);
        }
        return sb.toString();
    }

    /**
     * Splits a string on the given separator, and un-escapes any instance of the separator or
     * escape character that was escaped with an escape character. Use on strings joined via
     * safeJoin.
     */
    public static List<String> safeSplit(String data, char separator) {
        if (separator == ESCAPE_CHARACTER) {
            throw new IllegalArgumentException("Separator cannot be the escape character");
        }
        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();
        boolean escapeNext = false;

        for (char c : data.toCharArray()) {
            if (escapeNext) {
                currentLine.append(c);
                escapeNext = false;
            } else if (c == ESCAPE_CHARACTER) {
                escapeNext = true;
            } else if (c == separator) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder();
            } else {
                currentLine.append(c);
            }
        }
        // Add the last line if there's any content
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        return lines;
    }

    public static String stripEmojis(String initial) {
        return replaceWithEmojis(initial, true);
    }

    public static String replaceWithEmojis(String initial) {
        return replaceWithEmojis(initial, false);
    }

    private static Pattern emojiToReplace = Pattern.compile("<(?<cat>\\w+):(?<name>\\w+)>");

    private static String replaceWithEmojis(String initial, boolean replaceWithBlank) {
        StringBuilder output = new StringBuilder();
        int index = 0;
        do {
            // Get the start of the next emoji, append everything we've gone past to the
            int pos = initial.indexOf('<', index);
            if (pos == -1) {
                output.append(initial.substring(index));
                break;
            }
            output.append(initial.substring(index, pos));

            int end = initial.indexOf('>', pos);
            if (end == -1) {
                output.append(initial.substring(pos));
                break;
            }
            String candidateEmoji = initial.substring(pos, end + 1);

            Matcher emojiBits = emojiToReplace.matcher(candidateEmoji);
            if (emojiBits.matches()) {
                if (!replaceWithBlank) {
                    String category = emojiBits.group("cat");
                    String name = emojiBits.group("name");
                    TI4Emoji emoji = TI4Emoji.findEmoji(category, name);
                    if (emoji != null) {
                        output.append(emoji.emojiString());
                    } else {
                        output.append(candidateEmoji);
                    }
                }
            } else {
                output.append(candidateEmoji);
            }
            index = end + 1;
        } while (index < initial.length());

        return output.toString();
    }

    public static List<String> chunkMessage(String input, int maxLength) {
        if (input == null || maxLength <= 0) return Collections.emptyList();
        if (input.length() <= maxLength) return List.of(input);

        // Try each of these separators in sequence, trying to find the first one that works.
        List<String> chunkSeparators = List.of("\r\n\r\n", "\n\n", "\r\n", "\n", ". ", " ");

        for (String sep : chunkSeparators) {
            String[] chunks = input.split(Pattern.quote(sep));
            List<String> messages = new ArrayList<>();
            StringBuilder currentMessage = new StringBuilder();
            for (String chunk : chunks) {
                if (chunk.length() > maxLength) {
                    // This chunk is too big, so this separator won't work
                    messages.clear();
                    break;
                }
                if (currentMessage.length() + chunk.length() + sep.length() > maxLength) {
                    // This chunk would make the message too big, so start a new message
                    if (currentMessage.length() > 0) {
                        messages.add(currentMessage.toString());
                    }
                    currentMessage = new StringBuilder(chunk);
                } else {
                    // This chunk fits in the current message
                    if (currentMessage.length() > 0) {
                        currentMessage.append(sep);
                    }
                    currentMessage.append(chunk);
                }
            }
            if (currentMessage.length() > 0) {
                messages.add(currentMessage.toString());
            }
            if (!messages.isEmpty()) {
                return messages;
            }
        }

        // Nothing good worked, just do the crappy character-by-character split
        List<List<String>> partitioned = ListUtils.partition(List.of(input.split("")), maxLength);
        List<String> messages = new ArrayList<>();
        for (List<String> part : partitioned) {
            messages.add(String.join("", part));
        }
        return messages;
    }
}

package ti4.service.emoji;

public enum DiceEmojis implements TI4Emoji {
    // Green
    d10green_0,
    d10green_1,
    d10green_2,
    d10green_3,
    d10green_4,
    d10green_5,
    d10green_6,
    d10green_7,
    d10green_8,
    d10green_9,
    // Red
    d10red_0,
    d10red_1,
    d10red_2,
    d10red_3,
    d10red_4,
    d10red_5,
    d10red_6,
    d10red_7,
    d10red_8,
    d10red_9,
    // Blue
    d10blue_0,
    d10blue_1,
    d10blue_2,
    d10blue_3,
    d10blue_4,
    d10blue_5,
    d10blue_6,
    d10blue_7,
    d10blue_8,
    d10blue_9,
    // Gray
    d10grey_0,
    d10grey_1,
    d10grey_2,
    d10grey_3,
    d10grey_4,
    d10grey_5,
    d10grey_6,
    d10grey_7,
    d10grey_8,
    d10grey_9;

    public static String getDieEmoji(String color, int value) {
        return switch (color) {
            case "red" -> getRedDieEmoji(value);
            case "blue" -> getBlueDieEmoji(value);
            case "green" -> getGreenDieEmoji(value);
            case "gray", "grey" -> getGrayDieEmoji(value);
            default -> String.valueOf(value);
        };
    }

    public static String getRedDieEmoji(int value) {
        return switch (value) {
            case 0, 10 -> d10red_0.toString();
            case 1 -> d10red_1.toString();
            case 2 -> d10red_2.toString();
            case 3 -> d10red_3.toString();
            case 4 -> d10red_4.toString();
            case 5 -> d10red_5.toString();
            case 6 -> d10red_6.toString();
            case 7 -> d10red_7.toString();
            case 8 -> d10red_8.toString();
            case 9 -> d10red_9.toString();
            default -> String.valueOf(value);
        };
    }

    public static String getGreenDieEmoji(int value) {
        return switch (value) {
            case 0, 10 -> d10green_0.toString();
            case 1 -> d10green_1.toString();
            case 2 -> d10green_2.toString();
            case 3 -> d10green_3.toString();
            case 4 -> d10green_4.toString();
            case 5 -> d10green_5.toString();
            case 6 -> d10green_6.toString();
            case 7 -> d10green_7.toString();
            case 8 -> d10green_8.toString();
            case 9 -> d10green_9.toString();
            default -> String.valueOf(value);
        };
    }

    private static String getBlueDieEmoji(int value) {
        return switch (value) {
            case 0, 10 -> d10blue_0.toString();
            case 1 -> d10blue_1.toString();
            case 2 -> d10blue_2.toString();
            case 3 -> d10blue_3.toString();
            case 4 -> d10blue_4.toString();
            case 5 -> d10blue_5.toString();
            case 6 -> d10blue_6.toString();
            case 7 -> d10blue_7.toString();
            case 8 -> d10blue_8.toString();
            case 9 -> d10blue_9.toString();
            default -> String.valueOf(value);
        };
    }

    public static String getGrayDieEmoji(int value) {
        return switch (value) {
            case 0, 10 -> d10grey_0.toString();
            case 1 -> d10grey_1.toString();
            case 2 -> d10grey_2.toString();
            case 3 -> d10grey_3.toString();
            case 4 -> d10grey_4.toString();
            case 5 -> d10grey_5.toString();
            case 6 -> d10grey_6.toString();
            case 7 -> d10grey_7.toString();
            case 8 -> d10grey_8.toString();
            case 9 -> d10grey_9.toString();
            default -> String.valueOf(value);
        };
    }

    @Override
    public String toString() {
        return emojiString();
    }
}

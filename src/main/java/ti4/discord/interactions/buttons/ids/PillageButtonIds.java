package ti4.discord.interactions.buttons.ids;

import org.apache.commons.lang3.StringUtils;

public final class PillageButtonIds {

    public static final String PREFIX = "pillage_";
    public static final String DECLINE_PREFIX = "declinePillage_";

    private PillageButtonIds() {}

    public enum Stage {
        UNCHECKED("unchecked"),
        TRADE_GOOD("checked"),
        COMMODITY("checkedcomm");

        private final String key;

        Stage(String key) {
            this.key = key;
        }

        private static Stage fromKey(String key) {
            if (key.contains(UNCHECKED.key)) return UNCHECKED;
            if (key.contains(COMMODITY.key)) return COMMODITY;
            return TRADE_GOOD;
        }
    }

    public static String format(String targetColor, Stage stage) {
        return PREFIX + targetColor + "_" + stage.key;
    }

    public static String formatDecline(String targetColor) {
        return DECLINE_PREFIX + targetColor;
    }

    public static Parsed parse(String buttonId) {
        String[] parts = requirePrefix(buttonId, PREFIX).split("_");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Malformed pillage button id: " + buttonId);
        }
        return new Parsed(parts[0], Stage.fromKey(parts[1]));
    }

    public static String parseDeclinedColor(String buttonId) {
        return StringUtils.substringBefore(requirePrefix(buttonId, DECLINE_PREFIX), "_");
    }

    private static String requirePrefix(String buttonId, String prefix) {
        if (buttonId == null || !buttonId.startsWith(prefix)) {
            throw new IllegalArgumentException("Unknown pillage button id: " + buttonId);
        }
        return buttonId.substring(prefix.length());
    }

    public record Parsed(String targetColor, Stage stage) {}
}

package ti4.discord.interactions.buttons.ids;

public final class AutoAssignGroundHitsButtonIds {

    public static final String PREFIX = "autoAssignGroundHits_";

    private AutoAssignGroundHitsButtonIds() {}

    public static String format(String planetName, int hits) {
        return PREFIX + planetName + "_" + hits;
    }

    public static Parsed parse(String buttonId) {
        if (buttonId == null || !buttonId.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Unknown auto-assign ground hits button id: " + buttonId);
        }
        String[] parts = buttonId.substring(PREFIX.length()).split("_");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Malformed auto-assign ground hits button id: " + buttonId);
        }
        return new Parsed(parts[0], Integer.parseInt(parts[1]));
    }

    public record Parsed(String planetName, int hits) {}
}

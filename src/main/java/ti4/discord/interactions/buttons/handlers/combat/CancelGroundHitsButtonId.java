package ti4.discord.interactions.buttons.handlers.combat;

public record CancelGroundHitsButtonId(String tilePosition, int hits, String planet, boolean interlocking) {

    public static final String PREFIX = "cancelGroundHits_";
    private static final String INTERLOCKING_SUFFIX = "_interlocking";

    public static String of(String tilePosition, int hits, String planet) {
        return new CancelGroundHitsButtonId(tilePosition, hits, planet, false).toButtonId();
    }

    public static String interlockingOf(String tilePosition, int hits, String planet) {
        return new CancelGroundHitsButtonId(tilePosition, hits, planet, true).toButtonId();
    }

    public String toButtonId() {
        return PREFIX + tilePosition + "_" + hits + "_" + planet + (interlocking ? INTERLOCKING_SUFFIX : "");
    }

    public static CancelGroundHitsButtonId parse(String buttonID) {
        String payload = buttonID.substring(buttonID.indexOf(PREFIX) + PREFIX.length());
        boolean interlocking = payload.endsWith(INTERLOCKING_SUFFIX);
        if (interlocking) {
            payload = payload.substring(0, payload.length() - INTERLOCKING_SUFFIX.length());
        }
        String[] segments = payload.split("_", 3);
        return new CancelGroundHitsButtonId(segments[0], Integer.parseInt(segments[1]), segments[2], interlocking);
    }
}

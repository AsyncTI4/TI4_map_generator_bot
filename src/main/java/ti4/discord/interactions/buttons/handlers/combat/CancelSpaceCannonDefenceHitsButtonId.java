package ti4.discord.interactions.buttons.handlers.combat;

public record CancelSpaceCannonDefenceHitsButtonId(String tilePosition, int hits, String planet) {

    public static final String PREFIX = "cancelPdsDefenceHits_";

    public static String of(String tilePosition, int hits, String planet) {
        return new CancelSpaceCannonDefenceHitsButtonId(tilePosition, hits, planet).toButtonId();
    }

    public String toButtonId() {
        return PREFIX + tilePosition + "_" + hits + "_" + planet;
    }

    public static CancelSpaceCannonDefenceHitsButtonId parse(String buttonID) {
        String payload = buttonID.substring(buttonID.indexOf(PREFIX) + PREFIX.length());
        String[] segments = payload.split("_", 3);
        return new CancelSpaceCannonDefenceHitsButtonId(segments[0], Integer.parseInt(segments[1]), segments[2]);
    }
}

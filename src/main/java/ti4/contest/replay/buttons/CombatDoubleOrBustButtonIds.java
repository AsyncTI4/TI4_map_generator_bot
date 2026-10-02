package ti4.contest.replay.buttons;

public final class CombatDoubleOrBustButtonIds {

    public static final String PREFIX = "combatDoubleOrBust_";

    private CombatDoubleOrBustButtonIds() {}

    public static String format(Long contestId) {
        return PREFIX + contestId;
    }

    public static Long parse(String buttonId) {
        if (buttonId == null || !buttonId.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Unknown Double or Bust button id: " + buttonId);
        }
        return Long.parseLong(buttonId.substring(PREFIX.length()));
    }
}

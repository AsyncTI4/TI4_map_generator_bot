package ti4.contest.replay.buttons;

import ti4.contest.replay.core.CombatSideBetType;

public final class CombatSideBetButtonIds {

    public static final String PREFIX = "combatSideBet_";
    private static final String DELIMITER = "~";

    private CombatSideBetButtonIds() {}

    public static String format(Long contestId, CombatSideBetType type, String faction) {
        return PREFIX + contestId + DELIMITER + type.key() + DELIMITER + faction;
    }

    public static Parsed parse(String buttonId) {
        if (buttonId == null || !buttonId.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Unknown side bet button id: " + buttonId);
        }

        String encoded = buttonId.substring(PREFIX.length());
        String[] parts = encoded.split(DELIMITER, 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Malformed side bet button id: " + buttonId);
        }

        Long contestId = Long.parseLong(parts[0]);
        CombatSideBetType type = CombatSideBetType.fromKey(parts[1]);
        return new Parsed(contestId, type, parts[2]);
    }

    public record Parsed(Long contestId, CombatSideBetType betType, String targetFaction) {}
}

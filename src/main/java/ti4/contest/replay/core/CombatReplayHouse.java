package ti4.contest.replay.core;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum CombatReplayHouse {
    NAALU("Naalu"),
    MENTAK("Mentak"),
    HACAN("Hacan");

    private static final String CHANNEL_PREFIX = "lazax-";
    private static final List<CombatReplayHouse> ASSIGNMENT_ORDER = List.of(NAALU, MENTAK, HACAN);

    private final String displayName;

    CombatReplayHouse(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public String roleName() {
        return displayName + "Delegation";
    }

    public String channelName() {
        return CHANNEL_PREFIX + displayName.toLowerCase(Locale.ROOT);
    }

    public static List<CombatReplayHouse> assignmentOrder() {
        return ASSIGNMENT_ORDER;
    }

    public static CombatReplayHouse fromName(String value) {
        if (value == null || value.isBlank()) return null;
        return Arrays.stream(values())
                .filter(house -> house.name().equalsIgnoreCase(value) || house.displayName.equalsIgnoreCase(value))
                .findFirst()
                .orElse(null);
    }
}

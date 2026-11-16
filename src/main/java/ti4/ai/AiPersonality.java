package ti4.ai;

import java.util.Arrays;
import java.util.Locale;
import ti4.game.Player;

public enum AiPersonality {
    STANDARD,
    BOAT_FLOAT;

    public static final String KEY = "aiPersonality";
    public static final String BOAT_FLOAT_PROPERTY = "ai.personality.boatfloat";
    private static final String ALL = "all";

    public static AiPersonality of(Player seat) {
        String stored = seat.getStoredValue(KEY);
        if (!stored.isBlank()) {
            return Arrays.stream(values())
                    .filter(personality -> personality.name().equalsIgnoreCase(stored.trim()))
                    .findFirst()
                    .orElse(STANDARD);
        }
        return boatFloatByProperty(seat) ? BOAT_FLOAT : STANDARD;
    }

    public static void set(Player seat, AiPersonality personality) {
        seat.setStoredValue(KEY, personality.name());
    }

    public static boolean isBoatFloat(Player seat) {
        return of(seat) == BOAT_FLOAT;
    }

    private static boolean boatFloatByProperty(Player seat) {
        String value = System.getProperty(BOAT_FLOAT_PROPERTY, "");
        if (value.isBlank()) return false;
        String faction = seat.getFaction() == null ? "" : seat.getFaction().toLowerCase(Locale.ROOT);
        return Arrays.stream(value.split(","))
                .map(entry -> entry.trim().toLowerCase(Locale.ROOT))
                .anyMatch(entry -> ALL.equals(entry) || entry.equals(faction));
    }
}

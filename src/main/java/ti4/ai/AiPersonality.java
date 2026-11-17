package ti4.ai;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import ti4.game.Player;

public enum AiPersonality {
    STOOGE(2),
    FLOATER(1),
    NEUTRAL(0),
    WARRIOR(-1),
    WARLORD(-2);

    public static final String KEY = "aiPersonality";
    public static final String PROPERTY = "ai.personality";
    private static final String ENTRIES = ",";
    private static final String ASSIGN = "=";

    private final int startingRelations;

    AiPersonality(int startingRelations) {
        this.startingRelations = startingRelations;
    }

    public int startingRelations() {
        return startingRelations;
    }

    public static AiPersonality of(Player seat) {
        String stored = seat.getStoredValue(KEY);
        if (!stored.isBlank()) return parse(stored).orElse(NEUTRAL);
        return byProperty(seat).orElse(NEUTRAL);
    }

    public static void set(Player seat, AiPersonality personality) {
        seat.setStoredValue(KEY, personality.name());
    }

    public static Optional<AiPersonality> parse(String name) {
        return Arrays.stream(values())
                .filter(personality -> personality.name().equalsIgnoreCase(name.trim()))
                .findFirst();
    }

    private static Optional<AiPersonality> byProperty(Player seat) {
        String value = System.getProperty(PROPERTY, "");
        if (value.isBlank()) return Optional.empty();
        String faction = seat.getFaction() == null ? "" : seat.getFaction().toLowerCase(Locale.ROOT);
        for (String entry : value.split(ENTRIES)) {
            if (!entry.contains(ASSIGN)) {
                Optional<AiPersonality> everyone = parse(entry);
                if (everyone.isPresent()) return everyone;
                continue;
            }
            String[] parts = entry.split(ASSIGN, 2);
            if (parts[0].trim().toLowerCase(Locale.ROOT).equals(faction)) return parse(parts[1]);
        }
        return Optional.empty();
    }
}

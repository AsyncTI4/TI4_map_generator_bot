package ti4.ai.profile;

import java.util.Random;

public enum AggressionLevel {
    PEACEFUL,
    CAUTIOUS,
    OPPORTUNIST,
    AGGRESSIVE,
    WARLORD;

    private static final int[] HIDDEN_DRAW_WEIGHTS = {1, 3, 4, 3, 1};

    public static AggressionLevel fromOrdinal(int ordinal) {
        AggressionLevel[] levels = values();
        return levels[Math.clamp(ordinal, 0, levels.length - 1)];
    }

    public static AggressionLevel drawHidden(Random random) {
        int total = 0;
        for (int weight : HIDDEN_DRAW_WEIGHTS) total += weight;
        int roll = random.nextInt(total);
        for (int i = 0; i < HIDDEN_DRAW_WEIGHTS.length; i++) {
            roll -= HIDDEN_DRAW_WEIGHTS[i];
            if (roll < 0) return values()[i];
        }
        return OPPORTUNIST;
    }
}

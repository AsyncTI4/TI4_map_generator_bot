package ti4.ai.profile;

import java.util.Random;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Player;

public record AiProfile(String brainId, AggressionLevel baseLevel, AggressionMode mode, PauseState pause, long seed) {

    public enum AggressionMode {
        DYNAMIC,
        LOCKED
    }

    public enum PauseState {
        RUNNING,
        MANUAL,
        AUTO
    }

    static final String BRAIN_KEY = "aiBrain";
    static final String BASE_LEVEL_KEY = "aiAggBase";
    static final String MODE_KEY = "aiAggMode";
    static final String PAUSE_KEY = "aiPaused";
    static final String SEED_KEY = "aiSeed";

    public static AiProfile createHidden(String brainId, Random random) {
        return new AiProfile(
                brainId,
                AggressionLevel.drawHidden(random),
                AggressionMode.DYNAMIC,
                PauseState.RUNNING,
                random.nextLong() & Long.MAX_VALUE);
    }

    public static AiProfile of(Player seat) {
        return new AiProfile(
                seat.getStoredValue(BRAIN_KEY),
                AggressionLevel.fromOrdinal(parseInt(seat.getStoredValue(BASE_LEVEL_KEY), 2)),
                parseEnum(seat.getStoredValue(MODE_KEY), AggressionMode.class, AggressionMode.DYNAMIC),
                parseEnum(seat.getStoredValue(PAUSE_KEY), PauseState.class, PauseState.RUNNING),
                parseLong(seat.getStoredValue(SEED_KEY)));
    }

    public void writeTo(Player seat) {
        seat.setStoredValue(BRAIN_KEY, brainId);
        seat.setStoredValue(BASE_LEVEL_KEY, String.valueOf(baseLevel.ordinal()));
        seat.setStoredValue(MODE_KEY, mode.name());
        seat.setStoredValue(PAUSE_KEY, pause.name());
        seat.setStoredValue(SEED_KEY, String.valueOf(seed));
    }

    public AiProfile withPause(PauseState newPause) {
        return new AiProfile(brainId, baseLevel, mode, newPause, seed);
    }

    public boolean isPaused() {
        return pause != PauseState.RUNNING;
    }

    public static void clear(Player seat) {
        for (String key : new String[] {BRAIN_KEY, BASE_LEVEL_KEY, MODE_KEY, PAUSE_KEY, SEED_KEY}) {
            seat.removeStoredValue(key);
        }
    }

    private static int parseInt(String value, int fallback) {
        return StringUtils.isNumeric(value) ? Integer.parseInt(value) : fallback;
    }

    private static long parseLong(String value) {
        return StringUtils.isNumeric(value) ? Long.parseLong(value) : 0L;
    }

    private static <E extends Enum<E>> E parseEnum(String value, Class<E> type, E fallback) {
        if (StringUtils.isBlank(value)) return fallback;
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}

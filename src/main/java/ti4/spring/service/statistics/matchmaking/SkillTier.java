package ti4.spring.service.statistics.matchmaking;

import java.util.Arrays;
import java.util.Locale;
import lombok.Getter;

/**
 * Skill bands used to group games by the average matchmaking rating of their players.
 *
 * <p>Bounds are half-open {@code [minimum, maximum)} display ratings, aligned to the 100-point brackets reported by
 * the {@code matchmaking_rating} command so a tier never splits a bracket.
 */
@Getter
public enum SkillTier {
    LOWER(Long.MIN_VALUE, Bounds.LOWER_MEDIUM),
    MEDIUM(Bounds.LOWER_MEDIUM, Bounds.MEDIUM_HIGHER),
    HIGHER(Bounds.MEDIUM_HIGHER, Long.MAX_VALUE);

    /**
     * Chosen from the observed distribution of 19,154 completed games (August 2026) to approximate a 30/40/30 split
     * while landing on bracket edges: LOWER 25.3%, MEDIUM 42.7%, HIGHER 32.0%. No other pair of bracket-aligned
     * boundaries comes closer — the 2100-2199 bracket alone holds 23.6% of games, so it dominates whichever tier it
     * falls in. Re-derive these if the player base shifts.
     */
    private static final class Bounds {
        /** Bottom of MEDIUM: 25.3% of games fall below this. */
        private static final long LOWER_MEDIUM = 2000;

        /** Bottom of HIGHER: 68.0% of games fall below this. */
        private static final long MEDIUM_HIGHER = 2200;

        private Bounds() {}
    }

    private final long minimumDisplayRatingInclusive;
    private final long maximumDisplayRatingExclusive;

    SkillTier(long minimumDisplayRatingInclusive, long maximumDisplayRatingExclusive) {
        this.minimumDisplayRatingInclusive = minimumDisplayRatingInclusive;
        this.maximumDisplayRatingExclusive = maximumDisplayRatingExclusive;
    }

    public boolean contains(long displayRating) {
        return displayRating >= minimumDisplayRatingInclusive && displayRating < maximumDisplayRatingExclusive;
    }

    public static SkillTier fromDisplayRating(long displayRating) {
        return Arrays.stream(values())
                .filter(skillTier -> skillTier.contains(displayRating))
                .findFirst()
                .orElseThrow();
    }

    /** Parses a slash-command option value, returning null for a missing or unrecognised value. */
    public static SkillTier fromOptionValue(String optionValue) {
        if (optionValue == null || optionValue.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(skillTier -> skillTier.name().equalsIgnoreCase(optionValue.strip()))
                .findFirst()
                .orElse(null);
    }

    /** Human-readable range, e.g. "below 1800", "1800-2199", "2200+". */
    public String getLabel() {
        if (minimumDisplayRatingInclusive == Long.MIN_VALUE) {
            return "below " + maximumDisplayRatingExclusive;
        }
        if (maximumDisplayRatingExclusive == Long.MAX_VALUE) {
            return minimumDisplayRatingInclusive + "+";
        }
        return minimumDisplayRatingInclusive + "-" + (maximumDisplayRatingExclusive - 1);
    }

    public String getDisplayName() {
        return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT);
    }
}

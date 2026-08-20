package ti4.spring.service.statistics.matchmaking;

import java.util.Arrays;
import java.util.Locale;
import lombok.Getter;

@Getter
public enum SkillTier {
    LOWER(Long.MIN_VALUE, Bounds.LOWER_MEDIUM),
    MEDIUM(Bounds.LOWER_MEDIUM, Bounds.MEDIUM_HIGHER),
    HIGHER(Bounds.MEDIUM_HIGHER, Long.MAX_VALUE);

    private static final class Bounds {
        // Bottom ~25% of games
        private static final long LOWER_MEDIUM = 2000;

        // ~40% of games fall between this and LOWER_MEDIUM
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

    public static SkillTier fromOptionValue(String optionValue) {
        if (optionValue == null || optionValue.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(skillTier -> skillTier.name().equalsIgnoreCase(optionValue.strip()))
                .findFirst()
                .orElse(null);
    }

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

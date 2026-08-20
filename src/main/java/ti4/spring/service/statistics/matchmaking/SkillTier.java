package ti4.spring.service.statistics.matchmaking;

import java.util.Arrays;
import java.util.Locale;
import lombok.Getter;

@Getter
public enum SkillTier {
    LOWER(Long.MIN_VALUE, Bounds.LOWER_MEDIUM),
    MEDIUM(Bounds.LOWER_MEDIUM, Bounds.MEDIUM_HIGHER),
    HIGHER(Bounds.MEDIUM_HIGHER, Long.MAX_VALUE);

    /** Prefix on a filter value that inverts it, e.g. "-LOWER" means every game that is not LOWER. */
    public static final String EXCLUSION_PREFIX = "-";

    private static final class Bounds {
        // Bottom ~13% of games
        private static final long LOWER_MEDIUM = 1900;

        // ~74% of games fall between this and LOWER_MEDIUM
        private static final long MEDIUM_HIGHER = 2300;

        private Bounds() {}
    }

    /** A parsed filter value: a tier, optionally inverted by the {@value #EXCLUSION_PREFIX} prefix. */
    public record Selection(SkillTier skillTier, boolean excluded) {

        public boolean matches(long displayRating) {
            return skillTier.contains(displayRating) != excluded;
        }

        public String getLabel() {
            return excluded ? "not " + skillTier.getDisplayName() : skillTier.getDisplayName();
        }
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

    /** Parses a filter value, honouring a leading {@value #EXCLUSION_PREFIX}. Returns null if unrecognised. */
    public static Selection parseSelection(String optionValue) {
        if (optionValue == null || optionValue.isBlank()) {
            return null;
        }
        String remaining = optionValue.strip();
        boolean excluded = remaining.startsWith(EXCLUSION_PREFIX);
        if (excluded) {
            remaining = remaining.substring(EXCLUSION_PREFIX.length()).strip();
        }
        SkillTier skillTier = fromOptionValue(remaining);
        return skillTier == null ? null : new Selection(skillTier, excluded);
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

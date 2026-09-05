package ti4.discord.interactions.buttons.handlers.matchmaking;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;

@UtilityClass
public class MatchmakingOptions {

    public static final String BASE_ONLY_EXPANSION_OPTION = "Base only";
    public static final String POK_ONLY_EXPANSION_OPTION = "Prophecy of Kings only";
    public static final String TE_ONLY_EXPANSION_OPTION = "Thunder's Edge only";
    public static final String POK_AND_TE_EXPANSION_OPTION = "Prophecy of Kings and Thunder's Edge";
    public static final String DISCORDANT_STARS_EXPANSION_OPTION = "Discordant Stars (Homebrew)";
    public static final String TWILIGHTS_FALL_EXPANSION_OPTION = "Twilight's Fall";
    public static final String FRANKEN_EXPANSION_OPTION = "Franken (Homebrew)";
    public static final List<String> EXPANSION_OPTIONS = List.of(
            BASE_ONLY_EXPANSION_OPTION,
            POK_ONLY_EXPANSION_OPTION,
            TE_ONLY_EXPANSION_OPTION,
            POK_AND_TE_EXPANSION_OPTION,
            DISCORDANT_STARS_EXPANSION_OPTION,
            TWILIGHTS_FALL_EXPANSION_OPTION,
            FRANKEN_EXPANSION_OPTION);

    private static final Map<String, String> EXPANSION_SHORT_NAMES = Map.of(
            BASE_ONLY_EXPANSION_OPTION, "base game",
            POK_ONLY_EXPANSION_OPTION, "PoK",
            TE_ONLY_EXPANSION_OPTION, "TE",
            POK_AND_TE_EXPANSION_OPTION, "PoK + TE",
            DISCORDANT_STARS_EXPANSION_OPTION, "DS",
            TWILIGHTS_FALL_EXPANSION_OPTION, "TF",
            FRANKEN_EXPANSION_OPTION, "Franken");

    public static final List<String> PLAYER_COUNT_OPTIONS = List.of("3", "4", "5", "6", "7", "8");
    public static final List<String> VICTORY_POINT_OPTIONS = List.of("10", "12", "14");

    public static final int STRICT_SIMILAR_ACTIVE_HOURS_REQUIREMENT = 12;
    public static final int LOOSE_SIMILAR_ACTIVE_HOURS_REQUIREMENT = 10;
    public static final String STRICT_SIMILAR_ACTIVE_HOURS_OPTION =
            "Strict (" + STRICT_SIMILAR_ACTIVE_HOURS_REQUIREMENT + " hours must match)";
    public static final String LOOSE_SIMILAR_ACTIVE_HOURS_OPTION =
            "Loose (" + LOOSE_SIMILAR_ACTIVE_HOURS_REQUIREMENT + " hours must match)";
    public static final String NO_SIMILAR_ACTIVE_HOURS_OPTION = "No";

    public static final List<String> SIMILAR_ACTIVE_HOURS_OPTIONS_STRICTEST_FIRST =
            List.of(STRICT_SIMILAR_ACTIVE_HOURS_OPTION, LOOSE_SIMILAR_ACTIVE_HOURS_OPTION);

    private static final Map<String, Integer> SIMILAR_ACTIVE_HOURS_SHARED_HOUR_REQUIREMENTS = Map.of(
            STRICT_SIMILAR_ACTIVE_HOURS_OPTION, STRICT_SIMILAR_ACTIVE_HOURS_REQUIREMENT,
            LOOSE_SIMILAR_ACTIVE_HOURS_OPTION, LOOSE_SIMILAR_ACTIVE_HOURS_REQUIREMENT);

    private static final Map<String, String> SIMILAR_ACTIVE_HOURS_SHORT_NAMES = Map.of(
            STRICT_SIMILAR_ACTIVE_HOURS_OPTION, "strict",
            LOOSE_SIMILAR_ACTIVE_HOURS_OPTION, "loose");

    // Discord role names the matchmaker tracks to keep Floaters and Warriors apart.
    public static final String FLOATERS_ROLE_NAME = "Floaters";
    public static final String WARRIORS_ROLE_NAME = "Warriors";

    public static final String SLOWER_PACE_OPTION = "Slower";
    public static final String FAST_PACE_OPTION = "Average (30 days)";
    public static final String FASTER_PACE_OPTION = "Faster (15 days)";
    public static final String FASTEST_PACE_OPTION = "Fastest (7 days)";
    public static final List<String> PACE_RESTRICTION_OPTIONS =
            List.of(SLOWER_PACE_OPTION, FAST_PACE_OPTION, FASTER_PACE_OPTION, FASTEST_PACE_OPTION);

    public static final Map<String, Integer> PACE_RESTRICTION_TO_GAME_DAYS_TO_COMPLETE_REQUIREMENT =
            Map.of(FASTER_PACE_OPTION, 21, FASTEST_PACE_OPTION, 11);

    public static final List<String> RESTRICTION_OPTIONS = SIMILAR_ACTIVE_HOURS_OPTIONS_STRICTEST_FIRST;

    private static final Map<String, String> PACE_SHORT_NAMES = Map.of(
            SLOWER_PACE_OPTION, "Slower",
            FAST_PACE_OPTION, "Average",
            FASTER_PACE_OPTION, "Faster",
            FASTEST_PACE_OPTION, "Fastest");

    public static final String UNRANKED_OPTION = "Unranked";
    public static final List<String> TIGL_RANK_OPTIONS =
            List.of(UNRANKED_OPTION, "Minister", "Agent", "Commander", "Hero");

    public static final Map<String, Integer> MAX_QUEUE_TIME_OPTIONS_TO_HOURS;

    static {
        MAX_QUEUE_TIME_OPTIONS_TO_HOURS = new LinkedHashMap<>();
        MAX_QUEUE_TIME_OPTIONS_TO_HOURS.put("4 hours", 4);
        MAX_QUEUE_TIME_OPTIONS_TO_HOURS.put("8 hours", 8);
        MAX_QUEUE_TIME_OPTIONS_TO_HOURS.put("1 day", 24);
        MAX_QUEUE_TIME_OPTIONS_TO_HOURS.put("2 days", 48);
        MAX_QUEUE_TIME_OPTIONS_TO_HOURS.put("1 week", 168);
    }

    private static final int DEFAULT_MAX_QUEUE_TIME_HOURS = 48;

    public static boolean isSimilarActiveHoursLevel(String restriction) {
        return SIMILAR_ACTIVE_HOURS_SHARED_HOUR_REQUIREMENTS.containsKey(restriction);
    }

    public static int requiredSharedActiveHours(Collection<String> restrictions) {
        return restrictions.stream()
                .mapToInt(restriction -> SIMILAR_ACTIVE_HOURS_SHARED_HOUR_REQUIREMENTS.getOrDefault(restriction, 0))
                .max()
                .orElse(0);
    }

    public static Optional<String> strictestSimilarActiveHours(Collection<String> restrictions) {
        return SIMILAR_ACTIVE_HOURS_OPTIONS_STRICTEST_FIRST.stream()
                .filter(restrictions::contains)
                .findFirst();
    }

    public static List<String> collapseToStrictestActiveHours(Collection<String> restrictions) {
        Optional<String> strictest = strictestSimilarActiveHours(restrictions);
        return restrictions.stream()
                .distinct()
                .filter(restriction -> !isSimilarActiveHoursLevel(restriction)
                        || strictest.filter(restriction::equals).isPresent())
                .sorted()
                .toList();
    }

    public static String shortSimilarActiveHoursLabel(String similarActiveHoursOption) {
        return "similar hours ("
                + SIMILAR_ACTIVE_HOURS_SHORT_NAMES.getOrDefault(similarActiveHoursOption, similarActiveHoursOption)
                + ")";
    }

    public static String describeSimilarActiveHours(Collection<String> restrictions) {
        return strictestSimilarActiveHours(restrictions).orElse(NO_SIMILAR_ACTIVE_HOURS_OPTION);
    }

    public static String shortExpansionName(String expansion) {
        return EXPANSION_SHORT_NAMES.getOrDefault(expansion, expansion);
    }

    public static String shortPaceName(String pace) {
        return PACE_SHORT_NAMES.getOrDefault(pace, pace);
    }

    public static int getHours(String maxQueueTime) {
        if (maxQueueTime == null) return DEFAULT_MAX_QUEUE_TIME_HOURS;
        return MAX_QUEUE_TIME_OPTIONS_TO_HOURS.getOrDefault(maxQueueTime.trim(), DEFAULT_MAX_QUEUE_TIME_HOURS);
    }
}

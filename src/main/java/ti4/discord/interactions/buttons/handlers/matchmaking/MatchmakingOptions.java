package ti4.discord.interactions.buttons.handlers.matchmaking;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;

@UtilityClass
public class MatchmakingOptions {

    public static final List<String> EXPANSION_OPTIONS = List.of(
            "Base Only",
            "Prophecy of Kings",
            "Thunder's Edge",
            "Prophecy of Kings and Thunder's Edge",
            "Twilight's Fall");
    public static final List<String> PLAYER_COUNT_OPTIONS = List.of("3", "4", "5", "6", "7", "8");
    public static final List<String> VICTORY_POINT_OPTIONS = List.of("10", "12", "14");
    private static final String SIMILAR_ACTIVE_HOURS_OPTION = "Similar Active Hours";
    private static final String SIMILAR_PLAYER_SKILL_OPTION = "Similar Player Skill";
    public static final String NO_PACE_OPTION = "No Pace";
    public static final String SLOW_PACE_OPTION = "Slow (90 days)";
    public static final String FAST_PACE_OPTION = "Fast (30 days)";
    public static final String FASTER_PACE_OPTION = "Faster (15 days)";
    public static final String FASTEST_PACE_OPTION = "Fastest (7 days)";
    private static final String TIGL_OPTION = "Twilight Imperium Global League";
    public static final List<String> PACE_RESTRICTION_OPTIONS = List.of(
            NO_PACE_OPTION, SLOW_PACE_OPTION, FAST_PACE_OPTION, FASTER_PACE_OPTION, FASTEST_PACE_OPTION);
    public static final List<String> RESTRICTION_OPTIONS = List.of(
            SIMILAR_ACTIVE_HOURS_OPTION,
            SIMILAR_PLAYER_SKILL_OPTION,
            TIGL_OPTION);

    public static final Map<String, Integer> MAX_QUEUE_TIME_OPTIONS_TO_HOURS;

    static {
        MAX_QUEUE_TIME_OPTIONS_TO_HOURS = new LinkedHashMap<>();
        MAX_QUEUE_TIME_OPTIONS_TO_HOURS.put("1 hour", 1);
        MAX_QUEUE_TIME_OPTIONS_TO_HOURS.put("4 hours", 4);
        MAX_QUEUE_TIME_OPTIONS_TO_HOURS.put("8 hours", 8);
        MAX_QUEUE_TIME_OPTIONS_TO_HOURS.put("24 hours", 24);
        MAX_QUEUE_TIME_OPTIONS_TO_HOURS.put("48 hours", 48);
        MAX_QUEUE_TIME_OPTIONS_TO_HOURS.put("1 week", 168);
    }

    public static List<String> getPlayerCountOptionsDescending() {
        return PLAYER_COUNT_OPTIONS.stream().sorted(Collections.reverseOrder()).toList();
    }

    public static List<String> getShuffledVictoryPointOptions() {
        var victoryPointOptions = new ArrayList<>(VICTORY_POINT_OPTIONS);
        Collections.shuffle(victoryPointOptions);
        return victoryPointOptions;
    }

    public static List<String> getShuffledExpansionsOptions() {
        var expansionsPlusBase = new ArrayList<>(EXPANSION_OPTIONS);
        Collections.shuffle(expansionsPlusBase);
        return expansionsPlusBase;
    }

    private static boolean csvContains(String csv, String value) {
        return Arrays.asList(csv.split(",")).contains(value);
    }

    public static List<String> getShuffledPaceRestrictions() {
        List<String> pacesRestrictions = new ArrayList<>(PACE_RESTRICTION_OPTIONS);
        pacesRestrictions.remove(NO_PACE_OPTION);
        Collections.shuffle(pacesRestrictions);
        return pacesRestrictions;
    }

    public static List<Predicate<String>> getTiglRestrictionPredicates() {
        return buildRestrictionPredicates(restrictionsCsv -> csvContains(restrictionsCsv, TIGL_OPTION));
    }

    private static List<Predicate<String>> buildRestrictionPredicates(Predicate<String> predicate) {
        return List.of(predicate, predicate.negate());
    }

    public static boolean wantsSimilarActiveHours(String restrictionsCsv) {
        return restrictionsCsv.contains(SIMILAR_ACTIVE_HOURS_OPTION);
    }

    public static boolean wantsSimilarPlayerSkill(String restrictionsCsv) {
        return restrictionsCsv.contains(SIMILAR_PLAYER_SKILL_OPTION);
    }

    public static boolean wantsTigl(String restrictionsCsv) {
        return restrictionsCsv.contains(TIGL_OPTION);
    }
}

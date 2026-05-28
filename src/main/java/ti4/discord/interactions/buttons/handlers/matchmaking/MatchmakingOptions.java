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

    public static final List<String> EXPANSION_OPTIONS = List.of("PoK", "TE", "PoK + TE");
    public static final List<String> PLAYER_COUNT_OPTIONS = List.of("3", "4", "5", "6", "7", "8");
    public static final List<String> VICTORY_POINT_OPTIONS = List.of("10", "12", "14");
    public static final List<String> RESTRICTION_OPTIONS = List.of(
            "Similar Active Hours",
            "Similar Player Skill",
            "Twilight Imperium Global League",
            "Fast Pace (30 days)",
            "Faster Pace (14 days)",
            "Fastest Pace (7 days)");

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

    public static List<String> getVictoryPointOptionsDescending() {
        return VICTORY_POINT_OPTIONS.stream().sorted(Collections.reverseOrder()).toList();
    }

    public static List<String> getShuffledExpansionsWithBaseIncluded() {
        var expansionsPlusBase = new ArrayList<>(EXPANSION_OPTIONS);
        expansionsPlusBase.add("Base Only");

        Collections.shuffle(expansionsPlusBase);

        return expansionsPlusBase;
    }

    private static boolean csvContains(String csv, String value) {
        return Arrays.asList(csv.split(",")).contains(value);
    }

    public static List<String> getPaceRestrictions() {
        return RESTRICTION_OPTIONS.stream()
                .filter(restriction -> restriction.contains("Pace"))
                .toList();
    }

    public static List<Predicate<String>> getTiglRestrictionPredicates() {
        return buildRestrictionPredicates(
                restrictionsCsv -> csvContains(restrictionsCsv, "Twilight Imperium Global League"));
    }

    private static List<Predicate<String>> buildRestrictionPredicates(Predicate<String> predicate) {
        return List.of(predicate, predicate.negate());
    }
}

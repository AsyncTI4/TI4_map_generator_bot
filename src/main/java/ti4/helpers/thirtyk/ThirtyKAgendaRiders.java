package ti4.helpers.thirtyk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.math.NumberUtils;

@UtilityClass
public class ThirtyKAgendaRiders {

    public static final String EMBASSY_RIDER = "Embassy Rider";
    public static final String DUMMY_RIDER = "Dummy Rider";
    public static final String PUBLIC_OUTCRY = "Public Outcry";
    public static final String PUBLIC_OUTCRY_RIDER = "Public Outcry Rider";
    public static final String FOR = "for";
    public static final String AGAINST = "against";
    private static final String POLITICS_RIDER = "Politics Rider";
    private static final Set<String> UNCOPYABLE_RIDERS = Set.of(POLITICS_RIDER, PUBLIC_OUTCRY, PUBLIC_OUTCRY_RIDER);
    private static final Set<String> NO_VOTE_RIDERS =
            Set.of(EMBASSY_RIDER, DUMMY_RIDER, PUBLIC_OUTCRY, PUBLIC_OUTCRY_RIDER);

    public record RiderEntry(String outcome, String identifier, String rider) {

        public String entry() {
            return identifier + "_" + rider;
        }
    }

    public static boolean forbidsVoting(String rider) {
        return NO_VOTE_RIDERS.contains(rider);
    }

    public static boolean isForAgainst(String outcome) {
        return FOR.equalsIgnoreCase(outcome) || AGAINST.equalsIgnoreCase(outcome);
    }

    public static String opposingOutcome(String outcome) {
        if (FOR.equalsIgnoreCase(outcome)) return AGAINST;
        if (AGAINST.equalsIgnoreCase(outcome)) return FOR;
        return null;
    }

    public static boolean isCopyable(String outcome, String rider) {
        return isForAgainst(outcome) && rider.contains("Rider") && !UNCOPYABLE_RIDERS.contains(rider);
    }

    public static List<RiderEntry> riderEntries(Map<String, String> votes) {
        List<RiderEntry> entries = new ArrayList<>();
        for (Map.Entry<String, String> outcome : votes.entrySet()) {
            for (String piece : pieces(outcome.getValue())) {
                // TODO: faction ids containing '_' (pi_* factions) split at the wrong place here.
                int separator = piece.indexOf('_');
                if (separator < 1) continue;
                String rider = piece.substring(separator + 1);
                if (NumberUtils.isDigits(rider)) continue;
                entries.add(new RiderEntry(outcome.getKey(), piece.substring(0, separator), rider));
            }
        }
        return entries;
    }

    public static List<RiderEntry> copyableRiders(Map<String, String> votes) {
        return riderEntries(votes).stream()
                .filter(entry -> isCopyable(entry.outcome(), entry.rider()))
                .toList();
    }

    public static List<String> numericVoteOutcomes(Map<String, String> votes, Set<String> identifiers) {
        List<String> outcomes = new ArrayList<>();
        for (Map.Entry<String, String> outcome : votes.entrySet()) {
            for (String piece : pieces(outcome.getValue())) {
                int separator = piece.indexOf('_');
                if (separator < 1) continue;
                boolean mine =
                        identifiers.contains(piece.substring(0, separator).toLowerCase());
                if (mine && NumberUtils.isDigits(piece.substring(separator + 1))) {
                    outcomes.add(outcome.getKey());
                }
            }
        }
        return outcomes;
    }

    public static String withEntry(String existing, String entry) {
        if (existing == null || existing.isBlank() || "empty".equalsIgnoreCase(existing)) return entry;
        return existing + ";" + entry;
    }

    public static String renameEntry(String existing, String from, String to) {
        if (existing == null) return null;
        return pieces(existing).stream()
                .map(piece -> piece.equals(from) ? to : piece)
                .collect(Collectors.joining(";"));
    }

    private static List<String> pieces(String value) {
        if (value == null || value.isBlank() || "empty".equalsIgnoreCase(value)) return List.of();
        return Arrays.stream(value.split(";")).filter(piece -> !piece.isBlank()).toList();
    }
}

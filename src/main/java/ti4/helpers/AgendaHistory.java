package ti4.helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import ti4.game.Game;

@UtilityClass
public class AgendaHistory {

    static final String KEY = "agendaHistory";
    private static final String RECORDS = ";";
    private static final String FIELD = "|";
    private static final String VOTERS = "+";
    private static final String UNSAFE = RECORDS + FIELD + VOTERS + ":,";
    private static final String RESOLVED = "resolved";
    private static final String QUASHED = "quashed";
    private static final String PIECES = ";";
    private static final char OWNER = '_';
    private static final int FIELDS = 5;
    private static final int MAX_RECORDS = 16;

    public record Entry(int round, String agenda, String how, String outcome, List<String> voters) {

        public boolean resolved() {
            return RESOLVED.equals(how);
        }

        public boolean quashed() {
            return QUASHED.equals(how);
        }

        public boolean votedForOutcome(String colorOrFaction) {
            return voters.stream().anyMatch(colorOrFaction::equalsIgnoreCase);
        }

        String encode() {
            return String.join(FIELD, String.valueOf(round), agenda, how, outcome, String.join(VOTERS, voters));
        }
    }

    public static void recordResolved(Game game, String agenda, String outcome) {
        record(game, new Entry(game.getRound(), agenda, RESOLVED, outcome, votersFor(game, outcome)));
    }

    public static void recordQuashed(Game game, String agenda) {
        record(game, new Entry(game.getRound(), agenda, QUASHED, "", List.of()));
    }

    public static Optional<Entry> of(Game game, int round, String agenda) {
        if (StringUtils.isBlank(agenda)) return Optional.empty();
        return all(game).stream()
                .filter(entry -> entry.round() == round && entry.agenda().equals(agenda))
                .reduce((first, second) -> second);
    }

    public static List<Entry> all(Game game) {
        List<Entry> entries = new ArrayList<>();
        for (String encoded : StringUtils.split(game.getStoredValue(KEY), RECORDS)) {
            String[] fields = StringUtils.splitPreserveAllTokens(encoded, FIELD);
            if (fields == null || fields.length != FIELDS || !NumberUtils.isDigits(fields[0])) continue;
            entries.add(new Entry(
                    Integer.parseInt(fields[0]),
                    fields[1],
                    fields[2],
                    fields[3],
                    Arrays.asList(StringUtils.split(fields[4], VOTERS))));
        }
        return entries;
    }

    private static void record(Game game, Entry entry) {
        if (StringUtils.isBlank(entry.agenda())
                || !StringUtils.containsNone(
                        entry.agenda() + entry.outcome() + String.join("", entry.voters()), UNSAFE)) {
            return;
        }
        List<Entry> kept = new ArrayList<>(all(game).stream()
                .filter(other ->
                        other.round() != entry.round() || !other.agenda().equals(entry.agenda()))
                .toList());
        kept.add(entry);
        while (kept.size() > MAX_RECORDS) kept.removeFirst();
        game.setStoredValue(
                KEY, String.join(RECORDS, kept.stream().map(Entry::encode).toList()));
    }

    private static List<String> votersFor(Game game, String outcome) {
        Set<String> voters = new LinkedHashSet<>();
        for (var votes : game.getCurrentAgendaVotes().entrySet()) {
            if (!votes.getKey().equalsIgnoreCase(outcome)) continue;
            for (String piece : votes.getValue().split(PIECES)) {
                int split = piece.indexOf(OWNER);
                if (split > 0 && NumberUtils.isDigits(piece.substring(split + 1))) {
                    voters.add(piece.substring(0, split).toLowerCase());
                }
            }
        }
        return List.copyOf(voters);
    }
}

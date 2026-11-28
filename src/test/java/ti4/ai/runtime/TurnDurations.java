package ti4.ai.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import ti4.game.Game;
import ti4.game.Player;

// Splits a self-play game into turns and measures each one: a seat's turn in the action phase, and every other phase
// (strategy, status, agenda) as one stretch of its own. For each turn it keeps the virtual game time it took (how long
// the table waited, including AI waits and timeouts), the real time the AI spent thinking in its ticks, and the number
// of ticks. A stall shows up as a turn with a long game time and little thinking; a slow AI as the reverse. Each turn
// is also handed to a listener the moment it closes, so a long game logs its turns as it plays, not all at its end.
final class TurnDurations {

    private static final String ACTION = "action";

    record Turn(String game, int round, String who, int number, long gameMillis, long thinkNanos, int ticks) {

        String line() {
            return String.format(
                    Locale.ROOT,
                    "TURN %s r%d %s #%d: game time %.0fs, AI thinking %.1fs over %d ticks",
                    game,
                    round,
                    who,
                    number,
                    gameMillis / 1000.0,
                    thinkNanos / 1e9,
                    ticks);
        }
    }

    private final String game;
    private final Consumer<Turn> closed;
    private final List<Turn> turns = new ArrayList<>();
    private final Map<String, Integer> turnsTaken = new LinkedHashMap<>();
    private String openKey;
    private int openRound;
    private String openWho;
    private long openedAt;
    private long thinkNanos;
    private int ticks;

    TurnDurations(String game) {
        this(game, turn -> {});
    }

    TurnDurations(String game, Consumer<Turn> closed) {
        this.game = game;
        this.closed = closed;
    }

    void observe(Game game, long now) {
        String who = who(game);
        String key = game.getRound() + "|" + who;
        if (key.equals(openKey)) return;
        close(now);
        openKey = key;
        openRound = game.getRound();
        openWho = who;
        openedAt = now;
    }

    void ticked(long nanos) {
        thinkNanos += nanos;
        ticks++;
    }

    // Hands over the turns measured since the last call, so a game run in several stretches never prints one twice.
    List<Turn> finish(long now) {
        close(now);
        List<Turn> finished = List.copyOf(turns);
        turns.clear();
        return finished;
    }

    private void close(long now) {
        if (openKey == null) return;
        int number = turnsTaken.merge(openRound + "|" + openWho, 1, Integer::sum);
        Turn turn = new Turn(game, openRound, openWho, number, now - openedAt, thinkNanos, ticks);
        turns.add(turn);
        closed.accept(turn);
        openKey = null;
        thinkNanos = 0;
        ticks = 0;
    }

    private static String who(Game game) {
        String phase =
                game.getPhaseOfGame() == null ? "" : game.getPhaseOfGame().toLowerCase(Locale.ROOT);
        if (!ACTION.equals(phase)) return phase.startsWith("agenda") ? "agenda" : phase;
        Player active = game.getActivePlayer();
        return active == null ? ACTION : active.getFaction();
    }

    // Per seat: how many action-phase turns it took, their average and longest game time, and its average thinking.
    static List<String> summary(List<Turn> turns) {
        Map<String, List<Turn>> bySeat = new LinkedHashMap<>();
        for (Turn turn : turns)
            bySeat.computeIfAbsent(turn.who(), who -> new ArrayList<>()).add(turn);
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, List<Turn>> seat : bySeat.entrySet()) {
            List<Turn> list = seat.getValue();
            Turn longest = list.stream()
                    .max(java.util.Comparator.comparingLong(Turn::gameMillis))
                    .orElseThrow();
            double gameSeconds =
                    list.stream().mapToLong(Turn::gameMillis).average().orElse(0) / 1000.0;
            double thinkSeconds =
                    list.stream().mapToLong(Turn::thinkNanos).average().orElse(0) / 1e9;
            lines.add(String.format(
                    Locale.ROOT,
                    "TURNS %s %s: %d, average game time %.0fs, average AI thinking %.1fs, longest r%d #%d %.0fs",
                    longest.game(),
                    seat.getKey(),
                    list.size(),
                    gameSeconds,
                    thinkSeconds,
                    longest.round(),
                    longest.number(),
                    longest.gameMillis() / 1000.0));
        }
        return lines;
    }
}

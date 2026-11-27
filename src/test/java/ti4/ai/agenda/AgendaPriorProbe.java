package ti4.ai.agenda;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import ti4.ai.brain.DecisionMemo;
import ti4.ai.eval.DecidingSeat;
import ti4.game.Game;
import ti4.game.Player;

/**
 * Self-play diagnostics for the value of a vote kept for a later agenda (-Dai.selfplay.agendaprior=true). Once per
 * agenda, for every seat, it runs the full look-ahead over the remaining agenda deck that a kept vote is priced from
 * (AgendaPrior.deckSpread), as that seat, and prints one AGENDAPRIOR line. The averages of these lines are what the
 * cheap default uses instead of the look-ahead. It runs in its own isolated memo, so it changes nothing a seeded game
 * does.
 */
public final class AgendaPriorProbe {

    public static final String PROPERTY = "ai.selfplay.agendaprior";
    public static final String PREFIX = "AGENDAPRIOR";

    private AgendaPriorProbe() {}

    public static boolean enabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    public static List<String> report(String gameName, Game game) {
        String agenda = AgendaPolicy.currentAgenda(game);
        boolean finalAgenda = AgendaRound.isFinalAgenda(game);
        int deckSize = AgendaPrior.remainingDeck(game).size();
        int tableVotes = game.getRealPlayers().stream()
                .mapToInt(player -> VoteCapacity.of(game, player))
                .sum();
        List<String> lines = new ArrayList<>();
        for (Player seat : game.getRealPlayers()) {
            try (DecisionMemo.Open probe = DecisionMemo.openIsolated()) {
                lines.add(DecidingSeat.of(
                        seat, () -> line(gameName, game, seat, agenda, finalAgenda, deckSize, tableVotes)));
            }
        }
        return lines;
    }

    private static String line(
            String gameName, Game game, Player seat, String agenda, boolean finalAgenda, int deckSize, int tableVotes) {
        long start = System.nanoTime();
        double deckSpread = AgendaPrior.fullDeckSpread(game, seat);
        long nanos = System.nanoTime() - start;
        Optional<String> known = AgendaKnowledge.next(game, seat);
        return String.format(
                Locale.ROOT,
                "%s %s r%d %s seat=%s final=%b known=%s deck=%d deckSpread=%.4f tableVotes=%d seatVotes=%d ms=%.0f",
                PREFIX,
                gameName,
                game.getRound(),
                agenda,
                seat.getFaction(),
                finalAgenda,
                known.orElse("-"),
                deckSize,
                deckSpread,
                tableVotes,
                VoteCapacity.of(game, seat),
                nanos / 1e6);
    }
}

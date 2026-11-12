package ti4.ai.agenda;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.promise.Promise;
import ti4.ai.promise.PromiseBook;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.AgendaHistory;

@UtilityClass
public class PromisedVotes {

    private static final String RESOLVED_AGENDA = "resolvedAgendaId";
    private static final String RESOLVED_OUTCOME = "resolvedAgendaOutcome";

    public static String agendaKey(Game game) {
        return AgendaPolicy.currentAgenda(game);
    }

    public static boolean onThisAgenda(Game game, Promise promise) {
        String key = agendaKey(game);
        return promise.term() == Promise.Term.VOTE_FOR
                && promise.thisRound(game)
                && !key.isBlank()
                && key.equals(promise.agenda());
    }

    public static Optional<Promise> binding(Game game, Player seat) {
        return PromiseBook.bindingOn(game, seat).stream()
                .filter(promise -> onThisAgenda(game, promise))
                .findFirst();
    }

    public static boolean forbidsRider(Game game, Player seat) {
        if (!PromiseBook.active(game, seat)) return false;
        return PromiseBook.all(game).stream()
                .filter(promise -> promise.isPromiser(seat) && onThisAgenda(game, promise))
                .anyMatch(promise -> promise.status() == Promise.Status.ACCEPTED
                        || (promise.open() && promise.proposer().equals(promise.promiser())));
    }

    public static boolean hasVoted(Game game, Player player) {
        return AgendaSwing.votedFor(game, player).isPresent();
    }

    public static Optional<Promise.Status> verdict(Game game, Promise promise) {
        if (promise.status() != Promise.Status.ACCEPTED || promise.term() != Promise.Term.VOTE_FOR) {
            return Optional.empty();
        }
        Player promiser = game.getPlayerFromColorOrFaction(promise.promiser());
        if (promiser == null) return Optional.of(Promise.Status.VOID);
        if (!onThisAgenda(game, promise)) return Optional.of(afterTheAgenda(game, promise, promiser));
        if (AgendaSwing.backedAnotherOutcome(game, promiser, promise.target())) {
            return Optional.of(Promise.Status.BROKEN);
        }
        return AgendaSwing.votedFor(game, promiser)
                .filter(promise.target()::equalsIgnoreCase)
                .map(ignored -> Promise.Status.KEPT);
    }

    private static Promise.Status afterTheAgenda(Game game, Promise promise, Player promiser) {
        return AgendaHistory.of(game, promise.round(), promise.agenda())
                .filter(AgendaHistory.Entry::resolved)
                .filter(entry -> entry.outcome().equalsIgnoreCase(promise.target()))
                .filter(entry ->
                        entry.votedForOutcome(promiser.getColor()) || entry.votedForOutcome(promiser.getFaction()))
                .map(entry -> Promise.Status.KEPT)
                .orElse(Promise.Status.EXPIRED);
    }

    public static Optional<String> resolvedOutcome(Game game, Promise promise) {
        Optional<String> recorded = AgendaHistory.of(game, promise.round(), promise.agenda())
                .filter(AgendaHistory.Entry::resolved)
                .map(AgendaHistory.Entry::outcome);
        if (recorded.isPresent()) return recorded;
        String outcome = game.getStoredValue(RESOLVED_OUTCOME);
        if (!promise.thisRound(game) || outcome.isBlank() || promise.agenda().isBlank()) return Optional.empty();
        return promise.agenda().equals(game.getStoredValue(RESOLVED_AGENDA)) ? Optional.of(outcome) : Optional.empty();
    }

    public static Optional<Promise.Status> settlement(Game game, Promise promise) {
        if (promise.term() != Promise.Term.VOTE_FOR || promise.status() != Promise.Status.KEPT) {
            return Optional.empty();
        }
        return resolvedOutcome(game, promise)
                .filter(promise.target()::equalsIgnoreCase)
                .map(ignored -> Promise.Status.OWED);
    }

    static int promisedVotes(Promise promise, int castable) {
        return promise.votes() > 0 ? Math.min(promise.votes(), castable) : castable;
    }

    static AgendaForecast placeAccepted(Game game, Player viewer, AgendaForecast forecast) {
        if (forecast.isEmpty() || !PromiseBook.active(game, viewer)) return forecast;
        AgendaForecast placed = forecast;
        for (Promise promise : PromiseBook.all(game)) {
            if (promise.status() != Promise.Status.ACCEPTED || !onThisAgenda(game, promise)) continue;
            Player promiser = game.getPlayerFromColorOrFaction(promise.promiser());
            if (promiser == null || promiser == viewer) continue;
            int left = placed.votesLeft(promiser);
            if (left > 0) placed = placed.withVotes(promiser, promise.target(), promisedVotes(promise, left));
        }
        return placed;
    }
}

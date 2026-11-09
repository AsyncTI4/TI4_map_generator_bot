package ti4.ai.agenda;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.promise.Promise;
import ti4.ai.promise.PromiseBook;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class PromisedVotes {

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

    public static boolean hasVoted(Game game, Player player) {
        return AgendaSwing.votedFor(game, player).isPresent();
    }

    public static Optional<Promise.Status> verdict(Game game, Promise promise) {
        if (promise.status() != Promise.Status.ACCEPTED || promise.term() != Promise.Term.VOTE_FOR) {
            return Optional.empty();
        }
        if (!onThisAgenda(game, promise)) return Optional.of(Promise.Status.EXPIRED);
        Player promiser = game.getPlayerFromColorOrFaction(promise.promiser());
        if (promiser == null) return Optional.of(Promise.Status.VOID);
        if (AgendaSwing.backedAnotherOutcome(game, promiser, promise.target())) {
            return Optional.of(Promise.Status.BROKEN);
        }
        return AgendaSwing.votedFor(game, promiser)
                .filter(promise.target()::equalsIgnoreCase)
                .map(ignored -> Promise.Status.KEPT);
    }

    static AgendaForecast placeAccepted(Game game, Player viewer, AgendaForecast forecast) {
        if (forecast.isEmpty() || !PromiseBook.active(game, viewer)) return forecast;
        AgendaForecast placed = forecast;
        for (Promise promise : PromiseBook.all(game)) {
            if (promise.status() != Promise.Status.ACCEPTED || !onThisAgenda(game, promise)) continue;
            Player promiser = game.getPlayerFromColorOrFaction(promise.promiser());
            if (promiser == null || promiser == viewer) continue;
            int left = placed.votesLeft(promiser);
            if (left > 0) placed = placed.withVotes(promiser, promise.target(), left);
        }
        return placed;
    }
}

package ti4.ai.promise;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;

@UtilityClass
public class PromiseChecks {

    public static Optional<Promise.Status> territorial(Game game, Promise promise) {
        if (promise.status() != Promise.Status.ACCEPTED || promise.term() == Promise.Term.VOTE_FOR) {
            return Optional.empty();
        }
        if (!promise.thisRound(game)) return Optional.of(Promise.Status.KEPT);
        Player promiser = game.getPlayerFromColorOrFaction(promise.promiser());
        Player promisee = game.getPlayerFromColorOrFaction(promise.promisee());
        if (promiser == null || promisee == null) return Optional.of(Promise.Status.VOID);
        if (entered(game, promiser, promisee, promise.guarded(), promise.term() == Promise.Term.STAY_OUT)) {
            return Optional.of(Promise.Status.BROKEN);
        }
        if (entered(game, promisee, promiser, promise.held(), false)) return Optional.of(Promise.Status.VOID);
        return Optional.empty();
    }

    private static boolean entered(
            Game game, Player activator, Player owner, List<String> positions, boolean shipsOnly) {
        return positions.stream()
                .map(game::getTileByPosition)
                .filter(tile -> tile != null && tile.hasPlayerCC(activator))
                .anyMatch(tile -> movedIn(activator, tile) || (!shipsOnly && Presence.at(owner, tile)));
    }

    private static boolean movedIn(Player activator, Tile tile) {
        return FoWHelper.playerHasUnitsInSystem(activator, tile);
    }
}

package ti4.ai.promise;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitType;

@UtilityClass
public class PromiseChecks {

    private static final List<String> AFTER_ACTION_PHASES = List.of("status", "agenda");

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
        return actionPhaseOver(game) ? Optional.of(Promise.Status.KEPT) : Optional.empty();
    }

    public static Optional<Promise> withoutLeftSystems(Game game, Promise promise) {
        if (promise.status() != Promise.Status.ACCEPTED || promise.term() != Promise.Term.NO_ATTACK) {
            return Optional.empty();
        }
        Player promiser = game.getPlayerFromColorOrFaction(promise.promiser());
        Player promisee = game.getPlayerFromColorOrFaction(promise.promisee());
        if (promiser == null || promisee == null) return Optional.empty();
        List<String> stillGuarded = promise.guarded().stream()
                .filter(position -> stillGuarded(game.getTileByPosition(position), promiser, promisee))
                .toList();
        return stillGuarded.size() == promise.guarded().size()
                ? Optional.empty()
                : Optional.of(promise.withGuarded(stillGuarded));
    }

    private static boolean stillGuarded(Tile tile, Player promiser, Player promisee) {
        return tile != null && (tile.hasPlayerCC(promiser) || Presence.at(promisee, tile));
    }

    private static boolean actionPhaseOver(Game game) {
        String phase = Objects.toString(game.getPhaseOfGame(), "").toLowerCase(Locale.ROOT);
        return AFTER_ACTION_PHASES.stream().anyMatch(phase::startsWith);
    }

    private static boolean entered(
            Game game, Player activator, Player owner, List<String> positions, boolean shipsOnly) {
        return positions.stream()
                .map(game::getTileByPosition)
                .filter(tile -> tile != null && tile.hasPlayerCC(activator))
                .anyMatch(tile -> shipsOnly ? shipsMovedIn(activator, tile) : attacked(activator, owner, tile));
    }

    private static boolean attacked(Player activator, Player owner, Tile tile) {
        return FoWHelper.playerHasUnitsInSystem(activator, tile) || Presence.at(owner, tile);
    }

    private static boolean shipsMovedIn(Player activator, Tile tile) {
        return BoardView.hasOwnShips(activator, tile) && !producesIn(activator, tile);
    }

    private static boolean producesIn(Player player, Tile tile) {
        return tile.getUnitHolders().values().stream()
                .anyMatch(holder -> BoardView.count(holder, player, UnitType.Spacedock) > 0);
    }
}

package ti4.ai.trade;

import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.Territory;
import ti4.ai.eval.Territory.Zone;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;

@UtilityClass
class ThroneTiming {

    private static final int OPEN_ROUND = 4;
    private static final int LATE_ROUND = 3;
    private static final int LATE_ROUND_TOKENS = 1;
    private static final double TENSION_STRENGTH = 1.2;
    private static final Set<Zone> SLICE_ZONES = Set.of(Zone.SLICE, Zone.LANE);

    static boolean open(Game game, Player seat, Player partner) {
        if (game.getRound() >= OPEN_ROUND) return true;
        if (game.getRound() == LATE_ROUND && lateInRound(seat)) return true;
        return sliceTaken(game, seat) && sliceTaken(game, partner) && tension(game, seat, partner);
    }

    static boolean sliceTaken(Game game, Player player) {
        Territory territory = Territory.of(game, player);
        Set<String> claimed = game.getRealPlayers().stream()
                .flatMap(other -> other.getPlanets().stream())
                .collect(Collectors.toSet());
        return game.getTileMap().values().stream()
                .filter(tile -> SLICE_ZONES.contains(territory.zone(tile)))
                .flatMap(tile -> tile.getPlanetUnitHolders().stream())
                .map(Planet::getName)
                .allMatch(claimed::contains);
    }

    static boolean tension(Game game, Player seat, Player partner) {
        if (ThroneValue.sharedSystems(game, seat, partner) > 0 || ThroneValue.sharedSystems(game, partner, seat) > 0) {
            return true;
        }
        return TradeLegality.neighbours(game, seat, partner)
                && ThroneValue.fleetCost(game, partner) >= TENSION_STRENGTH * ThroneValue.fleetCost(game, seat);
    }

    private static boolean lateInRound(Player seat) {
        return seat.isPassed() || seat.getTacticalCC() <= LATE_ROUND_TOKENS;
    }
}

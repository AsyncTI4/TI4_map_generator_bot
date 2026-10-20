package ti4.ai.secrets;

import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.Territory;
import ti4.ai.eval.Territory.Zone;
import ti4.ai.scoring.ObjectiveValue;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
class TraitReach {

    private static final double NEARER_FREE = 1.0;
    private static final double FARTHER_FREE = 0.5;
    private static final double RIVAL_HELD = 0.3;
    private static final double RICH_COUNT = 5.0;
    private static final double THIN_COUNT = 2.0;
    private static final Set<Zone> NEARER_ZONES = Set.of(Zone.HOME, Zone.LANE, Zone.SLICE);

    static boolean applies(String secretId) {
        return ObjectiveValue.TRAIT_SECRETS.containsKey(secretId);
    }

    static double feasibility(Game game, Player seat, String secretId, double floor) {
        double count = weightedCount(game, seat, ObjectiveValue.TRAIT_SECRETS.get(secretId));
        if (count >= RICH_COUNT) return 1.0;
        if (count <= THIN_COUNT) return floor;
        return floor + (1.0 - floor) * (count - THIN_COUNT) / (RICH_COUNT - THIN_COUNT);
    }

    static double weightedCount(Game game, Player seat, String trait) {
        Territory territory = Territory.of(game, seat);
        double total = 0;
        for (Tile tile : game.getTileMap().values()) {
            Zone zone = territory.zone(tile);
            if (zone == Zone.OTHER_HOME) continue;
            for (Planet planet : tile.getPlanetUnitHolders()) {
                if (!ObjectiveValue.countsForScoring(game, planet)
                        || !planet.getPlanetTypes().contains(trait)) {
                    continue;
                }
                total += weight(BoardView.controller(game, planet.getName()), seat, zone);
            }
        }
        return total;
    }

    private static double weight(Player controller, Player seat, Zone zone) {
        if (controller == seat) return NEARER_FREE;
        if (controller != null) return RIVAL_HELD;
        return NEARER_ZONES.contains(zone) ? NEARER_FREE : FARTHER_FREE;
    }
}

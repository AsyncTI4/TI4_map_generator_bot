package ti4.ai.strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.FracturePrize;
import ti4.ai.eval.MovementGraph;
import ti4.ai.eval.Territory;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
class ReachLever {

    static final String ANTIMASS_DEFLECTORS = "amd";
    private static final String ABSOL_ANTIMASS_DEFLECTORS = "absol_amd";
    private static final int RANGE = 4;

    private record Target(String label, Tile tile) {}

    static Optional<String> antimassTarget(Game game, Player seat) {
        Tile home = seat.getHomeSystemTile();
        if (home == null || seat.hasTech(ANTIMASS_DEFLECTORS) || seat.hasTech(ABSOL_ANTIMASS_DEFLECTORS)) {
            return Optional.empty();
        }
        Map<String, Integer> without = MovementGraph.reach(game, seat, home.getPosition(), RANGE);
        Map<String, Integer> with =
                MovementGraph.withAntimassDeflectors(() -> MovementGraph.reach(game, seat, home.getPosition(), RANGE));
        return targets(game, seat).stream()
                .filter(target -> shorterWith(with, without, target.tile().getPosition()))
                .map(Target::label)
                .findFirst();
    }

    private static boolean shorterWith(Map<String, Integer> with, Map<String, Integer> without, String position) {
        Integer shortcut = with.get(position);
        if (shortcut == null) return false;
        Integer detour = without.get(position);
        return detour == null || shortcut < detour;
    }

    private static List<Target> targets(Game game, Player seat) {
        List<Target> targets = new ArrayList<>();
        Tile mecatol = game.getMecatolTile();
        if (mecatol != null) targets.add(new Target("Mecatol", mecatol));
        for (Tile tile : game.getTileMap().values()) {
            if (FracturePrize.hasIngress(tile)) targets.add(new Target("Ingress", tile));
        }
        Territory territory = Territory.of(game, seat);
        for (Tile tile : game.getTileMap().values()) {
            if (territory.zone(tile) == Territory.Zone.SLICE && hasUnclaimedPlanet(game, tile)) {
                targets.add(new Target("slice planets", tile));
            }
        }
        return targets;
    }

    private static boolean hasUnclaimedPlanet(Game game, Tile tile) {
        for (Planet planet : tile.getPlanetUnitHolders()) {
            if (BoardView.controller(game, planet.getName()) == null) return true;
        }
        return false;
    }
}

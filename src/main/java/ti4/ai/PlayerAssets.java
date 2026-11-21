package ti4.ai;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.ValueScale;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.Units.UnitKey;
import ti4.model.UnitModel;

@UtilityClass
public class PlayerAssets {

    static final double COMMAND_TOKEN = 2.4;
    static final double FRAGMENT = 1.0;
    static final double PROMISSORY_NOTE = 1.0;

    public record Holdings(double total, Map<String, Double> units, Set<String> planets, Set<String> tokens) {

        public double outside(Collection<String> positions) {
            double inside = positions.stream()
                    .mapToDouble(position -> units.getOrDefault(position, 0.0))
                    .sum();
            return total - inside;
        }

        public boolean hasUnitsAt(String position) {
            return units.getOrDefault(position, 0.0) > 0;
        }
    }

    public static double value(Game game, Player player) {
        return holdings(game, player).total();
    }

    public static Holdings holdings(Game game, Player player) {
        Map<String, Double> units = units(game, player);
        double unitTotal =
                units.values().stream().mapToDouble(Double::doubleValue).sum();
        double total = player.getTg() * ValueScale.TRADE_GOOD
                + player.getCommodities() * ValueScale.COMMODITY
                + player.getPromissoryNotes().size() * PROMISSORY_NOTE
                + (player.getTacticalCC() + player.getFleetCC() + player.getStrategicCC()) * COMMAND_TOKEN
                + player.getFragments().size() * FRAGMENT
                + readiedPlanets(game, player)
                + unitTotal;
        return new Holdings(total, units, Set.copyOf(player.getPlanets()), tokens(game, player));
    }

    private static Set<String> tokens(Game game, Player player) {
        Set<String> positions = new TreeSet<>();
        for (Tile tile : game.getTileMap().values()) {
            if (CommandCounterHelper.hasCC(player, tile)) positions.add(tile.getPosition());
        }
        return positions;
    }

    private static double readiedPlanets(Game game, Player player) {
        double total = 0;
        for (String name : player.getReadiedPlanets()) {
            Planet planet = game.getPlanetsInfo().get(name);
            if (planet != null) total += ValueScale.spendable(planet.getResources(), planet.getInfluence());
        }
        return total;
    }

    private static Map<String, Double> units(Game game, Player player) {
        Map<String, Double> byPosition = new TreeMap<>();
        for (Tile tile : game.getTileMap().values()) {
            double total = 0;
            for (UnitHolder holder : tile.getUnitHolders().values()) {
                for (Map.Entry<UnitKey, Integer> unit : holder.getUnits().entrySet()) {
                    if (!player.unitBelongsToPlayer(unit.getKey())) continue;
                    UnitModel model = player.getUnitFromUnitKey(unit.getKey());
                    if (model != null) total += model.getCost() * ValueScale.RESOURCE * unit.getValue();
                }
            }
            if (total > 0) byPosition.put(tile.getPosition(), total);
        }
        return byPosition;
    }
}

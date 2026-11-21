package ti4.ai;

import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.ValueScale;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitKey;
import ti4.model.UnitModel;

@UtilityClass
public class PlayerAssets {

    static final double COMMAND_TOKEN = 2.4;
    static final double FRAGMENT = 1.0;
    static final double PROMISSORY_NOTE = 1.0;

    public static double value(Game game, Player player) {
        return player.getTg() * ValueScale.TRADE_GOOD
                + player.getCommodities() * ValueScale.COMMODITY
                + player.getPromissoryNotes().size() * PROMISSORY_NOTE
                + (player.getTacticalCC() + player.getFleetCC() + player.getStrategicCC()) * COMMAND_TOKEN
                + player.getFragments().size() * FRAGMENT
                + readiedPlanets(game, player)
                + units(game, player);
    }

    private static double readiedPlanets(Game game, Player player) {
        double total = 0;
        for (String name : player.getReadiedPlanets()) {
            Planet planet = game.getPlanetsInfo().get(name);
            if (planet != null) total += ValueScale.spendable(planet.getResources(), planet.getInfluence());
        }
        return total;
    }

    private static double units(Game game, Player player) {
        double total = 0;
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder holder : tile.getUnitHolders().values()) {
                for (Map.Entry<UnitKey, Integer> unit : holder.getUnits().entrySet()) {
                    if (!player.unitBelongsToPlayer(unit.getKey())) continue;
                    UnitModel model = player.getUnitFromUnitKey(unit.getKey());
                    if (model != null) total += model.getCost() * ValueScale.RESOURCE * unit.getValue();
                }
            }
        }
        return total;
    }
}

package ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.kalora;

import java.util.Map;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.tuple.Pair;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.FoWHelper;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollService;

@UtilityClass
public class KaloraCommanderHandler {

    public static void addCommanderBombardmentUnits(
            Player player, Tile tile, Map<Pair<UnitModel, UnitHolder>, Integer> units) {
        var game = player.getGame();
        if (game == null) return;
        String guardKey = "kaloraCommanderBombardResolving_" + player.getFaction();
        if (!game.getStoredValue(guardKey).isEmpty()) return;
        game.setStoredValue(guardKey, "true");
        try {
            for (String adjacentPos : FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false, false)) {
                Tile adjacentTile = game.getTileByPosition(adjacentPos);
                if (adjacentTile == null) continue;
                Map<Pair<UnitModel, UnitHolder>, Integer> adjacentUnits =
                        CombatRollService.getUnitsInBombardment(adjacentTile, player, null);
                adjacentUnits.forEach((pair, count) -> units.merge(pair, count, Integer::sum));
            }
        } finally {
            game.setStoredValue(guardKey, "");
        }
    }
}

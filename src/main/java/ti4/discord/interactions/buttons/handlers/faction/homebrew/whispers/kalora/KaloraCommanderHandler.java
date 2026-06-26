package ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.kalora;

import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollService;

@UtilityClass
public class KaloraCommanderHandler {

    public static void addCommanderBombardmentUnits(Player player, Tile tile, Map<UnitModel, Integer> units) {
        var game = player.getGame();
        if (game == null) return;
        String guardKey = "kaloraCommanderBombardResolving_" + player.getFaction();
        if (!game.getStoredValue(guardKey).isEmpty()) return;
        game.setStoredValue(guardKey, "true");
        try {
            for (String adjacentPos : FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false, false)) {
                Tile adjacentTile = game.getTileByPosition(adjacentPos);
                if (adjacentTile == null) continue;
                CombatRollService.getUnitsInBombardment(adjacentTile, player, null)
                        .forEach((unit, count) -> units.merge(unit, count, Integer::sum));
            }
        } finally {
            game.setStoredValue(guardKey, "");
        }
    }
}

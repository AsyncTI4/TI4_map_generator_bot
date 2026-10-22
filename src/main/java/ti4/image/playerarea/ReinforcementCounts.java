package ti4.image.playerarea;

import java.util.HashMap;
import java.util.Map;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;

final class ReinforcementCounts {

    private final Map<UnitKey, Integer> unitsOnBoard = new HashMap<>();

    Map<UnitKey, Integer> unitsOnBoard(Game game) {
        if (unitsOnBoard.isEmpty()) {
            for (Tile tile : game.getTileMap().values()) {
                for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                    count(unitHolder, false);
                }
            }
            for (Player player : game.getPlayers().values()) {
                UnitHolder nombox = player.getNomboxTile().getSpaceUnitHolder();
                if (nombox == null) continue;
                count(nombox, true);
            }
        }
        return unitsOnBoard;
    }

    private void count(UnitHolder unitHolder, boolean ignoreInfantryFighters) {
        for (UnitKey unitKey : unitHolder.getUnitKeys()) {
            int count = unitsOnBoard.getOrDefault(unitKey, 0);
            if (unitKey.unitType() == UnitType.Infantry || unitKey.unitType() == UnitType.Fighter) {
                if (ignoreInfantryFighters) continue;
                count++;
            } else {
                count += unitHolder.getUnitCount(unitKey);
            }
            unitsOnBoard.put(unitKey, count);
        }
    }
}

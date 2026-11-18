package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
class Acd2UnitHelper {

    static List<UnitModel> getUnitsWithCostAtMost(Player player, Tile tile, int cost) {
        return Arrays.stream(UnitType.values())
                .map(type -> player.getPriorityUnitByAsyncID(type.getValue(), tile.getSpaceUnitHolder()))
                .filter(Objects::nonNull)
                .filter(unit -> unit.getCost() > 0 && unit.getCost() <= cost)
                .filter(unit -> !unit.getIsStructure() && !unit.getIsPlanetOnly())
                .toList();
    }
}

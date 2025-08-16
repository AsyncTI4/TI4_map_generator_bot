package ti4.service.unit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.experimental.UtilityClass;
import ti4.helpers.Units;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;

@UtilityClass
public class CheckUnitContainmentService {

    public static List<Tile> getTilesContainingPlayersUnits(Game game, Player p1, Units.UnitType... type) {
        var unitTypes = new ArrayList<Units.UnitType>();
        Collections.addAll(unitTypes, type);

        return game.getTileMap().values().stream()
            .filter(t -> t.containsPlayersUnitsWithKeyCondition(p1, unit -> unitTypes.contains(unit.getUnitType())))
            .toList();
    }
}

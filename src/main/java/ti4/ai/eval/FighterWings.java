package ti4.ai.eval;

import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
public class FighterWings {

    private static final String HYBRID_CRYSTAL_FIGHTER_II = "hcf2";

    public static boolean flies(Game game, Player seat) {
        return ShipSpeed.printed(game, seat, UnitType.Fighter) > 0;
    }

    static boolean flies(Player seat) {
        UnitModel fighter = seat.getUnitByType(UnitType.Fighter);
        return fighter != null && fighter.getMoveValue() > 0;
    }

    public static int perFleetSlot(Player seat) {
        return seat.hasTech(HYBRID_CRYSTAL_FIGHTER_II) ? 2 : 1;
    }

    public static int room(Player seat, Map<UnitType, Integer> shipsInSpace, int groundInSpace, boolean dock) {
        int capacity = 0;
        for (Map.Entry<UnitType, Integer> ship : shipsInSpace.entrySet()) {
            if (ship.getKey() == UnitType.Fighter) continue;
            capacity += Math.max(0, ship.getValue()) * BoardView.capacity(seat, ship.getKey());
        }
        return capacity + (dock ? BoardView.DOCK_FIGHTER_ALLOWANCE : 0) - groundInSpace;
    }

    public static int fleetSlots(
            Game game, Player seat, Map<UnitType, Integer> shipsInSpace, int groundInSpace, boolean dock) {
        int nonFighters = shipsInSpace.entrySet().stream()
                .filter(ship -> ship.getKey() != UnitType.Fighter)
                .mapToInt(ship -> Math.max(0, ship.getValue()))
                .sum();
        if (!flies(game, seat)) return nonFighters;
        int excess = Math.max(0, fighters(shipsInSpace) - Math.max(0, room(seat, shipsInSpace, groundInSpace, dock)));
        int perSlot = perFleetSlot(seat);
        return nonFighters + (excess + perSlot - 1) / perSlot;
    }

    public static boolean fitsCapacity(
            Game game, Player seat, Map<UnitType, Integer> shipsInSpace, int groundInSpace, boolean dock) {
        return flies(game, seat) || fighters(shipsInSpace) <= room(seat, shipsInSpace, groundInSpace, dock);
    }

    private static int fighters(Map<UnitType, Integer> shipsInSpace) {
        return Math.max(0, shipsInSpace.getOrDefault(UnitType.Fighter, 0));
    }
}

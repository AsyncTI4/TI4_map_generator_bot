package ti4.image.tile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;

final class UnitOrder {

    private static final List<UnitType> TYPE_ORDER = List.of(
            UnitType.Infantry,
            UnitType.Fighter,
            UnitType.Spacedock,
            UnitType.Pds,
            UnitType.Mech,
            UnitType.Monument,
            UnitType.PlenaryOrbital,
            UnitType.Flagship,
            UnitType.Dreadnought,
            UnitType.Carrier,
            UnitType.Cruiser,
            UnitType.Destroyer,
            UnitType.Warsun,
            UnitType.TyrantsLament,
            UnitType.Cavalry,
            UnitType.Lady,
            UnitType.Celagrom,
            UnitType.Aurelion);

    private UnitOrder() {}

    static List<UnitKey> sorted(Game game, UnitHolder unitHolder) {
        List<UnitKey> remaining = new ArrayList<>(unitHolder.getUnitKeys());
        List<String> playerOrder = activePlayerLast(game, unitHolder);
        List<UnitKey> sortedUnits = new ArrayList<>();
        for (UnitType type : TYPE_ORDER) {
            for (String colorID : playerOrder) {
                for (UnitKey id : remaining) {
                    if (id != null && id.unitType() == type && id.colorID().equals(colorID)) {
                        sortedUnits.add(id);
                    }
                }
            }
        }
        for (UnitKey key : sortedUnits) remaining.remove(key);
        sortedUnits.addAll(remaining);
        return sortedUnits;
    }

    private static List<String> activePlayerLast(Game game, UnitHolder unitHolder) {
        List<String> playerOrder = unitHolder.getUnitColorsOnHolder();
        if (game.getActivePlayer() == null || playerOrder.isEmpty()) {
            return playerOrder;
        }
        String activePlayerColor = null;
        if ("space".equals(unitHolder.getName())) {
            activePlayerColor = game.getActivePlayer().getColorID();
        } else {
            for (Player p : game.getPlayers().values()) {
                if (p.hasPlanet(unitHolder.getName())) activePlayerColor = p.getColorID();
            }
        }
        if (activePlayerColor != null && playerOrder.contains(activePlayerColor)) {
            while (!playerOrder.getLast().equals(activePlayerColor)) {
                Collections.rotate(playerOrder, 1);
            }
        }
        return playerOrder;
    }
}

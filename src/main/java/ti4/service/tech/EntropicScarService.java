package ti4.service.tech;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;

@UtilityClass
public class EntropicScarService {

    public static List<String> getAvailableTechnologies(Game game, Player player) {
        List<String> technologies = new ArrayList<>(player.getFactionTechs());
        if (game.isTwilightsFallMode()) {
            technologies.add("antimatter");
            technologies.add("wavelength");
        }
        technologies.remove("vax");
        technologies.remove("vay");
        player.getTechs().forEach(technologies::remove);
        return technologies;
    }

    public static boolean hasPendingTechnologyChoice(Game game, Player player) {
        boolean canPay =
                player.getStrategicCC() > 0 || player.hasRelicReady("emelpar") || player.hasRelicReady("absol_emelpar");
        return canPay && !getAvailableTechnologies(game, player).isEmpty() && hasShipsInScar(game, player);
    }

    private static boolean hasShipsInScar(Game game, Player player) {
        return game.getTileMap().values().stream().anyMatch(tile -> {
            boolean isAurelionSystem = tile.getSpaceUnitHolder().getUnitKeys().stream()
                    .anyMatch(unitKey -> unitKey.unitType() == UnitType.Aurelion);
            return (tile.isScar() || isAurelionSystem)
                    && Tile.tileHasPlayerShips(player).test(tile);
        });
    }
}

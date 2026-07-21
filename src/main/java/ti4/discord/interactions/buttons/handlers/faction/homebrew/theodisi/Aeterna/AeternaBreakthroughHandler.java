package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Aeterna;

import java.util.HashSet;
import java.util.Set;

import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
public class AeternaBreakthroughHandler {
    
    public static boolean hasTwilightDefenseSystem(Player player, Planet planet) {
        if (player == null || planet == null || !player.hasUnlockedBreakthrough("aeternabt")) {
            return false;
        }

        return planet.getUnitKeysForPlayer(player).stream()
            .map(unitKey -> unitKey.unitType())
            .anyMatch(type -> type == UnitType.Mech || type == UnitType.Spacedock);
    }

    public static boolean hasTwilightDefenseCoverage(Game game, Player player, String tilePosition) {
        if (game == null || player == null || tilePosition == null) {
            return false;
        }

        Set<String> coveredTiles = new HashSet<>(
            FoWHelper.getAdjacentTiles(game, tilePosition, player, false, true));
        coveredTiles.add(tilePosition);

        for (String position : coveredTiles) {
            Tile tile = game.getTileByPosition(position);
            if (tile == null || tile.isScar(game)) {
                continue;
            }

            for (UnitHolder holder : tile.getUnitHolders().values()) {
                if (holder instanceof Planet planet && hasTwilightDefenseSystem(player, planet)) {
                    return true;
                }
            }
        }

        return false;
    }

    public static UnitModel getTwilightDefenseCannon(Player player, Planet planet, boolean deepSpaceCannon) {
        if (!hasTwilightDefenseSystem(player, planet)) {
            return null;
        }

        UnitModel cannon = new UnitModel();
        cannon.setSpaceCannonDieCount(1);
        cannon.setSpaceCannonHitsOn(6);
        cannon.setDeepSpaceCannon(deepSpaceCannon);
        cannon.setName(Helper.getPlanetRepresentationPlusEmoji(planet.getName()) + "space cannon");
        cannon.setAsyncId(planet.getName() + "aeternabt");
        cannon.setId(planet.getName() + "aeternabt");
        cannon.setBaseType("pds");
        cannon.setFaction(player.getFaction());
        return cannon;
    }
}

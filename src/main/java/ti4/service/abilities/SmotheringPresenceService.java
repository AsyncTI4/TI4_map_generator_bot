package ti4.service.abilities;

import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.service.unit.UnitQueryService;

/** Queries the Twilight's Fall Smothering Presence rule without applying its surrounding effects. */
@UtilityClass
public class SmotheringPresenceService {

    public static boolean isSmothered(Game game, Player player, Tile tile) {
        return game.getRealPlayersExcludingThis(player).stream()
                .filter(otherPlayer -> otherPlayer.hasTech("tf-smotheringpresence"))
                .anyMatch(otherPlayer -> UnitQueryService.hasUnitsInAdjacentSystems(
                        game, otherPlayer, tile, UnitType.Pds, UnitType.Spacedock));
    }
}

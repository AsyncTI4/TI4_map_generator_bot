package ti4.service.unit;

import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;

/** @deprecated Use {@link UnitQueryService}. */
@Deprecated
@UtilityClass
public class CheckUnitContainmentService {

    public static List<Tile> getTilesContainingPlayersUnits(Game game, Player player, UnitType... unitTypes) {
        return UnitQueryService.getTilesContainingPlayersUnits(game, player, unitTypes);
    }
}

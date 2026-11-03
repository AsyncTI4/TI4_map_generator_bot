package ti4.service.actioncard;

import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
public class DisableService {

    private static final String DISABLED_INVASION = "disabledPdsInvasion";
    private static final String SEPARATOR = "_";

    public static void activate(Game game, Tile tile, Player disabler) {
        game.setStoredValue(DISABLED_INVASION, tile.getPosition() + SEPARATOR + disabler.getFaction());
    }

    public static void clear(Game game) {
        game.removeStoredValue(DISABLED_INVASION);
    }

    public static boolean hasOpposingPds(Game game, Tile tile, Player disabler) {
        return game.getRealPlayers().stream()
                .filter(other -> isOpponentOf(disabler, other))
                .anyMatch(other -> tile.getUnitHolders().values().stream()
                        .anyMatch(holder -> holder.getUnitCount(UnitType.Pds, other) > 0));
    }

    public static boolean losesPdsAbilities(Game game, @Nullable Tile tile, Player pdsOwner, @Nullable UnitModel unit) {
        if (tile == null || unit == null || unit.getUnitType() != UnitType.Pds) return false;
        String active = game.getStoredValue(DISABLED_INVASION);
        if (active.isEmpty()) return false;
        String[] positionAndFaction = active.split(SEPARATOR, 2);
        if (!positionAndFaction[0].equals(tile.getPosition())) return false;
        Player disabler = game.getPlayerFromColorOrFaction(positionAndFaction[1]);
        return disabler != null && isOpponentOf(disabler, pdsOwner);
    }

    private static boolean isOpponentOf(Player disabler, Player other) {
        return other != disabler && !disabler.getAllianceMembers().contains(other.getFaction());
    }
}

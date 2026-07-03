package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.crystellum;

import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.model.UnitModel;

@UtilityClass
public class CrystellumBreakthroughHandler {
    private static final String BT_ID = "crystellumbt";

    public static boolean canUseDefensiveArchitectureSustain(Game game, Player player, Tile tile, UnitModel unitModel) {
        if (game == null || player == null || tile == null || unitModel == null) {
            return false;
        }
        if (!unitModel.isNonFighterShip()) {
            return false;
        }
        if (unitModel.getSustainDamage()) {
            return false;
        }
        if (!player.hasUnlockedBreakthrough(BT_ID)) {
            return false;
        }
        if (!tile.getPosition().equals(game.getActiveSystem())) {
            return false;
        }
        String latestAssignHitsKey = player.getFaction() + "latestAssignHits";
        return "spacecombat".equalsIgnoreCase(game.getStoredValue(latestAssignHitsKey));
    }
}

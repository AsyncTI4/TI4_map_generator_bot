package ti4.discord.interactions.buttons.handlers.faction.homebrew.goose.jotunn;

import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollType;

@UtilityClass
public class JotunnPromissoryHandler {
    private static final String RIME_BOUND = "aopnjotunn";

    public static boolean isRimeBoundActive(Game game, Player roller, Tile tile, CombatRollType rollType) {
        return rollType == CombatRollType.combatround
                && roller == game.getActivePlayer()
                && game.getRealPlayers().stream()
                        .filter(holder -> holder.getPromissoryNotesInPlayArea().contains(RIME_BOUND))
                        .anyMatch(holder ->
                                tile.containsPlayersUnitsWithModelCondition(holder, UnitModel::getIsStructure));
    }
}

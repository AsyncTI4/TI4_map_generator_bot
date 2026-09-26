package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.dream;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;
import ti4.service.transaction.SendPromissoryService;

@UtilityClass
public class DreamPromissoryHandler {
    private static final String VISIONS_PROMISSORY = "bepndream";

    public static boolean hasVisionsInPlayArea(Player player) {
        return player != null && player.getPromissoryNotesInPlayArea().contains(VISIONS_PROMISSORY);
    }

    public static void returnVisionsOnSystemActivation(
            GenericInteractionCreateEvent event, Game game, Player activatingPlayer, Tile tile) {
        if (game == null || activatingPlayer == null || tile == null || !hasVisionsInPlayArea(activatingPlayer)) {
            return;
        }

        Player dreamPlayer = game.getPNOwner(VISIONS_PROMISSORY);
        if (dreamPlayer == null
                || dreamPlayer == activatingPlayer
                || !FoWHelper.playerHasUnitsInSystem(dreamPlayer, tile)) {
            return;
        }

        SendPromissoryService.returnPromissoryFromPlayAreaToOwner(
                event, game, activatingPlayer, dreamPlayer, VISIONS_PROMISSORY);
    }
}

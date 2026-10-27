package ti4.discord.interactions.buttons.handlers.actioncards;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;
import ti4.service.actioncard.DisableService;

@UtilityClass
class DisableButtonHandler {

    @ButtonHandler("resolveDisable")
    public static void resolveDisable(Player player, Game game, ButtonInteractionEvent event) {
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        if (tile == null || !DisableService.hasOpposingPds(game, tile, player)) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    player.getRepresentationUnfogged()
                            + ", the active system contains no PDS belonging to your opponents, so _Disable_ has nothing to disable.");
            return;
        }
        DisableService.activate(game, tile, player);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                game.isFowMode() ? player.getCorrectChannel() : event.getChannel(),
                player.getFactionEmojiOrColor() + " resolved _Disable_: the PDS in " + tile.getRepresentation()
                        + " that belong to your opponents lose PLANETARY SHIELD and SPACE CANNON during this invasion.");
    }
}

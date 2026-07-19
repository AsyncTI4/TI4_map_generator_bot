package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.RemoveCommandCounterService;

@UtilityClass
class DecisiveVictoryAcd2ButtonHandler {

    @ButtonHandler("resolveDecisiveVictory")
    public static void resolveDecisiveVictory(Player player, Game game, ButtonInteractionEvent event) {
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        if (tile == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + " could not resolve _Decisive Victory_ because there is no active system.");
            event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
            return;
        }

        RemoveCommandCounterService.fromTile(player.getColor(), tile, game);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getFactionEmojiOrColor() + " resolved _Decisive Victory_ and removed their command token from "
                        + tile.getRepresentationForButtons(game, player) + ".");
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }
}

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
import ti4.service.unit.AddUnitService;

@UtilityClass
class OpportunistsAcd2ButtonHandler {

    @ButtonHandler("resolveOpportunists")
    public static void resolveOpportunists(Player player, Game game, ButtonInteractionEvent event) {
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        if (tile == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Could not find the active system for _Opportunists_.");
            event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
            return;
        }

        AddUnitService.addUnits(event, tile, game, game.getNeutralColor(), "2 dd, cr");
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.toString() + " added 1 neutral cruiser and 2 neutral destroyers to "
                        + tile.getRepresentationForButtons(game, player) + ".");
    }
}

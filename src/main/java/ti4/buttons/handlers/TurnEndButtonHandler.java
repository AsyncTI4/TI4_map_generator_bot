package ti4.buttons.handlers;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.commands2.player.TurnEnd;
import ti4.helpers.ButtonHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.leader.CommanderUnlockCheckService;

@UtilityClass
class TurnEndButtonHandler {

    @ButtonHandler("turnEnd")
    public static void turnEnd(ButtonInteractionEvent event, Game game, Player player) {
        if (game.isFowMode() && !player.isActivePlayer()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "You are not the active player. Force End Turn with /player turn_end.");
            return;
        }
        CommanderUnlockCheckService.checkPlayer(player, "hacan");
        TurnEnd.pingNextPlayer(event, game, player);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);

        ButtonHelper.updateMap(game, event, "End of Turn " + player.getTurnCount() + ", Round " + game.getRound() + " for " + player.getFactionEmoji());
    }
}

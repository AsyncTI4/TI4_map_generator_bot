package ti4.buttons.handlers;

import org.apache.commons.lang3.function.Consumers;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ButtonHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.turn.EndTurnService;

@UtilityClass
class TurnEndButtonHandler {

    @ButtonHandler("turnEnd")
    public static void turnEnd(ButtonInteractionEvent event, Game game, Player player) {
        if (player.hasAbility("the_starlit_path") && game.getStoredValue("pathOf" + player.getFaction()).isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "You cannot end turn until you set your path with the end of turn buttons.");
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), "Use Buttons to choose your next turn's path", ButtonHelper.getPathButtons(game, player));
            return;
        }
        if (game.isFowMode() && !player.isActivePlayer()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "You are not the active player. If you need to, you can force end the current player's turn with `/player turn_end`.");
            return;
        }
        CommanderUnlockCheckService.checkPlayer(player, "hacan");
        EndTurnService.endTurnAndUpdateMap(event, game, player);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }
}

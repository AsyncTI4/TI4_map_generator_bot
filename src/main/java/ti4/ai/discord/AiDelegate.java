package ti4.ai.discord;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.ai.runtime.AiRuntime;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class AiDelegate extends GameStateSubcommand {

    AiDelegate() {
        super("delegate", "If an AI is holding up the game, ask the table to choose for it now", false, false);
        addOptions(AiCommandSupport.seatOption("Only this AI seat (default: all of them)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!AiCommandSupport.mayManage(event, getGame())) {
            MessageHelper.replyToMessage(event, AiCommandSupport.notAllowed());
            return;
        }
        List<Player> seats = AiCommandSupport.targetSeats(event, getGame());
        if (seats.isEmpty()) {
            MessageHelper.replyToMessage(event, AiCommandSupport.noSeat(event));
            return;
        }
        boolean explicit = event.getOption(Constants.FACTION_COLOR) != null;
        seats.forEach(seat -> AiRuntime.requestDelegation(getGame().getName(), seat.getUserID(), explicit));
        MessageHelper.replyToMessage(
                event,
                "The AI will act if it can. If it is stuck on something it doesn't understand, its options will be"
                        + " posted for any player to choose.");
    }
}

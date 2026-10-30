package ti4.ai.discord;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.ai.seat.AiSeatService;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Player;
import ti4.message.MessageHelper;

class AiRemove extends GameStateSubcommand {

    AiRemove() {
        super("remove", "Remove an AI player before the game starts", true, false);
        addOptions(AiCommandSupport.seatOption("The AI seat to remove (needed when there are several)"));
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
        if (seats.size() > 1) {
            MessageHelper.replyToMessage(event, "This game has several AI seats; choose one with `faction_or_color`.");
            return;
        }
        String name = seats.getFirst().getRepresentationNoPing();
        String refusal = AiSeatService.removeSeat(getGame(), seats.getFirst());
        MessageHelper.replyToMessage(event, refusal != null ? refusal : name + " was removed.");
    }
}

package ti4.ai.discord;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.ai.runtime.AiRuntime;
import ti4.ai.seat.AiSeatService;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Player;
import ti4.message.MessageHelper;

class AiResume extends GameStateSubcommand {

    AiResume() {
        super("resume", "Let paused AI players play again", true, false);
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
        seats.forEach(seat -> AiSeatService.setPaused(seat, false));
        AiRuntime.register(getGame().getName());
        MessageHelper.replyToMessage(event, AiPause.names(seats) + " playing again.");
    }
}

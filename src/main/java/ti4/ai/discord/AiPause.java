package ti4.ai.discord;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.ai.seat.AiSeatService;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Player;
import ti4.message.MessageHelper;

class AiPause extends GameStateSubcommand {

    AiPause() {
        super("pause", "Pause AI players; the table then makes their choices", true, false);
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
        seats.forEach(seat -> AiSeatService.setPaused(seat, true));
        MessageHelper.replyToMessage(
                event,
                names(seats) + " paused. When a paused AI holds up the game, any player will be asked to choose for"
                        + " it. Use `/ai resume` to let it play again.");
    }

    static String names(List<Player> seats) {
        return String.join(
                        ", ",
                        seats.stream().map(Player::getRepresentationNoPing).toList())
                + (seats.size() == 1 ? " is" : " are");
    }
}

package ti4.ai.discord;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.ai.AiSeats;
import ti4.ai.AiSettings;
import ti4.ai.profile.AiProfile;
import ti4.ai.runtime.AiRuntime;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.MessageHelper;

class AiStatus extends GameStateSubcommand {

    AiStatus() {
        super("status", "Show whether this game's AI players are running", false, false);
        addOptions(AiCommandSupport.seatOption("Only this AI seat (default: all of them)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        List<Player> seats = AiCommandSupport.targetSeats(event, game);
        if (seats.isEmpty()) {
            MessageHelper.replyToMessage(event, AiCommandSupport.noSeat(event));
            return;
        }
        StringBuilder status = new StringBuilder();
        for (Player seat : seats) {
            status.append("- ")
                    .append(seat.getRepresentationNoPing())
                    .append(" is ")
                    .append(state(game, seat))
                    .append(".\n");
        }
        if (AiSeats.isSelfPlay(game)) {
            status.append("Every seat is an AI, so they also advance the game between phases and make the choices"
                    + " they are unsure about.\n");
        }
        if (!AiRuntime.isTracked(game.getName())) status.append("They are not being watched yet.");
        MessageHelper.replyToMessage(event, status.toString().trim());
    }

    private static String state(Game game, Player seat) {
        if (!AiSettings.isEnabled()) return "disabled on this bot, so the table chooses for it";
        if (!AiSeats.isActiveAiSeat(seat)) return "not playing";
        if (AiProfile.of(seat).isPaused()) return "paused, so the table chooses for it";
        if (AiRuntime.isCoolingDown(game.getName())) {
            return "holding back for a few minutes after repeated button failures, so the table chooses for it";
        }
        return "running";
    }
}

package ti4.discord.interactions.commands.developer;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.commands.Subcommand;
import ti4.message.MessageHelper;

class QueueForGameButton extends Subcommand {

    private static final String QUEUE_BUTTON_ID = "queueForGame~MDL";
    private static final String LEAVE_QUEUE_BUTTON_ID = "leaveQueueForGame";

    QueueForGameButton() {
        super("queue_for_game_button", "Post the Queue for Game button.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        List<Button> buttons = List.of(
                Buttons.green(QUEUE_BUTTON_ID, "Queue for Game"), Buttons.red(LEAVE_QUEUE_BUTTON_ID, "Leave Queue"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                "You can use this button to queue for a game. Specify what game qualities you're looking for as well as the time frame you're willing to stay in queue.",
                buttons);
    }
}

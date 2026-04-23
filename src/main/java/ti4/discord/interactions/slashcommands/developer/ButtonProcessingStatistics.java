package ti4.discord.interactions.slashcommands.developer;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.buttons.ButtonProcessor;
import ti4.discord.interactions.slashcommands.Subcommand;
import ti4.message.MessageHelper;

class ButtonProcessingStatistics extends Subcommand {

    ButtonProcessingStatistics() {
        super("button_processing_statistics", "Get stats related to button processing.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String buttonProcessingStatistics = ButtonProcessor.getButtonProcessingStatistics();
        MessageHelper.sendMessageToChannel(event.getChannel(), buttonProcessingStatistics);
    }
}

package ti4.commands2.developer;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.Subcommand;
import ti4.processors.ButtonProcessor;

public class ButtonProcessingStatistics extends Subcommand {

     ButtonProcessingStatistics() {
        super("button_processing_statistics", "Get stats related to button processing.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        ButtonProcessor.logButtonProcessingStatistics();
    }
}

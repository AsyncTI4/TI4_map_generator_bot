package ti4.commands.developer;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Subcommand;
import ti4.message.MessageHelper;

class CustomCommand extends Subcommand {

    CustomCommand() {
        super("custom_command", "Custom command written for a custom purpose.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannel(event.getChannel(), "Running custom command.");
        customCommand();
        MessageHelper.sendMessageToChannel(event.getChannel(), "Finished custom command.");
    }

    private void customCommand() {
        // do something
    }
}

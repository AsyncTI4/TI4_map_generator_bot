package ti4.commands.developer;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Subcommand;
import ti4.message.MessageHelper;
import ti4.spring.service.persistence.DeleteAllEntitiesService;

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
        DeleteAllEntitiesService.getBean().deleteAllEntities();
    }
}

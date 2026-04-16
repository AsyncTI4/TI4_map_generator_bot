package ti4.discord.interactions.commands.admin;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.Subcommand;
import ti4.message.MessageHelper;
import ti4.spring.service.deploy.ActiveLeaseService;

class DisableBot extends Subcommand {

    DisableBot() {
        super("disable_bot", "Stop this bot process from accepting new commands.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        ActiveLeaseService.setCurrentProcessReady(false);
        MessageHelper.sendMessageToEventChannel(event, "Bot has been told to stop processing events.");
    }
}

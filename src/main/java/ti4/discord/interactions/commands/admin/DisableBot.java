package ti4.discord.interactions.commands.admin;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.Subcommand;
import ti4.message.MessageHelper;
import ti4.spring.context.SpringContext;
import ti4.spring.service.deploy.ActiveLeaseService;

class DisableBot extends Subcommand {

    DisableBot() {
        super("disable_bot", "Drain this bot process and shut it down.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        ActiveLeaseService activeLeaseService = SpringContext.getBean(ActiveLeaseService.class);
        if (!activeLeaseService.requestDrain()) {
            MessageHelper.sendMessageToEventChannel(event, "This bot process is not active or is already draining.");
            return;
        }
        MessageHelper.sendMessageToEventChannel(
                event, "Bot is draining now and will release the active lease when shutdown completes.");
    }
}

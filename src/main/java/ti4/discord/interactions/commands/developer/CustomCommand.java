package ti4.discord.interactions.commands.developer;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.Subcommand;
import ti4.message.GameMessageManager;
import ti4.message.MessageHelper;
import ti4.spring.service.deploy.ActiveLeaseService;

class CustomCommand extends Subcommand {

    CustomCommand() {
        super("custom_command", "Custom command written for a custom purpose.");
    }

    // TODO: Runs the one-time GameMessages.json -> game_message migration on 2026-10-04. Once it has run, this body
    // can be replaced with the next custom purpose.
    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!ActiveLeaseService.isCurrentProcessReady()) {
            MessageHelper.sendMessageToEventChannel(
                    event, "This instance isn't ready (starting up or draining), so the migration was not run.");
            return;
        }
        MessageHelper.sendMessageToEventChannel(
                event, "Pausing interactions while GameMessages.json is imported into the database...");
        ActiveLeaseService.setCurrentProcessReady(false);
        String result;
        try {
            result = GameMessageManager.importLegacyFile();
        } finally {
            ActiveLeaseService.setCurrentProcessReady(true);
        }
        MessageHelper.sendMessageToEventChannel(event, result + "\nInteractions resumed.");
    }
}

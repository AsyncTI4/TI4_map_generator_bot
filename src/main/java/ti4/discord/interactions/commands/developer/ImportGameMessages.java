package ti4.discord.interactions.commands.developer;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.Subcommand;
import ti4.message.GameMessageManager;
import ti4.message.MessageHelper;
import ti4.spring.service.deploy.ActiveLeaseService;

@Deprecated(forRemoval = true, since = "2026-10")
class ImportGameMessages extends Subcommand {

    ImportGameMessages() {
        super(
                "import_game_messages",
                "One-time: pause interactions and copy pm_json/GameMessages.json into the game_message table.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToEventChannel(
                event, "Pausing interactions while GameMessages.json is imported into the database...");
        boolean wasReady = ActiveLeaseService.isCurrentProcessReady();
        ActiveLeaseService.setCurrentProcessReady(false);
        String result;
        try {
            result = GameMessageManager.importLegacyFile();
        } finally {
            ActiveLeaseService.setCurrentProcessReady(wasReady);
        }
        MessageHelper.sendMessageToEventChannel(event, result + "\nInteractions resumed.");
    }
}

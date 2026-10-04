package ti4.discord.interactions.commands.developer;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.Subcommand;
import ti4.message.GameMessageManager;
import ti4.message.MessageHelper;

@Deprecated(forRemoval = true, since = "2026-10")
class ImportGameMessages extends Subcommand {

    ImportGameMessages() {
        super("import_game_messages", "One-time: copy pm_json/GameMessages.json into the game_message table.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToEventChannel(event, GameMessageManager.importLegacyFile());
    }
}

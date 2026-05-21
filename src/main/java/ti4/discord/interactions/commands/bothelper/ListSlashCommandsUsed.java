package ti4.discord.interactions.commands.bothelper;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.service.statistics.ListSlashCommandsUsedService;

class ListSlashCommandsUsed extends Subcommand {

    public ListSlashCommandsUsed() {
        super(Constants.LIST_SLASH_COMMANDS_USED, "List the frequency with which slash commands are used");
    }

    public void execute(SlashCommandInteractionEvent event) {
        ListSlashCommandsUsedService.queueReply(event);
    }
}

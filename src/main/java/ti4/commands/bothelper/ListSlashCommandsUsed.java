package ti4.commands.bothelper;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.service.statistics.ListSlashCommandsUsedService;

class ListSlashCommandsUsed extends Subcommand {

    public ListSlashCommandsUsed() {
        super(Constants.LIST_SLASH_COMMANDS_USED, "List the frequency with which slash commands are used");
        addOptions(new OptionData(
                OptionType.BOOLEAN, Constants.ONLY_LAST_MONTH, "Only include games started in last month? y/n"));
    }

    public void execute(SlashCommandInteractionEvent event) {
        ListSlashCommandsUsedService.queueReply(event);
    }
}

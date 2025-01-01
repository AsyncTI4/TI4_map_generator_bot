package ti4.commands.statistics;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.service.statistics.ListTitlesGivenService;

class ListTitlesGiven extends Subcommand {

    public ListTitlesGiven() {
        super(Constants.LIST_TITLES_GIVEN, "List the frequency with which slash commands are used");
        addOptions(new OptionData(OptionType.STRING, Constants.TITLE, "Breakdown for a specific title"));
    }

    public void execute(SlashCommandInteractionEvent event) {
        ListTitlesGivenService.queueReply(event);
    }
}

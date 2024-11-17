package ti4.commands2.search;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.Command.Option;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

public class SearchCommands extends Subcommand {

    public SearchCommands() {
        super(Constants.SEARCH_COMMANDS, "List all of the bot's commands and subcommands");
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.INCLUDE_OPTIONS, "True to include command options"));
        addOptions(new OptionData(OptionType.STRING, Constants.SEARCH, "Searches the text and limits results to those containing this string."));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean includeOptions = event.getOption(Constants.INCLUDE_OPTIONS, false, OptionMapping::getAsBoolean);
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);

        StringBuilder sb = new StringBuilder("__**Command List**__");
        List<Command> commands = event.getGuild().retrieveCommands().complete();
        int commandCount = commands.size();
        sb.append(" (").append(commandCount).append("/100)\n");

        //COMMANDS
        for (Command command : commands) {
            var subcommands = command.getSubcommands();
            int subcommandCount = subcommands.size();
            List<Option> options = command.getOptions();

            String commandText = "`/" + command.getFullCommandName() + "` : **" + command.getDescription() + "**";
            if (searchString == null || subcommands.stream().anyMatch(s -> s.getFullCommandName().toLowerCase().contains(searchString) || s.getDescription().toLowerCase().contains(searchString))) sb.append(commandText).append(" (").append(subcommandCount).append("/25)\n");
            commandCount++;

            //COMMAND OPTIONS
            if (includeOptions) {
                for (Option option : options) {
                    String optionText = "> `     " + option.getName() + "` : " + option.getDescription();
                    if (searchString == null || optionText.toLowerCase().contains(searchString.toLowerCase())) sb.append(optionText).append("\n");
                }
            }

            //SUBCOMMANDS
            for (var subcommand : subcommands) {
                String subcommandText = "> `/" + subcommand.getFullCommandName() + "` : " + subcommand.getDescription();
                if (searchString == null || subcommandText.toLowerCase().contains(searchString.toLowerCase())) sb.append(subcommandText).append("\n");
                subcommandCount++;

                //SUBCOMMAND OPTIONS
                if (includeOptions) {
                    List<Option> suboptions = subcommand.getOptions();
                    for (Option option : suboptions) {
                        String optionText = "> `     " + option.getName() + "` : " + option.getDescription();
                        if (searchString == null || optionText.toLowerCase().contains(searchString.toLowerCase())) sb.append(optionText).append("\n");
                    }
                }
            }
        }

        String searchDescription = searchString == null ? "" : " search: " + searchString;
        String threadName = event.getFullCommandName() + searchDescription;
        MessageHelper.sendMessageToThread(event.getChannel(), threadName, sb.toString());
    }
}

package ti4.commands.search;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.Command.Option;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class SearchCommandsSubcommand extends Subcommand {

    public SearchCommandsSubcommand() {
        super(Constants.SEARCH_COMMANDS, "List all of the bot's commands and subcommands");
        addOptions(new OptionData(OptionType.STRING, Constants.SEARCH, "Searches the text and limits results to those containing this string."));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.INCLUDE_OPTIONS, "True to include command options"));
        addOptions(new OptionData(OptionType.BOOLEAN, "show_counts", "True to show some debug count info"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean includeOptions = event.getOption(Constants.INCLUDE_OPTIONS, false, OptionMapping::getAsBoolean);
        String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);
        boolean showCounts = event.getOption("show_counts", false, OptionMapping::getAsBoolean);

        StringBuilder sb = new StringBuilder("# __**Command List**__");
        List<Command> commands = event.getGuild().retrieveCommands().complete();
        int commandCount = commands.size();
        if (showCounts) sb.append(" (").append(commandCount).append("/100)");
        sb.append("\n");

        //COMMANDS
        for (Command command : commands) {
            var subcommands = command.getSubcommands();
            int subcommandCount = subcommands.size();
            List<Option> options = command.getOptions();

            StringBuilder commandSB = new StringBuilder();

            String commandText = "## `/" + command.getFullCommandName() + "` : **" + command.getDescription() + "**";
            commandSB.append(commandText);
            if (showCounts) commandSB.append(" (").append(subcommandCount).append("/25)");
            commandSB.append("\n");

            //COMMAND OPTIONS
            if (includeOptions) {
                for (Option option : options) {
                    String optionText = "> `     " + option.getName() + "` : " + option.getDescription();
                    if (searchString == null || optionText.toLowerCase().contains(searchString.toLowerCase())) {
                        commandSB.append(optionText).append("\n");
                    }
                }
            }

            //SUBCOMMANDS
            for (var subcommand : subcommands) {
                String subcommandText = "> `/" + subcommand.getFullCommandName() + "` : " + subcommand.getDescription();
                List<Option> suboptions = subcommand.getOptions();

                //SUBCOMMAND OPTIONS
                StringBuilder suboptionSB = new StringBuilder();
                if (includeOptions) {
                    for (Option option : suboptions) {
                        String optionText = "> `     " + option.getName() + "` : " + option.getDescription();
                        if (searchString == null || optionText.toLowerCase().contains(searchString.toLowerCase())) {
                            suboptionSB.append(optionText).append("\n");
                        }
                    }
                }

                boolean foundMatchingOptions = !suboptionSB.isEmpty();
                if (searchString == null || subcommandText.toLowerCase().contains(searchString.toLowerCase()) || foundMatchingOptions) {
                    commandSB.append(subcommandText).append("\n");
                    commandSB.append(suboptionSB);
                }
            }

            if (searchString == null || commandSB.toString().toLowerCase().contains(searchString.toLowerCase())) {
                sb.append(commandSB);
            }
        }

        if (sb.length() < 22) {
            sb.append("> No commands found by search string: ").append(searchString);
        }

        String searchDescription = searchString == null ? "" : " search: " + searchString;
        String threadName = event.getFullCommandName() + searchDescription;
        MessageHelper.sendMessageToThread(event.getChannel(), threadName, sb.toString());
    }
}

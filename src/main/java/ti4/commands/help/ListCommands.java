package ti4.commands.help;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.Command.Option;
import net.dv8tion.jda.api.interactions.commands.Command.Subcommand;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

public class ListCommands extends HelpSubcommandData {
    public ListCommands() {
        super(Constants.LIST_COMMANDS, "List all of the bot's commands and subcommands");
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.INCLUDE_SUBCOMMANDS, "True to include subcommands"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.INCLUDE_OPTIONS, "True to include command options"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean includeSubcommands = event.getOption(Constants.INCLUDE_SUBCOMMANDS, false, OptionMapping::getAsBoolean);
        boolean includeOptions = event.getOption(Constants.INCLUDE_OPTIONS, false, OptionMapping::getAsBoolean);

        StringBuilder sb = new StringBuilder("__**Command List**__");
        List<Command> commands = event.getGuild().retrieveCommands().complete();
        int commandCount = commands.size();
        sb.append(" (" + commandCount + "/50)\n");

        //COMMANDS
        for (Command command : commands) {
            List<Subcommand> subcommands = command.getSubcommands();
            int subcommandCount = subcommands.size();
            List<Option> options = command.getOptions();
            int optionCount = options.size();

            sb.append("`/" + command.getFullCommandName() + "` : **" + command.getDescription() + "** (").append(subcommandCount).append("/25)\n");
            commandCount++;

            //COMMAND OPTIONS
            if (includeOptions) {
                for (Option option : options) {
                    sb.append("> `     ").append(option.getName()).append("` : " + option.getDescription()).append("\n");
                }
            }

            //SUBCOMMANDS
            if (includeSubcommands) {
                for (Subcommand subcommand : subcommands) {
                    sb.append("> `/" + subcommand.getFullCommandName() + "` : " + subcommand.getDescription()).append("\n");
                    subcommandCount++;

                    //SUBCOMMAND OPTIONS
                    if (includeOptions) {
                        List<Option> suboptions = subcommand.getOptions();
                        for (Option option : suboptions) {
                            sb.append("> `     ").append(option.getName()).append("` : " + option.getDescription()).append("\n");
                        }
                    }
                }
            }
        }
        event.getChannel().sendMessage("__**Command List**__").queue(m -> m.createThreadChannel("Command List").queue(t -> MessageHelper.sendMessageToChannel(t, sb.toString())));
    }
}

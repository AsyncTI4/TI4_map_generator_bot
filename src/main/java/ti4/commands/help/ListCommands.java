package ti4.commands.help;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.Command.Subcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

public class ListCommands extends HelpSubcommandData {
    public ListCommands() {
        super(Constants.LIST_COMMANDS, "List all of the bot's commands and subcommands");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        StringBuilder sb = new StringBuilder("__**Command List**__\n");

        List<Command> commands = event.getGuild().retrieveCommands().complete();
        for (Command command : commands) {
            sb.append("`/" + command.getFullCommandName() + "` : **" + command.getDescription() + "**\n");
            List<Subcommand> subcommands = command.getSubcommands();
            for (Subcommand subcommand : subcommands) {
                sb.append("> `/" + subcommand.getFullCommandName() + "` : " + subcommand.getDescription()).append("\n");
            }
        }
        event.getChannel().sendMessage("__**Command List**__").queue(m -> m.createThreadChannel("Command List").queue(t -> MessageHelper.sendMessageToChannel(t, sb.toString())));
    }
}

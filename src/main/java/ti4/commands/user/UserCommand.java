package ti4.commands.user;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.helpers.Constants;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

public class UserCommand implements Command {

    private final Collection<UserSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getActionID() {
        return Constants.USER;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommandName = event.getInteraction().getSubcommandName();
        for (UserSubcommandData subcommand : subcommandData) {
            if (Objects.equals(subcommand.getName(), subcommandName)) {
                subcommand.preExecute(event);
                subcommand.execute(event);
                subcommand.postExecute(event);
                break;
            }
        }
    }

    protected String getActionDescription() {
        return "User";
    }

    private Collection<UserSubcommandData> getSubcommands() {
        Collection<UserSubcommandData> subcommands = new HashSet<>();
        subcommands.add(new ShowUserSettings());
        subcommands.add(new SetPreferredColourList());
        subcommands.add(new SetPersonalPingInterval());
        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(Commands.slash(getActionID(), getActionDescription()).addSubcommands(getSubcommands()));
    }
}

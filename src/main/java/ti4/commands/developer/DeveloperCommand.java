package ti4.commands.developer;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.AsyncTI4DiscordBot;
import ti4.commands.Command;
import ti4.helpers.Constants;
import ti4.helpers.SlashCommandAcceptanceHelper;

public class DeveloperCommand implements Command {

    private final Collection<DeveloperSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getActionID() {
        return Constants.DEVELOPER;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return SlashCommandAcceptanceHelper.shouldAcceptIfHasRole(getActionID(), event, AsyncTI4DiscordBot.developerRoles);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommandName = event.getInteraction().getSubcommandName();
        for (DeveloperSubcommandData subcommand : subcommandData) {
            if (Objects.equals(subcommand.getName(), subcommandName)) {
                subcommand.preExecute(event);
                subcommand.execute(event);
                break;
            }
        }
    }

    protected String getActionDescription() {
        return "Developer";
    }

    private Collection<DeveloperSubcommandData> getSubcommands() {
        Collection<DeveloperSubcommandData> subcommands = new HashSet<>();
        subcommands.add(new SetGlobalSetting());
        subcommands.add(new RunManualDataMigration());
        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(
            Commands.slash(getActionID(), getActionDescription())
                .addSubcommands(getSubcommands()));
    }
}

package ti4.commands.bothelper;

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

public class BothelperCommand implements Command {

    private final Collection<BothelperSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getActionId() {
        return Constants.BOTHELPER;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return SlashCommandAcceptanceHelper.shouldAcceptIfHasRole(getActionId(), event, AsyncTI4DiscordBot.bothelperRoles);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        //BothelperSubcommandData subCommandExecuted;
        String subcommandName = event.getInteraction().getSubcommandName();
        for (BothelperSubcommandData subcommand : subcommandData) {
            if (Objects.equals(subcommand.getName(), subcommandName)) {
                subcommand.preExecute(event);
                subcommand.execute(event);
                break;
            }
        }
    }

    protected String getActionDescription() {
        return "Bothelper";
    }

    private Collection<BothelperSubcommandData> getSubcommands() {
        Collection<BothelperSubcommandData> subcommands = new HashSet<>();
        // subcommands.add(new ImportTTPG());
        subcommands.add(new CreateGameChannels());
        subcommands.add(new ControlGameCreation());
        subcommands.add(new CreateFOWGameChannels());
        subcommands.add(new ServerLimitStats());
        // subcommands.add(new ListOldChannels());
        //subcommands.add(new ListOldThreads());
        subcommands.add(new ArchiveOldThreads());
        subcommands.add(new FixGameChannelPermissions());
        subcommands.add(new ListCategoryChannelCounts());
        subcommands.add(new BeginVideoGeneration());
        // subcommands.add(new CreatePlanet());
        // subcommands.add(new CreateTile());
        // subcommands.add(new ReExportAllTiles());
        subcommands.add(new JazzCommand());
        subcommands.add(new ListButtons());
        subcommands.add(new ListSlashCommandsUsed());
        subcommands.add(new ServerGameStats());
        subcommands.add(new ListDeadGames());
        subcommands.add(new RemoveTitle());
        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(
            Commands.slash(getActionId(), getActionDescription())
                .addSubcommands(getSubcommands()));
    }
}

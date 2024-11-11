package ti4.commands.bothelper;

import java.util.Collection;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.AsyncTI4DiscordBot;
import ti4.commands.Command;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.helpers.SlashCommandAcceptanceHelper;

public class BothelperCommand implements Command {

    private final Collection<Subcommand> subcommands = List.of(
            new CreateGameChannels(),
            new ControlGameCreation(),
            new CreateFOWGameChannels(),
            new ServerLimitStats(),
            new ArchiveOldThreads(),
            new FixGameChannelPermissions(),
            new ListCategoryChannelCounts(),
            new BeginVideoGeneration(),
            new JazzCommand(),
            new ListButtons(),
            new ServerGameStats(),
            new ListDeadGames(),
            new RemoveTitle());


    @Override
    public String getActionId() {
        return Constants.BOTHELPER;
    }

    @Override
    public String getActionDescription() {
        return "BotHelper";
    }

    @Override
    public Collection<Subcommand> getSubcommands() {
        return subcommands;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return Command.super.accept(event) &&
                SlashCommandAcceptanceHelper.acceptIfHasRoles(event, AsyncTI4DiscordBot.bothelperRoles);
    }
}

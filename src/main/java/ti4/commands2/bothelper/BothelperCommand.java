package ti4.commands2.bothelper;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.AsyncTI4DiscordBot;
import ti4.commands2.CommandHelper;
import ti4.commands2.ParentCommand;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;

public class BothelperCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
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
                    new ListSlashCommandsUsed(),
                    new RemoveTitle())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return ParentCommand.super.accept(event) &&
            CommandHelper.acceptIfHasRoles(event, AsyncTI4DiscordBot.bothelperRoles);
    }

    @Override
    public String getName() {
        return Constants.BOTHELPER;
    }

    @Override
    public String getDescription() {
        return "BotHelper";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}

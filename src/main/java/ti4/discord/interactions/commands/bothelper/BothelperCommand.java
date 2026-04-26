package ti4.discord.interactions.commands.bothelper;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.JdaService;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.ParentCommand;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;

public class BothelperCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
                    new CreateGameChannels(),
                    new CreateFOWGameChannels(),
                    new ServerLimitStats(),
                    new ArchiveOldThreads(),
                    new FixGameChannelPermissions(),
                    new ListCategoryChannelCounts(),
                    new BeginVideoGeneration(),
                    new JazzCommand(),
                    new ListButtons(),
                    new ReloadGame(),
                    new ServerGameStats(),
                    new CorrectFaction(),
                    new ListDeadGames(),
                    new RemoveTitle(),
                    new EditTrackRecord(),
                    new SetGameLimit(),
                    new SetStatsTrackedUser(),
                    new ListStoredValues(),
                    new EditStoredValue(),
                    new ListSlashCommandsUsed(),
                    new ReserveGame(),
                    new DeleteFOWCommThreads())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return ParentCommand.super.accept(event) && CommandHelper.acceptIfHasRoles(event, JdaService.bothelperRoles);
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

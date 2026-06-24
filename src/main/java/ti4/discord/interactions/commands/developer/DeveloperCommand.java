package ti4.discord.interactions.commands.developer;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.JdaService;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.ParentCommand;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;

public class DeveloperCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
                    new SetGlobalSetting(),
                    new RunManualDataMigration(),
                    new GiveTheBotABreather(),
                    new ButtonProcessingStatistics(),
                    new CacheStatistics(),
                    new RestoreGame(),
                    new ReloadCorruptedSaves(),
                    new RunCron(),
                    new RunAgainstAllGames(),
                    new CustomCommand(),
                    new RunAgainstSpecificGame(),
                    new ProduceNucleusGenStats(),
                    new AnnounceActiveGames(),
                    new DatabasePersistence(),
                    new DeleteUserMessages(),
                    new PostMatchmakingButtons(),
                    new RunSql())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return Constants.DEVELOPER;
    }

    public String getDescription() {
        return "Developer";
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return ParentCommand.super.accept(event) && CommandHelper.acceptIfHasRoles(event, JdaService.developerRoles);
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}

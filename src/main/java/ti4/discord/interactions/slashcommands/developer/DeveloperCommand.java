package ti4.discord.interactions.slashcommands.developer;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.JdaService;
import ti4.discord.interactions.slashcommands.CommandHelper;
import ti4.discord.interactions.slashcommands.ParentCommand;
import ti4.discord.interactions.slashcommands.Subcommand;
import ti4.helpers.Constants;

public class DeveloperCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
                    new SetGlobalSetting(),
                    new RunManualDataMigration(),
                    new GiveTheBotABreather(),
                    new ButtonProcessingStatistics(),
                    new CacheStatistics(),
                    new RestoreGame(),
                    new RunCron(),
                    new RunAgainstAllGames(),
                    new CustomCommand(),
                    new RunAgainstSpecificGame(),
                    new ProduceNucleusGenStats(),
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

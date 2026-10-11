package ti4.discord.interactions.commands.developer;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.JdaService;
import ti4.discord.interactions.commands.AbstractParentCommand;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.helpers.Constants;

public class DeveloperCommand extends AbstractParentCommand {

    public DeveloperCommand() {
        super(
                Constants.DEVELOPER,
                "Developer",
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
                new ModifyMatchmakingQueue(),
                new ShowMatchmakingRatingHistory(),
                new RunSql());
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return super.accept(event) && CommandHelper.acceptIfHasRoles(event, JdaService.developerRoles);
    }
}

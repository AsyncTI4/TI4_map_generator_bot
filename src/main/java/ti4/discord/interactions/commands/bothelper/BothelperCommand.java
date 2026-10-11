package ti4.discord.interactions.commands.bothelper;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.JdaService;
import ti4.discord.interactions.commands.AbstractParentCommand;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.helpers.Constants;

public class BothelperCommand extends AbstractParentCommand {

    public BothelperCommand() {
        super(
                Constants.BOTHELPER,
                "BotHelper",
                new CreateGameChannels(),
                new CreateFOWGameChannels(),
                new ServerLimitStats(),
                new ArchiveOldThreads(),
                new FixGameChannelPermissions(),
                new ListCategoryChannelCounts(),
                new BeginVideoGeneration(),
                new JazzCommand(),
                new ListButtons(),
                new ImpersonateBot(),
                new ReloadGame(),
                new ServerGameStats(),
                // new CorrectFaction(),
                new ListDeadGames(),
                new RemoveTitle(),
                new EditTrackRecord(),
                new SetGameLimit(),
                new SetStatsTrackedUser(),
                new ListStoredValues(),
                new EditStoredValue(),
                new ListSlashCommandsUsed(),
                new ReserveGame(),
                new DeleteFOWCommThreads(),
                new KeepThreadAlive(),
                new ManageRoles());
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return super.accept(event) && CommandHelper.acceptIfHasRoles(event, JdaService.bothelperRoles);
    }
}

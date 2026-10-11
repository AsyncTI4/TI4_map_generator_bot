package ti4.discord.interactions.commands.statistics;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.AbstractParentCommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.persistence.DatabasePersistenceGate;

public class StatisticsCommand extends AbstractParentCommand {

    public StatisticsCommand() {
        super(
                Constants.STATISTICS,
                "Statistics",
                new OptIn(),
                new OptOut(),
                new SurveyResults(),
                new CommunityStatistics(),
                new GameStatistics(),
                new PlayerStatistics(),
                TurnTimeStatistic.average(),
                TurnTimeStatistic.median(),
                new CompareActivityTimes(),
                new DiceLuck(),
                new HitsPerTurn(),
                new MatchmakingRatingCommand(),
                new LifetimeRecord(),
                new GameWinsWithOtherFactions(),
                new ListTitlesGiven(),
                new ExportToCSV(),
                new PoliticsPosition());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (DatabasePersistenceGate.isDisabled()) {
            MessageHelper.sendMessageToEventChannel(
                    event, "Statistics are temporarily unavailable while database maintenance is in progress.");
            return;
        }
        super.execute(event);
    }
}

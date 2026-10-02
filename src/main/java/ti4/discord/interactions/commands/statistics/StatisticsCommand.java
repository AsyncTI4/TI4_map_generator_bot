package ti4.discord.interactions.commands.statistics;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.ParentCommand;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.persistence.DatabasePersistenceGate;

public class StatisticsCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
                    new OptIn(),
                    new OptOut(),
                    new SurveyResults(),
                    new CommunityStatistics(),
                    new GameStatistics(),
                    new PlayerStatistics(),
                    new AverageTurnTime(),
                    new MedianTurnTime(),
                    new CompareActivityTimes(),
                    new DiceLuck(),
                    new HitsPerTurn(),
                    new MatchmakingRatingCommand(),
                    new LifetimeRecord(),
                    new GameWinsWithOtherFactions(),
                    new ListTitlesGiven(),
                    new ExportToCSV(),
                    new PoliticsPosition())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return Constants.STATISTICS;
    }

    public String getDescription() {
        return "Statistics";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (DatabasePersistenceGate.isDisabled()) {
            MessageHelper.sendMessageToEventChannel(
                    event, "Statistics are temporarily unavailable while database maintenance is in progress.");
            return;
        }
        ParentCommand.super.execute(event);
    }
}

package ti4.discord.interactions.slashcommands.statistics;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ti4.discord.interactions.slashcommands.ParentCommand;
import ti4.discord.interactions.slashcommands.Subcommand;
import ti4.helpers.Constants;

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
                    new CompareAFKTimes(),
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
}

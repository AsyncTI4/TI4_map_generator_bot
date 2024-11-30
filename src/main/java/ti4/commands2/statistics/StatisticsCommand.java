package ti4.commands2.statistics;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ti4.commands2.ParentCommand;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;

public class StatisticsCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
        new GameStatistics(),
        new PlayerStatistics(),
        new AverageTurnTime(),
        new MedianTurnTime(),
        //new CompareAFKTimes(),
        new DiceLuck(),
        new LifetimeRecord(),
        new FactionRecordOfTech(),
        new FactionRecordOfSCPick(),
        new GameWinsWithOtherFactions(),
        new StellarConverterStatistics(),
        new ListTitlesGiven(),
        new ExportToCSV()
    ).collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));


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

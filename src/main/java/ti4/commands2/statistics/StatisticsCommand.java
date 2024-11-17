package ti4.commands2.statistics;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.CommandHelper;
import ti4.commands2.ParentCommand;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;

public class StatisticsCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
        new GameStats(),
        new PlayerStats(),
        new AverageTurnTime(),
        new MedianTurnTime(),
        new CompareAFKTimes(),
        new DiceLuck(),
        new LifetimeRecord(),
        new FactionRecordOfTech(),
        new FactionRecordOfSCPick(),
        new GameWinsWithOtherFactions(),
        new StellarConverter(),
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
    public boolean accept(SlashCommandInteractionEvent event) {
        return ParentCommand.super.accept(event) &&
            CommandHelper.acceptIfPlayerInGameAndGameChannel(event);
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}

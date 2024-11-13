package ti4.commands.statistics;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.CommandHelper;
import ti4.commands.ParentCommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;

public class StatisticsCommand implements ParentCommand {

    private final Collection<StatisticsSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getName() {
        return Constants.STATISTICS;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommandName = event.getInteraction().getSubcommandName();
        StatisticsSubcommandData executedCommand = null;
        for (StatisticsSubcommandData subcommand : subcommandData) {
            if (Objects.equals(subcommand.getName(), subcommandName)) {
                subcommand.preExecute(event);
                subcommand.execute(event);
                executedCommand = subcommand;
                break;
            }
        }
        if (executedCommand == null) {
            reply(event);
        } else {
            executedCommand.reply(event);
        }
    }

    public static void reply(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        Game game = CommandHelper.getGameName(event);
        GameSaveLoadManager.saveGame(game, event);
    }

    public String getDescription() {
        return "Statistics";
    }

    private Collection<StatisticsSubcommandData> getSubcommands() {
        Collection<StatisticsSubcommandData> subcommands = new HashSet<>();
        subcommands.add(new GameStats());
        subcommands.add(new PlayerStats());
        subcommands.add(new AverageTurnTime());
        subcommands.add(new MedianTurnTime());
        subcommands.add(new CompareAFKTimes());
        subcommands.add(new DiceLuck());
        subcommands.add(new LifetimeRecord());
        subcommands.add(new FactionRecordOfTech());
        subcommands.add(new FactionRecordOfSCPick());
        subcommands.add(new GameWinsWithOtherFactions());
        subcommands.add(new StellarConverter());
        subcommands.add(new ListTitlesGiven());
        subcommands.add(new ExportToCSV());

        return subcommands;
    }

    @Override
    public void register(CommandListUpdateAction commands) {
        commands.addCommands(
            Commands.slash(getName(), getDescription())
                .addSubcommands(getSubcommands()));
    }
}

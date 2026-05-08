package ti4.discord.interactions.commands.statistics;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.ParentCommand;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.persistence.SqlitePersistenceGate;

public class StatisticsCommand2 implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
                    new TwilightsFallSpliceWinRates(),
                    new ExpeditionWinRates(),
                    new StellarConverterStatistics(),
                    new FactionRecordOfTech(),
                    new FactionRecordOfSCPick(),
                    new FactionTopColors(),
                    new FactionGames())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return Constants.STATISTICS + 2;
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
        if (SqlitePersistenceGate.isDisabled()) {
            MessageHelper.sendMessageToEventChannel(
                    event, "Statistics are unavailable while SQLite-backed auxiliary persistence is off.");
            return;
        }
        ParentCommand.super.execute(event);
    }
}

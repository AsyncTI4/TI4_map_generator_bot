package ti4.discord.interactions.commands.statistics;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.AbstractParentCommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.persistence.DatabasePersistenceGate;

public class StatisticsCommand2 extends AbstractParentCommand {

    public StatisticsCommand2() {
        super(
                Constants.STATISTICS + 2,
                "Statistics",
                new ActionCardStats(),
                new TwilightsFallSpliceWinRates(),
                new ExpeditionWinRates(),
                new SliceTileWinRates(),
                new PlanetWinRates(),
                new SupportWinRates(),
                new StellarConverterStatistics(),
                new FactionRecordOfTech(),
                new FactionRecordOfSCPick(),
                new FactionTopColors(),
                new FactionGames());
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

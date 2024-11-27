package ti4.commands2.statistics;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;
import ti4.service.statistics.StellarConverterStatisticsService;

class StellarConverterStatistics extends Subcommand {

    public StellarConverterStatistics() {
        super(Constants.STELLAR_CONVERTER, "Number of times each planet has been converted");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        StellarConverterStatisticsService.queueReply(event);
    }
}

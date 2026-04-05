package ti4.commands.statistics;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;

class ExpeditionWinRates extends Subcommand {

    ExpeditionWinRates() {
        super(Constants.EXPEDITION_WIN_RATES, "Thunder's Edge expedition win rates");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        ExpeditionWinRateStatisticsService.queueReply(event);
    }
}

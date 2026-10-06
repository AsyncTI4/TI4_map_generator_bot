package ti4.discord.interactions.commands.statistics;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.service.statistics.SupportWinRateStatisticsService;

class SupportWinRates extends Subcommand {

    SupportWinRates() {
        super(Constants.SUPPORT_WIN_RATES, "Win rates and swap rates for Support for the Throne");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        SupportWinRateStatisticsService.queueReply(event);
    }
}

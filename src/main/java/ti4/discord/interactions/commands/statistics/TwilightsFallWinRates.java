package ti4.discord.interactions.commands.statistics;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;

class TwilightsFallWinRates extends Subcommand {

    TwilightsFallWinRates() {
        super(Constants.TWILIGHTS_FALL_WIN_RATES, "Twilight's Fall ability, unit upgrade, and genome win rates");
        addOptions(GameStatisticsFilterer.gameStatsFilters());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        TwilightsFallWinRateStatisticsService.queueReply(event);
    }
}

package ti4.discord.interactions.commands.statistics;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.service.statistics.PlanetWinRateStatisticsService;

class PlanetWinRates extends Subcommand {

    PlanetWinRates() {
        super(Constants.PLANET_WIN_RATES, "Win rates by the planets a player controls at the end of the game");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        PlanetWinRateStatisticsService.queueReply(event);
    }
}

package ti4.discord.interactions.commands.statistics;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.service.statistics.PlanetWinRateStatisticsService;

class PlanetWinRates extends Subcommand {

    PlanetWinRates() {
        super(Constants.PLANET_WIN_RATES, "Win rates by the planets a player controls at the end of the game");
        addOptions(new OptionData(
                OptionType.BOOLEAN,
                PlanetWinRateStatisticsService.POK_ONLY_OPTION,
                "'true' for Prophecy of Kings games only, dropping Thunder's Edge (default: both)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        PlanetWinRateStatisticsService.queueReply(event);
    }
}

package ti4.discord.interactions.commands.statistics;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.service.statistics.SliceTileWinRateStatisticsService;

class SliceTileWinRates extends Subcommand {

    SliceTileWinRates() {
        super(Constants.SLICE_TILE_WIN_RATES, "Win rates by system tile in a player's slice");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        SliceTileWinRateStatisticsService.queueReply(event);
    }
}

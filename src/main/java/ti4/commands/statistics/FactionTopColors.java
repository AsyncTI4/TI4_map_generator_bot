package ti4.commands.statistics;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.service.statistics.FactionTopColorsStatisticsService;

class FactionTopColors extends Subcommand {

    public FactionTopColors() {
        super(Constants.FACTION_TOP_COLORS, "Top 8 colors used by each faction");
        addOptions(GameStatisticsFilterer.gameStatsFilters());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        FactionTopColorsStatisticsService.queueReply(event);
    }
}

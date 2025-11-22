package ti4.commands.statistics;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.service.statistics.game.ThundersEdgeBreakthroughStatisticsService;

class ThundersEdgeBreakthroughs extends Subcommand {

    public ThundersEdgeBreakthroughs() {
        super(Constants.STATISTICS_THUNDERS_EDGE_BREAKTHROUGHS, "Thunder's Edge Breakthrough statistics");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        ThundersEdgeBreakthroughStatisticsService.queueReply(event);
    }
}

package ti4.commands.statistics;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.service.statistics.game.CommunityStatisticsService;

class CommunityStatistics extends Subcommand {

    public CommunityStatistics() {
        super(Constants.COMMUNITY, "Community Statistics");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        CommunityStatisticsService.queueReply(event);
    }
}

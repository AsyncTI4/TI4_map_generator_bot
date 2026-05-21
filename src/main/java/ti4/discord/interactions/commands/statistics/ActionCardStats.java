package ti4.discord.interactions.commands.statistics;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.service.statistics.ActionCardStatsService;

class ActionCardStats extends Subcommand {

    ActionCardStats() {
        super(Constants.ACTION_CARD_STATS, "Action card play statistics");
        addOptions(GameStatisticsFilterer.gameStatsFilters());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        ActionCardStatsService.queueReply(event);
    }
}

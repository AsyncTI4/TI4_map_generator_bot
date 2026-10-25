package ti4.discord.interactions.commands.statistics;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.service.statistics.ScoringStatisticsService;

class ScoringStats extends Subcommand {

    ScoringStats() {
        super(Constants.SCORING, "The most common winning paths and how often winners scored each type of point");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        ScoringStatisticsService.queueReply(event);
    }
}

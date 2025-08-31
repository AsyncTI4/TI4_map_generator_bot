package ti4.commands.statistics;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Subcommand;
import ti4.service.statistics.matchmaking.MatchmakingRatingEventService;

class MatchmakingRatingCommand extends Subcommand {

    MatchmakingRatingCommand() {
        super("matchmaking_rating", "Calculates the top 50 high confidence MMRs using the TrueSkill algorithm");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MatchmakingRatingEventService.queueReply(event);
    }
}

package ti4.commands.statistics;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.Subcommand;
import ti4.service.statistics.matchmaking.MatchmakingRatingEventService;

class MatchmakingRatingCommand extends Subcommand {

    MatchmakingRatingCommand() {
        super("matchmaking_rating", "Calculates the top 50 high confidence MMRs using the TrueSkill algorithm");
        addOptions(
                new OptionData(OptionType.BOOLEAN, "tigl_only", "True to only include TIGL games"),
                new OptionData(
                        OptionType.BOOLEAN,
                        "show_my_rating",
                        "If true, shows your rating instead of calibration percent when fully calibrated"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MatchmakingRatingEventService.queueReply(event);
    }
}

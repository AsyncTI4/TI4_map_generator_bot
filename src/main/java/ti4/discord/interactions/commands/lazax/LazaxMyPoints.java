package ti4.discord.interactions.commands.lazax;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.contest.replay.service.CombatReplayLeaderboardService;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.spring.context.SpringContext;

class LazaxMyPoints extends Subcommand {

    LazaxMyPoints() {
        super(Constants.LAZAX_MY_POINTS, "Show your Lazax War Archives leaderboard points.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String message = SpringContext.getBean(CombatReplayLeaderboardService.class)
                .buildUserPointsMessage(event.getUser().getId());
        LazaxReplyHelper.replyEphemeral(event, message);
    }
}

package ti4.discord.interactions.commands.lazax;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.contest.replay.service.CombatReplayLeaderboardService;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.spring.context.SpringContext;

class LazaxDelegationLeaderboard extends Subcommand {

    LazaxDelegationLeaderboard() {
        super(Constants.LAZAX_DELEGATION_LEADERBOARD, "Repost the public Lazax delegation leaderboard.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!LazaxCommandAuthorization.isSeasonAdmin(event)) {
            LazaxReplyHelper.replyEphemeral(event, "You are not authorized to repost the delegation leaderboard.");
            return;
        }

        boolean posted =
                SpringContext.getBean(CombatReplayLeaderboardService.class).postDelegationLeaderboard();
        LazaxReplyHelper.replyEphemeral(
                event,
                posted
                        ? "Reposted the Lazax delegation leaderboard."
                        : "Could not repost the Lazax delegation leaderboard.");
    }

    @Override
    public boolean isEphemeral(SlashCommandInteractionEvent event) {
        return true;
    }
}

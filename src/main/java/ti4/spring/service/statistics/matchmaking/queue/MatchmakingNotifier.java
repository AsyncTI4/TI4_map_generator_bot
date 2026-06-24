package ti4.spring.service.statistics.matchmaking.queue;

import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import ti4.discord.JdaService;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.game.CreateGameLaunchPostService;
import ti4.service.game.CreateGameService;

@UtilityClass
class MatchmakingNotifier {

    static void notifyPartyRemoved(List<MatchmakingQueueMember> partyMembers, String leaverId) {
        if (partyMembers.size() <= 1) return;
        String message = "Your matchmaking group was removed from the queue because <@" + leaverId + "> left it.";
        for (MatchmakingQueueMember member : partyMembers) {
            if (member.getUserId().equals(leaverId)) continue;
            User user = JdaService.jda.getUserById(member.getUserId());
            if (user == null) continue;
            MessageHelper.sendMessageToUser(message, user);
        }
    }

    static void notifyExpired(List<QueuedParty> expired) {
        if (expired.isEmpty()) return;
        BotLogger.info("Expiring " + expired.size() + " matchmaking parties.");
        String expiryMessage = "The matchmaking service wasn't able to find you a game in the time frame you selected. "
                + "Queue again when ready and consider being open to additional game types or longer wait times.";
        for (QueuedParty party : expired) {
            for (MatchmakingQueueMember member : party.members()) {
                User user = JdaService.jda.getUserById(member.getUserId());
                if (user == null) continue;
                MessageHelper.sendMessageToUser(expiryMessage, user);
            }
        }
    }

    static void postMatchedGames(List<MatchedGame> gamesToCreate) {
        Guild guild = JdaService.guildPrimary;
        if (gamesToCreate.isEmpty() || guild == null) return;

        List<ForumChannel> forums =
                guild.getForumChannelsByName(CreateGameLaunchPostService.MAKING_NEW_GAMES_CHANNEL, true);
        if (forums.isEmpty()) {
            BotLogger.error("MatchmakerService could not find a thread container named #"
                    + CreateGameLaunchPostService.MAKING_NEW_GAMES_CHANNEL + ".");
            return;
        }
        ForumChannel forum = forums.getFirst();

        for (MatchedGame game : gamesToCreate) {
            List<MatchmakingQueueMember> queueMembers = game.members();
            List<Member> members = queueMembers.stream()
                    .map(member -> guild.getMemberById(member.getUserId()))
                    .filter(Objects::nonNull)
                    .toList();
            if (members.size() != queueMembers.size()) continue;

            String gameFunName = CreateGameService.autoGenerateGameName();
            String threadTitle = "Matchmaker Game: " + gameFunName.replace(":", "");
            String setupMessage = describeSetup(game);
            // Forum channels require an initial message payload, so the setup text becomes the post body.
            forum.createForumPost(threadTitle, MessageCreateData.fromContent(setupMessage))
                    .queue(
                            forumPost -> CreateGameLaunchPostService.postLaunchButtons(
                                    forumPost.getThreadChannel(), members, gameFunName),
                            BotLogger::catchRestError);
        }
    }

    private static String describeSetup(MatchedGame game) {
        String restrictionsText = game.restrictions().isEmpty() ? "None" : String.join(", ", game.restrictions());
        return "The players were matched on the following game setup:\n"
                + "- **Player count:** " + game.playerCount() + "\n"
                + "- **Victory point goal:** " + game.victoryPointGoal() + "\n"
                + "- **Expansion:** " + game.expansion() + "\n"
                + "- **Pace:** " + game.pace() + "\n"
                + "- **Restrictions:** " + restrictionsText;
    }
}

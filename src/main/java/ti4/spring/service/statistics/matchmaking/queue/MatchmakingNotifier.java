package ti4.spring.service.statistics.matchmaking.queue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import ti4.discord.JdaService;
import ti4.discord.interactions.buttons.handlers.matchmaking.MatchmakingOptions;
import ti4.discord.utility.DiscordRoleUtility;
import ti4.helpers.StringHelper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.game.CreateGameLaunchPostService;
import ti4.service.game.CreateGameService;

@UtilityClass
class MatchmakingNotifier {

    static void notifyExpired(List<QueuedParty> expired) {
        if (expired.isEmpty()) return;
        BotLogger.info("Expiring " + expired.size() + " matchmaking parties.");
        String expiryMessage = "The matchmaking service wasn't able to find you a game in the time frame you selected. "
                + "Queue again when ready and consider being open to additional game types or longer wait times.";
        for (QueuedParty party : expired) {
            if (party.members().size() != 1) continue;
            User user = JdaService.jda.getUserById(party.members().getFirst().getUserId());
            if (user == null) continue;
            MessageHelper.sendMessageToUser(expiryMessage, user);
        }
    }

    static void postMatchedGames(List<MatchedGame> gamesToCreate) {
        Guild guild = JdaService.guildPrimary;
        if (gamesToCreate.isEmpty() || guild == null) return;

        Map<String, ForumChannel> forumByNameCache = new HashMap<>();
        for (MatchedGame game : gamesToCreate) {
            String forumName = !game.tiglRanks().isEmpty()
                    ? CreateGameLaunchPostService.MAKING_TIGL_GAMES_CHANNEL
                    : CreateGameLaunchPostService.MAKING_NEW_GAMES_CHANNEL;
            ForumChannel forum = forumByNameCache.computeIfAbsent(forumName, name -> findForum(guild, name));
            if (forum == null) continue;

            List<MatchmakingQueueMember> queueMembers = game.members();
            List<Member> members = queueMembers.stream()
                    .map(member -> guild.getMemberById(member.getUserId()))
                    .filter(Objects::nonNull)
                    .toList();
            if (members.size() != queueMembers.size()) continue;

            String gameFunName = CreateGameService.autoGenerateGameName();
            String threadTitle = MatchDescriber.threadTitle(game);
            String setupMessage = MatchDescriber.setupBody(game);
            // Forum channels require an initial message payload, so the setup text becomes the post body.
            forum.createForumPost(threadTitle, MessageCreateData.fromContent(setupMessage))
                    .queue(
                            forumPost -> {
                                ThreadChannel thread = forumPost.getThreadChannel();
                                CreateGameLaunchPostService.postLaunchButtons(thread, members, gameFunName);
                                postLfgPing(thread, game);
                            },
                            BotLogger::catchRestError);
        }
    }

    private static void postLfgPing(ThreadChannel thread, MatchedGame game) {
        int playersNeeded =
                Integer.parseInt(game.playerCount()) - game.members().size();
        if (playersNeeded <= 0) return;

        List<String> tiglRankMentions = game.tiglRanks().stream()
                .filter(rank -> !MatchmakingOptions.UNRANKED_OPTION.equals(rank))
                .map(rank -> DiscordRoleUtility.getRole("TIGL - " + rank, thread.getGuild()))
                .filter(Objects::nonNull)
                .map(Role::getAsMention)
                .toList();

        if (tiglRankMentions.isEmpty()) {
            postLfgRolePing(thread, playersNeeded);
        } else {
            postTiglRankRolePing(thread, tiglRankMentions, playersNeeded);
        }
    }

    private static void postLfgRolePing(ThreadChannel thread, int playersNeeded) {
        Role lfgRole = DiscordRoleUtility.getRole("LFG", thread.getGuild());
        String message = lfgRole == null
                ? "Ping the `@LFG` role to find additional members, if the game doesn't fill soon."
                : lfgRole.getAsMention() + " this game needs " + StringHelper.pluralize(playersNeeded, "player")
                        + " to start.";
        MessageHelper.sendMessageToChannel(thread, message);
    }

    private static void postTiglRankRolePing(ThreadChannel thread, List<String> tiglRankMentions, int playersNeeded) {
        String message = String.join(" ", tiglRankMentions) + " this game needs "
                + StringHelper.pluralize(playersNeeded, "player") + " to start.";
        MessageHelper.sendMessageToChannel(thread, message);
    }

    private static ForumChannel findForum(Guild guild, String forumName) {
        List<ForumChannel> forums = guild.getForumChannelsByName(forumName, true);
        if (forums.isEmpty()) {
            BotLogger.error("MatchmakerService could not find a thread container named #" + forumName + ".");
            return null;
        }
        return forums.getFirst();
    }
}

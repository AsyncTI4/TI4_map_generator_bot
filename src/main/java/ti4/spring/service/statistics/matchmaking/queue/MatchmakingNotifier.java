package ti4.spring.service.statistics.matchmaking.queue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
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
import ti4.settings.users.UserSettings;

@UtilityClass
class MatchmakingNotifier {

    static void notifyExpired(List<QueuedParty> expired) {
        if (expired.isEmpty()) return;
        String expiryMessage = "The matchmaking service wasn't able to find you a game in the time frame you selected. "
                + "Queue again when ready and consider being open to additional game types or longer wait times.";
        for (QueuedParty party : expired) {
            BotLogger.info(describeExpiredParty(party));
            if (party.members().size() != 1) continue;
            User user = JdaService.jda.getUserById(party.members().getFirst().getUserId());
            if (user == null) continue;
            MessageHelper.sendMessageToUser(expiryMessage, user);
        }
    }

    private static String describeExpiredParty(QueuedParty party) {
        UserSettings settings = party.leaderSettings();
        int maxHours = MatchmakingOptions.getHours(settings.getMatchmakingMaxQueueTime());
        List<String> memberIds =
                party.members().stream().map(MatchmakingQueueMember::getUserId).toList();
        List<String> extras = new ArrayList<>();
        if (party.party().isTigl()) {
            extras.add("tiglRanks=" + settings.getMatchmakingTiglRanks());
        }
        List<String> avoidList = settings.getMatchmakingAvoidList();
        if (avoidList != null && !avoidList.isEmpty()) {
            extras.add("avoidList=" + avoidList);
        }
        String extrasText = extras.isEmpty() ? "" : ", " + String.join(", ", extras);
        return String.format(
                "Matchmaking queue: dropping party %d (tigl=%s, members=%s) after reaching max queue time of %dh."
                        + " Settings: expansions=%s, playerCounts=%s, victoryPoints=%s, paces=%s, restrictions=%s%s",
                party.party().getId(),
                party.party().isTigl(),
                memberIds,
                maxHours,
                settings.getMatchmakingExpansions(),
                settings.getMatchmakingPlayerCounts(),
                settings.getMatchmakingVictoryPointGoals(),
                settings.getMatchmakingPaces(),
                settings.getMatchmakingRestrictions(),
                extrasText);
    }

    interface MatchPostOutcome {
        void posted(MatchedGame game);

        void notPosted(MatchedGame game);

        void membersLeftGuild(MatchedGame game, List<Long> partyIdsWithDepartedMembers);
    }

    static void postMatchedGames(List<MatchedGame> gamesToCreate, MatchPostOutcome outcome) {
        if (gamesToCreate.isEmpty()) return;
        Guild guild = JdaService.guildPrimary;
        if (guild == null) {
            gamesToCreate.forEach(game -> report(game, outcome::notPosted));
            return;
        }

        Map<String, ForumChannel> forumByNameCache = new HashMap<>();
        for (MatchedGame game : gamesToCreate) {
            try {
                postMatchedGame(guild, forumByNameCache, game, outcome);
            } catch (Exception e) {
                BotLogger.error("Matchmaking queue: failed to post a matched game; its parties stay queued.", e);
                report(game, outcome::notPosted);
            }
        }
    }

    private static void postMatchedGame(
            Guild guild, Map<String, ForumChannel> forumByNameCache, MatchedGame game, MatchPostOutcome outcome) {
        String forumName = !game.tiglRanks().isEmpty()
                ? CreateGameLaunchPostService.MAKING_TIGL_GAMES_CHANNEL
                : CreateGameLaunchPostService.MAKING_NEW_GAMES_CHANNEL;
        ForumChannel forum = forumByNameCache.computeIfAbsent(forumName, name -> findForum(guild, name));
        if (forum == null) {
            report(game, outcome::notPosted);
            return;
        }

        List<Long> partyIdsWithDepartedMembers = game.members().stream()
                .filter(member -> guild.getMemberById(member.getUserId()) == null)
                .map(MatchmakingQueueMember::getPartyId)
                .distinct()
                .toList();
        if (!partyIdsWithDepartedMembers.isEmpty()) {
            BotLogger.info("Matchmaking queue: not posting a match because parties " + partyIdsWithDepartedMembers
                    + " have members who left the server; removing those parties and keeping the rest queued.");
            report(game, matched -> outcome.membersLeftGuild(matched, partyIdsWithDepartedMembers));
            return;
        }

        List<Member> members = game.members().stream()
                .map(member -> guild.getMemberById(member.getUserId()))
                .filter(Objects::nonNull)
                .toList();
        String gameFunName = CreateGameService.autoGenerateGameName();
        String threadTitle = MatchDescriber.threadTitle(game);
        String setupMessage = MatchDescriber.setupBody(game);
        // Forum channels require an initial message payload, so the setup text becomes the post body.
        forum.createForumPost(threadTitle, MessageCreateData.fromContent(setupMessage))
                .queue(
                        forumPost -> {
                            report(game, outcome::posted);
                            ThreadChannel thread = forumPost.getThreadChannel();
                            Consumer<Message> onLaunchPosted = message -> {
                                if (getPlayersNeeded(game) > 0) {
                                    MatchmakingQueueSearchService.get()
                                            .register(thread.getId(), message.getId(), toSearchCriteria(game));
                                }
                                postLfgPing(thread, game);
                            };
                            CreateGameLaunchPostService.postLaunchButtons(thread, members, gameFunName, onLaunchPosted);
                        },
                        error -> {
                            report(game, outcome::notPosted);
                            BotLogger.catchRestError(error);
                        });
    }

    private static void report(MatchedGame game, Consumer<MatchedGame> outcomeStep) {
        try {
            outcomeStep.accept(game);
        } catch (Exception e) {
            BotLogger.error("Matchmaking queue: failed to update the queue after a match post.", e);
        }
    }

    private static int getPlayersNeeded(MatchedGame game) {
        return Integer.parseInt(game.playerCount()) - game.members().size();
    }

    private static PlayerSearchCriteria toSearchCriteria(MatchedGame game) {
        return new PlayerSearchCriteria(
                List.of(game.playerCount()),
                List.of(game.victoryPointGoal()),
                List.of(game.expansion()),
                List.of(game.pace()),
                game.restrictions(),
                !game.tiglRanks().isEmpty(),
                game.tiglRanks());
    }

    private static void postLfgPing(ThreadChannel thread, MatchedGame game) {
        int playersNeeded = getPlayersNeeded(game);
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

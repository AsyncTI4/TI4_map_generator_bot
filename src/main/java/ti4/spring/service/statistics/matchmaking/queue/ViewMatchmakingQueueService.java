package ti4.spring.service.statistics.matchmaking.queue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import org.springframework.stereotype.Service;
import ti4.discord.JdaService;
import ti4.discord.interactions.buttons.handlers.game.CreateGameButtonHandler;
import ti4.discord.interactions.buttons.handlers.matchmaking.MatchmakingOptions;
import ti4.logging.BotLogger;
import ti4.service.persistence.DatabasePersistenceGate;
import ti4.settings.users.UserSettings;
import ti4.settings.users.UserSettingsManager;
import ti4.spring.context.SpringContext;

@AllArgsConstructor
@Service
public class ViewMatchmakingQueueService {

    private static final int MAX_EMBED_DESCRIPTION_LENGTH = 4000;

    private final MatchmakingQueuePartyRepository partyRepository;
    private final MatchmakingQueueMemberRepository memberRepository;
    private final MatchmakingQueueSearchRepository searchRepository;

    public List<MessageEmbed> getMessageEmbeds(Boolean tiglFilter) {
        if (DatabasePersistenceGate.isDisabled()) {
            return List.of(messageEmbed("Queueing is currently disabled."));
        }

        List<String> lines = new ArrayList<>(queuedPartyLines(tiglFilter));
        lines.addAll(queuedGameLines(tiglFilter));
        if (lines.isEmpty()) {
            return List.of(messageEmbed("There are no players in the queue right now."));
        }

        return paginateIntoEmbeds(lines);
    }

    private List<String> queuedPartyLines(Boolean tiglFilter) {
        List<MatchmakingQueueParty> parties = partyRepository.findAllByQueuedTrueOrderByQueuedAtAsc().stream()
                .filter(party -> tiglFilter == null || party.isTigl() == tiglFilter)
                .toList();
        if (parties.isEmpty()) {
            return List.of();
        }

        List<Long> partyIds = parties.stream().map(MatchmakingQueueParty::getId).toList();
        Map<Long, List<MatchmakingQueueMember>> membersByParty = memberRepository.findAllByPartyIdIn(partyIds).stream()
                .collect(Collectors.groupingBy(MatchmakingQueueMember::getPartyId));

        List<String> lines = new ArrayList<>();
        for (MatchmakingQueueParty party : parties) {
            List<MatchmakingQueueMember> members = membersByParty.getOrDefault(party.getId(), List.of());
            UserSettings settings = UserSettingsManager.get(party.getLeaderId());
            lines.add(describeQueueEntry(memberMentions(members), partyCriteria(settings, party.isTigl())));
        }
        return lines;
    }

    private List<String> queuedGameLines(Boolean tiglFilter) {
        Guild guild = JdaService.guildPrimary;
        return searchRepository.findAllByOrderByCreatedAtAsc().stream()
                .filter(search -> tiglFilter == null || search.isTigl() == tiglFilter)
                .map(search -> describeQueueEntry(
                        signedUpMentions(guild, search), MatchmakingQueueSearchService.toCriteria(search)))
                .toList();
    }

    private static String signedUpMentions(Guild guild, MatchmakingQueueSearch search) {
        List<Member> signedUp = signedUpMembers(guild, search);
        if (signedUp.isEmpty()) {
            return "<#" + search.getThreadId() + ">";
        }
        return signedUp.stream().map(Member::getAsMention).collect(Collectors.joining(", "));
    }

    private static List<Member> signedUpMembers(Guild guild, MatchmakingQueueSearch search) {
        ThreadChannel thread = guild == null ? null : guild.getThreadChannelById(search.getThreadId());
        if (thread == null) {
            return List.of();
        }
        try {
            Message signupMessage =
                    thread.retrieveMessageById(search.getMessageId()).complete();
            return CreateGameButtonHandler.fetchMembersFromMessage(signupMessage, guild).stream()
                    .distinct()
                    .toList();
        } catch (RuntimeException e) {
            BotLogger.warning("Could not read the sign-up message for queued game thread " + search.getThreadId(), e);
            return List.of();
        }
    }

    private static List<MessageEmbed> paginateIntoEmbeds(List<String> partyLines) {
        List<String> pages = new ArrayList<>();
        StringBuilder page = new StringBuilder();
        for (String line : partyLines) {
            if (!page.isEmpty() && page.length() + line.length() > MAX_EMBED_DESCRIPTION_LENGTH) {
                pages.add(page.toString());
                page.setLength(0);
            }
            page.append(line);
        }
        if (!page.isEmpty()) {
            pages.add(page.toString());
        }

        List<MessageEmbed> embeds = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            String title = pages.size() == 1
                    ? "Matchmaking Queue"
                    : "Matchmaking Queue (Page " + (i + 1) + "/" + pages.size() + ")";
            embeds.add(new EmbedBuilder()
                    .setTitle(title)
                    .setDescription(pages.get(i))
                    .build());
        }
        return embeds;
    }

    private static MessageEmbed messageEmbed(String description) {
        return new EmbedBuilder()
                .setTitle("Matchmaking Queue")
                .setDescription(description)
                .build();
    }

    private static String memberMentions(List<MatchmakingQueueMember> members) {
        return members.stream().map(member -> "<@" + member.getUserId() + ">").collect(Collectors.joining(", "));
    }

    private static PlayerSearchCriteria partyCriteria(UserSettings settings, boolean tigl) {
        return new PlayerSearchCriteria(
                settings.getMatchmakingPlayerCounts(),
                settings.getMatchmakingVictoryPointGoals(),
                settings.getMatchmakingExpansions(),
                settings.getMatchmakingPaces(),
                settings.getMatchmakingRestrictions(),
                tigl,
                settings.getMatchmakingTiglRanks());
    }

    private static String describeQueueEntry(String mentions, PlayerSearchCriteria criteria) {
        StringBuilder line = new StringBuilder("\n• ").append(mentions).append(" — ");
        line.append(joinInOrder(criteria.playerCounts(), MatchmakingOptions.PLAYER_COUNT_OPTIONS, "/"))
                .append("p");
        line.append(" · ")
                .append(joinInOrder(criteria.victoryPointGoals(), MatchmakingOptions.VICTORY_POINT_OPTIONS, "/"))
                .append("vp");
        String expansions = criteria.expansions().stream()
                .sorted(byCanonicalOrder(MatchmakingOptions.EXPANSION_OPTIONS))
                .map(MatchmakingOptions::shortExpansionName)
                .collect(Collectors.joining("/"));
        line.append(" · ").append(expansions);
        String paces = criteria.paces().stream()
                .sorted(byCanonicalOrder(MatchmakingOptions.PACE_RESTRICTION_OPTIONS))
                .map(MatchmakingOptions::shortPaceName)
                .map(String::toLowerCase)
                .collect(Collectors.joining("/"));
        line.append(" · ").append(paces).append(" pace");
        List<String> restrictions = criteria.restrictions();
        if (!restrictions.isEmpty()) {
            String restrictionsText = restrictions.stream()
                    .sorted(byCanonicalOrder(MatchmakingOptions.RESTRICTION_OPTIONS))
                    .map(ViewMatchmakingQueueService::labelRestriction)
                    .collect(Collectors.joining(", "));
            line.append(" · ").append(restrictionsText);
        }
        if (criteria.tigl()) {
            line.append(" · TIGL (")
                    .append(String.join("/", criteria.tiglRanks()))
                    .append(")");
        }
        return line.toString();
    }

    private static String labelRestriction(String restriction) {
        if (MatchmakingOptions.isSimilarActiveHoursLevel(restriction)) {
            return MatchmakingOptions.shortSimilarActiveHoursLabel(restriction);
        }
        return restriction;
    }

    private static Comparator<String> byCanonicalOrder(List<String> canonicalOrder) {
        return Comparator.comparingInt(value -> {
            int index = canonicalOrder.indexOf(value);
            return index < 0 ? Integer.MAX_VALUE : index;
        });
    }

    private static String joinInOrder(List<String> values, List<String> canonicalOrder, String separator) {
        return values.stream().sorted(byCanonicalOrder(canonicalOrder)).collect(Collectors.joining(separator));
    }

    public static ViewMatchmakingQueueService get() {
        return SpringContext.getBean(ViewMatchmakingQueueService.class);
    }
}

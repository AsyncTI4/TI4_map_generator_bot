package ti4.spring.service.statistics.matchmaking;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.springframework.stereotype.Service;
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

    public List<MessageEmbed> describeQueueFor(String requestingUserId) {
        if (DatabasePersistenceGate.isDisabled()) {
            return List.of(messageEmbed("Queueing is currently disabled."));
        }

        List<MatchmakingQueueParty> parties = partyRepository.findAllByQueuedTrueOrderByQueuedAtAsc();
        if (parties.isEmpty()) {
            return List.of(messageEmbed("There are no players in the queue right now."));
        }

        List<Long> partyIds = parties.stream().map(MatchmakingQueueParty::getId).toList();
        Map<Long, List<MatchmakingQueueMember>> membersByParty = memberRepository.findAllByPartyIdIn(partyIds).stream()
                .collect(Collectors.groupingBy(MatchmakingQueueMember::getPartyId));

        List<String> partyLines = new ArrayList<>();
        for (MatchmakingQueueParty party : parties) {
            List<MatchmakingQueueMember> members = membersByParty.getOrDefault(party.getId(), List.of());
            if (members.isEmpty()) continue;
            partyLines.add(describeQueuedParty(members, UserSettingsManager.get(party.getLeaderId())));
        }

        if (partyLines.isEmpty()) {
            return List.of(messageEmbed("There are no players in the queue right now."));
        }

        return paginateIntoEmbeds(partyLines);
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

    private static String describeQueuedParty(List<MatchmakingQueueMember> members, UserSettings settings) {
        String mentions =
                members.stream().map(member -> "<@" + member.getUserId() + ">").collect(Collectors.joining(", "));
        StringBuilder line = new StringBuilder("\n• ").append(mentions).append(" — ");
        line.append(join(settings.getMatchmakingPlayerCounts())).append("p");
        line.append(" · ")
                .append(join(settings.getMatchmakingVictoryPointGoals()))
                .append("vp");
        line.append(" · ").append(join(settings.getMatchmakingExpansions()));
        line.append(" · ").append(join(settings.getMatchmakingPaces()));
        List<String> restrictions = settings.getMatchmakingRestrictions();
        if (!restrictions.isEmpty()) {
            line.append(" · ").append(String.join(", ", restrictions));
        }
        return line.toString();
    }

    private static String join(List<String> values) {
        return String.join("/", values);
    }

    public static ViewMatchmakingQueueService get() {
        return SpringContext.getBean(ViewMatchmakingQueueService.class);
    }
}

package ti4.spring.service.statistics.matchmaking;

import java.util.ArrayList;
import java.util.List;
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

    private final MatchmakingQueueEntryRepository matchmakingQueueEntryRepository;

    public List<MessageEmbed> describeQueueFor(String requestingUserId) {
        if (DatabasePersistenceGate.isDisabled()) {
            return List.of(messageEmbed("Queueing is currently disabled."));
        }

        List<MatchmakingQueueEntryEntity> entries = matchmakingQueueEntryRepository.findAllByOrderByQueuedAtAsc();
        UserSettings requesterSettings = UserSettingsManager.get(requestingUserId);
        List<String> requesterAvoidList = requesterSettings.getMatchmakingAvoidList();

        List<String> playerLines = new ArrayList<>();
        for (MatchmakingQueueEntryEntity entry : entries) {
            String userId = entry.getUserId();

            UserSettings settings = UserSettingsManager.get(userId);
            if (requesterAvoidList.contains(userId)
                    || settings.getMatchmakingAvoidList().contains(requestingUserId)) {
                continue;
            }
            playerLines.add(describeQueuedPlayer(userId, settings));
        }

        if (playerLines.isEmpty()) {
            return List.of(messageEmbed("There are no players in the queue that you can be matched with right now."));
        }

        return paginateIntoEmbeds(playerLines);
    }

    private static List<MessageEmbed> paginateIntoEmbeds(List<String> playerLines) {
        List<String> pages = new ArrayList<>();
        StringBuilder page = new StringBuilder();
        for (String line : playerLines) {
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

    private static String describeQueuedPlayer(String userId, UserSettings settings) {
        StringBuilder line = new StringBuilder("\n• <@").append(userId).append("> — ");
        line.append(joinOrAny(settings.getMatchmakingPlayerCounts())).append("p");
        line.append(" · ").append(joinOrAny(settings.getMatchmakingVictoryPointGoals())).append("vp");
        line.append(" · ").append(joinOrAny(settings.getMatchmakingExpansions()));
        line.append(" · ").append(joinOrAny(settings.getMatchmakingPaces()));
        List<String> restrictions = settings.getMatchmakingRestrictions();
        if (!restrictions.isEmpty()) {
            line.append(" · ").append(String.join(", ", restrictions));
        }
        return line.toString();
    }

    private static String joinOrAny(List<String> values) {
        return values.isEmpty() ? "Any" : String.join("/", values);
    }

    public static ViewMatchmakingQueueService get() {
        return SpringContext.getBean(ViewMatchmakingQueueService.class);
    }
}

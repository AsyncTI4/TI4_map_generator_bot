package ti4.service.tigl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.User;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.JdaService;
import ti4.service.emoji.MiscEmojis;
import ti4.website.UltimateStatisticsWebsiteHelper;

@UtilityClass
public class TiglRankHistoryService {

    public static final String STANDARD_LEAGUE = "Standard";
    public static final String FRACTURED_LEAGUE = "Fractured";

    public static Optional<String> rankAtTimestamp(TiglPlayerRankHistory history, String league, long epochMillis) {
        if (history == null || history.getRanks() == null) {
            return Optional.empty();
        }

        LocalDate asOf =
                Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDate();
        TiglRankEntry latest = null;
        LocalDate latestEarned = null;
        for (TiglRankEntry entry : history.getRanks()) {
            if (!league.equalsIgnoreCase(entry.getLeague())) {
                continue;
            }
            LocalDate earned = parseDate(entry.getDate());
            if (earned == null || earned.isAfter(asOf)) {
                continue;
            }
            if (latestEarned == null || !earned.isBefore(latestEarned)) {
                latestEarned = earned;
                latest = entry;
            }
        }
        return Optional.ofNullable(latest).map(TiglRankEntry::getRankName);
    }

    public static String leagueFor(boolean fractured) {
        return fractured ? FRACTURED_LEAGUE : STANDARD_LEAGUE;
    }

    private static LocalDate parseDate(String date) {
        if (StringUtils.isBlank(date)) {
            return null;
        }
        try {
            return LocalDate.parse(date.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static Map<Long, TiglPlayerRankHistory> fetchHistories(List<Long> discordUserIds) {
        TiglRankHistoryRequest request = new TiglRankHistoryRequest();
        request.setDiscordUserIds(discordUserIds);
        return indexByUserId(UltimateStatisticsWebsiteHelper.fetchTiglRankHistory(request));
    }

    public static String getRankMessage(List<Long> discordUserIds, boolean includeHistory) {
        TiglRankHistoryRequest request = new TiglRankHistoryRequest();
        request.setDiscordUserIds(discordUserIds);

        return renderMessage(
                discordUserIds, UltimateStatisticsWebsiteHelper.fetchTiglRankHistory(request), includeHistory);
    }

    static String renderMessage(List<Long> discordUserIds, TiglRankHistoryResponse response, boolean includeHistory) {
        Map<Long, TiglPlayerRankHistory> byUserId = indexByUserId(response);

        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(MiscEmojis.TIGL).append("TIGL Ranks\n");
        for (Long discordUserId : discordUserIds) {
            appendPlayer(sb, discordUserId, byUserId.get(discordUserId), includeHistory);
        }
        return sb.toString();
    }

    private static Map<Long, TiglPlayerRankHistory> indexByUserId(TiglRankHistoryResponse response) {
        Map<Long, TiglPlayerRankHistory> byUserId = new LinkedHashMap<>();
        if (response == null || response.getData() == null || response.getData().getItems() == null) {
            return byUserId;
        }
        for (TiglPlayerRankHistory item : response.getData().getItems()) {
            if (item != null && item.getDiscordUserId() != null) {
                byUserId.put(item.getDiscordUserId(), item);
            }
        }
        return byUserId;
    }

    private static void appendPlayer(
            StringBuilder sb, Long discordUserId, TiglPlayerRankHistory history, boolean includeHistory) {
        sb.append("\n**").append(resolveName(discordUserId)).append("**\n");
        if (history == null) {
            sb.append("> No TIGL data found.\n");
            return;
        }

        sb.append("> ").append(describeCurrentRanks(history.getCurrentRanks())).append('\n');
        if (!includeHistory) {
            return;
        }

        List<TiglRankEntry> ranks = activeLadderEntries(history);
        if (ranks.isEmpty()) {
            sb.append("> No rank history.\n");
            return;
        }
        for (TiglRankEntry entry : ranks) {
            sb.append("> ").append(describeEntry(entry)).append('\n');
        }
    }

    private static List<TiglRankEntry> activeLadderEntries(TiglPlayerRankHistory history) {
        if (history.getRanks() == null) {
            return List.of();
        }
        return history.getRanks().stream()
                .filter(entry -> STANDARD_LEAGUE.equalsIgnoreCase(entry.getLeague())
                        || FRACTURED_LEAGUE.equalsIgnoreCase(entry.getLeague()))
                .toList();
    }

    private static String describeCurrentRanks(TiglCurrentRanks currentRanks) {
        if (currentRanks == null) {
            return "No current ranks.";
        }
        List<String> parts = new ArrayList<>();
        addRank(parts, STANDARD_LEAGUE, currentRanks.getStandard());
        addRank(parts, FRACTURED_LEAGUE, currentRanks.getFractured());
        return parts.isEmpty() ? "No current ranks." : String.join(" · ", parts);
    }

    private static void addRank(List<String> parts, String league, String rank) {
        if (StringUtils.isNotBlank(rank)) {
            parts.add(league + ": " + rank);
        }
    }

    private static String describeEntry(TiglRankEntry entry) {
        StringBuilder line = new StringBuilder();
        line.append('`').append(entry.getDate()).append('`');
        line.append(' ').append(entry.getLeague());
        line.append(" — ").append(entry.getRankName());
        line.append(" (").append(entry.getDuration()).append(entry.getDuration() == 1 ? " day)" : " days)");
        if (StringUtils.isNotBlank(entry.getGameId())) {
            line.append(" · ").append(entry.getGameId());
        }
        return line.toString();
    }

    private static String resolveName(Long discordUserId) {
        User user = JdaService.jda == null ? null : JdaService.jda.getUserById(discordUserId);
        return user == null ? String.valueOf(discordUserId) : user.getName();
    }
}

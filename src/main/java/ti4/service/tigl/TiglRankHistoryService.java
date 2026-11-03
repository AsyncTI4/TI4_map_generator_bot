package ti4.service.tigl;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.User;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.JdaService;
import ti4.helpers.Constants;
import ti4.logging.BotLogger;
import ti4.service.emoji.MiscEmojis;
import ti4.website.UltimateStatisticsWebsiteHelper;

@UtilityClass
public class TiglRankHistoryService {

    public static final String STANDARD_LEAGUE = "Standard";
    public static final String FRACTURED_LEAGUE = "Fractured";

    private static final DateTimeFormatter EARNED_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy.MM.dd").withZone(ZoneOffset.UTC);

    public static Optional<String> rankAtTimestamp(TiglPlayerRankHistory history, String league, long epochMillis) {
        if (history == null || history.getRanks() == null) {
            return Optional.empty();
        }

        TiglRankEntry latest = null;
        for (TiglRankEntry entry : history.getRanks()) {
            if (!league.equalsIgnoreCase(entry.getLeague())) {
                continue;
            }
            Long earned = entry.getTimestamp();
            if (earned == null || earned > epochMillis) {
                continue;
            }
            if (latest == null || earned > latest.getTimestamp()) {
                latest = entry;
            }
        }
        return Optional.ofNullable(latest).map(TiglRankEntry::getRankName);
    }

    // Unknown properties are ignored, so a payload that renamed or dropped "timestamp" parses cleanly and leaves
    // every entry undated. Nothing then throws: every player records as Unranked, every game's minimum rank
    // collapses, and the /game replace rank gate waves everyone through. That is silent, wrong, and only fixable at
    // the source, so it pings rather than being left in the log to scroll past. Once per response, not per player -
    // a snapshot asks per player per ladder, which would ping a dozen times for one broken payload.
    private static void reportUndatedEntries(TiglRankHistoryResponse response) {
        int undated = countUndatedLiveLadderEntries(response);
        if (undated == 0) {
            return;
        }
        BotLogger.error(Constants.lazikPing() + " " + Constants.niuPing() + " The TIGL rank history endpoint returned "
                + undated + " Standard/Fractured entries with no `timestamp`, so they cannot be placed in time."
                + " Every affected player will be recorded as Unranked and replacement rank checks will not hold."
                + " This usually means the payload field was renamed or dropped.");
    }

    static int countUndatedLiveLadderEntries(TiglRankHistoryResponse response) {
        if (response == null || response.getData() == null || response.getData().getItems() == null) {
            return 0;
        }
        int undated = 0;
        for (TiglPlayerRankHistory item : response.getData().getItems()) {
            if (item == null || item.getRanks() == null) {
                continue;
            }
            for (TiglRankEntry entry : item.getRanks()) {
                if (entry.getTimestamp() == null && isLiveLadder(entry.getLeague())) {
                    undated++;
                }
            }
        }
        return undated;
    }

    private static boolean isLiveLadder(String league) {
        return STANDARD_LEAGUE.equalsIgnoreCase(league) || FRACTURED_LEAGUE.equalsIgnoreCase(league);
    }

    public static String leagueFor(boolean fractured) {
        return fractured ? FRACTURED_LEAGUE : STANDARD_LEAGUE;
    }

    public static Map<Long, TiglPlayerRankHistory> fetchHistories(List<Long> discordUserIds) {
        return indexByUserId(fetchAndValidate(discordUserIds));
    }

    public static String getRankMessage(List<Long> discordUserIds, boolean includeHistory) {
        return renderMessage(discordUserIds, fetchAndValidate(discordUserIds), includeHistory);
    }

    private static TiglRankHistoryResponse fetchAndValidate(List<Long> discordUserIds) {
        TiglRankHistoryRequest request = new TiglRankHistoryRequest();
        request.setDiscordUserIds(discordUserIds);

        TiglRankHistoryResponse response = UltimateStatisticsWebsiteHelper.fetchTiglRankHistory(request);
        reportUndatedEntries(response);
        return response;
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
        line.append('`').append(describeEarnedDate(entry)).append('`');
        line.append(' ').append(entry.getLeague());
        line.append(" — ").append(entry.getRankName());
        line.append(" (").append(entry.getDuration()).append(entry.getDuration() == 1 ? " day)" : " days)");
        if (StringUtils.isNotBlank(entry.getGameId())) {
            line.append(" · ").append(entry.getGameId());
        }
        return line.toString();
    }

    // Formatted in UTC rather than the host's zone, so the same rank does not render on different days depending on
    // where the bot happens to run.
    private static String describeEarnedDate(TiglRankEntry entry) {
        return entry.getTimestamp() == null
                ? "unknown"
                : EARNED_DATE_FORMAT.format(Instant.ofEpochMilli(entry.getTimestamp()));
    }

    private static String resolveName(Long discordUserId) {
        User user = JdaService.jda == null ? null : JdaService.jda.getUserById(discordUserId);
        return user == null ? String.valueOf(discordUserId) : user.getName();
    }
}

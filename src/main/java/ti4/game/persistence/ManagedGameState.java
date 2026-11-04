package ti4.game.persistence;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.entities.ISnowflake;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.commands.statistics.GameStatisticsFilterer;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.FoWHelper;

public record ManagedGameState(
        String name,
        String revisionKey,
        boolean hasEnded,
        boolean competitiveExportEligible,
        boolean hasWinner,
        boolean vpGoalReached,
        boolean fowMode,
        boolean factionReactMode,
        boolean twilightsFallMode,
        boolean colorReactMode,
        boolean stratReactMode,
        boolean fastScFollowMode,
        boolean fogQol01,
        boolean injectRules,
        boolean autoPingEnabled,
        boolean temporaryPingDisable,
        long creationDateTime,
        long lastModifiedDate,
        String activePlayerId,
        long lastActivePlayerChange,
        long endedDate,
        int round,
        String guildId,
        String mainGameChannelId,
        String tableTalkChannelId,
        String launchPostThreadId,
        List<Participant> participants,
        Map<String, String> teamMateToPrimaryId) {

    private static final String TEAM_MATE_ENTRY_SEPARATOR = ",";
    private static final String TEAM_MATE_PRIMARY_SEPARATOR = ":";

    public record Participant(String userId, String userName, boolean realPlayer, boolean realOrEliminated) {}

    public ManagedGameState {
        participants = sortedByUserId(participants);
        teamMateToPrimaryId = teamMateToPrimaryId == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new TreeMap<>(teamMateToPrimaryId));
    }

    public static ManagedGameState of(Game game) {
        return new ManagedGameState(
                game.getName(),
                game.getRevisionKey(),
                game.isHasEnded(),
                game.isHasEnded()
                        && GameStatisticsFilterer.getCompetitiveExportGamesFilter()
                                .test(game),
                game.hasWinner(),
                game.getPlayers().values().stream().anyMatch(player -> player.getTotalVictoryPoints() >= game.getVp()),
                game.isFowMode(),
                game.isBotFactionReacts(),
                game.isTwilightsFallMode(),
                game.isBotColorReacts(),
                game.isBotStratReacts(),
                game.isFastSCFollowMode(),
                FoWHelper.isFogQol01(game),
                game.isInjectRulesLinks(),
                game.getAutoPingStatus(),
                game.isTemporaryPingDisable(),
                game.getCreationDateTime(),
                game.getLastModifiedDate(),
                sanitizeToNull(game.getActivePlayerID()),
                game.getLastActivePlayerChange() == null
                        ? 0
                        : game.getLastActivePlayerChange().getTime(),
                game.getEndedDate(),
                game.getRound(),
                numericOrNull(game.getGuildID()),
                resolvedOrStoredId(game.getMainGameChannel(), game.getMainChannelID()),
                resolvedOrStoredId(game.getTableTalkChannel(), game.getTableTalkChannelID()),
                numericOrNull(game.getLaunchPostThreadID()),
                game.getPlayers().values().stream()
                        .map(player -> toParticipant(game, player))
                        .toList(),
                collectTeamMates(game));
    }

    public boolean matchesRevision(String fileRevisionKey) {
        return StringUtils.isNotBlank(fileRevisionKey) && fileRevisionKey.equals(revisionKey);
    }

    public String encodedTeamMateToPrimaryId() {
        if (teamMateToPrimaryId.isEmpty()) return null;
        return teamMateToPrimaryId.entrySet().stream()
                .map(entry -> entry.getKey() + TEAM_MATE_PRIMARY_SEPARATOR + entry.getValue())
                .collect(Collectors.joining(TEAM_MATE_ENTRY_SEPARATOR));
    }

    public static Map<String, String> decodeTeamMateToPrimaryId(String encoded) {
        if (StringUtils.isBlank(encoded)) return Collections.emptyMap();
        Map<String, String> teamMateToPrimaryId = new TreeMap<>();
        for (String entry : encoded.split(TEAM_MATE_ENTRY_SEPARATOR)) {
            String teamMateId = StringUtils.substringBefore(entry, TEAM_MATE_PRIMARY_SEPARATOR);
            String primaryId = StringUtils.substringAfter(entry, TEAM_MATE_PRIMARY_SEPARATOR);
            if (StringUtils.isNoneBlank(teamMateId, primaryId)) {
                teamMateToPrimaryId.putIfAbsent(teamMateId, primaryId);
            }
        }
        return teamMateToPrimaryId;
    }

    private static Participant toParticipant(Game game, Player player) {
        boolean realPlayer = (player.isRealPlayer() && !player.isNpc()) || (player.isEliminated() && game.isHasEnded());
        boolean realOrEliminated = player.isRealPlayer() || player.isEliminated();
        return new Participant(player.getUserID(), player.getUserName(), realPlayer, realOrEliminated);
    }

    private static Map<String, String> collectTeamMates(Game game) {
        if (!game.isCommunityMode()) {
            return Collections.emptyMap();
        }
        return game.getPlayers().values().stream()
                .flatMap(player -> player.getTeamMateIDs().stream()
                        .filter(id -> !id.equals(player.getUserID()))
                        .map(id -> Map.entry(id, player.getUserID())))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue, (first, _) -> first));
    }

    private static String resolvedOrStoredId(ISnowflake resolved, String storedId) {
        if (resolved != null) return resolved.getId();
        return numericOrNull(storedId);
    }

    private static String numericOrNull(String id) {
        return StringUtils.isNumeric(id) ? id : null;
    }

    private static String sanitizeToNull(String str) {
        if (StringUtils.isBlank(str) || "null".equalsIgnoreCase(str)) {
            return null;
        }
        return str;
    }

    private static List<Participant> sortedByUserId(List<Participant> participants) {
        return participants.stream()
                .sorted(Comparator.comparing(Participant::userId))
                .toList();
    }
}

package ti4.game.persistence;

import java.util.Comparator;
import java.util.List;
import net.dv8tion.jda.api.entities.ISnowflake;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.FoWHelper;

public record ManagedGameState(
        String name,
        boolean hasEnded,
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
        List<Participant> participants) {

    public record Participant(String userId, String userName, boolean realPlayer) {}

    public ManagedGameState {
        participants = sortedByUserId(participants);
    }

    public static ManagedGameState of(Game game) {
        return new ManagedGameState(
                game.getName(),
                game.isHasEnded(),
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
                game.getCreationDateTime(),
                game.getLastModifiedDate(),
                sanitizeToNull(game.getActivePlayerID()),
                game.getLastActivePlayerChange() == null
                        ? 0
                        : game.getLastActivePlayerChange().getTime(),
                game.getEndedDate(),
                game.getRound(),
                resolvedOrStoredId(game.getGuild(), game.getGuildID()),
                resolvedOrStoredId(game.getMainGameChannel(), game.getMainChannelID()),
                resolvedOrStoredId(game.getTableTalkChannel(), game.getTableTalkChannelID()),
                resolvedOrStoredId(game.getLaunchPostThread(), game.getLaunchPostThreadID()),
                game.getPlayers().values().stream()
                        .map(player -> toParticipant(game, player))
                        .toList());
    }

    private static Participant toParticipant(Game game, Player player) {
        boolean realPlayer = (player.isRealPlayer() && !player.isNpc()) || (player.isEliminated() && game.isHasEnded());
        return new Participant(player.getUserID(), player.getUserName(), realPlayer);
    }

    private static String resolvedOrStoredId(ISnowflake resolved, String storedId) {
        if (resolved != null) return resolved.getId();
        return StringUtils.isNumeric(storedId) ? storedId : null;
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

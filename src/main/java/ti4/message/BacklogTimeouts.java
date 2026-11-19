package ti4.message;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import ti4.discord.JdaService;
import ti4.game.Game;
import ti4.service.game.GameNameService;
import ti4.service.megagame.MegagameGroupService;

@UtilityClass
public class BacklogTimeouts {

    static final int SECONDS_PER_PENDING_SEND = 2;
    static final int MAX_TIMEOUT_SECONDS = 600;

    private static final Set<String> trackedGames = ConcurrentHashMap.newKeySet();
    private static final Map<String, Integer> pendingByChannel = new ConcurrentHashMap<>();

    // TODO: backlog timeouts suit any big game, not just megagames; widen once agreed upstream.
    public static void track(Game game) {
        if (game == null || game.getName() == null) {
            return;
        }
        if (MegagameGroupService.hasTagOutsideFog(game)) {
            trackedGames.add(game.getName());
        } else {
            trackedGames.remove(game.getName());
        }
    }

    static boolean appliesTo(MessageChannel channel) {
        if (channel == null || trackedGames.isEmpty()) {
            return false;
        }
        return isTracked(GameNameService.getGameNameFromChannel(channel));
    }

    static boolean isTracked(String gameName) {
        return gameName != null && trackedGames.contains(gameName);
    }

    static int reserve(String channelId) {
        return pendingByChannel.merge(channelId, 1, Integer::sum) - 1;
    }

    static void release(String channelId) {
        pendingByChannel.computeIfPresent(channelId, (_, pending) -> pending <= 1 ? null : pending - 1);
    }

    static long timeoutSeconds(int pendingAhead) {
        long seconds = JdaService.DISCORD_REQUEST_TIMEOUT_SECONDS + (long) pendingAhead * SECONDS_PER_PENDING_SEND;
        return Math.min(seconds, MAX_TIMEOUT_SECONDS);
    }
}

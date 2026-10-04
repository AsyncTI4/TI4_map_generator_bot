package ti4.service.testbed;

import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;

@UtilityClass
public class TestBedCombatThreads {

    private static final Pattern SYSTEM_POSITION = Pattern.compile("-system-([^-]+)-turn-");

    static boolean isCombatThread(Game game, String threadName) {
        return threadName.startsWith(game.getName() + "-round-") && threadName.contains("-vs-");
    }

    static boolean isCombatThread(Game game, ThreadChannel thread) {
        return isCombatThread(game, thread.getName());
    }

    @Nullable
    static ThreadChannel latest(Game game, Player seat) {
        return latestMatching(game, seat, false);
    }

    @Nullable
    static ThreadChannel latestOngoing(Game game, Player seat) {
        return latestMatching(game, seat, true);
    }

    @Nullable
    private static ThreadChannel latestMatching(Game game, Player seat, boolean ongoingOnly) {
        TextChannel parent = game.isFowMode() ? seat.getPrivateChannel() : game.getMainGameChannel();
        if (parent == null) return null;
        List<ThreadChannel> threads = parent.getThreadChannels().stream()
                .filter(thread -> isCombatThread(game, thread))
                .filter(thread -> game.isFowMode() || thread.getName().contains(seat.getFaction()))
                .filter(thread -> !ongoingOnly || isOngoing(game, thread.getName()))
                .toList();
        return threads.stream()
                .max(Comparator.comparingLong(ThreadChannel::getLatestMessageIdLong))
                .orElse(null);
    }

    static boolean isOngoing(Game game, String threadName) {
        String position = systemPosition(threadName);
        Tile tile = position == null ? null : game.getTileByPosition(position);
        if (tile == null) return false;
        if (hasOpposingSides(ButtonHelper.getPlayersWithShipsInTheSystem(game, tile))) return true;
        for (Planet planet : tile.getPlanetUnitHolders()) {
            if (hasOpposingSides(ButtonHelper.getPlayersWithUnitsOnAPlanet(game, planet))) return true;
        }
        return false;
    }

    @Nullable
    static String systemPosition(String threadName) {
        Matcher matcher = SYSTEM_POSITION.matcher(threadName);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static boolean hasOpposingSides(List<Player> players) {
        return players.stream()
                .anyMatch(player ->
                        players.stream().anyMatch(other -> other != player && !player.isPlayerMemberOfAlliance(other)));
    }
}

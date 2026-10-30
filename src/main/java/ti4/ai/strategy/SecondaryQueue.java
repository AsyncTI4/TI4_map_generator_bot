package ti4.ai.strategy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.strategycard.PlayStrategyCardService;

@UtilityClass
public class SecondaryQueue {

    public static final long RECHECK_MILLIS = 10_000L;
    private static final long STALE_MILLIS = 10 * 60_000L;
    private static final Map<String, Map<String, Long>> OPEN = new ConcurrentHashMap<>();

    public static void primaryStarted(Game game, int initiative, long now) {
        open(game).put(primaryKey(game, initiative), now);
    }

    public static void primaryResolved(Game game, int initiative) {
        open(game).remove(primaryKey(game, initiative));
    }

    public static void followStarted(Game game, Player seat, int initiative, long now) {
        open(game).put(followKey(game, seat, initiative), now);
    }

    public static void followFinished(Game game, Player seat, int initiative) {
        open(game).remove(followKey(game, seat, initiative));
    }

    public static void forget(String gameName) {
        OPEN.remove(gameName);
    }

    public static Map<String, Long> snapshot(String gameName) {
        return Map.copyOf(OPEN.getOrDefault(gameName, Map.of()));
    }

    public static void restore(String gameName, Map<String, Long> open) {
        OPEN.put(gameName, new ConcurrentHashMap<>(open));
    }

    public static Optional<String> blockerFor(Game game, Player seat, int initiative, long now) {
        Player holder = game.getPlayerFromSC(initiative);
        if (holder == null || holder == seat) return Optional.empty();
        List<Player> order = PlayStrategyCardService.getPlayersInFollowOrder(game, holder);
        if (!order.contains(seat)) return Optional.empty();
        if (isOpen(game, primaryKey(game, initiative), now)) return Optional.of("the primary ability");
        for (Player earlier : order) {
            if (earlier == seat) break;
            Optional<String> unfinished = unfinishedSecondary(game, earlier, holder, initiative, now);
            if (unfinished.isPresent()) return unfinished;
        }
        return Optional.empty();
    }

    public static Optional<String> unfinished(Game game, int initiative, long now) {
        Player holder = game.getPlayerFromSC(initiative);
        if (holder == null) return Optional.empty();
        for (Player player : PlayStrategyCardService.getPlayersInFollowOrder(game, holder)) {
            Optional<String> unfinished = unfinishedSecondary(game, player, holder, initiative, now);
            if (unfinished.isPresent()) return unfinished;
        }
        return Optional.empty();
    }

    private static Optional<String> unfinishedSecondary(
            Game game, Player player, Player holder, int initiative, long now) {
        if (player == holder) return Optional.empty();
        if (!player.hasFollowedSC(initiative)) return Optional.of(player.getRepresentationNoPing());
        if (isOpen(game, followKey(game, player, initiative), now))
            return Optional.of(player.getRepresentationNoPing());
        return Optional.empty();
    }

    private static boolean isOpen(Game game, String key, long now) {
        Map<String, Long> open = OPEN.get(game.getName());
        if (open == null) return false;
        Long since = open.get(key);
        if (since == null) return false;
        if (now - since > STALE_MILLIS) {
            open.remove(key);
            return false;
        }
        return true;
    }

    private static Map<String, Long> open(Game game) {
        return OPEN.computeIfAbsent(game.getName(), name -> new ConcurrentHashMap<>());
    }

    private static String primaryKey(Game game, int initiative) {
        return game.getRound() + "|primary|" + initiative;
    }

    private static String followKey(Game game, Player seat, int initiative) {
        return game.getRound() + "|follow|" + initiative + "|" + seat.getFaction();
    }
}

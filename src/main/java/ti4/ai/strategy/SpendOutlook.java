package ti4.ai.strategy;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class SpendOutlook {

    private static final int MAX_CACHED = 64;
    private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();

    private record Cached(WeakReference<Game> game, long modified, boolean ahead) {

        boolean isFor(Game current) {
            return game.get() == current && modified == current.getLastModifiedDate();
        }
    }

    public static boolean spendingAhead(Game game, Player seat) {
        String key = game.getName() + "|" + seat.getUserID();
        Cached cached = CACHE.get(key);
        if (cached != null && cached.isFor(game)) return cached.ahead();
        boolean ahead = DiplomacyUse.spendingAhead(game, seat, true);
        if (CACHE.size() > MAX_CACHED) CACHE.clear();
        CACHE.put(key, new Cached(new WeakReference<>(game), game.getLastModifiedDate(), ahead));
        return ahead;
    }
}

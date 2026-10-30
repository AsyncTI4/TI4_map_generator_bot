package ti4.ai.runtime;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;

/**
 * What the seats holding Produce en Masse did about it: one line per holder at each status phase, the docks every seat
 * built over the game, and (over the whole batch) the most production in one system each holder reached.
 */
final class ProduceTracker {

    private static final String PRODUCE_EN_MASSE = "pem";
    private static final int SCORED = 99;
    private static final Map<String, Integer> BATCH_BEST = new HashMap<>();

    private final Map<String, Integer> docksOnBoard = new HashMap<>();
    private final Map<String, Integer> docksBuilt = new LinkedHashMap<>();
    private final Map<String, Integer> bestProduction = new HashMap<>();

    void observe(Game game) {
        for (Player seat : game.getRealPlayers()) {
            int docks = docks(game, seat);
            Integer before = docksOnBoard.put(seat.getFaction(), docks);
            if (before != null && docks > before) docksBuilt.merge(seat.getFaction(), docks - before, Integer::sum);
        }
    }

    void record(Game game) {
        for (Player seat : game.getRealPlayers()) {
            boolean scored = seat.getSecretsScored().containsKey(PRODUCE_EN_MASSE);
            if (!scored && !seat.getSecretsUnscored().containsKey(PRODUCE_EN_MASSE)) continue;
            Tile best = null;
            int production = 0;
            for (Tile tile : game.getTileMap().values()) {
                int here = Helper.getProductionValue(seat, game, tile, false);
                if (here > production) {
                    production = here;
                    best = tile;
                }
            }
            bestProduction.merge(seat.getFaction(), scored ? SCORED : production, Math::max);
            System.out.printf(
                    "Produce holder %s %s r%d: best system %s production %d, docks %d, Space Dock II %s%s%n",
                    game.getName(),
                    seat.getFaction(),
                    game.getRound(),
                    best == null ? "none" : best.getPosition(),
                    production,
                    docks(game, seat),
                    seat.hasTech("sd2"),
                    scored ? ", scored" : "");
        }
    }

    void finish(Game game) {
        System.out.println("Docks built " + game.getName() + ": " + docksBuilt);
        synchronized (BATCH_BEST) {
            bestProduction.forEach(
                    (faction, best) -> BATCH_BEST.merge(game.getName() + "|" + faction, best, Math::max));
        }
    }

    static void printBatch() {
        Map<Integer, Integer> counts = new TreeMap<>();
        synchronized (BATCH_BEST) {
            BATCH_BEST.values().forEach(best -> counts.merge(best, 1, Integer::sum));
            BATCH_BEST.clear();
        }
        System.out.println("Best production in one system per Produce en Masse holder (99 is scored): " + counts);
    }

    private static int docks(Game game, Player seat) {
        int docks = 0;
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder holder : tile.getUnitHolders().values()) {
                docks += ti4.ai.eval.BoardView.count(holder, seat, UnitType.Spacedock);
            }
        }
        return docks;
    }
}

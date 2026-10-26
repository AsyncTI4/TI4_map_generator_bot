package ti4.ai.tactical;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import ti4.ai.eval.BoardView;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

public record FractureMarch(int round, String position) {

    private static final Map<String, FractureMarch> MARCHES = new ConcurrentHashMap<>();
    private static final int STALE_ROUNDS = 2;

    public static Optional<FractureMarch> of(Game game, Player seat) {
        FractureMarch march = MARCHES.get(key(game, seat));
        return march != null && march.alive(game, seat) ? Optional.of(march) : Optional.empty();
    }

    public static void record(Game game, Player seat, TacticalPlan plan) {
        if (TacticalPlanner.isMarchStep(game, seat, plan)) {
            remember(game, seat, plan.target());
        } else if (of(game, seat).filter(march -> leavesTheMarch(march, plan)).isPresent()) {
            MARCHES.remove(key(game, seat));
        }
    }

    public static void forget(String gameName) {
        MARCHES.keySet().removeIf(key -> key.startsWith(gameName + "|"));
    }

    public static Map<String, FractureMarch> snapshot(String gameName) {
        String prefix = gameName + "|";
        Map<String, FractureMarch> marches = new TreeMap<>();
        MARCHES.forEach((key, march) -> {
            if (key.startsWith(prefix)) marches.put(key.substring(prefix.length()), march);
        });
        return marches;
    }

    public static void restore(String gameName, Map<String, FractureMarch> marches) {
        forget(gameName);
        marches.forEach((faction, march) -> MARCHES.put(gameName + "|" + faction, march));
    }

    static void remember(Game game, Player seat, String position) {
        MARCHES.put(key(game, seat), new FractureMarch(game.getRound(), position));
    }

    private static String key(Game game, Player seat) {
        return game.getName() + "|" + seat.getFaction();
    }

    private static boolean leavesTheMarch(FractureMarch march, TacticalPlan plan) {
        return plan.moves().stream().anyMatch(move -> move.origin().equals(march.position()))
                && !plan.target().equals(march.position());
    }

    private boolean alive(Game game, Player seat) {
        Tile tile = game.getTileByPosition(position);
        if (tile == null || !BoardView.hasOwnShips(seat, tile)) return false;
        return game.getRound() - round <= STALE_ROUNDS;
    }
}

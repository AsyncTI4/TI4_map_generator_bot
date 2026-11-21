package ti4.ai.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import ti4.ai.eval.BoardView;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

/**
 * What Defy Space and Time holders did about the wormhole nexus in one self-play game: at each status phase whether the
 * nexus was locked or open, whether the holder had a ship in it and a Ghost Ship in hand, and whether the secret was
 * already scored. A game's lines are printed when its objective summary is taken, with how often the nexus flipped
 * from locked to open between status phases.
 */
final class NexusWatch {

    private static final String DEFY_SPACE_AND_TIME = "dfat";
    private static final String GHOST_SHIP = "ghost_ship";
    private static final String LOCKED_MALLICE = "lockedmallice";
    private static final String MALLICE = "mallice";

    private static final Map<Game, State> GAMES = Collections.synchronizedMap(new IdentityHashMap<>());

    private static final class State {
        final List<String> holders = new ArrayList<>();
        Boolean wasLocked;
        int flips;
    }

    private NexusWatch() {}

    static void record(Game game) {
        Optional<Tile> nexus = nexus(game);
        if (nexus.isEmpty()) return;
        State state = GAMES.computeIfAbsent(game, ignored -> new State());
        boolean locked = isLocked(nexus.get());
        synchronized (state) {
            if (Boolean.TRUE.equals(state.wasLocked) && !locked) state.flips++;
            state.wasLocked = locked;
            for (Player seat : game.getRealPlayers()) {
                if (!holds(seat)) continue;
                state.holders.add(String.format(
                        "Defy Space and Time %s r%d: nexus %s, ship in nexus %s, Ghost Ship held %s%s",
                        seat.getFaction(),
                        game.getRound(),
                        locked ? "locked" : "open",
                        BoardView.hasOwnShips(seat, nexus.get()),
                        seat.getActionCards().containsKey(GHOST_SHIP),
                        seat.getSecretsScored().containsKey(DEFY_SPACE_AND_TIME) ? ", scored" : ""));
            }
        }
    }

    static void print(Game game) {
        State state = GAMES.remove(game);
        if (state == null) return;
        synchronized (state) {
            if (state.holders.isEmpty() && state.flips == 0) return;
            StringBuilder out = new StringBuilder("Wormhole nexus flips: " + state.flips);
            state.holders.forEach(line -> out.append('\n').append(line));
            game.getRealPlayers().stream()
                    .filter(NexusWatch::holds)
                    .forEach(seat -> out.append('\n')
                            .append("Defy Space and Time ")
                            .append(seat.getFaction())
                            .append(" final: ")
                            .append(
                                    seat.getSecretsScored().containsKey(DEFY_SPACE_AND_TIME)
                                            ? "scored"
                                            : "not scored"));
            System.out.println(out);
        }
    }

    private static boolean holds(Player seat) {
        return seat.getSecretsUnscored().containsKey(DEFY_SPACE_AND_TIME)
                || seat.getSecretsScored().containsKey(DEFY_SPACE_AND_TIME);
    }

    private static Optional<Tile> nexus(Game game) {
        return game.getTileMap().values().stream()
                .filter(tile -> tile.getPlanetUnitHolders().stream()
                        .anyMatch(planet -> planet.getName().contains(MALLICE)))
                .findFirst();
    }

    private static boolean isLocked(Tile nexus) {
        return nexus.getPlanetUnitHolders().stream()
                .anyMatch(planet -> planet.getName().contains(LOCKED_MALLICE));
    }
}

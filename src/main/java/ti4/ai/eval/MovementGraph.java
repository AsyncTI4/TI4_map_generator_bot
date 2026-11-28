package ti4.ai.eval;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.TickMemo;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;

@UtilityClass
public class MovementGraph {

    public static final int NONE = Integer.MAX_VALUE;
    private static final String ANTIMASS_DEFLECTORS = "amd";
    private static final String MAGMUS_REACTOR = "mr";
    private static final String GASHLAI_PHYSIOLOGY = "gashlai_physiology";
    private static final String DISTANCES_MEMO = "movement.distances";
    private static final ThreadLocal<Boolean> ASSUMING_ANTIMASS = new ThreadLocal<>();

    public record Route(int calm, int rifting) {

        public boolean calmlyReaches() {
            return calm != NONE;
        }

        @Nullable
        Route within(int budget) {
            int riftingWithin = rifting <= budget ? rifting : NONE;
            if (riftingWithin == NONE) return null;
            return new Route(calm <= budget ? calm : NONE, riftingWithin);
        }
    }

    public static <T> T withAntimassDeflectors(Supplier<T> evaluation) {
        Boolean before = ASSUMING_ANTIMASS.get();
        ASSUMING_ANTIMASS.set(Boolean.TRUE);
        try {
            return evaluation.get();
        } finally {
            if (before == null) {
                ASSUMING_ANTIMASS.remove();
            } else {
                ASSUMING_ANTIMASS.set(before);
            }
        }
    }

    public static boolean assumesAntimass() {
        return Boolean.TRUE.equals(ASSUMING_ANTIMASS.get());
    }

    public static Map<String, Integer> distances(Game game, Player player, String origin, int budget) {
        return distances(game, player, origin, budget, Set.of());
    }

    public static Map<String, Integer> distances(
            Game game, Player player, String origin, int budget, Set<String> screened) {
        String key = searchKey(game, player, origin, screened);
        Map<String, Integer> found =
                searched(game, player, origin, budget, screened, key).within(budget);
        return TickMemo.checked(
                DISTANCES_MEMO,
                key + "#" + budget,
                found,
                () -> search(game, player, origin, budget, screened).within(budget));
    }

    public static Map<String, Integer> riftingDistances(Game game, Player player, String origin, int budget) {
        Searched searched = searched(game, player, origin, budget, Set.of(), searchKey(game, player, origin, Set.of()));
        Map<String, Integer> found = new LinkedHashMap<>();
        searched.routes().forEach((position, route) -> {
            if (route.rifting() <= budget) found.put(position, route.rifting());
        });
        return Collections.unmodifiableMap(found);
    }

    @Nullable
    static Integer distance(Game game, Player player, String origin, String target, int budget) {
        return distance(game, player, origin, target, budget, Set.of());
    }

    @Nullable
    static Integer distance(Game game, Player player, String origin, String target, int budget, Set<String> screened) {
        Integer distance = searched(game, player, origin, budget, screened, searchKey(game, player, origin, screened))
                .view()
                .get(target);
        return distance == null || distance > budget ? null : distance;
    }

    @Nullable
    public static Route route(Game game, Player player, String origin, String target, int budget) {
        return route(game, player, origin, target, budget, Set.of());
    }

    @Nullable
    static Route route(Game game, Player player, String origin, String target, int budget, Set<String> screened) {
        if (budget <= 0) return null;
        Map<String, Route> routes = searched(
                        game, player, origin, budget, screened, searchKey(game, player, origin, screened))
                .routes();
        Route route = EnemyShortcuts.shorter(
                routes.get(target), EnemyShortcuts.shortcut(game, player, origin, target, routes, screened));
        return route == null ? null : route.within(budget);
    }

    private static String searchKey(Game game, Player player, String origin, Set<String> screened) {
        return player.getFaction() + "#" + origin + "#" + new TreeSet<>(screened) + "#" + assumesAntimass() + "#"
                + MoveEnablers.key(game, player);
    }

    private static Searched searched(
            Game game, Player player, String origin, int budget, Set<String> screened, String key) {
        Map<String, Searched> memo = TickMemo.mapFor(game, DISTANCES_MEMO);
        Searched known = memo.get(key);
        if (known == null || known.budget() < budget) {
            known = search(game, player, origin, budget, screened);
            memo.put(key, known);
        }
        return known;
    }

    private record Searched(int budget, Map<String, Route> routes, Map<String, Integer> view) {
        Map<String, Integer> within(int asked) {
            if (asked >= budget) return view;
            Map<String, Integer> nearer = new LinkedHashMap<>();
            view.forEach((position, distance) -> {
                if (distance <= asked) nearer.put(position, distance);
            });
            return Collections.unmodifiableMap(nearer);
        }
    }

    private record State(String position, int bonusTaken, int rolled) {}

    private static Searched search(Game game, Player player, String origin, int budget, Set<String> screened) {
        Tile start = game.getTileByPosition(origin);
        if (start == null) return new Searched(budget, Map.of(), Map.of());
        Map<String, int[]> best = new LinkedHashMap<>();
        best.put(origin, new int[] {0, 0});
        Map<State, Integer> reached = new HashMap<>();
        State first = new State(origin, 0, 0);
        reached.put(first, 0);
        Deque<State> queue = new ArrayDeque<>();
        queue.add(first);
        while (!queue.isEmpty()) {
            State state = queue.removeFirst();
            int distance = reached.get(state);
            String position = state.position();
            Tile tile = game.getTileByPosition(position);
            if (tile == null) continue;
            if (!position.equals(origin) && (screened.contains(position) || blocksPassage(game, player, tile))) {
                continue;
            }
            boolean bonus = state.bonusTaken() == 0 && AnomalyRules.riftBonus(game, player, tile);
            int rolled = state.rolled() + (AnomalyRules.rollsLeaving(game, player, tile) ? 1 : 0);
            int stepped = distance + (bonus ? 0 : 1);
            if (rolled > 1 || stepped > budget) continue;
            for (String next : Adjacency.forMovement(game, player, position)) {
                Tile nextTile = game.getTileByPosition(next);
                if (!canTransit(game, player, nextTile)) continue;
                State nextState = new State(next, state.bonusTaken() + (bonus ? 1 : 0), rolled);
                Integer known = reached.get(nextState);
                if (known != null && known <= stepped) continue;
                reached.put(nextState, stepped);
                if (bonus) {
                    queue.addFirst(nextState);
                } else {
                    queue.addLast(nextState);
                }
                if (next.equals(origin) || !canEnter(game, player, nextTile)) continue;
                int effective = Math.max(1, stepped);
                int[] found = best.computeIfAbsent(next, ignored -> new int[] {NONE, NONE});
                if (rolled == 0) found[0] = Math.min(found[0], effective);
                found[1] = Math.min(found[1], effective);
            }
        }
        boolean originRolls = AnomalyRules.rollsLeaving(game, player, start);
        Map<String, Route> routes = new LinkedHashMap<>();
        Map<String, Integer> view = new LinkedHashMap<>();
        best.forEach((position, found) -> {
            routes.put(position, new Route(found[0], found[1]));
            int shown = originRolls ? found[1] : found[0];
            if (shown <= budget) view.put(position, shown);
        });
        return new Searched(budget, Collections.unmodifiableMap(routes), Collections.unmodifiableMap(view));
    }

    // TODO: per-ship passage through enemy ships (Yssaril flagship, Corsair, Myru Vos, Aether Passage, Luminous) is
    // not modelled; it needs a per-type graph.
    static boolean blocksPassage(Game game, Player player, @Nullable Tile tile) {
        if (tile == null) return true;
        boolean anomaliesIgnored = AnomalyRules.ignoresAnomalies(game, player);
        return (!anomaliesIgnored && AnomalyRules.isNebula(game, tile) && !AnomalyRules.passesNebulae(game, player))
                || (!anomaliesIgnored && tile.isSupernova() && !AnomalyRules.passesSupernovas(player))
                || (FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game) && !hasLightWaveDeflector(player))
                || AerieRules.blocks(game, player, tile);
    }

    public static boolean canEnter(Game game, Player player, @Nullable Tile tile) {
        if (tile == null || tile.getTileModel() == null || tile.getTileModel().isHyperlane()) return false;
        if (AnomalyRules.holdsCirclet(player) || AnomalyRules.ignoresAnomalies(game, player)) return true;
        if (tile.isSupernova()) return player.hasTech(MAGMUS_REACTOR);
        return !tile.isAsteroidField() || hasAntimassDeflectors(player);
    }

    static boolean canTransit(Game game, Player player, @Nullable Tile tile) {
        return canEnter(game, player, tile)
                || (tile != null && tile.isSupernova() && player.hasAbility(GASHLAI_PHYSIOLOGY));
    }

    private static boolean hasLightWaveDeflector(Player player) {
        return player.hasTech("lwd") || player.hasTech("absol_lwd");
    }

    private static boolean hasAntimassDeflectors(Player player) {
        return Boolean.TRUE.equals(ASSUMING_ANTIMASS.get())
                || player.hasTech(ANTIMASS_DEFLECTORS)
                || player.hasTech("absol_amd");
    }
}

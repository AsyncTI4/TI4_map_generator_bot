package ti4.ai.eval;

import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.TickMemo;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitType;
import ti4.model.WormholeModel.Wormhole;

@UtilityClass
public class EnemyShortcuts {

    private static final String SPATIAL_CONDUIT_CYLINDER = "scc";
    private static final String TAIVRA = "ghostagent";
    private static final String MENDOSA = "saaragent";
    private static final Set<String> MEMORIA = Set.of("nomad_flagship", "nomad_flagship2");
    private static final String FASTEST_MEMO = "shortcut.fastest";
    private static final String LINKS_MEMO = "shortcut.links";

    public static boolean imagined(Game game, Player seat) {
        return DecidingSeat.orSelf(game, seat) != seat;
    }

    @Nullable
    static MovementGraph.Route shortcut(
            Game game,
            Player seat,
            String origin,
            String target,
            Map<String, MovementGraph.Route> routes,
            Set<String> screened) {
        if (origin.equals(target) || !linksAnywhere(game, seat) || !imagined(game, seat)) return null;
        Tile from = game.getTileByPosition(origin);
        Tile to = game.getTileByPosition(target);
        if (from == null || to == null || !MovementGraph.canEnter(game, seat, to)) return null;
        MovementGraph.Route best = null;
        if (seat.hasTechReady(SPATIAL_CONDUIT_CYLINDER) && holdsUnits(seat, from) && holdsUnits(seat, to)) {
            best = leaving(game, seat, from, 0, 0);
        }
        if (taivraLinks(game, seat, to)) {
            for (String hub : Adjacency.wormholeSystems(game)) {
                if (hub.equals(target)) continue;
                best = shorter(best, viaHub(game, seat, origin, hub, routes, screened));
            }
        }
        return best;
    }

    private static boolean linksAnywhere(Game game, Player seat) {
        Map<String, Boolean> memo = TickMemo.mapFor(game, LINKS_MEMO);
        return memo.computeIfAbsent(
                seat.getFaction(),
                ignored -> seat.hasTechReady(SPATIAL_CONDUIT_CYLINDER) || seat.hasUnexhaustedLeader(TAIVRA));
    }

    private static boolean taivraLinks(Game game, Player seat, Tile to) {
        if (!seat.hasUnexhaustedLeader(TAIVRA)) return false;
        return to.getWormholes(game).stream().anyMatch(wormhole -> wormhole != Wormhole.DELTA);
    }

    @Nullable
    private static MovementGraph.Route viaHub(
            Game game,
            Player seat,
            String origin,
            String hub,
            Map<String, MovementGraph.Route> routes,
            Set<String> screened) {
        Tile tile = game.getTileByPosition(hub);
        if (tile == null) return null;
        if (hub.equals(origin)) return leaving(game, seat, tile, 0, 0);
        if (screened.contains(hub) || MovementGraph.blocksPassage(game, seat, tile)) return null;
        MovementGraph.Route toHub = routes.get(hub);
        if (toHub == null) return null;
        return leaving(game, seat, tile, toHub.calm(), toHub.rifting());
    }

    private static MovementGraph.Route leaving(Game game, Player seat, Tile hub, int calm, int rifting) {
        if (AnomalyRules.rollsLeaving(game, seat, hub)) {
            return new MovementGraph.Route(MovementGraph.NONE, plusOne(calm));
        }
        return new MovementGraph.Route(plusOne(calm), plusOne(rifting));
    }

    private static int plusOne(int distance) {
        return distance == MovementGraph.NONE ? MovementGraph.NONE : distance + 1;
    }

    @Nullable
    static MovementGraph.Route shorter(@Nullable MovementGraph.Route first, @Nullable MovementGraph.Route second) {
        if (first == null) return second;
        if (second == null) return first;
        return new MovementGraph.Route(
                Math.min(first.calm(), second.calm()), Math.min(first.rifting(), second.rifting()));
    }

    private static boolean holdsUnits(Player seat, Tile tile) {
        for (UnitHolder holder : tile.getUnitHolders().values()) {
            if (!holder.getUnitKeysForPlayer(seat).isEmpty()) return true;
        }
        return false;
    }

    public static int mendosaLift(Game game, Player seat) {
        if (!imagined(game, seat) || !seat.hasUnexhaustedLeader(MENDOSA)) return 0;
        Map<String, Integer> memo = TickMemo.mapFor(game, FASTEST_MEMO);
        return memo.computeIfAbsent("all", ignored -> fastestOnBoard(game));
    }

    private static int fastestOnBoard(Game game) {
        int fastest = 0;
        for (Player player : game.getRealPlayers()) {
            for (Tile tile : game.getTileMap().values()) {
                for (UnitType type :
                        BoardView.ships(BoardView.space(tile), player).keySet()) {
                    fastest = Math.max(fastest, ShipSpeed.printed(game, player, type));
                }
            }
        }
        return fastest;
    }

    public static boolean hasMemoria(Game game, Player seat) {
        return imagined(game, seat) && MEMORIA.stream().anyMatch(seat::ownsUnit);
    }

    @Nullable
    public static MovementGraph.Route memoria(
            Game game, Player seat, Tile origin, Tile target, int budget, Set<String> screened) {
        if (budget <= 0 || !hasMemoria(game, seat)) return null;
        MovementGraph.Route best = null;
        for (Tile tile : game.getTileMap().values()) {
            if (tile == origin || !holdsMech(seat, tile)) continue;
            if (tile == target) return leaving(game, seat, origin, 0, 0);
            if (screened.contains(tile.getPosition())
                    || !MovementGraph.canTransit(game, seat, tile)
                    || MovementGraph.blocksPassage(game, seat, tile)) {
                continue;
            }
            MovementGraph.Route onward =
                    MovementGraph.route(game, seat, tile.getPosition(), target.getPosition(), budget - 1, screened);
            if (onward == null) continue;
            MovementGraph.Route jump = leaving(game, seat, origin, 0, 0);
            best = shorter(
                    best,
                    new MovementGraph.Route(
                            jump.calm() == MovementGraph.NONE ? MovementGraph.NONE : plusOne(onward.calm()),
                            plusOne(jump.calm() == MovementGraph.NONE ? onward.calm() : onward.rifting())));
        }
        return best;
    }

    private static boolean holdsMech(Player seat, Tile tile) {
        for (UnitHolder holder : tile.getUnitHolders().values()) {
            if (BoardView.count(holder, seat, UnitType.Mech) > 0) return true;
        }
        return false;
    }
}

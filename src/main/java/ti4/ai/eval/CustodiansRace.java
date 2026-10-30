package ti4.ai.eval;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.scoring.SpendCost;
import ti4.ai.scoring.Wallet;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitType;
import ti4.model.Mapper;
import ti4.model.TechnologyModel;

public final class CustodiansRace {

    public enum Drive {
        OWNED,
        LIKELY
    }

    public enum Order {
        KNOWN,
        SEAT_FIRST,
        UNKNOWN
    }

    private enum Mode {
        OWNED,
        LIKELY,
        MIXED
    }

    public static final int COST = 6;
    static final double TAKE_FIRST = 0.8;
    private static final double BEATEN = 1 - TAKE_FIRST;
    private static final double LIKELY_FIRST = 0.95;
    private static final double COIN_FLIP = 0.5;
    private static final int MAX_HOPS = 4;
    private static final int UNREACHABLE = Integer.MAX_VALUE;
    private static final String GRAVITY_DRIVE = "gd";
    private static final String ACTION_PHASE = "action";

    private CustodiansRace() {}

    public static boolean open(Game game) {
        return custodiansTile(game) != null;
    }

    public static Snapshot of(Game game, Player seat) {
        return new Snapshot(game, seat);
    }

    @Nullable
    private static Tile custodiansTile(Game game) {
        for (Tile tile : game.getTileMap().values()) {
            if (tile.isMecatol(game) && tile.getPlanetUnitHolders().stream().anyMatch(BoardView::hasCustodians)) {
                return tile;
            }
        }
        return null;
    }

    public static final class Snapshot {

        private record Transport(Tile tile, int baseMove) {}

        private final Game game;
        private final Player seat;
        private final Tile mecatol;
        private final Map<String, Map<String, Integer>> reaches = new HashMap<>();
        private final Map<String, Integer> hops = new HashMap<>();
        private final Map<String, List<Transport>> transports = new HashMap<>();
        private final Map<String, OptionalInt> arrivals = new HashMap<>();
        private final Map<String, OptionalInt> idleArrivals = new HashMap<>();
        private final Map<String, Boolean> payable = new HashMap<>();
        private final Map<String, Boolean> covered = new HashMap<>();
        private final Map<String, Integer> projections = new HashMap<>();

        private Snapshot(Game game, Player seat) {
            this.game = game;
            this.seat = seat;
            this.mecatol = custodiansTile(game);
        }

        public boolean isOpen() {
            return mecatol != null;
        }

        public OptionalInt arrival(Player player) {
            return arrivals.computeIfAbsent(player.getFaction(), faction -> mixedArrival(player));
        }

        public OptionalInt arrivalWith(Player player, Drive drive) {
            Mode mode = drive == Drive.OWNED ? Mode.OWNED : Mode.LIKELY;
            return earliest(player, transport -> pureArrival(player, transport, mode));
        }

        public OptionalInt idleArrival(Player player) {
            return idleArrivals.computeIfAbsent(player.getFaction(), faction -> idle(player));
        }

        public OptionalInt arrivalFrom(Player player, Tile tile) {
            if (mecatol == null || coveredByOthers(player)) return OptionalInt.empty();
            int needed = hopsFrom(player, tile, bestTransportMove(player), Mode.LIKELY);
            if (needed == UNREACHABLE) return OptionalInt.empty();
            return OptionalInt.of(adjusted(player, baseRound() + Math.max(1, needed)));
        }

        public OptionalInt hopsToMecatol(Player player, Tile from) {
            if (mecatol == null) return OptionalInt.empty();
            int needed = hopsFrom(player, from, bestTransportMove(player), Mode.LIKELY);
            return needed == UNREACHABLE ? OptionalInt.empty() : OptionalInt.of(needed);
        }

        public boolean canLandThisRound(Player player) {
            if (mecatol == null || coveredByOthers(player)) return false;
            return transportsOf(player).stream()
                    .anyMatch(transport -> actsNow(player, transport.tile())
                            && hopsFrom(player, transport.tile(), transport.baseMove(), Mode.OWNED) <= 1);
        }

        public double winChance(Player seatOfInterest, int arrival, Order order) {
            double chance = 1.0;
            for (Player rival : game.getRealPlayers()) {
                if (rival == seatOfInterest) continue;
                OptionalInt theirs = arrival(rival);
                if (theirs.isEmpty() || theirs.getAsInt() > arrival) continue;
                chance *= theirs.getAsInt() < arrival ? BEATEN : sameRound(seatOfInterest, rival, order);
            }
            return chance;
        }

        public double raceValue(Player seatOfInterest, OptionalInt arrival, Order order) {
            if (mecatol == null || arrival.isEmpty()) return 0;
            double prize = ObjectiveValue.VICTORY_POINT_VALUE
                    + MecatolStake.holdValue(game, seatOfInterest)
                    + mecatolPlanetValue()
                    + IconStake.freeValue(game, seatOfInterest, mecatol);
            return prize * winChance(seatOfInterest, arrival.getAsInt(), order);
        }

        private double sameRound(Player seatOfInterest, Player rival, Order order) {
            if (seatOfInterest.hasTheZeroToken()) return LIKELY_FIRST;
            if (rival.hasTheZeroToken()) return BEATEN;
            return switch (order) {
                case KNOWN -> rival.getInitiative() < seatOfInterest.getInitiative() ? BEATEN : LIKELY_FIRST;
                case SEAT_FIRST -> LIKELY_FIRST;
                case UNKNOWN -> COIN_FLIP;
            };
        }

        private double mecatolPlanetValue() {
            return mecatol.getPlanetUnitHolders().stream()
                    .filter(BoardView::hasCustodians)
                    .mapToDouble(BoardView::planetValue)
                    .max()
                    .orElse(0);
        }

        private OptionalInt mixedArrival(Player player) {
            return earliest(player, transport -> mixedArrival(player, transport));
        }

        private int mixedArrival(Player player, Transport transport) {
            int base = baseRound();
            if (!actsNow(player, transport.tile())) {
                return arrivingNextRound(player, transport, base, Mode.LIKELY);
            }
            if (hopsFrom(player, transport.tile(), transport.baseMove(), Mode.OWNED) <= 1) return base;
            int needed = hopsFrom(player, transport.tile(), transport.baseMove(), Mode.MIXED);
            return needed == UNREACHABLE ? UNREACHABLE : base + needed - 1;
        }

        private int pureArrival(Player player, Transport transport, Mode mode) {
            int base = baseRound();
            if (!actsNow(player, transport.tile())) return arrivingNextRound(player, transport, base, mode);
            int needed = hopsFrom(player, transport.tile(), transport.baseMove(), mode);
            if (needed == UNREACHABLE) return UNREACHABLE;
            return needed <= 1 ? base : base + needed - 1;
        }

        private int arrivingNextRound(Player player, Transport transport, int base, Mode mode) {
            int needed = hopsFrom(player, transport.tile(), transport.baseMove(), mode);
            return needed == UNREACHABLE ? UNREACHABLE : base + Math.max(1, needed);
        }

        private OptionalInt idle(Player player) {
            int base = baseRound();
            return earliest(player, transport -> {
                if (actsNow(player, transport.tile())
                        && hopsFrom(player, transport.tile(), transport.baseMove(), Mode.OWNED) <= 1) {
                    return base;
                }
                return arrivingNextRound(player, transport, base, Mode.LIKELY);
            });
        }

        private OptionalInt earliest(Player player, ToIntFunction<Transport> arrivalOf) {
            if (mecatol == null || coveredByOthers(player)) return OptionalInt.empty();
            int best = UNREACHABLE;
            for (Transport transport : transportsOf(player)) {
                best = Math.min(best, arrivalOf.applyAsInt(transport));
            }
            return best == UNREACHABLE ? OptionalInt.empty() : OptionalInt.of(adjusted(player, best));
        }

        private int adjusted(Player player, int raw) {
            int base = baseRound();
            int arrival = raw;
            if (arrival == base && !canPayNow(player)) arrival = base + 1;
            if (arrival > base && projectedInfluence(player) < COST) arrival++;
            return arrival;
        }

        private boolean canPayNow(Player player) {
            return payable.computeIfAbsent(player.getFaction(), faction -> {
                if (player == seat) {
                    return ScoringReserve.planForCustodians(game, seat).isPresent()
                            || IconStake.behindCustodians(game, seat)
                                    && Wallet.of(game, seat).canPay(SpendCost.influence(COST));
                }
                return Wallet.of(game, player).canPay(SpendCost.influence(COST));
            });
        }

        private int projectedInfluence(Player player) {
            return projections.computeIfAbsent(player.getFaction(), faction -> {
                int owned = player.getPlanets().stream()
                        .mapToInt(planet -> BoardView.planetInfluence(game, planet))
                        .sum();
                return owned + player.getTg() * Wallet.tradeGoodValue(player) + reachableFreeInfluence(player);
            });
        }

        private int reachableFreeInfluence(Player player) {
            Set<String> tiles = new HashSet<>();
            for (Transport transport : transportsOf(player)) {
                int move = moveFor(player, transport.baseMove(), Mode.LIKELY, 1);
                tiles.addAll(reach(player, transport.tile().getPosition(), move).keySet());
            }
            int influence = 0;
            for (String position : tiles) {
                Tile tile = game.getTileByPosition(position);
                if (tile == null || BoardView.hasEnemyUnits(game, player, tile)) continue;
                for (Planet planet : tile.getPlanetUnitHolders()) {
                    boolean free =
                            BoardView.controller(game, planet.getName()) == null && !BoardView.hasCustodians(planet);
                    if (free) influence += Math.max(0, planet.getInfluence());
                }
            }
            return influence;
        }

        private boolean coveredByOthers(Player player) {
            return covered.computeIfAbsent(
                    player.getFaction(),
                    faction -> ButtonHelper.getPlayersWithPds2Cover(player, game, mecatol.getPosition()).stream()
                            .anyMatch(other -> other != player));
        }

        private boolean actsNow(Player player, Tile tile) {
            if (!ACTION_PHASE.equals(game.getPhaseOfGame())) return true;
            return !player.isPassed() && player.getTacticalCC() > 0 && !tile.hasPlayerCC(player);
        }

        private int baseRound() {
            String phase =
                    game.getPhaseOfGame() == null ? "" : game.getPhaseOfGame().toLowerCase();
            boolean afterActionPhase = phase.startsWith("status") || phase.startsWith("agenda");
            return game.getRound() + (afterActionPhase ? 1 : 0);
        }

        private List<Transport> transportsOf(Player player) {
            return transports.computeIfAbsent(player.getFaction(), faction -> {
                List<Transport> found = new ArrayList<>();
                for (Tile tile : game.getTileMap().values()) {
                    int move = transportMove(player, tile);
                    if (move > 0 && carriesGroundForces(player, tile)) found.add(new Transport(tile, move));
                }
                return found;
            });
        }

        private static int transportMove(Player player, Tile tile) {
            int move = 0;
            for (UnitType type : BoardView.ships(BoardView.space(tile), player).keySet()) {
                if (BoardView.MOVING_SHIPS.contains(type) && BoardView.capacity(player, type) > 0) {
                    move = Math.max(move, BoardView.moveValue(player, type));
                }
            }
            return move;
        }

        private static boolean carriesGroundForces(Player player, Tile tile) {
            for (UnitHolder holder : tile.getUnitHolders().values()) {
                if (BoardView.groundForces(holder, player) > 0) return true;
            }
            return false;
        }

        private int bestTransportMove(Player player) {
            int best = 0;
            for (Transport transport : transportsOf(player)) best = Math.max(best, transport.baseMove());
            return best > 0 ? best : BoardView.moveValue(player, UnitType.Carrier);
        }

        private int hopsFrom(Player player, Tile from, int baseMove, Mode mode) {
            String key = player.getFaction() + "|" + from.getPosition() + "|" + baseMove + "|" + mode;
            return hops.computeIfAbsent(key, ignored -> countHops(player, from, baseMove, mode));
        }

        // TODO: intermediate layers may pass through systems holding enemy ships, where a real move would stop
        private int countHops(Player player, Tile from, int baseMove, Mode mode) {
            String target = mecatol.getPosition();
            if (from.getPosition().equals(target)) return 0;
            Set<String> layer = Set.of(from.getPosition());
            for (int hop = 1; hop <= MAX_HOPS; hop++) {
                int move = moveFor(player, baseMove, mode, hop);
                Set<String> next = new HashSet<>();
                for (String position : layer)
                    next.addAll(reach(player, position, move).keySet());
                if (next.contains(target)) return hop;
                layer = next;
            }
            return UNREACHABLE;
        }

        private int moveFor(Player player, int baseMove, Mode mode, int hop) {
            boolean owned = player.hasTech(GRAVITY_DRIVE);
            boolean likely = owned || ownsPropulsionTech(player);
            return baseMove
                    + switch (mode) {
                        case OWNED -> owned ? 1 : 0;
                        case LIKELY -> likely ? 1 : 0;
                        case MIXED -> (hop == 1 ? owned : likely) ? 1 : 0;
                    };
        }

        private static boolean ownsPropulsionTech(Player player) {
            for (String id : player.getTechs()) {
                TechnologyModel tech = Mapper.getTech(id);
                if (tech != null && tech.isPropulsionTech()) return true;
            }
            return false;
        }

        private Map<String, Integer> reach(Player player, String position, int move) {
            return reaches.computeIfAbsent(
                    player.getFaction() + "|" + position + "|" + move,
                    key -> MovementGraph.reach(game, player, position, move));
        }
    }
}

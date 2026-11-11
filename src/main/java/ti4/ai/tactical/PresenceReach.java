package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.CombatModifiers.Side;
import ti4.ai.eval.CombatOdds.Force;
import ti4.ai.eval.FleetLimit;
import ti4.ai.eval.MovementGraph;
import ti4.ai.scoring.Footprint;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.ai.trace.Thinking;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitType;

final class PresenceReach {

    private static final double NEXT_ROUND_SHARE = 0.5;
    private static final double COVER_WIN_CHANCE = 0.6;
    private static final double EPSILON = 1e-9;
    private static final Map<UnitType, Integer> PAWN = Map.of(UnitType.Destroyer, 1);

    record Opening(String position, double gain, @Nullable Player occupant, Map<UnitType, Integer> helpers) {}

    private record Mover(
            String origin, UnitType type, double cost, List<Footprint.Move> moves, Map<UnitType, Integer> units) {}

    @Nullable
    private final Game game;

    @Nullable
    private final Player seat;

    @Nullable
    private final Tile dock;

    private final List<Opening> openings = new ArrayList<>();
    private final Map<String, Map<String, Integer>> reaches = new HashMap<>();
    private final Map<String, Double> wins = new HashMap<>();
    private final Map<String, Double> credits = new HashMap<>();

    private PresenceReach(@Nullable Game game, @Nullable Player seat, @Nullable Tile dock) {
        this.game = game;
        this.seat = seat;
        this.dock = dock;
    }

    static PresenceReach none() {
        return new PresenceReach(null, null, null);
    }

    static PresenceReach of(Game game, Player seat, Tile dock, DefenseOutlook defense) {
        try (Thinking.Span ignored = Thinking.enter("production.presence")) {
            ObjectiveValue objectives = new ObjectiveValue(game, seat);
            if (!objectives.caresAboutPresence()) return none();
            PresenceReach reach = new PresenceReach(game, seat, dock);
            reach.search(objectives, defense);
            return reach;
        }
    }

    List<Opening> openings() {
        return Collections.unmodifiableList(openings);
    }

    double credit(Opening opening, UnitType type) {
        return credits.computeIfAbsent(opening.position() + "|" + type, ignored -> computeCredit(opening, type));
    }

    private double computeCredit(Opening opening, UnitType type) {
        if (!dockReaches(opening.position(), type)) return 0;
        double share = NEXT_ROUND_SHARE * opening.gain();
        Player occupant = opening.occupant();
        if (occupant == null) return share;
        Map<UnitType, Integer> joined = new EnumMap<>(UnitType.class);
        joined.putAll(opening.helpers());
        joined.merge(type, 1, Integer::sum);
        double gained =
                win(opening.position(), occupant, joined) - win(opening.position(), occupant, opening.helpers());
        return share * Math.max(0, gained);
    }

    private void search(ObjectiveValue objectives, DefenseOutlook defense) {
        List<Mover> movers = movers(defense);
        Set<String> candidates = candidates(movers);
        Set<Mover> used = Collections.newSetFromMap(new IdentityHashMap<>());
        Footprint footprint = objectives.before();
        for (int step = 0; step < FleetLimit.ships(seat); step++) {
            double base = objectives.gain(footprint);
            Map<String, Double> gains = new HashMap<>();
            for (String position : candidates) {
                double gain = objectives.gain(footprint.withBuilt(position, PAWN)) - base;
                if (gain > EPSILON) gains.put(position, gain);
            }
            if (gains.isEmpty()) return;
            List<String> ordered = gains.keySet().stream()
                    .sorted(Comparator.comparingDouble((String position) -> -gains.get(position))
                            .thenComparing(Comparator.naturalOrder()))
                    .toList();
            Search state = new Search(objectives, footprint, base, movers, used);
            Optional<Footprint> covered = Optional.empty();
            for (String position : ordered) {
                covered = state.cover(position, gains.get(position));
                if (covered.isPresent()) break;
            }
            if (covered.isPresent()) {
                footprint = covered.get();
                continue;
            }
            Optional<Opening> opening = state.bestOpening(ordered, gains);
            if (opening.isEmpty()) return;
            openings.add(opening.get());
            candidates.remove(opening.get().position());
            footprint = footprint.withBuilt(opening.get().position(), PAWN);
        }
    }

    private final class Search {
        private final ObjectiveValue objectives;
        private final Footprint footprint;
        private final double base;
        private final List<Mover> movers;
        private final Set<Mover> used;

        Search(ObjectiveValue objectives, Footprint footprint, double base, List<Mover> movers, Set<Mover> used) {
            this.objectives = objectives;
            this.footprint = footprint;
            this.base = base;
            this.movers = movers;
            this.used = used;
        }

        Optional<Footprint> cover(String position, double gain) {
            Player occupant = occupant(position);
            List<Mover> reaching = movers.stream()
                    .filter(mover -> !used.contains(mover) && reaches(mover, position, true))
                    .filter(mover -> leavesNothingBehind(position, List.of(mover), gain))
                    .sorted(Comparator.comparingDouble(Mover::cost))
                    .toList();
            if (occupant == null) {
                if (reaching.isEmpty()) return Optional.empty();
                Mover cheapest = reaching.getFirst();
                used.add(cheapest);
                return Optional.of(footprint.after(position, cheapest.moves(), List.of()));
            }
            List<Mover> group = new ArrayList<>();
            for (Mover mover : attackers(reaching, position)) {
                group.add(mover);
                if (win(position, occupant, unitsOf(group)) < COVER_WIN_CHANCE) continue;
                if (!leavesNothingBehind(position, group, gain)) return Optional.empty();
                used.addAll(group);
                return Optional.of(footprint.after(position, movesOf(group), List.of()));
            }
            return Optional.empty();
        }

        private List<Mover> attackers(List<Mover> reaching, String position) {
            List<Mover> attackers = new ArrayList<>(reaching.stream()
                    .filter(mover -> reaches(mover, position, false))
                    .toList());
            reaching.stream()
                    .filter(mover -> !reaches(mover, position, false))
                    .max(Comparator.comparingDouble(Mover::cost))
                    .ifPresent(attackers::add);
            return attackers;
        }

        Optional<Opening> bestOpening(List<String> ordered, Map<String, Double> gains) {
            Opening best = null;
            double bestCredit = EPSILON;
            for (String position : ordered) {
                if (!dockReachesAny(position)) continue;
                Player occupant = occupant(position);
                List<Mover> helpers = occupant == null
                        ? List.of()
                        : movers.stream()
                                .filter(mover -> !used.contains(mover) && reaches(mover, position, false))
                                .filter(mover -> leavesNothingBehind(position, List.of(mover), gains.get(position)))
                                .toList();
                Opening opening = new Opening(position, gains.get(position), occupant, unitsOf(helpers));
                double credit = buildableTypes().stream()
                        .mapToDouble(type -> computeCredit(opening, type))
                        .max()
                        .orElse(0);
                if (credit > bestCredit) {
                    best = opening;
                    bestCredit = credit;
                }
            }
            return Optional.ofNullable(best);
        }

        private boolean leavesNothingBehind(String position, List<Mover> group, double gain) {
            return objectives.gain(footprint.after(position, movesOf(group), List.of())) - base >= gain - EPSILON;
        }
    }

    private List<Mover> movers(DefenseOutlook defense) {
        List<Mover> movers = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            UnitHolder space = BoardView.space(tile);
            int ships = BoardView.nonFighterShips(space, seat);
            if (ships == 0) continue;
            int fighters = BoardView.count(space, seat, UnitType.Fighter);
            List<UnitMove> leaving = new ArrayList<>();
            Map<UnitType, Integer> stack = BoardView.ships(space, seat);
            for (UnitType type : cheapestFirst(stack.keySet())) {
                if (type == UnitType.Fighter || BoardView.moveValue(seat, type) <= 0) continue;
                int carried = ships == 1 ? Math.min(BoardView.capacity(seat, type), fighters) : 0;
                for (int ship = 0; ship < stack.get(type); ship++) {
                    List<UnitMove> released = defense.holdBack(tile, leaving, shipMove(tile, type, carried));
                    if (released.stream().noneMatch(move -> move.type() == type)) break;
                    movers.add(mover(tile.getPosition(), type, released));
                    leaving.addAll(released);
                }
            }
        }
        return movers;
    }

    private List<UnitType> cheapestFirst(Set<UnitType> types) {
        return types.stream()
                .sorted(Comparator.comparingDouble(type -> BoardView.model(seat, type)
                        .map(model -> (double) model.getCost())
                        .orElse(0.0)))
                .toList();
    }

    private static List<UnitMove> shipMove(Tile tile, UnitType type, int carriedFighters) {
        List<UnitMove> wanted = new ArrayList<>();
        wanted.add(new UnitMove(tile.getPosition(), BoardView.SPACE, type, 1));
        if (carriedFighters > 0) {
            wanted.add(new UnitMove(tile.getPosition(), BoardView.SPACE, UnitType.Fighter, carriedFighters));
        }
        return wanted;
    }

    private Mover mover(String origin, UnitType type, List<UnitMove> released) {
        Map<UnitType, Integer> units = new EnumMap<>(UnitType.class);
        List<Footprint.Move> moves = new ArrayList<>();
        for (UnitMove move : released) {
            units.merge(move.type(), move.count(), Integer::sum);
            moves.add(new Footprint.Move(move.origin(), move.holder(), move.type(), move.count()));
        }
        double cost = BoardView.model(seat, type)
                .map(model -> (double) model.getCost())
                .orElse(0.0);
        return new Mover(origin, type, cost, moves, units);
    }

    private Set<String> candidates(List<Mover> movers) {
        Set<String> positions = new LinkedHashSet<>();
        for (Mover mover : movers) {
            positions.addAll(reach(mover.origin(), BoardView.moveValueWithBoosts(seat, mover.type()))
                    .keySet());
        }
        for (UnitType type : buildableTypes()) {
            positions.addAll(reach(dock.getPosition(), BoardView.moveValueWithBoosts(seat, type))
                    .keySet());
        }
        positions.remove(dock.getPosition());
        positions.removeIf(position -> !openToPresence(position));
        return positions;
    }

    private boolean openToPresence(String position) {
        Tile tile = game.getTileByPosition(position);
        if (tile == null) return false;
        UnitHolder space = BoardView.space(tile);
        if (!BoardView.ships(space, seat).isEmpty()) return false;
        long rivals = game.getRealPlayers().stream()
                .filter(player ->
                        player != seat && !BoardView.ships(space, player).isEmpty())
                .count();
        return rivals < 2;
    }

    @Nullable
    private Player occupant(String position) {
        UnitHolder space = BoardView.space(game.getTileByPosition(position));
        return game.getRealPlayers().stream()
                .filter(player ->
                        player != seat && !BoardView.ships(space, player).isEmpty())
                .findFirst()
                .orElse(null);
    }

    private List<UnitType> buildableTypes() {
        return BoardView.MOVING_SHIPS.stream()
                .filter(type -> seat.getUnitByType(type) != null && BoardView.moveValue(seat, type) > 0)
                .toList();
    }

    private boolean reaches(Mover mover, String position, boolean boosted) {
        int move =
                boosted ? BoardView.moveValueWithBoosts(seat, mover.type()) : BoardView.moveValue(seat, mover.type());
        Integer distance = reach(mover.origin(), BoardView.moveValueWithBoosts(seat, mover.type()))
                .get(position);
        return distance != null && distance <= move;
    }

    private boolean dockReaches(String position, UnitType type) {
        int move = BoardView.moveValueWithBoosts(seat, type);
        if (seat.getUnitByType(type) == null || BoardView.moveValue(seat, type) <= 0) return false;
        Integer distance = reach(dock.getPosition(), move).get(position);
        return distance != null && distance <= move;
    }

    private boolean dockReachesAny(String position) {
        return buildableTypes().stream().anyMatch(type -> dockReaches(position, type));
    }

    private Map<String, Integer> reach(String origin, int move) {
        return reaches.computeIfAbsent(origin + "|" + move, ignored -> MovementGraph.reach(game, seat, origin, move));
    }

    private double win(String position, Player occupant, Map<UnitType, Integer> units) {
        if (units.isEmpty()) return 0;
        return wins.computeIfAbsent(position + "|" + units, ignored -> resolve(position, occupant, units));
    }

    // TODO: space cannon at the target and Nekro flagship ground forces fighting in space are not modelled, so win
    // chances run low.
    private double resolve(String position, Player occupant, Map<UnitType, Integer> units) {
        Tile tile = game.getTileByPosition(position);
        UnitHolder space = BoardView.space(tile);
        Side own = new Side(seat, units);
        Side enemy = new Side(occupant, BoardView.ships(space, occupant));
        Force attack =
                CombatForces.force(seat, CombatForces.combatants(game, tile, space, own, enemy, Map.of()), false);
        Force hold = CombatForces.force(
                occupant,
                CombatForces.combatants(game, tile, space, enemy, own, CombatForces.damaged(space, enemy)),
                false);
        return CombatForces.win(attack, hold, true);
    }

    private static Map<UnitType, Integer> unitsOf(List<Mover> group) {
        Map<UnitType, Integer> units = new EnumMap<>(UnitType.class);
        for (Mover mover : group) mover.units().forEach((type, count) -> units.merge(type, count, Integer::sum));
        return units;
    }

    private static List<Footprint.Move> movesOf(List<Mover> group) {
        return group.stream().flatMap(mover -> mover.moves().stream()).toList();
    }
}

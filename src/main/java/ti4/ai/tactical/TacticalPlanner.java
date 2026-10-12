package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.CombatModifiers.Side;
import ti4.ai.eval.MovementGraph;
import ti4.ai.eval.Territory;
import ti4.ai.explore.ExploreDeck;
import ti4.ai.explore.ExploreOutlook;
import ti4.ai.explore.ExploreSite;
import ti4.ai.promissory.PlayAreaNotes;
import ti4.ai.scoring.Footprint;
import ti4.ai.scoring.ObjectivePolicy;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.scoring.SpendCost;
import ti4.ai.secrets.ActionSecretTiming;
import ti4.ai.tactical.DefenseOutlook.Blend;
import ti4.ai.tactical.IncomingForces.Exclusion;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.model.UnitModel;

@UtilityClass
public class TacticalPlanner {

    public static final double MIN_SCORE = 1.0;
    private static final double MIN_WIN = 0.5;
    private static final double DESPERATE_MIN_WIN = 0.3;
    private static final double DESPERATE_THREAT = 0.5;
    private static final double HOME_LANDING_THREAT = 0.25;
    private static final double HOME_LANDING_SHARE = 0.5;
    private static final int FINALISTS = 2;
    private static final int MAX_ORIGINS = 3;
    private static final boolean STAGE_ENABLED = true;
    private static final double STAGE_SHARE = 0.35;
    private static final double STAGE_MIN_DENIAL = 0.5;
    private static final double STAGE_FUTURE_MARGIN = 1.0;
    private static final double DIPLOMACY_DISCOUNT = 0.5;
    private static final double STAGE_SHIP_STAKE = 0.5;
    private static final int STAGE_TARGETS = 3;
    private static final int STAGING_TILES_PER_TARGET = 2;
    static final double ATTACK_MIN_SCORE = 4.0;
    private static final double DISTANCE_COST = 0.2;
    private static final double PLANET_TEMPO_VALUE = 0.8;
    private static final double LANE_PLANET_BONUS = 1.5;
    private static final double SLICE_PLANET_BONUS = 1.0;
    private static final double BORDER_PLANET_BONUS = 0.3;
    private static final double FOREIGN_PLANET_PENALTY = 1.0;
    private static final double FRONTIER_CARD_VALUE = 1.5;
    private static final double FRONTIER_POSITION_VALUE = 1.0;
    private static final String DARK_ENERGY_TAP = "det";
    private static final String HAZARDOUS = "hazardous";
    private static final int INFANTRY = 0;
    private static final int MECH = 1;
    private static final int LAST_EARLY_ROUND = 4;
    private static final double EARLY_GAME_FADE_PER_ROUND = 0.25;
    private static final double CUSTODIANS_VALUE = ObjectiveValue.VICTORY_POINT_VALUE;
    private static final String DARKEN_THE_SKIES = "dts";
    private static final String CONQUER_THE_WEAK = "conquer";
    private static final String MAKE_AN_EXAMPLE = "mew";
    private static final int DEMONSTRATION_SHIPS = 3;
    private static final double PATIENT_SHARE = 0.25;
    private static final double GROUP_MOVE_COST = 0.3;
    private static final double FORWARD_GROUND_FORCE_VALUE = 0.5;
    private static final double FIGHTER_ESCORT_VALUE = 0.15;
    private static final double SHIP_COST_WEIGHT = 0.05;
    private static final double HOME_DEFENCE_VALUE = 6.0;
    private static final int CUSTODIANS_COST = 6;
    static final int HOME_GARRISON = 1;
    private static final int REINFORCEMENT_BUNDLE = 3;
    private static final double CHEAPEST_UNIT_COST = 0.5;
    private static final int MECATOL_GARRISON = 1;
    private static final List<UnitType> TRANSPORT_PREFERENCE =
            List.of(UnitType.Carrier, UnitType.Flagship, UnitType.Dreadnought, UnitType.Warsun);
    private static final List<UnitType> GROUND_FORCE_ORDER = List.of(UnitType.Infantry, UnitType.Mech);
    private static final List<UnitType> POSITION_SHIPS = List.of(
            UnitType.Destroyer,
            UnitType.Cruiser,
            UnitType.Carrier,
            UnitType.Dreadnought,
            UnitType.Flagship,
            UnitType.Warsun);

    public static Optional<TacticalPlan> best(Game game, Player seat) {
        return best(game, seat, List.of());
    }

    public static Optional<TacticalPlan> best(Game game, Player seat, List<SetupHint> hints) {
        if (seat.getTacticalCC() <= 0) return Optional.empty();
        int reservedTokens = ScoringReserve.of(game, seat).tokens();
        if (seat.getTacticalCC() + seat.getStrategicCC() - 1 < reservedTokens) return Optional.empty();
        Context context = new Context(game, seat, Set.of(), hints);
        List<TacticalPlan> plans = candidates(context, tile -> activatable(game, seat, tile));
        if (plans.isEmpty()) return Optional.empty();
        TacticalPlan top = plans.getFirst();
        boolean anotherActionAfter =
                seat.getTacticalCC() >= 2 && seat.getTacticalCC() + seat.getStrategicCC() - 2 >= reservedTokens;
        if (top.kind() != Kind.PRODUCE || !anotherActionAfter) return Optional.of(top);
        return plans.stream()
                .filter(plan -> plan.kind() != Kind.PRODUCE)
                .filter(plan ->
                        plan.moves().stream().anyMatch(move -> move.origin().equals(top.target())))
                .findFirst()
                .or(() -> Optional.of(top));
    }

    public static List<TacticalPlan> ranked(Game game, Player seat) {
        return candidates(new Context(game, seat), tile -> activatable(game, seat, tile));
    }

    public static List<TacticalPlan> rankedFreeing(Game game, Player seat, String freedPosition) {
        Set<String> freed = Set.of(freedPosition);
        return candidates(
                new Context(game, seat, freed),
                tile -> freed.contains(tile.getPosition()) || activatable(game, seat, tile));
    }

    public static double bestScore(List<TacticalPlan> plans) {
        return plans.isEmpty() ? 0 : plans.getFirst().score();
    }

    private static boolean activatable(Game game, Player seat, Tile tile) {
        return !tile.hasPlayerCC(seat) && ButtonHelper.canActivateTile(game, seat, tile);
    }

    private static List<TacticalPlan> candidates(Context context, Predicate<Tile> activatable) {
        List<TacticalPlan> plans = new ArrayList<>(context.game.getTileMap().values().stream()
                .filter(activatable)
                .map(tile -> bestFor(context, tile))
                .flatMap(Optional::stream)
                .toList());
        plans.addAll(stage(context, activatable));
        return plans.stream()
                .filter(plan -> plan.score() >= MIN_SCORE)
                .sorted(Comparator.comparingDouble(TacticalPlan::score).reversed())
                .toList();
    }

    public static Optional<TacticalPlan> bestForWarfare(Game game, Player seat) {
        Context context = new Context(game, seat);
        return game.getTileMap().values().stream()
                .filter(tile -> tile.hasPlayerCC(seat) || ButtonHelper.canActivateTile(game, seat, tile))
                .map(tile -> bestFor(context, tile))
                .flatMap(Optional::stream)
                .filter(plan -> plan.score() >= MIN_SCORE)
                .max(Comparator.comparingDouble(TacticalPlan::score));
    }

    public static boolean worthProducingAt(Game game, Player seat, Tile tile) {
        return produce(new Context(game, seat), tile)
                .filter(plan -> plan.score() >= MIN_SCORE)
                .isPresent();
    }

    public static Optional<TacticalPlan> forTarget(Game game, Player seat, String target) {
        Tile tile = game.getTileByPosition(target);
        return tile == null ? Optional.empty() : bestFor(new Context(game, seat), tile);
    }

    private static Optional<TacticalPlan> bestFor(Context context, Tile tile) {
        if (!MovementGraph.canEnter(context.game, context.seat, tile)) return Optional.empty();
        List<TacticalPlan> plans = new ArrayList<>();
        expand(context, tile).ifPresent(plans::add);
        attack(context, tile).ifPresent(plans::add);
        produce(context, tile).ifPresent(plans::add);
        position(context, tile).ifPresent(plans::add);
        defend(context, tile).ifPresent(plans::add);
        return plans.stream()
                .map(plan -> plan.withScore(PlayAreaNotes.adjustedScore(context.game, context.seat, tile, plan)
                        + SetupHint.bonusFor(context.hints, plan)))
                .max(Comparator.comparingDouble(TacticalPlan::score));
    }

    private static Optional<TacticalPlan> expand(Context context, Tile tile) {
        Game game = context.game;
        Player seat = context.seat;
        if (BoardView.hasEnemyUnits(game, seat, tile)) return Optional.empty();
        List<Planet> free = tile.getPlanetUnitHolders().stream()
                .filter(planet -> BoardView.controller(game, planet.getName()) == null)
                .filter(planet -> !BoardView.hasCustodians(planet) || context.canPayCustodians())
                .sorted(Comparator.comparingDouble(BoardView::planetValue).reversed())
                .toList();
        if (free.isEmpty()) return Optional.empty();
        TacticalPlan best = landInPlace(context, tile, free).orElse(null);
        for (Tile origin : context.origins()) {
            if (origin == tile) continue;
            Optional<TacticalPlan> plan = expandFrom(context, tile, origin, free);
            if (plan.isPresent() && (best == null || plan.get().score() > best.score())) best = plan.get();
        }
        if (best == null || context.coveredByEnemySpaceCannon(tile)) return Optional.empty();
        return Optional.of(best);
    }

    private static Optional<TacticalPlan> landInPlace(Context context, Tile tile, List<Planet> free) {
        Player seat = context.seat;
        UnitHolder space = BoardView.space(tile);
        List<UnitMove> groundForces = new ArrayList<>();
        addMove(
                groundForces,
                tile.getPosition(),
                BoardView.SPACE,
                UnitType.Infantry,
                BoardView.count(space, seat, UnitType.Infantry));
        addMove(
                groundForces,
                tile.getPosition(),
                BoardView.SPACE,
                UnitType.Mech,
                BoardView.count(space, seat, UnitType.Mech));
        int landed = Math.min(unitCount(groundForces), free.size());
        if (landed == 0) return Optional.empty();
        List<Planet> targets = free.subList(0, landed);
        List<UnitMove> landing = take(mechsFirstForHazardousPlanets(groundForces, targets), landed);
        Map<Planet, int[]> forces =
                distribute(targets, countOf(landing, UnitType.Infantry), countOf(landing, UnitType.Mech));
        Map<String, Integer> landings = new LinkedHashMap<>();
        double value = 0;
        for (Planet planet : targets) {
            int[] split = forces.get(planet);
            landings.put(planet.getName(), split[INFANTRY] + split[MECH]);
            value += BoardView.planetValue(planet)
                    + planetTempo(context.game)
                    + context.exploreValue(tile, planet, split[INFANTRY], split[MECH]);
            if (BoardView.hasCustodians(planet)) value += CUSTODIANS_VALUE;
        }
        value += context.objectiveGain(tile, List.of(), landings.keySet());
        return Optional.of(new TacticalPlan(Kind.EXPAND, tile.getPosition(), List.of(), landings, value));
    }

    private static Optional<TacticalPlan> expandFrom(Context context, Tile tile, Tile origin, List<Planet> free) {
        Optional<TacticalPlan> first = expandFrom(context, tile, origin, free, List.of());
        return secondPass(context, origin, first, leaving -> expandFrom(context, tile, origin, free, leaving));
    }

    private static Optional<TacticalPlan> expandFrom(
            Context context, Tile tile, Tile origin, List<Planet> free, List<UnitMove> shipsLeaving) {
        Player seat = context.seat;
        List<UnitMove> groundForces =
                availableGroundForces(context.game, seat, origin, context.defense(), shipsLeaving);
        int available = unitCount(groundForces);
        if (available == 0) return Optional.empty();
        int hazardous = (int) free.stream().filter(TacticalPlanner::isHazardous).count();
        List<UnitMove> transports = transports(context, origin, tile, Math.min(available, free.size() + hazardous));
        if (transports.isEmpty()) return Optional.empty();
        int capacity = capacityOf(seat, transports);
        int forward = context.forwardNeed(tile, transports.getFirst().type());
        int reserved = context.reservedForOtherTransports(origin, tile, transports);
        int landed = Math.min(Math.min(capacity, available), free.size());
        int loadable = Math.max(landed, Math.min(capacity, available - reserved));
        List<Planet> targets = free.subList(0, landed);
        List<UnitMove> ordered = mechsFirstForHazardousPlanets(groundForces, targets);
        List<UnitMove> cargo = take(ordered, loadable);
        Optional<List<UnitMove>> legal = loadedMoves(context, origin, tile, transports, cargo);
        if (legal.isEmpty()) {
            cargo = take(ordered, landed);
            legal = loadedMoves(context, origin, tile, transports, cargo);
        }
        if (legal.isEmpty()) return Optional.empty();
        Map<Planet, int[]> forces =
                distribute(targets, countOf(cargo, UnitType.Infantry), countOf(cargo, UnitType.Mech));
        Map<String, Integer> landings = new LinkedHashMap<>();
        double value = 0;
        for (Planet planet : targets) {
            int[] landing = forces.get(planet);
            landings.put(planet.getName(), landing[INFANTRY] + landing[MECH]);
            value += BoardView.planetValue(planet)
                    + planetTempo(context.game)
                    + context.exploreValue(tile, planet, landing[INFANTRY], landing[MECH]);
            if (BoardView.hasCustodians(planet)) value += CUSTODIANS_VALUE;
            value += context.expansionZoneBonus(tile);
        }
        int spareForces = unitCount(cargo) - landed;
        value += context.offense()
                .expansionImperial(tile, targets, defendersAfterExpanding(context, tile, transports, forces));
        value += FORWARD_GROUND_FORCE_VALUE * Math.min(spareForces, forward);
        value += FIGHTER_ESCORT_VALUE * countOf(legal.get(), UnitType.Fighter);
        value += context.objectiveGain(tile, legal.get(), landings.keySet());
        double score = value
                - DISTANCE_COST
                        * context.distance(origin, tile, transports.getFirst().type())
                - GROUP_MOVE_COST * (movedNonFighterShips(transports) - 1)
                - context.defense().leaveCost(origin, legal.get());
        return Optional.of(new TacticalPlan(Kind.EXPAND, tile.getPosition(), legal.get(), landings, score));
    }

    private static Defenders defendersAfterExpanding(
            Context context, Tile tile, List<UnitMove> transports, Map<Planet, int[]> forces) {
        Defenders ours = context.defense().defendersAt(tile);
        for (UnitMove transport : transports) ours = ours.plusShips(transport.type(), transport.count(), 0);
        for (Map.Entry<Planet, int[]> landing : forces.entrySet()) {
            ours = ours.plusGround(landing.getKey().getName(), UnitType.Infantry, landing.getValue()[INFANTRY]);
            ours = ours.plusGround(landing.getKey().getName(), UnitType.Mech, landing.getValue()[MECH]);
        }
        return ours;
    }

    private static List<UnitMove> transports(Context context, Tile origin, Tile tile, int wanted) {
        Player seat = context.seat;
        UnitHolder space = BoardView.space(origin);
        List<UnitMove> transports = new ArrayList<>();
        int capacity = 0;
        for (UnitType type : TRANSPORT_PREFERENCE) {
            if (BoardView.capacity(seat, type) <= 0 || !context.reachesWithGravityDrive(origin, tile, type)) continue;
            int ships = BoardView.undamaged(space, seat, type);
            int taken = 0;
            while (taken < ships && (capacity == 0 || capacity < wanted)) {
                taken++;
                capacity += BoardView.capacity(seat, type);
            }
            if (taken > 0) transports.add(new UnitMove(origin.getPosition(), BoardView.SPACE, type, taken));
        }
        return transports;
    }

    private static List<UnitMove> mechsFirstForHazardousPlanets(List<UnitMove> forces, List<Planet> targets) {
        long hazardous = targets.stream().filter(TacticalPlanner::isHazardous).count();
        List<UnitMove> mechs = ofType(forces, UnitType.Mech);
        int forHazardous = (int) Math.min(hazardous, unitCount(mechs));
        List<UnitMove> ordered = new ArrayList<>(take(mechs, forHazardous));
        ordered.addAll(ofType(forces, UnitType.Infantry));
        ordered.addAll(skip(mechs, forHazardous));
        return ordered;
    }

    private static List<UnitMove> ofType(List<UnitMove> moves, UnitType type) {
        return moves.stream().filter(move -> move.type() == type).toList();
    }

    private static int countOf(List<UnitMove> moves, UnitType type) {
        return unitCount(ofType(moves, type));
    }

    private static boolean isHazardous(Planet planet) {
        return planet.getPlanetTypes().contains(HAZARDOUS);
    }

    private static Map<Planet, int[]> distribute(List<Planet> targets, int infantry, int mechs) {
        Map<Planet, int[]> forces = new LinkedHashMap<>();
        targets.forEach(planet -> forces.put(planet, new int[2]));
        int[] left = {infantry, mechs};
        for (Planet planet : targets) {
            if (isHazardous(planet)) give(forces.get(planet), left, MECH);
        }
        for (Planet planet : targets) {
            if (total(forces.get(planet)) == 0) giveEither(forces.get(planet), left);
        }
        for (Planet planet : targets) {
            if (isHazardous(planet) && forces.get(planet)[MECH] == 0) give(forces.get(planet), left, INFANTRY);
        }
        while (left[INFANTRY] + left[MECH] > 0) {
            for (Planet planet : targets) giveEither(forces.get(planet), left);
        }
        return forces;
    }

    private static void giveEither(int[] landing, int[] left) {
        if (left[INFANTRY] > 0) {
            give(landing, left, INFANTRY);
        } else {
            give(landing, left, MECH);
        }
    }

    private static void give(int[] landing, int[] left, int type) {
        if (left[type] <= 0) return;
        left[type]--;
        landing[type]++;
    }

    private static int total(int[] landing) {
        return landing[INFANTRY] + landing[MECH];
    }

    private static Optional<List<UnitMove>> loadedMoves(
            Context context, Tile origin, Tile tile, List<UnitMove> transports, List<UnitMove> cargo) {
        List<UnitMove> moves = new ArrayList<>(transports);
        moves.addAll(cargo);
        int spare = capacityOf(context.seat, transports) - unitCount(cargo);
        int fighters = Math.min(spare, escortFighters(context, origin, moves));
        if (fighters > 0) moves.add(new UnitMove(origin.getPosition(), BoardView.SPACE, UnitType.Fighter, fighters));
        return legalMoves(context, origin, tile, moves);
    }

    private static int escortFighters(Context context, Tile origin, List<UnitMove> leaving) {
        int fighters = BoardView.count(BoardView.space(origin), context.seat, UnitType.Fighter);
        if (fighters == 0) return 0;
        List<UnitMove> all = List.of(new UnitMove(origin.getPosition(), BoardView.SPACE, UnitType.Fighter, fighters));
        return countOf(context.defense().holdBack(origin, leaving, all), UnitType.Fighter);
    }

    private static Optional<TacticalPlan> secondPass(
            Context context,
            Tile origin,
            Optional<TacticalPlan> first,
            Function<List<UnitMove>, Optional<TacticalPlan>> again) {
        if (first.isEmpty()) return first;
        DefenseOutlook defense = context.defense();
        if (!defense.isGuarded(origin)
                || defense.leaveCost(origin, first.get().moves()) <= DefenseOutlook.GARRISON_TOLERANCE) {
            return first;
        }
        return better(first, again.apply(departingShips(first.get().moves())));
    }

    private static Optional<TacticalPlan> better(Optional<TacticalPlan> first, Optional<TacticalPlan> second) {
        if (second.isEmpty()) return first;
        if (first.isEmpty()) return second;
        return second.get().score() > first.get().score() ? second : first;
    }

    private static List<UnitMove> departingShips(List<UnitMove> moves) {
        return moves.stream()
                .filter(move -> BoardView.SPACE.equals(move.holder()))
                .filter(move -> BoardView.MOVING_SHIPS.contains(move.type()) || move.type() == UnitType.Fighter)
                .toList();
    }

    private static Optional<TacticalPlan> attack(Context context, Tile tile) {
        return context.attackPlan(tile);
    }

    private static Optional<TacticalPlan> bestAttack(Context context, Tile tile) {
        Player opponent = singleOpponent(context.game, context.seat, tile);
        if (opponent == null) return Optional.empty();
        return finalPlan(context, tile, candidatePool(context, tile, opponent));
    }

    private static List<Candidate> candidatePool(Context context, Tile tile, Player opponent) {
        List<Candidate> pool = new ArrayList<>();
        for (Tile origin : context.origins()) {
            if (origin != tile) pool.addAll(candidatesFrom(context, tile, origin, opponent));
        }
        pool.addAll(combinations(context, tile, opponent, pool));
        return pool;
    }

    private static List<Candidate> combinations(Context context, Tile tile, Player opponent, List<Candidate> singles) {
        Map<Tile, Candidate> bestPerOrigin = new LinkedHashMap<>();
        for (Candidate single : singles) {
            Tile origin = single.parts().getFirst().origin();
            Candidate known = bestPerOrigin.get(origin);
            if (known == null || single.quick().score() > known.quick().score()) bestPerOrigin.put(origin, single);
        }
        if (bestPerOrigin.size() < 2) return List.of();
        Candidate start = bestPerOrigin.values().stream()
                .max(Comparator.comparingDouble(found -> found.quick().score()))
                .orElseThrow();
        List<Candidate> others = bestPerOrigin.values().stream()
                .filter(found -> found != start)
                .sorted(Comparator.comparingDouble((Candidate found) -> fleetCost(context.seat, found.moves()))
                        .reversed())
                .toList();
        List<Candidate> combined = new ArrayList<>();
        Candidate current = start;
        for (Candidate next : others) {
            if (current.parts().size() >= MAX_ORIGINS) break;
            List<Part> parts = new ArrayList<>(current.parts());
            parts.addAll(next.parts());
            Optional<Candidate> merged =
                    candidate(context, tile, opponent, parts, current.heldBack() || next.heldBack(), null);
            if (merged.isEmpty()
                    || merged.get().quick().score() <= current.quick().score()) break;
            combined.add(merged.get());
            current = merged.get();
        }
        return combined;
    }

    static Optional<StrikeValue> explainAttack(Game game, Player seat, String position) {
        Tile tile = game.getTileByPosition(position);
        Player opponent = tile == null ? null : singleOpponent(game, seat, tile);
        if (opponent == null) return Optional.empty();
        Context context = new Context(game, seat);
        return candidatePool(context, tile, opponent).stream()
                .max(Comparator.comparingDouble(found -> found.quick().score()))
                .map(found -> context.offense().evaluate(found.strike(), found.estimate(), found.terms(), true));
    }

    private record Departure(List<UnitMove> ships, List<UnitMove> cargo, boolean heldBack) {}

    private record Part(Tile origin, Departure departure) {}

    private record Candidate(
            List<Part> parts,
            Strike strike,
            StrikeEstimate estimate,
            ContextTerms terms,
            StrikeValue quick,
            boolean heldBack,
            @Nullable Blend blend) {

        List<UnitMove> moves() {
            return strike.moves();
        }
    }

    private static List<Candidate> candidatesFrom(Context context, Tile tile, Tile origin, Player opponent) {
        List<Candidate> pool = new ArrayList<>(withGarrisonPasses(context, tile, origin, opponent, 0));
        int fighters = BoardView.count(BoardView.space(origin), context.seat, UnitType.Fighter);
        if (fighters > 0 && BoardView.hasEnemyShips(context.game, context.seat, tile)) {
            pool.addAll(withGarrisonPasses(context, tile, origin, opponent, fighters));
        }
        return pool;
    }

    private static List<Candidate> withGarrisonPasses(
            Context context, Tile tile, Tile origin, Player opponent, int fightersAvailable) {
        List<Candidate> first = variants(context, tile, origin, opponent, fightersAvailable, List.of());
        Optional<Candidate> top = first.stream()
                .max(Comparator.comparingDouble(found -> found.quick().score()));
        DefenseOutlook defense = context.defense();
        if (top.isEmpty()
                || !defense.isGuarded(origin)
                || defense.leaveCost(origin, top.get().moves(), top.get().blend())
                        <= DefenseOutlook.GARRISON_TOLERANCE) {
            return first;
        }
        List<Candidate> pool = new ArrayList<>(first);
        pool.addAll(variants(
                context,
                tile,
                origin,
                opponent,
                fightersAvailable,
                departingShips(top.get().moves())));
        return pool;
    }

    private static List<Candidate> variants(
            Context context,
            Tile tile,
            Tile origin,
            Player opponent,
            int fightersAvailable,
            List<UnitMove> shipsLeaving) {
        DefenseOutlook defense = context.defense();
        Departure allIn = departure(context, tile, origin, fightersAvailable, false, shipsLeaving, null);
        Optional<Candidate> everyShip =
                candidate(context, tile, opponent, List.of(new Part(origin, allIn)), false, null);
        List<Candidate> variants = new ArrayList<>(everyShip.stream().toList());
        if (!defense.isGuarded(origin)) return variants;
        double w0 = everyShip
                .map(found ->
                        found.estimate().escaped() ? 0.0 : found.estimate().spaceWin())
                .orElse(0.0);
        Blend blend = w0 > 0 ? new Blend(defense.excluding(new Exclusion(opponent, tile)), w0) : null;
        Departure held = departure(context, tile, origin, fightersAvailable, true, shipsLeaving, blend);
        if (held.heldBack()) {
            candidate(context, tile, opponent, List.of(new Part(origin, held)), true, blend)
                    .ifPresent(variants::add);
        }
        return variants;
    }

    private static Departure departure(
            Context context,
            Tile tile,
            Tile origin,
            int fightersAvailable,
            boolean holdBack,
            List<UnitMove> shipsLeaving,
            @Nullable Blend blend) {
        Game game = context.game;
        Player seat = context.seat;
        UnitHolder originSpace = BoardView.space(origin);
        List<UnitMove> ships = new ArrayList<>();
        for (UnitType type : BoardView.MOVING_SHIPS) {
            int available = BoardView.count(originSpace, seat, type);
            if (available == 0 || !context.reaches(origin, tile, type)) continue;
            ships.add(new UnitMove(origin.getPosition(), BoardView.SPACE, type, available));
        }
        Comparator<UnitType> preference = BoardView.hasEnemyShips(game, seat, tile)
                ? Comparator.comparingDouble(type -> cost(seat, type))
                : Comparator.comparingInt((UnitType type) -> BoardView.capacity(seat, type))
                        .thenComparingDouble(type -> -cost(seat, type));
        gravityDriveShip(
                        context,
                        origin,
                        tile,
                        BoardView.MOVING_SHIPS,
                        type -> BoardView.count(originSpace, seat, type),
                        preference)
                .ifPresent(ships::add);
        if (ships.isEmpty()) return new Departure(List.of(), List.of(), false);
        int escort = Math.min(fightersAvailable, capacityOf(seat, ships));
        List<UnitMove> candidates = new ArrayList<>(ships);
        if (escort > 0) {
            candidates.add(new UnitMove(origin.getPosition(), BoardView.SPACE, UnitType.Fighter, escort));
        }
        List<UnitMove> cargo = enemyPlanets(game, seat, tile).isEmpty()
                        && freePlanetsIn(context, tile).isEmpty()
                ? List.of()
                : take(
                        availableGroundForces(game, seat, origin, context.defense(), shipsLeaving, blend),
                        capacityOf(seat, ships) - escort);
        List<UnitMove> leaving = holdBack ? context.defense().holdBack(origin, cargo, candidates, blend) : candidates;
        List<UnitMove> leavingShips = shipsIn(leaving);
        int capacity = capacityOf(seat, leavingShips);
        int fighters = Math.min(countOf(leaving, UnitType.Fighter), capacity);
        List<UnitMove> departing = new ArrayList<>(leavingShips);
        if (fighters > 0) {
            departing.add(new UnitMove(origin.getPosition(), BoardView.SPACE, UnitType.Fighter, fighters));
        }
        return new Departure(departing, take(cargo, capacity - fighters), unitCount(leaving) < unitCount(candidates));
    }

    private static List<Planet> freePlanetsIn(Context context, Tile tile) {
        return tile.getPlanetUnitHolders().stream()
                .filter(planet -> BoardView.controller(context.game, planet.getName()) == null)
                .filter(planet -> !BoardView.hasCustodians(planet) || context.canPayCustodians())
                .toList();
    }

    private static Optional<Candidate> candidate(
            Context context, Tile tile, Player opponent, List<Part> parts, boolean heldBack, @Nullable Blend blend) {
        if (parts.stream().anyMatch(part -> part.departure().ships().isEmpty())) return Optional.empty();
        Optional<Candidate> first = evaluated(context, tile, opponent, parts, heldBack, blend, true);
        if (first.isEmpty()) return first;
        boolean raidsAHome = isAnotherPlayersHome(context.game, context.seat, tile)
                && !first.get().estimate().landings().isEmpty();
        if (!raidsAHome || homeLandingJustified(context, opponent, first.get())) return first;
        return evaluated(context, tile, opponent, parts, heldBack, blend, false);
    }

    private static boolean homeLandingJustified(Context context, Player opponent, Candidate candidate) {
        return homeLandingJustified(context, opponent, candidate.quick());
    }

    private static boolean homeLandingJustified(Context context, Player opponent, StrikeValue value) {
        double points = HOME_LANDING_SHARE * ObjectiveValue.VICTORY_POINT_VALUE;
        return context.defense().standings().winThreat(opponent) >= HOME_LANDING_THREAT
                || value.homeBlock() >= points
                || value.ownObjective() >= points;
    }

    private static Optional<Candidate> evaluated(
            Context context,
            Tile tile,
            Player opponent,
            List<Part> parts,
            boolean heldBack,
            @Nullable Blend blend,
            boolean homeLandings) {
        Game game = context.game;
        Player seat = context.seat;
        List<Tile> origins = parts.stream().map(Part::origin).toList();
        List<UnitMove> departing = new ArrayList<>();
        for (Part part : parts) {
            departing.addAll(part.departure().ships());
            departing.addAll(part.departure().cargo());
        }
        Strike strike =
                Strike.of(seat, tile, opponent, origins, departing, rules(context, tile, opponent, homeLandings));
        StrikeEstimate estimate = StrikeEvaluator.estimate(game, seat, strike);
        if (estimate.spaceWin() <= 0) return Optional.empty();
        Optional<List<UnitMove>> legal = executedMoves(context, parts, tile, estimate);
        if (legal.isEmpty()) return Optional.empty();
        Strike executed = strike.withMoves(legal.get());
        ContextTerms terms = terms(context, tile, executed, estimate);
        StrikeValue quick = context.offense().evaluate(executed, estimate, terms, false);
        return Optional.of(new Candidate(parts, executed, estimate, terms, quick, heldBack, blend));
    }

    private static StrikeRules rules(Context context, Tile tile, Player opponent, boolean homeLandings) {
        Game game = context.game;
        double threat = context.defense().standings().winThreat(opponent);
        boolean conquering = tile.isHomeSystem(game) && wantsToConquer(game, context.seat);
        double minWin = threat >= DESPERATE_THREAT ? DESPERATE_MIN_WIN : MIN_WIN;
        boolean structures = conquering || tile == opponent.getHomeSystemTile() || threat >= DESPERATE_THREAT;
        return new StrikeRules(conquering, minWin, structures, homeLandings, context.canPayCustodians());
    }

    private static Optional<List<UnitMove>> executedMoves(
            Context context, List<Part> parts, Tile tile, StrikeEstimate estimate) {
        Player seat = context.seat;
        int landed = estimate.groundSent().values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        List<UnitMove> allCargo = new ArrayList<>();
        parts.forEach(part -> allCargo.addAll(part.departure().cargo()));
        List<UnitMove> carried = take(allCargo, landed);
        int moving = parts.stream()
                .mapToInt(part -> movedNonFighterShips(part.departure().ships()))
                .sum();
        if (BoardView.nonFighterShips(BoardView.space(tile), seat) + moving > seat.getFleetCC()) {
            return Optional.empty();
        }
        List<UnitMove> executed = new ArrayList<>();
        for (Part part : parts) {
            List<UnitMove> own = new ArrayList<>(part.departure().ships());
            carried.stream()
                    .filter(move -> move.origin().equals(part.origin().getPosition()))
                    .forEach(own::add);
            Optional<List<UnitMove>> legal = legalMoves(context, part.origin(), tile, own);
            if (legal.isEmpty()) return Optional.empty();
            executed.addAll(legal.get());
        }
        return Optional.of(merge(executed));
    }

    private static ContextTerms terms(Context context, Tile tile, Strike strike, StrikeEstimate estimate) {
        Game game = context.game;
        Player seat = context.seat;
        double objective =
                context.objectiveGain(tile, strike.moves(), estimate.landings().keySet());
        Map<String, Double> explore = new HashMap<>();
        estimate.groundSent().forEach((name, sent) -> {
            if (tile.getUnitHolders().get(name) instanceof Planet planet) {
                explore.put(name, unexploredValue(context, tile, planet, sent));
            }
        });
        double[] secrets = secretBonuses(game, seat, tile, strike, estimate);
        boolean ownHome = tile == seat.getHomeSystemTile();
        double homeDefence = ownHome && estimate.spaceCombat() ? HOME_DEFENCE_VALUE : 0;
        double retake = ownHome ? context.defense().publicScoringLock() : 0;
        Map<UnitType, Integer> bombarding =
                StrikeEvaluator.bombardingShips(seat, BoardView.space(tile), new Side(seat, strike.ships()));
        Optional<BombardmentRules.Target> bombardment = BombardmentRules.target(game, seat, tile, bombarding);
        double example = makeAnExampleBonus(game, seat, tile, bombarding, bombardment);
        return new ContextTerms(
                objective,
                explore,
                secrets[0],
                secrets[1],
                homeDefence,
                retake,
                example,
                territoryValues(context, tile));
    }

    private static Map<String, Double> territoryValues(Context context, Tile tile) {
        double bonus = ownAreaBonus(context.territory().zone(tile));
        if (bonus == 0) return Map.of();
        Map<String, Double> values = new HashMap<>();
        tile.getPlanetUnitHolders().forEach(planet -> values.put(planet.getName(), bonus));
        return values;
    }

    private static double ownAreaBonus(Territory.Zone zone) {
        return switch (zone) {
            case LANE -> LANE_PLANET_BONUS;
            case SLICE -> SLICE_PLANET_BONUS;
            case BORDER -> BORDER_PLANET_BONUS;
            default -> 0;
        };
    }

    private static Optional<TacticalPlan> finalPlan(Context context, Tile tile, List<Candidate> pool) {
        if (pool.isEmpty()) return Optional.empty();
        List<Candidate> ranked = pool.stream()
                .sorted(Comparator.comparingDouble(
                                (Candidate found) -> found.quick().score())
                        .reversed())
                .toList();
        List<Candidate> finalists = new ArrayList<>(ranked.subList(0, Math.min(FINALISTS, ranked.size())));
        ranked.stream().filter(Candidate::heldBack).findFirst().ifPresent(found -> addDistinct(finalists, found));
        ranked.stream()
                .filter(found -> !found.heldBack())
                .findFirst()
                .ifPresent(found -> addDistinct(finalists, found));
        TacticalPlan best = null;
        for (Candidate finalist : finalists) {
            Optional<TacticalPlan> plan = fullPlan(context, tile, finalist);
            if (plan.isPresent() && (best == null || plan.get().score() > best.score())) best = plan.get();
        }
        return Optional.ofNullable(best);
    }

    private static void addDistinct(List<Candidate> finalists, Candidate candidate) {
        if (finalists.stream().noneMatch(found -> found == candidate)) finalists.add(candidate);
    }

    private static Optional<TacticalPlan> fullPlan(Context context, Tile tile, Candidate candidate) {
        if (candidate.estimate().spaceWin() < candidate.strike().rules().landMinWin()) return Optional.empty();
        StrikeValue value = context.offense()
                .evaluate(candidate.strike(), candidate.estimate(), candidate.terms(), true, ATTACK_MIN_SCORE);
        if (value.score() < ATTACK_MIN_SCORE) return Optional.empty();
        return Optional.of(new TacticalPlan(
                Kind.ATTACK,
                tile.getPosition(),
                candidate.moves(),
                candidate.estimate().landings(),
                value.score()));
    }

    private static List<TacticalPlan> stage(Context context, Predicate<Tile> activatable) {
        if (!STAGE_ENABLED) return List.of();
        Map<String, TacticalPlan> best = new LinkedHashMap<>();
        for (Tile target : stageTargets(context)) {
            Player opponent = singleOpponent(context.game, context.seat, target);
            for (Tile origin : context.origins()) {
                if (origin == target) continue;
                for (TacticalPlan plan : stageFrom(context, activatable, target, opponent, origin)) {
                    TacticalPlan known = best.get(plan.target());
                    if (known == null || plan.score() > known.score()) best.put(plan.target(), plan);
                }
            }
        }
        return new ArrayList<>(best.values());
    }

    private static List<Tile> stageTargets(Context context) {
        Game game = context.game;
        Player seat = context.seat;
        List<Tile> targets = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            Player opponent = singleOpponent(game, seat, tile);
            if (opponent == null || !worthStagingFor(context, tile, opponent)) continue;
            if (context.attackPlan(tile).isPresent()) continue;
            targets.add(tile);
        }
        targets.sort(Comparator.comparingDouble((Tile tile) -> prizeEstimate(context, tile))
                .reversed());
        return targets.subList(0, Math.min(STAGE_TARGETS, targets.size()));
    }

    private static boolean worthStagingFor(Context context, Tile tile, Player opponent) {
        if (context.defense().standings().denialWeight(opponent) >= STAGE_MIN_DENIAL) return true;
        if (tile == opponent.getHomeSystemTile() || holdsMecatol(context.game, tile)) return true;
        return tile.getPlanetUnitHolders().stream()
                .filter(planet -> BoardView.controller(context.game, planet.getName()) == opponent)
                .anyMatch(planet -> context.offense().deniesObjectives(opponent, planet));
    }

    private static double prizeEstimate(Context context, Tile tile) {
        double planets = tile.getPlanetUnitHolders().stream()
                .filter(planet -> !context.seat.getPlanets().contains(planet.getName()))
                .mapToDouble(BoardView::planetValue)
                .sum();
        boolean decisive = tile.isHomeSystem(context.game) || holdsMecatol(context.game, tile);
        return planets + (decisive ? ObjectiveValue.VICTORY_POINT_VALUE : 0);
    }

    private static boolean holdsMecatol(Game game, Tile tile) {
        return tile.getPlanetUnitHolders().stream()
                .anyMatch(planet -> game.mecatols().contains(planet.getName()));
    }

    // TODO: staged groups leave fighters behind and assume their ships are undamaged
    private static List<TacticalPlan> stageFrom(
            Context context, Predicate<Tile> activatable, Tile target, Player opponent, Tile origin) {
        Game game = context.game;
        Player seat = context.seat;
        UnitHolder originSpace = BoardView.space(origin);
        List<UnitMove> ships = new ArrayList<>();
        for (UnitType type : BoardView.MOVING_SHIPS) {
            int count = BoardView.count(originSpace, seat, type);
            if (count > 0) ships.add(new UnitMove(origin.getPosition(), BoardView.SPACE, type, count));
        }
        List<UnitMove> group = context.defense().holdBack(origin, List.of(), ships);
        if (group.isEmpty()) return List.of();
        boolean planetsToTake = !enemyPlanets(game, seat, target).isEmpty()
                || !freePlanetsIn(context, target).isEmpty();
        List<UnitMove> cargo = planetsToTake
                ? take(availableGroundForces(game, seat, origin, context.defense(), group), capacityOf(seat, group))
                : List.of();
        int slowest = group.stream()
                .mapToInt(move -> BoardView.moveValue(seat, move.type()))
                .min()
                .orElse(0);
        if (slowest <= 0 || context.reach(origin, slowest).containsKey(target.getPosition())) return List.of();
        List<UnitMove> departing = new ArrayList<>(group);
        departing.addAll(cargo);
        double groupCost = fleetCost(seat, group);
        List<Staging> options = new ArrayList<>();
        for (Tile staging : game.getTileMap().values()) {
            if (staging == origin || staging == target || !activatable.test(staging)) continue;
            if (!MovementGraph.canEnter(game, seat, staging)) continue;
            if (BoardView.hasEnemyShips(game, seat, staging) || context.coveredByEnemySpaceCannon(staging)) continue;
            if (group.stream().anyMatch(move -> !context.reaches(origin, staging, move.type()))) continue;
            if (!context.reach(staging, slowest).containsKey(target.getPosition())) continue;
            if (futureScore(context, target, opponent, staging, group, cargo)
                    < ATTACK_MIN_SCORE + STAGE_FUTURE_MARGIN) {
                continue;
            }
            options.add(new Staging(staging, counterAt(context, staging, opponent, group, groupCost)));
        }
        options.sort(Comparator.comparingDouble(Staging::counter));
        List<TacticalPlan> plans = new ArrayList<>();
        for (Staging option : options.subList(0, Math.min(STAGING_TILES_PER_TARGET, options.size()))) {
            stagePlan(context, target, opponent, origin, option, departing, group, cargo, groupCost)
                    .ifPresent(plans::add);
        }
        return plans;
    }

    private record Staging(Tile tile, double counter) {}

    private static double counterAt(
            Context context, Tile staging, Player opponent, List<UnitMove> group, double groupCost) {
        Defenders after = context.defense().defendersAt(staging);
        for (UnitMove move : group) after = after.plusShips(move.type(), move.count(), 0);
        return context.defense().counterstrike(staging, after, List.of(), STAGE_SHIP_STAKE * groupCost, 0, opponent);
    }

    private static Optional<TacticalPlan> stagePlan(
            Context context,
            Tile target,
            Player opponent,
            Tile origin,
            Staging option,
            List<UnitMove> departing,
            List<UnitMove> group,
            List<UnitMove> cargo,
            double groupCost) {
        Player seat = context.seat;
        Tile staging = option.tile();
        Optional<List<UnitMove>> legal = legalMoves(context, origin, staging, departing);
        if (legal.isEmpty()) return Optional.empty();
        double future = futureScore(context, target, opponent, staging, group, cargo);
        if (future < ATTACK_MIN_SCORE + STAGE_FUTURE_MARGIN) return Optional.empty();
        boolean diplomacy = context.defense().standings().holdsUnplayedDiplomacy(opponent)
                && target.getPlanetUnitHolders().stream()
                        .anyMatch(planet -> BoardView.controller(context.game, planet.getName()) == opponent
                                && !context.game.mecatols().contains(planet.getName()));
        int farthest = group.stream()
                .mapToInt(move -> context.distance(origin, staging, move.type()))
                .max()
                .orElse(0);
        double score = STAGE_SHARE * future * (diplomacy ? DIPLOMACY_DISCOUNT : 1)
                - option.counter()
                - context.defense().leaveCost(origin, legal.get())
                - DISTANCE_COST * farthest
                - GROUP_MOVE_COST * (movedNonFighterShips(legal.get()) - 1);
        TacticalPlan plan = new TacticalPlan(Kind.STAGE, staging.getPosition(), legal.get(), Map.of(), score);
        return Optional.of(plan.withScore(PlayAreaNotes.adjustedScore(context.game, seat, staging, plan)
                + SetupHint.bonusFor(context.hints, plan)));
    }

    private static double futureScore(
            Context context, Tile target, Player opponent, Tile staging, List<UnitMove> group, List<UnitMove> cargo) {
        String key = target.getPosition() + "#" + BoardView.ships(BoardView.space(staging), context.seat) + "#" + group
                + "#" + cargo;
        Double known = context.futureScores.get(key);
        if (known != null) return known;
        double score = computeFuture(context, target, opponent, staging, group, cargo);
        context.futureScores.put(key, score);
        return score;
    }

    private static double computeFuture(
            Context context, Tile target, Player opponent, Tile staging, List<UnitMove> group, List<UnitMove> cargo) {
        Game game = context.game;
        Player seat = context.seat;
        Map<UnitType, Integer> ships = new EnumMap<>(UnitType.class);
        ships.putAll(BoardView.ships(BoardView.space(staging), seat));
        group.forEach(move -> ships.merge(move.type(), move.count(), Integer::sum));
        Map<UnitType, Integer> ground = new EnumMap<>(UnitType.class);
        cargo.forEach(move -> ground.merge(move.type(), move.count(), Integer::sum));
        IncomingForces.IncomingForce reinforcement =
                context.defense().force(opponent, target, IncomingForces.Horizon.NEXT_ROUND);
        UnitHolder targetSpace = BoardView.space(target);
        Map<UnitType, Integer> extraShips = minus(reinforcement.ships(), BoardView.ships(targetSpace, opponent));
        Map<UnitType, Integer> waiting = new EnumMap<>(UnitType.class);
        BoardView.GROUND_FORCES.forEach(type -> waiting.put(type, BoardView.count(targetSpace, opponent, type)));
        Map<UnitType, Integer> extraGround = minus(reinforcement.ground(), waiting);
        double score = 0;
        for (boolean homeLandings : new boolean[] {true, false}) {
            StrikeRules rules = rules(context, target, opponent, homeLandings);
            Strike strike = new Strike(
                    target, opponent, List.of(), List.of(), ships, Map.of(), ground, extraShips, extraGround, rules);
            StrikeEstimate estimate = StrikeEvaluator.estimate(game, seat, strike);
            if (estimate.spaceWin() < rules.landMinWin()) return 0;
            StrikeValue value = context.offense()
                    .evaluate(strike, estimate, ContextTerms.none(), true, ATTACK_MIN_SCORE + STAGE_FUTURE_MARGIN);
            boolean raidsAHome = isAnotherPlayersHome(game, seat, target)
                    && !estimate.landings().isEmpty();
            score = value.score();
            if (!raidsAHome || homeLandingJustified(context, opponent, value)) break;
        }
        return score;
    }

    private static Map<UnitType, Integer> minus(Map<UnitType, Integer> total, Map<UnitType, Integer> present) {
        Map<UnitType, Integer> extra = new EnumMap<>(UnitType.class);
        total.forEach((type, count) -> {
            int left = count - present.getOrDefault(type, 0);
            if (left > 0) extra.put(type, left);
        });
        return extra;
    }

    private static double unexploredValue(Context context, Tile tile, Planet planet, int infantry) {
        return BoardView.controller(context.game, planet.getName()) == null
                ? context.exploreValue(tile, planet, infantry, 0)
                : 0;
    }

    private static Optional<UnitMove> gravityDriveShip(
            Context context,
            Tile origin,
            Tile tile,
            Collection<UnitType> types,
            ToIntFunction<UnitType> available,
            Comparator<UnitType> preference) {
        return types.stream()
                .filter(type -> available.applyAsInt(type) > 0)
                .filter(type ->
                        !context.reaches(origin, tile, type) && context.reachesWithGravityDrive(origin, tile, type))
                .max(preference)
                .map(type -> new UnitMove(origin.getPosition(), BoardView.SPACE, type, 1));
    }

    private static double cost(Player seat, UnitType type) {
        return BoardView.model(seat, type).map(UnitModel::getCost).orElse(0f);
    }

    private static double makeAnExampleBonus(
            Game game,
            Player seat,
            Tile tile,
            Map<UnitType, Integer> bombarding,
            Optional<BombardmentRules.Target> bombardment) {
        if (!seat.getSecretsUnscored().containsKey(MAKE_AN_EXAMPLE) || bombardment.isEmpty()) return 0;
        if (!(tile.getUnitHolders().get(bombardment.get().planet()) instanceof Planet planet)) return 0;
        return ObjectiveValue.VICTORY_POINT_VALUE
                * BombardmentRules.chanceToDestroyAll(
                        seat, bombarding, planet, bombardment.get().defender());
    }

    static boolean isAnotherPlayersHome(Game game, Player seat, Tile tile) {
        return tile.isHomeSystem(game) && tile != seat.getHomeSystemTile();
    }

    private static boolean wantsToConquer(Game game, Player seat) {
        return game.getRevealedPublicObjectives().containsKey(CONQUER_THE_WEAK)
                && !ObjectivePolicy.hasScored(game, seat, CONQUER_THE_WEAK);
    }

    static double actionSecretBonus(
            Game game,
            Player seat,
            Tile tile,
            Player opponent,
            List<UnitMove> ships,
            boolean spaceCombat,
            boolean groundCombat) {
        Map<UnitType, Integer> moving = new EnumMap<>(UnitType.class);
        ships.forEach(move -> moving.merge(move.type(), move.count(), Integer::sum));
        double[] bonuses = secretBonuses(game, seat, tile, opponent, moving, spaceCombat, groundCombat);
        return bonuses[0] + bonuses[1];
    }

    private static double[] secretBonuses(Game game, Player seat, Tile tile, Strike strike, StrikeEstimate estimate) {
        return secretBonuses(
                game, seat, tile, strike.opponent(), strike.ships(), estimate.spaceCombat(), estimate.groundCombat());
    }

    private static double[] secretBonuses(
            Game game,
            Player seat,
            Tile tile,
            Player opponent,
            Map<UnitType, Integer> ships,
            boolean space,
            boolean ground) {
        if (!space && !ground) return new double[] {0, 0};
        Set<String> held = seat.getSecretsUnscored().keySet();
        List<String> generic = new ArrayList<>();
        if (held.contains("sar") && opponent.getTotalVictoryPoints() == game.getHighestScore()) generic.add("sar");
        if (held.contains("btv") && tile.isAnomaly(game, seat)) generic.add("btv");
        if (held.contains(DARKEN_THE_SKIES) && tile.isHomeSystem(game) && tile != seat.getHomeSystemTile()) {
            generic.add(DARKEN_THE_SKIES);
        }
        if (held.contains("baf")
                && seat.getPromissoryNotesInPlayArea().stream().anyMatch(note -> game.getPNOwner(note) == opponent)) {
            generic.add("baf");
        }
        List<String> spaceOnly = new ArrayList<>();
        if (space) {
            UnitHolder holder = BoardView.space(tile);
            int arriving = ships.entrySet().stream()
                            .filter(ship -> BoardView.MOVING_SHIPS.contains(ship.getKey()))
                            .mapToInt(Map.Entry::getValue)
                            .sum()
                    + BoardView.nonFighterShips(holder, seat);
            if (held.contains("uf") && ships.getOrDefault(UnitType.Flagship, 0) > 0) spaceOnly.add("uf");
            if (held.contains("dyp") && arriving >= DEMONSTRATION_SHIPS) spaceOnly.add("dyp");
            if (held.contains("dtgs")
                    && BoardView.count(holder, opponent, UnitType.Flagship)
                                    + BoardView.count(holder, opponent, UnitType.Warsun)
                            > 0) {
                spaceOnly.add("dtgs");
            }
        }
        double bestSpaceOnly = secretPoints(game, seat, spaceOnly).stream()
                .findFirst()
                .orElse(0.0);
        List<Double> genericPoints = secretPoints(game, seat, generic);
        double firstGeneric = genericPoints.isEmpty() ? 0 : genericPoints.get(0);
        double secondGeneric = genericPoints.size() < 2 ? 0 : genericPoints.get(1);
        double spacePoints = 0;
        double groundPoints = 0;
        if (space && ground) {
            if (bestSpaceOnly + firstGeneric >= firstGeneric + secondGeneric) {
                spacePoints = bestSpaceOnly;
                groundPoints = firstGeneric;
            } else {
                spacePoints = firstGeneric;
                groundPoints = secondGeneric;
            }
        } else if (space) {
            spacePoints = Math.max(bestSpaceOnly, firstGeneric);
        } else {
            groundPoints = firstGeneric;
        }
        return new double[] {
            ObjectiveValue.VICTORY_POINT_VALUE * spacePoints, ObjectiveValue.VICTORY_POINT_VALUE * groundPoints
        };
    }

    private static List<Double> secretPoints(Game game, Player seat, List<String> secrets) {
        return secrets.stream()
                .map(secret -> ActionSecretTiming.pursueNow(game, seat, secret) ? 1.0 : PATIENT_SHARE)
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    private static Optional<TacticalPlan> produce(Context context, Tile tile) {
        Player seat = context.seat;
        if (!hasDock(seat, tile)) return Optional.empty();
        ProductionPlanner.BuildPlan build = ProductionPlanner.plan(context.game, seat, tile, 0, context.defense());
        if (build.isEmpty()) return Optional.empty();
        double objectiveGain = context.builtGain(tile, build);
        boolean forScoring = build.scoring() || objectiveGain > 0;
        if (build.units() < ProductionPlanner.MIN_UNITS && !forScoring && !build.defense()) return Optional.empty();
        return Optional.of(
                new TacticalPlan(Kind.PRODUCE, tile.getPosition(), List.of(), Map.of(), build.value() + objectiveGain));
    }

    private static double planetTempo(Game game) {
        return PLANET_TEMPO_VALUE * earlyGameWeight(game);
    }

    static double earlyGameWeight(Game game) {
        int roundsPastEarlyGame = Math.max(0, game.getRound() - LAST_EARLY_ROUND);
        return Math.max(0, 1 - EARLY_GAME_FADE_PER_ROUND * roundsPastEarlyGame);
    }

    private static Optional<TacticalPlan> position(Context context, Tile tile) {
        Game game = context.game;
        Player seat = context.seat;
        double frontier = frontierExplore(seat, tile);
        if (!context.objectives().caresAboutPresence() && frontier == 0) return Optional.empty();
        if (BoardView.hasEnemyShips(game, seat, tile) || context.coveredByEnemySpaceCannon(tile)) {
            return Optional.empty();
        }
        TacticalPlan best = null;
        for (Tile origin : context.origins()) {
            if (origin == tile) continue;
            for (UnitType type : POSITION_SHIPS) {
                if (BoardView.undamaged(BoardView.space(origin), seat, type) == 0
                        || !context.reachesWithGravityDrive(origin, tile, type)) {
                    continue;
                }
                List<UnitMove> moves = List.of(new UnitMove(origin.getPosition(), BoardView.SPACE, type, 1));
                Optional<List<UnitMove>> legal = legalMoves(context, origin, tile, moves);
                if (legal.isEmpty()) continue;
                double gain = context.objectiveGain(tile, legal.get(), List.of()) + frontier;
                if (gain <= 0) continue;
                double score = gain
                        - DISTANCE_COST * context.distance(origin, tile, type)
                        - SHIP_COST_WEIGHT * fleetCost(seat, legal.get())
                        - context.defense().leaveCost(origin, legal.get());
                if (best == null || score > best.score()) {
                    best = new TacticalPlan(Kind.POSITION, tile.getPosition(), legal.get(), Map.of(), score);
                }
            }
            Optional<TacticalPlan> group = groupPosition(context, tile, origin);
            if (group.isPresent() && (best == null || group.get().score() > best.score())) best = group.get();
        }
        return Optional.ofNullable(best);
    }

    private static double frontierExplore(Player seat, Tile tile) {
        if (!seat.hasTech(DARK_ENERGY_TAP) || BoardView.hasOwnShips(seat, tile)) return 0;
        boolean frontier = BoardView.space(tile).getTokenList().contains(Mapper.getTokenID(Constants.FRONTIER));
        return frontier ? FRONTIER_CARD_VALUE + FRONTIER_POSITION_VALUE : 0;
    }

    private static Optional<TacticalPlan> groupPosition(Context context, Tile tile, Tile origin) {
        Player seat = context.seat;
        List<UnitMove> ships = new ArrayList<>();
        UnitHolder originSpace = BoardView.space(origin);
        for (UnitType type : POSITION_SHIPS) {
            int available = BoardView.undamaged(originSpace, seat, type);
            if (available > 0 && context.reaches(origin, tile, type)) {
                ships.add(new UnitMove(origin.getPosition(), BoardView.SPACE, type, available));
            }
        }
        gravityDriveShip(
                        context,
                        origin,
                        tile,
                        POSITION_SHIPS,
                        type -> BoardView.undamaged(originSpace, seat, type),
                        Comparator.comparingDouble((UnitType type) -> -cost(seat, type)))
                .ifPresent(ships::add);
        List<UnitMove> departing = context.defense().holdBack(origin, List.of(), ships);
        if (movedNonFighterShips(departing) < 2) return Optional.empty();
        Optional<List<UnitMove>> legal = legalMoves(context, origin, tile, departing);
        if (legal.isEmpty()) return Optional.empty();
        double gain = context.objectiveGain(tile, legal.get(), List.of()) + frontierExplore(seat, tile);
        if (gain <= 0) return Optional.empty();
        int farthest = legal.get().stream()
                .filter(move -> BoardView.MOVING_SHIPS.contains(move.type()))
                .mapToInt(move -> context.distance(origin, tile, move.type()))
                .max()
                .orElse(0);
        double score = gain
                - DISTANCE_COST * farthest
                - GROUP_MOVE_COST * movedNonFighterShips(legal.get())
                - context.defense().leaveCost(origin, legal.get());
        return Optional.of(new TacticalPlan(Kind.POSITION, tile.getPosition(), legal.get(), Map.of(), score));
    }

    private record Reinforcement(
            UnitType type, int count, double gain, double perResource, Defenders after, double costAfter) {}

    private static Optional<TacticalPlan> defend(Context context, Tile tile) {
        Game game = context.game;
        Player seat = context.seat;
        DefenseOutlook defense = context.defense();
        if (!defense.isGuarded(tile) || BoardView.hasEnemyShips(game, seat, tile)) return Optional.empty();
        Defenders now = Defenders.of(seat, tile);
        double costNow = defense.cost(tile, now);
        if (costNow <= 0) return Optional.empty();
        double buildBonus = hasDock(seat, tile)
                ? ProductionPlanner.plan(game, seat, tile, 0, defense).nonDefenseValue()
                : 0;
        TacticalPlan best = null;
        for (Tile origin : context.origins()) {
            if (origin == tile) continue;
            Optional<TacticalPlan> plan = defendFrom(context, tile, origin, now, costNow, buildBonus);
            if (plan.isPresent() && (best == null || plan.get().score() > best.score())) best = plan.get();
        }
        return Optional.ofNullable(best);
    }

    // TODO: arriving ships are assumed undamaged (the less defensive reading)
    private static Optional<TacticalPlan> defendFrom(
            Context context, Tile tile, Tile origin, Defenders now, double costNow, double buildBonus) {
        Player seat = context.seat;
        DefenseOutlook defense = context.defense();
        List<UnitMove> available = new ArrayList<>();
        UnitHolder originSpace = BoardView.space(origin);
        for (UnitType type : BoardView.MOVING_SHIPS) {
            int count = BoardView.count(originSpace, seat, type);
            if (count > 0 && context.reaches(origin, tile, type)) {
                available.add(new UnitMove(origin.getPosition(), BoardView.SPACE, type, count));
            }
        }
        List<UnitMove> candidates = defense.holdBack(origin, List.of(), available);
        List<UnitMove> chosen = new ArrayList<>();
        Defenders after = now;
        double costAfter = costNow;
        while (costAfter > 0) {
            Optional<Reinforcement> best = bestReinforcement(context, tile, candidates, after, costAfter);
            if (best.isEmpty() || best.get().gain() < DefenseOutlook.HOLD_MIN_GAIN) break;
            Reinforcement next = best.get();
            chosen.add(new UnitMove(origin.getPosition(), BoardView.SPACE, next.type(), next.count()));
            candidates = DefenseOutlook.without(candidates, next.type(), next.count());
            after = next.after();
            costAfter = next.costAfter();
        }
        if (chosen.isEmpty()) return Optional.empty();
        Optional<List<UnitMove>> legal = legalMoves(context, origin, tile, merge(chosen));
        if (legal.isEmpty()) return Optional.empty();
        Map<String, Integer> landed = new LinkedHashMap<>();
        for (UnitMove move : legal.get()) {
            if (!BoardView.SPACE.equals(move.holder()) || !BoardView.GROUND_FORCES.contains(move.type())) continue;
            for (int unit = 0; unit < move.count(); unit++) {
                Planet planet = defense.mostExposedPlanet(tile, after);
                if (planet == null) break;
                after = after.plusGround(planet.getName(), move.type(), 1);
                landed.put(planet.getName(), after.groundOn(planet.getName()));
            }
        }
        int farthest = legal.get().stream()
                .filter(move -> BoardView.MOVING_SHIPS.contains(move.type()))
                .mapToInt(move -> context.distance(origin, tile, move.type()))
                .max()
                .orElse(0);
        double score = costNow
                - defense.cost(tile, after)
                - defense.leaveCost(origin, legal.get())
                - DISTANCE_COST * farthest
                - GROUP_MOVE_COST * (movedNonFighterShips(legal.get()) - 1)
                + buildBonus;
        return Optional.of(new TacticalPlan(Kind.DEFEND, tile.getPosition(), legal.get(), landed, score));
    }

    private static Optional<Reinforcement> bestReinforcement(
            Context context, Tile tile, List<UnitMove> candidates, Defenders after, double costAfter) {
        Player seat = context.seat;
        Reinforcement best = null;
        for (UnitType type : candidates.stream().map(UnitMove::type).distinct().toList()) {
            int available = candidates.stream()
                    .filter(move -> move.type() == type)
                    .mapToInt(UnitMove::count)
                    .sum();
            for (int count = 1; count <= Math.min(REINFORCEMENT_BUNDLE, available); count++) {
                Defenders grown = after.plusShips(type, count, 0);
                double cost = context.defense().cost(tile, grown);
                double gain = costAfter - cost;
                double perResource = gain / Math.max(CHEAPEST_UNIT_COST, count * cost(seat, type));
                Reinforcement option = new Reinforcement(type, count, gain, perResource, grown, cost);
                if (best == null || cheaperReinforcement(seat, option, best)) best = option;
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean cheaperReinforcement(Player seat, Reinforcement option, Reinforcement best) {
        int byValue = Double.compare(option.perResource(), best.perResource());
        if (byValue != 0) return byValue > 0;
        int byCapacity =
                Integer.compare(BoardView.capacity(seat, best.type()), BoardView.capacity(seat, option.type()));
        if (byCapacity != 0) return byCapacity > 0;
        return cost(seat, option.type()) < cost(seat, best.type());
    }

    private static Optional<List<UnitMove>> legalMoves(
            Context context, Tile origin, Tile target, List<UnitMove> moves) {
        Player seat = context.seat;
        int fleetAfterMove = BoardView.nonFighterShips(BoardView.space(target), seat) + movedNonFighterShips(moves);
        if (fleetAfterMove > seat.getFleetCC()) return Optional.empty();
        List<UnitMove> legal = new ArrayList<>(moves);
        UnitHolder space = BoardView.space(origin);
        int spare = capacityOf(seat, shipsIn(legal)) - cargoIn(legal);
        for (UnitType type : GROUND_FORCE_ORDER) {
            int carried = Math.min(spare, leftInSpace(seat, space, legal, type));
            if (carried > 0) {
                legal.add(new UnitMove(origin.getPosition(), BoardView.SPACE, type, carried));
                spare -= carried;
            }
        }
        int fighters = BoardView.count(space, seat, UnitType.Fighter);
        int strandedFighters = stranded(context, origin, legal);
        if (fighters > 0 && spare > 0 && strandedFighters > 0) {
            int carried = Math.min(spare, Math.min(fighters, strandedFighters));
            legal.add(new UnitMove(origin.getPosition(), BoardView.SPACE, UnitType.Fighter, carried));
        }
        return stranded(context, origin, legal) > 0 ? Optional.empty() : Optional.of(merge(legal));
    }

    private static int leftInSpace(Player seat, UnitHolder space, List<UnitMove> moves, UnitType type) {
        int moved = moves.stream()
                .filter(move -> BoardView.SPACE.equals(move.holder()) && move.type() == type)
                .mapToInt(UnitMove::count)
                .sum();
        return BoardView.count(space, seat, type) - moved;
    }

    private static int stranded(Context context, Tile origin, List<UnitMove> moves) {
        Player seat = context.seat;
        UnitHolder space = BoardView.space(origin);
        Map<UnitType, Integer> staying = new EnumMap<>(UnitType.class);
        staying.putAll(BoardView.ships(space, seat));
        for (UnitMove move : moves) {
            if (BoardView.SPACE.equals(move.holder()) && staying.containsKey(move.type())) {
                staying.merge(move.type(), -move.count(), Integer::sum);
            }
        }
        int capacity = staying.entrySet().stream()
                .filter(entry -> entry.getKey() != UnitType.Fighter)
                .mapToInt(entry -> Math.max(0, entry.getValue()) * BoardView.capacity(seat, entry.getKey()))
                .sum();
        int fighters = Math.max(0, staying.getOrDefault(UnitType.Fighter, 0));
        int allowance = hasDock(seat, origin) ? BoardView.DOCK_FIGHTER_ALLOWANCE : 0;
        int ground = BoardView.groundForces(space, seat) - groundForcesFromSpace(moves);
        return ground + Math.max(0, fighters - allowance) - capacity;
    }

    private static boolean hasDock(Player seat, Tile tile) {
        return tile.getPlanetUnitHolders().stream()
                .anyMatch(planet -> BoardView.count(planet, seat, UnitType.Spacedock) > 0);
    }

    private static List<UnitMove> merge(List<UnitMove> moves) {
        Map<String, UnitMove> merged = new LinkedHashMap<>();
        for (UnitMove move : moves) {
            merged.merge(
                    move.origin() + "|" + move.holder() + "|" + move.type(),
                    move,
                    (a, b) -> new UnitMove(a.origin(), a.holder(), a.type(), a.count() + b.count()));
        }
        return new ArrayList<>(merged.values());
    }

    private static List<UnitMove> shipsIn(List<UnitMove> moves) {
        return moves.stream()
                .filter(move -> BoardView.MOVING_SHIPS.contains(move.type()))
                .toList();
    }

    private static int movedNonFighterShips(List<UnitMove> moves) {
        return shipsIn(moves).stream().mapToInt(UnitMove::count).sum();
    }

    private static int cargoIn(List<UnitMove> moves) {
        return moves.stream()
                .filter(move -> BoardView.GROUND_FORCES.contains(move.type()) || move.type() == UnitType.Fighter)
                .mapToInt(UnitMove::count)
                .sum();
    }

    private static int groundForcesFromSpace(List<UnitMove> moves) {
        return moves.stream()
                .filter(move -> BoardView.SPACE.equals(move.holder()))
                .filter(move -> BoardView.GROUND_FORCES.contains(move.type()))
                .mapToInt(UnitMove::count)
                .sum();
    }

    private static int capacityOf(Player seat, List<UnitMove> moves) {
        return moves.stream()
                .mapToInt(move -> move.count() * BoardView.capacity(seat, move.type()))
                .sum();
    }

    @Nullable
    private static Player singleOpponent(Game game, Player seat, Tile tile) {
        List<Player> others = game.getPlayers().values().stream()
                .filter(other -> other != seat)
                .filter(other -> other.getColor() != null)
                .filter(other -> tile.containsPlayersUnits(other)
                        || tile.getPlanetUnitHolders().stream()
                                .anyMatch(planet -> other.getPlanets().contains(planet.getName())))
                .toList();
        if (others.size() != 1 || !others.getFirst().isRealPlayer()) return null;
        return others.getFirst();
    }

    static List<Planet> enemyPlanets(Game game, Player seat, Tile tile) {
        return tile.getPlanetUnitHolders().stream()
                .filter(planet -> !seat.getPlanets().contains(planet.getName()))
                .filter(planet -> BoardView.controller(game, planet.getName()) != null
                        || BoardView.enemyGroundForcesOn(game, seat, planet))
                .sorted(Comparator.comparingDouble(BoardView::planetValue).reversed())
                .toList();
    }

    static int movableGroundForces(Game game, Player seat, Tile origin) {
        return movableGroundForces(game, seat, origin, new DefenseOutlook(game, seat));
    }

    static int movableGroundForces(Game game, Player seat, Tile origin, DefenseOutlook defense) {
        return unitCount(availableGroundForces(game, seat, origin, defense, List.of()));
    }

    private static List<UnitMove> availableGroundForces(
            Game game, Player seat, Tile origin, DefenseOutlook defense, List<UnitMove> shipsLeaving) {
        return availableGroundForces(game, seat, origin, defense, shipsLeaving, null);
    }

    private static List<UnitMove> availableGroundForces(
            Game game,
            Player seat,
            Tile origin,
            DefenseOutlook defense,
            List<UnitMove> shipsLeaving,
            @Nullable Blend blend) {
        List<UnitMove> infantry = new ArrayList<>();
        List<UnitMove> mechs = new ArrayList<>();
        String position = origin.getPosition();
        UnitHolder space = BoardView.space(origin);
        addMove(
                infantry,
                position,
                BoardView.SPACE,
                UnitType.Infantry,
                BoardView.count(space, seat, UnitType.Infantry));
        addMove(mechs, position, BoardView.SPACE, UnitType.Mech, BoardView.count(space, seat, UnitType.Mech));
        for (Planet planet : origin.getPlanetUnitHolders()) {
            if (!seat.getPlanets().contains(planet.getName())) continue;
            int planetMechs = BoardView.count(planet, seat, UnitType.Mech);
            int garrison = garrison(game, origin, planet, defense, shipsLeaving, blend);
            int mechsStaying = Math.min(planetMechs, garrison);
            int infantryStaying = garrison - mechsStaying;
            int planetInfantry = BoardView.count(planet, seat, UnitType.Infantry);
            addMove(infantry, position, planet.getName(), UnitType.Infantry, planetInfantry - infantryStaying);
            addMove(mechs, position, planet.getName(), UnitType.Mech, planetMechs - mechsStaying);
        }
        infantry.addAll(mechs);
        return infantry;
    }

    private static void addMove(List<UnitMove> moves, String origin, String holder, UnitType type, int count) {
        if (count > 0) moves.add(new UnitMove(origin, holder, type, count));
    }

    private static int garrison(
            Game game,
            Tile origin,
            Planet planet,
            DefenseOutlook defense,
            List<UnitMove> shipsLeaving,
            @Nullable Blend blend) {
        if (origin.isHomeSystem(game)) {
            return Math.max(HOME_GARRISON, defense.groundToKeep(origin, planet, shipsLeaving, blend));
        }
        return game.mecatols().contains(planet.getName()) ? MECATOL_GARRISON : 0;
    }

    private static int unitCount(List<UnitMove> moves) {
        return moves.stream().mapToInt(UnitMove::count).sum();
    }

    private static List<UnitMove> take(List<UnitMove> available, int wanted) {
        List<UnitMove> taken = new ArrayList<>();
        int remaining = wanted;
        for (UnitMove move : available) {
            if (remaining <= 0) break;
            int count = Math.min(remaining, move.count());
            taken.add(new UnitMove(move.origin(), move.holder(), move.type(), count));
            remaining -= count;
        }
        return taken;
    }

    private static List<UnitMove> skip(List<UnitMove> available, int skipped) {
        List<UnitMove> rest = new ArrayList<>();
        int toSkip = skipped;
        for (UnitMove move : available) {
            int skippedHere = Math.min(toSkip, move.count());
            toSkip -= skippedHere;
            addMove(rest, move.origin(), move.holder(), move.type(), move.count() - skippedHere);
        }
        return rest;
    }

    private static double fleetCost(Player seat, List<UnitMove> moves) {
        return moves.stream()
                .mapToDouble(move -> BoardView.model(seat, move.type())
                                .map(UnitModel::getCost)
                                .orElse(0f)
                        * move.count())
                .sum();
    }

    private static final class Context {
        private final Game game;
        private final Player seat;
        private final Set<String> freed;
        private final List<SetupHint> hints;
        private final Map<String, Map<String, Integer>> reachCache = new HashMap<>();
        private final Map<String, Boolean> spaceCannonCache = new HashMap<>();
        private final Map<String, Integer> forwardNeeds = new HashMap<>();
        private List<Tile> origins;
        private ObjectiveValue objectives;
        private Boolean canPayCustodians;
        private DefenseOutlook defense;
        private OffenseOutlook offense;
        private final Map<String, Optional<TacticalPlan>> attacks = new HashMap<>();
        private final Map<String, Double> futureScores = new HashMap<>();
        private ExploreOutlook exploreOutlook;
        private final Map<String, Double> exploreValues = new HashMap<>();
        private Territory territory;

        Context(Game game, Player seat) {
            this(game, seat, Set.of());
        }

        Context(Game game, Player seat, Set<String> freed) {
            this(game, seat, freed, List.of());
        }

        Context(Game game, Player seat, Set<String> freed, List<SetupHint> hints) {
            this.game = game;
            this.seat = seat;
            this.freed = freed;
            this.hints = hints;
        }

        double exploreValue(Tile tile, Planet planet, int infantry, int mechs) {
            if (exploreOutlook == null) exploreOutlook = new ExploreOutlook(game, seat);
            return exploreValues.computeIfAbsent(
                    planet.getName() + "#" + infantry + "#" + mechs,
                    key -> ExploreDeck.expectedValueOfPlanet(
                            ExploreSite.landing(game, seat, tile, planet, infantry, mechs, exploreOutlook)));
        }

        Territory territory() {
            if (territory == null) territory = Territory.of(game, seat);
            return territory;
        }

        double expansionZoneBonus(Tile tile) {
            Territory.Zone zone = territory().zone(tile);
            if (zone == Territory.Zone.FOREIGN) return -FOREIGN_PLANET_PENALTY * earlyGameWeight(game);
            return ownAreaBonus(zone);
        }

        DefenseOutlook defense() {
            if (defense == null) defense = new DefenseOutlook(game, seat);
            return defense;
        }

        OffenseOutlook offense() {
            if (offense == null) offense = new OffenseOutlook(game, seat, defense());
            return offense;
        }

        Optional<TacticalPlan> attackPlan(Tile tile) {
            Optional<TacticalPlan> known = attacks.get(tile.getPosition());
            if (known != null) return known;
            Optional<TacticalPlan> computed = bestAttack(this, tile);
            attacks.put(tile.getPosition(), computed);
            return computed;
        }

        ObjectiveValue objectives() {
            if (objectives == null) objectives = new ObjectiveValue(game, seat);
            return objectives;
        }

        double objectiveGain(Tile target, List<UnitMove> moves, Collection<String> gainedPlanets) {
            List<Footprint.Move> footprintMoves = moves.stream()
                    .map(move -> new Footprint.Move(move.origin(), move.type(), move.count()))
                    .toList();
            ObjectiveValue value = objectives();
            return value.gain(value.before().after(target.getPosition(), footprintMoves, gainedPlanets));
        }

        double builtGain(Tile tile, ProductionPlanner.BuildPlan build) {
            Map<UnitType, Integer> built = new EnumMap<>(UnitType.class);
            for (ProductionPlanner.BuildOrder order : build.orders())
                built.merge(order.type(), order.units(), Integer::sum);
            ObjectiveValue value = objectives();
            return value.gain(value.before().withBuilt(tile.getPosition(), built));
        }

        boolean canPayCustodians() {
            if (canPayCustodians == null) {
                canPayCustodians = ScoringReserve.planAfterReserve(game, seat, SpendCost.influence(CUSTODIANS_COST))
                        .isPresent();
            }
            return canPayCustodians;
        }

        List<Tile> origins() {
            if (origins == null) {
                origins = game.getTileMap().values().stream()
                        .filter(tile -> !tile.hasPlayerCC(seat) || freed.contains(tile.getPosition()))
                        .filter(tile -> BoardView.hasOwnShips(seat, tile))
                        .filter(tile -> !tile.isGravityRift(game, seat))
                        .toList();
            }
            return origins;
        }

        boolean reaches(Tile origin, Tile target, UnitType type) {
            return distance(origin, target, type) <= BoardView.moveValue(seat, type);
        }

        boolean reachesWithGravityDrive(Tile origin, Tile target, UnitType type) {
            return distance(origin, target, type) <= BoardView.moveValueWithGravityDrive(seat, type);
        }

        Map<String, Integer> reach(Tile from, int move) {
            return reachCache.computeIfAbsent(
                    from.getPosition() + "#" + move, key -> MovementGraph.reach(game, seat, from.getPosition(), move));
        }

        int distance(Tile origin, Tile target, UnitType type) {
            int move = BoardView.moveValueWithGravityDrive(seat, type);
            Map<String, Integer> reach = reachCache.computeIfAbsent(
                    origin.getPosition() + "#" + move,
                    key -> MovementGraph.reach(game, seat, origin.getPosition(), move));
            return reach.getOrDefault(target.getPosition(), Integer.MAX_VALUE);
        }

        int forwardNeed(Tile tile, UnitType transport) {
            return forwardNeeds.computeIfAbsent(tile.getPosition() + "#" + transport, key -> {
                int move = BoardView.moveValueWithGravityDrive(seat, transport);
                return reachCache
                        .computeIfAbsent(
                                tile.getPosition() + "#" + move,
                                position -> MovementGraph.reach(game, seat, tile.getPosition(), move))
                        .keySet()
                        .stream()
                        .filter(position -> !position.equals(tile.getPosition()))
                        .map(game::getTileByPosition)
                        .filter(Objects::nonNull)
                        .mapToInt(this::claimablePlanets)
                        .sum();
            });
        }

        int reservedForOtherTransports(Tile origin, Tile target, List<UnitMove> leaving) {
            Map<UnitType, Integer> staying = new EnumMap<>(UnitType.class);
            for (UnitType type : TRANSPORT_PREFERENCE) {
                int left = BoardView.undamaged(BoardView.space(origin), seat, type) - countOf(leaving, type);
                if (left > 0 && BoardView.capacity(seat, type) > 0) staying.put(type, left);
            }
            int reserved = 0;
            for (Map.Entry<UnitType, Integer> ships : staying.entrySet()) {
                int need = otherExpansionNeed(origin, target, ships.getKey());
                reserved += Math.min(need, ships.getValue() * BoardView.capacity(seat, ships.getKey()));
            }
            return reserved;
        }

        private int otherExpansionNeed(Tile origin, Tile target, UnitType transport) {
            return game.getTileMap().values().stream()
                    .filter(other -> other != target && other != origin)
                    .filter(other -> reaches(origin, other, transport))
                    .mapToInt(this::claimablePlanets)
                    .max()
                    .orElse(0);
        }

        private int claimablePlanets(Tile tile) {
            if (BoardView.hasEnemyUnits(game, seat, tile)) return 0;
            return (int) tile.getPlanetUnitHolders().stream()
                    .filter(planet -> BoardView.controller(game, planet.getName()) == null)
                    .filter(planet -> !BoardView.hasCustodians(planet))
                    .count();
        }

        boolean coveredByEnemySpaceCannon(Tile tile) {
            return spaceCannonCache.computeIfAbsent(
                    tile.getPosition(),
                    position -> ButtonHelper.getPlayersWithPds2Cover(seat, game, position).stream()
                            .anyMatch(player -> player != seat));
        }
    }
}

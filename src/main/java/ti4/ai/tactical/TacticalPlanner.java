package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.DecisionMemo;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.CombatModifiers.Side;
import ti4.ai.eval.CustodiansRace;
import ti4.ai.eval.FleetLimit;
import ti4.ai.eval.FractureCampaign;
import ti4.ai.eval.FracturePrize;
import ti4.ai.eval.IconStake;
import ti4.ai.eval.MecatolStake;
import ti4.ai.eval.MoveBoost;
import ti4.ai.eval.MovementGraph;
import ti4.ai.eval.Territory;
import ti4.ai.explore.ExploreDeck;
import ti4.ai.explore.ExploreOutlook;
import ti4.ai.explore.ExploreSite;
import ti4.ai.explore.ExploreValues;
import ti4.ai.promissory.PlayAreaNotes;
import ti4.ai.sardakk.CommanderReach;
import ti4.ai.scoring.Footprint;
import ti4.ai.scoring.ObjectivePolicy;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.scoring.ScoringStakes;
import ti4.ai.scoring.SpendCost;
import ti4.ai.scoring.Wallet;
import ti4.ai.secrets.ActionSecretTiming;
import ti4.ai.strategy.CardValueScale;
import ti4.ai.strategy.MecatolCash;
import ti4.ai.strategy.OpeningHold;
import ti4.ai.strategy.TechPath;
import ti4.ai.tactical.DefenseOutlook.Blend;
import ti4.ai.tactical.IncomingForces.Exclusion;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.ai.tactical.TacticalPlan.UnitMove;
import ti4.ai.trace.Thinking;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.CheckDistanceHelper;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.model.Mapper;
import ti4.model.UnitModel;

@UtilityClass
public class TacticalPlanner {

    public static final double MIN_SCORE = 1.0;
    private static final double STALL_MIN_SCORE = 0.3;
    private static final int NEIGHBOUR_SEARCH_RANGE = 6;
    private static final double ADVANCE_SHARE = 0.5;
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
    private static final double STAGE_MIN_FEASIBILITY = 0.5;
    private static final double DIPLOMACY_DISCOUNT = 0.5;
    private static final double STAGE_SHIP_STAKE = 0.5;
    private static final int STAGE_TARGETS = 3;
    private static final int FRACTURE_STAGE_LEAD = 3;
    private static final double FRACTURE_STAGE_SHARE = 0.8;
    private static final double FRACTURE_STEP_SHARE = 0.85;
    private static final double FLANK_SPEED_COST = 1.5;
    private static final int STAGING_CANDIDATES = 4;
    private static final int STAGING_TILES_PER_TARGET = 2;
    static final double ATTACK_MIN_SCORE = 4.0;
    static final double NEUTRAL_ATTACK_MIN_SCORE = ExploreValues.COMMAND_TOKEN;
    private static final double DISTANCE_COST = 0.2;
    private static final double PLANET_TEMPO_VALUE = 0.8;
    private static final double APPROACH_SHARE = 0.5;
    private static final double ICON_MARCH_SHARE = 0.6;
    private static final int ICON_MARCH_RANGE = 8;
    private static final int APPROACH_GROUND_FORCES = 2;
    private static final double LANE_PLANET_BONUS = 1.5;
    private static final double SLICE_PLANET_BONUS = 1.0;
    private static final double BORDER_PLANET_BONUS = 0.3;
    private static final double FOREIGN_PLANET_PENALTY = 1.0;
    private static final double FRONTIER_POSITION_VALUE = 1.0;
    private static final String DARK_ENERGY_TAP = "det";
    private static final String HAZARDOUS = "hazardous";
    private static final int INFANTRY = 0;
    private static final int MECH = 1;
    private static final int LAST_EARLY_ROUND = 4;
    private static final double EARLY_GAME_FADE_PER_ROUND = 0.25;
    private static final double CUSTODIANS_VALUE = ObjectiveValue.VICTORY_POINT_VALUE;
    private static final double MECATOL_CASH_SHARE = 0.75;
    private static final String DARKEN_THE_SKIES = "dts";
    private static final String BRAVE_THE_VOID = "btv";
    private static final String BECOME_A_MARTYR = "bam";
    private static final String DESTROY_THEIR_GREATEST_SHIP = "dtgs";
    private static final Set<String> WHOLE_POINT_SECRETS =
            Set.of(DARKEN_THE_SKIES, DESTROY_THEIR_GREATEST_SHIP, BRAVE_THE_VOID);
    private static final Set<String> NEUTRAL_TARGET_SECRETS = Set.of(BRAVE_THE_VOID, "uf", "dyp", "fwp");
    private static final String CONQUER_THE_WEAK = "conquer";
    private static final String MECHANIZE_THE_MILITARY = "mtm";
    private static final String ADAPT_NEW_STRATEGIES = "ans";
    private static final double SCAR_SHARE = 0.6;
    private static final String MAKE_AN_EXAMPLE = "mew";
    private static final int DEMONSTRATION_SHIPS = 3;
    private static final double PATIENT_SHARE = 0.25;
    private static final double GROUP_MOVE_COST = 0.3;
    private static final double FORWARD_GROUND_FORCE_VALUE = 0.5;
    private static final double FIGHTER_ESCORT_VALUE = 0.15;
    private static final double SHIP_COST_WEIGHT = 0.05;
    private static final double HOME_DEFENCE_VALUE = 6.0;
    private static final int CUSTODIANS_COST = 6;
    private static final int REINFORCEMENT_BUNDLE = 3;
    private static final double CHEAPEST_UNIT_COST = 0.5;
    private static final int MECATOL_GARRISON = 1;
    private static final List<UnitType> TRANSPORT_PREFERENCE =
            List.of(UnitType.Carrier, UnitType.Flagship, UnitType.Dreadnought, UnitType.Warsun);
    private static final double FOLLOWER_SHARE = 0.25;
    private static final double FORWARD_POSITION_SHARE = 0.15;
    private static final double LOCKED_FORWARD_SHARE = 0.5;
    private static final List<UnitType> SCREEN_SHIPS = List.of(UnitType.Destroyer, UnitType.Cruiser);
    private static final List<UnitType> GROUND_FORCE_ORDER = List.of(UnitType.Infantry, UnitType.Mech);
    private static final List<UnitType> ESCORTS = List.of(UnitType.Destroyer, UnitType.Cruiser);
    private static final int MAX_ESCORTS = 3;
    private static final double EXPAND_EXPOSURE_SHARE = 0.5;
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

    public static boolean hasTokensForAnAction(Game game, Player seat) {
        return seat.getTacticalCC() > 0
                && coversReserve(seat, ScoringReserve.of(game, seat).tokens());
    }

    private static boolean coversReserve(Player seat, int reservedTokens) {
        return seat.getTacticalCC() + seat.getStrategicCC() - 1 >= reservedTokens;
    }

    public static Optional<TacticalPlan> best(Game game, Player seat, List<SetupHint> hints) {
        try (Thinking.Span ignored = Thinking.enter("tactical.best")) {
            return bestAffordable(game, seat, hints);
        }
    }

    private static Optional<TacticalPlan> bestAffordable(Game game, Player seat, List<SetupHint> hints) {
        if (seat.getTacticalCC() <= 0) return Optional.empty();
        int reservedTokens = ScoringReserve.of(game, seat).tokens();
        Context context = new Context(game, seat, Set.of(), hints);
        List<TacticalPlan> ranked = candidates(context, tile -> activatable(game, seat, tile), STALL_MIN_SCORE);
        boolean keepsReserve = coversReserve(seat, reservedTokens);
        if (!keepsReserve) reservedTokens = 0;
        List<TacticalPlan> all = keepsReserve ? ranked : outweighingTheReserve(game, seat, ranked);
        Optional<TacticalPlan> hold = holdForWarfareBuild(context, all).or(() -> holdForGravityDrive(context, all));
        if (hold.isPresent()) return hold;
        if (stallsWithAStrategyCard(context, all)) return Optional.empty();
        List<TacticalPlan> plans = withinTokenBudget(all, seat.getTacticalCC() - reservedTokens);
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

    private static List<TacticalPlan> outweighingTheReserve(Game game, Player seat, List<TacticalPlan> plans) {
        double worth = ScoringReserve.reservedWorth(game, seat);
        return plans.stream().filter(plan -> plan.score() > worth).toList();
    }

    private static List<TacticalPlan> withinTokenBudget(List<TacticalPlan> plans, int spareTokens) {
        long worthwhile = plans.stream()
                .filter(plan -> plan.score() >= MIN_SCORE)
                .map(TacticalPlan::target)
                .distinct()
                .count();
        if (spareTokens > worthwhile) return plans;
        return plans.stream().filter(plan -> plan.score() >= MIN_SCORE).toList();
    }

    private static Optional<TacticalPlan> holdForWarfareBuild(Context context, List<TacticalPlan> plans) {
        Tile home = context.seat.getHomeSystemTile();
        if (home == null || plans.isEmpty() || !leavesWithTransports(plans.getFirst(), home)) return Optional.empty();
        if (!OpeningHold.awaitsWarfareBuild(context.game, context.seat)) return Optional.empty();
        Set<String> goals = new HashSet<>();
        context.goals().goals().stream()
                .filter(RoundGoals.Goal::needsTransport)
                .forEach(goal -> goals.add(goal.position()));
        return plans.stream()
                .filter(plan -> !leavesWithTransports(plan, home) && !goals.contains(plan.target()))
                .findFirst()
                .or(() -> stepTowardNeighbour(context, home, goals));
    }

    private static Optional<TacticalPlan> holdForGravityDrive(Context context, List<TacticalPlan> plans) {
        Tile home = context.seat.getHomeSystemTile();
        if (home == null || plans.isEmpty() || !context.expectsGravityDrive()) return Optional.empty();
        if (keepsATransportHome(context, plans.getFirst(), home)) return Optional.empty();
        Set<String> goals = new HashSet<>();
        context.goals().goals().stream()
                .filter(RoundGoals.Goal::needsTransport)
                .forEach(goal -> goals.add(goal.position()));
        return plans.stream()
                .filter(plan -> plan.score() >= MIN_SCORE && keepsATransportHome(context, plan, home))
                .findFirst()
                .or(() -> stepTowardNeighbour(context, home, goals));
    }

    private static boolean stallsWithAStrategyCard(Context context, List<TacticalPlan> plans) {
        Tile home = context.seat.getHomeSystemTile();
        if (home == null || plans.isEmpty() || !context.expectsGravityDrive()) return false;
        if (keepsATransportHome(context, plans.getFirst(), home)) return false;
        return context.seat.getSCs().stream()
                .anyMatch(initiative -> !context.game.getPlayedSCs().contains(initiative));
    }

    private static boolean keepsATransportHome(Context context, TacticalPlan plan, Tile home) {
        if (plan.target().equals(home.getPosition())) return false;
        UnitHolder space = BoardView.space(home);
        int transports = 0;
        for (UnitType type : TRANSPORT_PREFERENCE) {
            if (BoardView.capacity(context.seat, type) <= 0) continue;
            transports += BoardView.undamaged(space, context.seat, type) - countOf(movesFrom(home, plan.moves()), type);
        }
        return transports > 0;
    }

    private static boolean leavesWithTransports(TacticalPlan plan, Tile home) {
        if (plan.target().equals(home.getPosition())) return true;
        return plan.moves().stream()
                .anyMatch(move -> move.origin().equals(home.getPosition())
                        && (move.type() == UnitType.Carrier || BoardView.GROUND_FORCES.contains(move.type())));
    }

    private static Optional<TacticalPlan> stepTowardNeighbour(Context context, Tile home, Set<String> goals) {
        Game game = context.game;
        Player seat = context.seat;
        UnitHolder space = BoardView.space(home);
        List<UnitType> steppers = POSITION_SHIPS.stream()
                .filter(type -> type != UnitType.Carrier && BoardView.undamaged(space, seat, type) > 0)
                .sorted(Comparator.comparingDouble(type -> cost(seat, type)))
                .toList();
        for (UnitType type : steppers) {
            List<UnitMove> step = List.of(new UnitMove(home.getPosition(), BoardView.SPACE, type, 1));
            Optional<TacticalPlan> best = Optional.empty();
            int bestDistance = Integer.MAX_VALUE;
            for (String position : MovementGraph.reach(game, seat, home.getPosition(), BoardView.moveValue(seat, type))
                    .keySet()) {
                Tile tile = game.getTileByPosition(position);
                if (tile == null || tile == home || goals.contains(position) || !holdsStep(context, tile)) continue;
                Optional<List<UnitMove>> legal = legalMoves(context, home, tile, step);
                if (legal.isEmpty() || groundForcesFromSpace(legal.get()) > 0) continue;
                if (context.defense().leaveCost(home, legal.get()) > 0) continue;
                int distance = distanceToNearestNeighbour(game, seat, tile);
                if (distance >= bestDistance) continue;
                Optional<TacticalPlan> stall = afterCannon(
                        context,
                        tile,
                        new TacticalPlan(Kind.POSITION, position, legal.get(), Map.of(), STALL_MIN_SCORE));
                if (stall.isEmpty()) continue;
                bestDistance = distance;
                best = stall;
            }
            if (best.isPresent()) return best;
        }
        return Optional.empty();
    }

    private static boolean holdsStep(Context context, Tile tile) {
        return activatable(context.game, context.seat, tile)
                && MovementGraph.canEnter(context.game, context.seat, tile)
                && !BoardView.hasEnemyUnits(context.game, context.seat, tile);
    }

    private static int distanceToNearestNeighbour(Game game, Player seat, Tile tile) {
        Map<String, Integer> around = MovementGraph.reach(game, seat, tile.getPosition(), NEIGHBOUR_SEARCH_RANGE);
        return game.getRealPlayers().stream()
                .filter(other -> other != seat && other.getHomeSystemTile() != null)
                .map(other -> around.get(other.getHomeSystemTile().getPosition()))
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .min()
                .orElse(NEIGHBOUR_SEARCH_RANGE + 1);
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

    public static List<TacticalPlan> rankedFreeingEveryToken(Game game, Player seat) {
        Set<String> freed = game.getTileMap().values().stream()
                .filter(tile -> tile.hasPlayerCC(seat))
                .map(Tile::getPosition)
                .collect(Collectors.toSet());
        return candidates(
                new Context(game, seat, freed),
                tile -> freed.contains(tile.getPosition()) || activatable(game, seat, tile));
    }

    public static double bestScore(List<TacticalPlan> plans) {
        return plans.isEmpty() ? 0 : plans.getFirst().score();
    }

    static boolean activatable(Game game, Player seat, Tile tile) {
        return !tile.hasPlayerCC(seat) && ButtonHelper.canActivateTile(game, seat, tile);
    }

    static List<TacticalPlan> candidates(Context context, Predicate<Tile> activatable) {
        return candidates(context, activatable, MIN_SCORE);
    }

    private static List<TacticalPlan> candidates(Context context, Predicate<Tile> activatable, double minScore) {
        try (Thinking.Span ignored = Thinking.enter("tactical.candidates", context::rankingKey)) {
            return sharedRanking(context, activatable).stream()
                    .filter(plan -> plan.score() >= minScore)
                    .toList();
        }
    }

    private static List<TacticalPlan> sharedRanking(Context context, Predicate<Tile> activatable) {
        return DecisionMemo.remember(
                () -> rankedCandidates(context, activatable),
                "tactical.ranking",
                context.seat,
                context.freed,
                context.hints,
                context.tokenFree,
                FleetLimit.ships(context.seat),
                MoveBoost.extra(context.seat));
    }

    private static List<TacticalPlan> rankedCandidates(Context context, Predicate<Tile> activatable) {
        String kind = !context.freed.isEmpty() ? "freeing" : !context.hints.isEmpty() ? "hinted" : "base";
        try (Thinking.Span ignored = Thinking.enter("tactical.rank." + kind)) {
            return computeRanking(context, activatable);
        }
    }

    private static List<TacticalPlan> computeRanking(Context context, Predicate<Tile> activatable) {
        List<TacticalPlan> plans = new ArrayList<>(context.game.getTileMap().values().stream()
                .filter(activatable)
                .map(tile -> bestFor(context, tile))
                .flatMap(Optional::stream)
                .toList());
        try (Thinking.Span ignored = Thinking.enter("tactical.for.stage")) {
            plans.addAll(stage(context, activatable));
        }
        try (Thinking.Span ignored = Thinking.enter("tactical.for.flank")) {
            plans.addAll(flankSpeedPlans(context, activatable));
        }
        return plans.stream()
                .map(plan -> plan.withScore(plan.score() - marchForfeit(context, plan)))
                .sorted(Comparator.comparingDouble(TacticalPlan::score).reversed())
                .toList();
    }

    public static Optional<TacticalPlan> bestForWarfare(Game game, Player seat) {
        try (Thinking.Span ignored = Thinking.enter("tactical.warfare")) {
            return bestTokenFree(game, seat);
        }
    }

    private static Optional<TacticalPlan> bestTokenFree(Game game, Player seat) {
        Context context = Context.tokenFree(game, seat);
        Predicate<Tile> activatable = tile -> tile.hasPlayerCC(seat) || ButtonHelper.canActivateTile(game, seat, tile);
        Stream<TacticalPlan> marches =
                stage(context, activatable).stream().filter(plan -> isFractureTile(game, plan.target()));
        return Stream.concat(
                        game.getTileMap().values().stream()
                                .filter(activatable)
                                .map(tile -> bestFor(context, tile))
                                .flatMap(Optional::stream),
                        marches)
                .filter(plan -> plan.score() >= 0)
                .max(Comparator.comparingDouble(TacticalPlan::score))
                .or(() -> freeStep(context));
    }

    private static Optional<TacticalPlan> freeStep(Context context) {
        return context.origins().stream()
                .map(origin -> stepTowardNeighbour(context, origin, Set.of()))
                .flatMap(Optional::stream)
                .findFirst();
    }

    public static Optional<TacticalPlan> forWarfareTarget(Game game, Player seat, String target) {
        Tile tile = game.getTileByPosition(target);
        return tile == null ? Optional.empty() : bestFor(Context.tokenFree(game, seat), tile);
    }

    static double ownAreaBonus(Territory.Zone zone) {
        return switch (zone) {
            case LANE -> LANE_PLANET_BONUS;
            case SLICE -> SLICE_PLANET_BONUS;
            case BORDER -> BORDER_PLANET_BONUS;
            default -> 0;
        };
    }

    static double frontierCardValue(Player seat) {
        return ExploreValues.frontierCard(seat.getGame(), seat);
    }

    private record Follower(Tile origin, UnitType type, int idle, double gain) {}

    private record Followers(List<UnitMove> moves, double value) {}

    private static TacticalPlan withFollowers(Context context, Tile target, TacticalPlan plan) {
        Followers followers;
        try (Thinking.Span ignored = Thinking.enter("tactical.for.followers")) {
            followers = followers(context, target, plan.moves(), plan.landings().keySet());
        }
        if (followers.moves().isEmpty()) return plan;
        List<UnitMove> moves = new ArrayList<>(plan.moves());
        int landed =
                plan.landings().values().stream().mapToInt(Integer::intValue).sum();
        String mainOrigin = shipsIn(plan.moves()).isEmpty()
                ? ""
                : shipsIn(plan.moves()).getFirst().origin();
        for (Tile origin : originsOf(context, followers.moves())) {
            List<UnitMove> fromOrigin = movesFrom(origin, merge(concat(plan.moves(), followers.moves())));
            List<UnitMove> cargo = origin.getPosition().equals(mainOrigin)
                    ? forwardCargo(context, origin, target, fromOrigin, landed)
                    : List.of();
            Optional<List<UnitMove>> legal = legalMoves(context, origin, target, concat(fromOrigin, cargo));
            if (legal.isEmpty()) return plan;
            moves.removeIf(move -> move.origin().equals(origin.getPosition()));
            moves.addAll(legal.get());
        }
        if (BoardView.nonFighterShips(BoardView.space(target), context.seat) + movedNonFighterShips(moves)
                > FleetLimit.ships(context.seat)) {
            return plan;
        }
        return new TacticalPlan(
                plan.kind(), plan.target(), merge(moves), plan.landings(), plan.score() + followers.value());
    }

    private static Followers followers(Context context, Tile target, List<UnitMove> base, Collection<String> landings) {
        List<Follower> candidates = new ArrayList<>();
        for (Tile origin : context.origins()) {
            if (origin == target) continue;
            candidates.addAll(idleShips(context, origin, target, base));
        }
        candidates.sort(Comparator.comparingDouble(Follower::gain).reversed());
        int room = FleetLimit.ships(context.seat)
                - BoardView.nonFighterShips(BoardView.space(target), context.seat)
                - movedNonFighterShips(base);
        List<UnitMove> moves = new ArrayList<>();
        double value = 0;
        double lossSoFar = 0;
        int joined = 0;
        for (Follower follower : candidates) {
            int taken = 0;
            double unloaded = unloadedHaulerPenalty(context, follower, base);
            while (taken < follower.idle() && room > 0) {
                UnitMove trying =
                        new UnitMove(follower.origin().getPosition(), BoardView.SPACE, follower.type(), taken + 1);
                double loss = context.departureLoss(target, base, concat(moves, List.of(trying)), landings);
                double marginal =
                        FOLLOWER_SHARE * follower.gain() / (joined + 1) - GROUP_MOVE_COST - unloaded + loss - lossSoFar;
                if (marginal <= 0) break;
                lossSoFar = loss;
                value += marginal;
                taken++;
                joined++;
                room--;
            }
            if (taken > 0) {
                moves.add(new UnitMove(follower.origin().getPosition(), BoardView.SPACE, follower.type(), taken));
            }
        }
        return new Followers(moves, value);
    }

    private static double unloadedHaulerPenalty(Context context, Follower follower, List<UnitMove> base) {
        boolean loadsWithMainGroup = shipsIn(base).stream()
                .anyMatch(move -> move.origin().equals(follower.origin().getPosition()));
        if (loadsWithMainGroup || !CapacityUse.hauler(context.seat, follower.type())) return 0;
        return CapacityUse.EMPTY_SLOT_VALUE * BoardView.capacity(context.seat, follower.type());
    }

    private static List<Follower> idleShips(Context context, Tile origin, Tile target, List<UnitMove> base) {
        Player seat = context.seat;
        UnitHolder space = BoardView.space(origin);
        List<UnitMove> idle = new ArrayList<>();
        for (UnitType type : BoardView.MOVING_SHIPS) {
            if (!context.reaches(origin, target, type)) continue;
            int count = BoardView.count(space, seat, type)
                    - countOf(movesFrom(origin, base), type)
                    - reservedShips(context, origin, target, type);
            if (count > 0) idle.add(new UnitMove(origin.getPosition(), BoardView.SPACE, type, count));
        }
        List<Follower> followers = new ArrayList<>();
        for (UnitMove move : context.defense().holdBack(origin, movesFrom(origin, base), idle)) {
            double gain = context.lockShare()
                    * context.goals()
                            .forwardGain(
                                    origin,
                                    target,
                                    BoardView.moveValue(seat, move.type()),
                                    BoardView.capacity(seat, move.type()) > 0);
            if (gain > 0) followers.add(new Follower(origin, move.type(), move.count(), gain));
        }
        return followers;
    }

    private static int reservedShips(Context context, Tile origin, Tile target, UnitType type) {
        int capacity = BoardView.capacity(context.seat, type);
        if (capacity <= 0) return 0;
        int need = context.otherExpansionNeed(origin, target, type);
        return need <= 0 ? 0 : (need + capacity - 1) / capacity;
    }

    private static List<UnitMove> forwardCargo(
            Context context, Tile origin, Tile target, List<UnitMove> group, int landed) {
        List<UnitMove> ships = shipsIn(group);
        if (ships.isEmpty()) return List.of();
        int alreadyForward = Math.max(0, unitCount(groundIn(group)) - landed);
        int spare = Math.min(
                capacityOf(context.seat, ships) - cargoIn(group),
                context.forwardNeed(target, ships.getFirst().type()) - alreadyForward);
        List<UnitMove> taken = new ArrayList<>();
        for (UnitMove available : availableGroundForces(context.game, context.seat, origin, context.defense(), ships)) {
            if (spare <= 0) break;
            if (BoardView.SPACE.equals(available.holder())) continue;
            int alreadyMoving = group.stream()
                    .filter(move -> move.holder().equals(available.holder()) && move.type() == available.type())
                    .mapToInt(UnitMove::count)
                    .sum();
            int count = Math.min(spare, available.count() - alreadyMoving);
            if (count <= 0) continue;
            taken.add(new UnitMove(available.origin(), available.holder(), available.type(), count));
            spare -= count;
        }
        return taken;
    }

    private static List<UnitMove> groundIn(List<UnitMove> moves) {
        return moves.stream()
                .filter(move -> BoardView.GROUND_FORCES.contains(move.type()))
                .toList();
    }

    private static List<Tile> originsOf(Context context, List<UnitMove> moves) {
        return moves.stream()
                .map(UnitMove::origin)
                .distinct()
                .map(context.game::getTileByPosition)
                .filter(Objects::nonNull)
                .toList();
    }

    private static List<UnitMove> movesFrom(Tile origin, List<UnitMove> moves) {
        return moves.stream()
                .filter(move -> move.origin().equals(origin.getPosition()))
                .toList();
    }

    private static double forwardPositionGain(Context context, Tile origin, Tile target, UnitType transport) {
        return FORWARD_POSITION_SHARE
                * context.lockShare()
                * context.goals().forwardGain(origin, target, BoardView.moveValue(context.seat, transport), true);
    }

    private static List<UnitMove> concat(List<UnitMove> first, List<UnitMove> second) {
        List<UnitMove> all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }

    public static boolean worthProducingAt(Game game, Player seat, Tile tile) {
        return produce(new Context(game, seat), tile)
                .filter(plan -> plan.score() >= MIN_SCORE)
                .isPresent();
    }

    public static Optional<TacticalPlan> forTarget(Game game, Player seat, String target) {
        try (Thinking.Span ignored = Thinking.enter("tactical.target")) {
            Tile tile = game.getTileByPosition(target);
            return tile == null ? Optional.empty() : bestFor(new Context(game, seat), tile);
        }
    }

    private static Optional<TacticalPlan> bestFor(Context context, Tile tile) {
        if (!MovementGraph.canEnter(context.game, context.seat, tile)) return Optional.empty();
        List<TacticalPlan> plans = new ArrayList<>();
        moving(context, tile, "tactical.for.expansion", () -> context.expansionPlan(tile))
                .ifPresent(plans::add);
        moving(context, tile, "tactical.for.attack", () -> attack(context, tile))
                .ifPresent(plans::add);
        timed("tactical.for.cannonRaid", () -> cannonRaid(context, tile)).ifPresent(plans::add);
        timed("tactical.for.produce", () -> produce(context, tile)).ifPresent(plans::add);
        moving(context, tile, "tactical.for.position", () -> position(context, tile))
                .ifPresent(plans::add);
        moving(context, tile, "tactical.for.screen", () -> screen(context, tile))
                .ifPresent(plans::add);
        moving(context, tile, "tactical.for.advance", () -> advance(context, tile))
                .ifPresent(plans::add);
        moving(context, tile, "tactical.for.pickup", () -> pickup(context, tile))
                .ifPresent(plans::add);
        moving(context, tile, "tactical.for.defend", () -> defend(context, tile))
                .ifPresent(plans::add);
        moving(context, tile, "tactical.for.garrison", () -> garrison(context, tile))
                .ifPresent(plans::add);
        return plans.stream()
                .map(plan -> plan.withScore(PlayAreaNotes.adjustedScore(context.game, context.seat, tile, plan)
                        + SetupHint.bonusFor(context.hints, plan)))
                .max(Comparator.comparingDouble(TacticalPlan::score))
                .map(plan -> timed("tactical.for.fighters", () -> Optional.of(withFighters(context, tile, plan)))
                        .orElse(plan));
    }

    private static Optional<TacticalPlan> moving(
            Context context, Tile tile, String span, Supplier<Optional<TacticalPlan>> plan) {
        if (context.unreachable(tile)) {
            try (Thinking.Span ignored = Thinking.enter("tactical.for.skipped")) {
                return Optional.empty();
            }
        }
        return timed(span, plan);
    }

    private static Optional<TacticalPlan> timed(String span, Supplier<Optional<TacticalPlan>> plan) {
        try (Thinking.Span ignored = Thinking.enter(span)) {
            return plan.get();
        }
    }

    private static Optional<TacticalPlan> garrison(Context context, Tile tile) {
        if (BoardView.hasEnemyUnits(context.game, context.seat, tile)) return Optional.empty();
        List<TacticalPlan> plans = new ArrayList<>();
        occupy(context, tile).ifPresent(plans::add);
        mechanize(context, tile).ifPresent(plans::add);
        return plans.stream().max(Comparator.comparingDouble(TacticalPlan::score));
    }

    private static Optional<TacticalPlan> occupy(Context context, Tile tile) {
        Player seat = context.seat;
        if (!context.campaign().holdsFringe()) return Optional.empty();
        Optional<Planet> fringe = fringeIn(context, tile);
        if (fringe.isEmpty()) return Optional.empty();
        Planet planet = fringe.get();
        return bestGarrison(
                        context,
                        tile,
                        origin -> availableGroundForces(context.game, seat, origin, context.defense(), List.of()),
                        arriving -> Map.of(planet.getName(), BoardView.groundForces(planet, seat) + arriving))
                .map(plan -> withFringePull(context, planet, plan));
    }

    private static Optional<Planet> fringeIn(Context context, Tile tile) {
        Player seat = context.seat;
        Optional<Planet> anchor = context.campaign().anchor();
        if (anchor.isEmpty()) return Optional.empty();
        return ownedPlanets(seat, tile)
                .filter(planet -> planet.getName().equals(anchor.get().getName()))
                .findFirst();
    }

    private static TacticalPlan withFringePull(Context context, Planet planet, TacticalPlan plan) {
        int arriving = plan.landings().getOrDefault(planet.getName(), 0) - BoardView.groundForces(planet, context.seat);
        return new TacticalPlan(
                plan.kind(),
                plan.target(),
                plan.moves(),
                plan.landings(),
                plan.score() + context.campaign().ferryPull(planet, arriving));
    }

    private static Optional<TacticalPlan> mechanize(Context context, Tile tile) {
        Player seat = context.seat;
        if (!context.campaign().holdsMechanize()) return Optional.empty();
        List<Planet> withoutMechs = ownedPlanets(seat, tile)
                .filter(planet -> context.campaign().bareTargets().contains(planet))
                .toList();
        if (withoutMechs.isEmpty()) return Optional.empty();
        return bestGarrison(
                context,
                tile,
                origin -> take(spareMechs(context, origin), withoutMechs.size()),
                arriving -> oneEach(seat, withoutMechs.subList(0, Math.min(arriving, withoutMechs.size()))));
    }

    private static List<UnitMove> spareMechs(Context context, Tile origin) {
        return spareMechs(context.game, context.seat, origin, context.defense());
    }

    static List<UnitMove> spareMechs(Game game, Player seat, Tile origin, DefenseOutlook defense) {
        List<UnitMove> spare = new ArrayList<>();
        for (UnitMove move : ofType(availableGroundForces(game, seat, origin, defense, List.of()), UnitType.Mech)) {
            UnitHolder holder = origin.getUnitHolders().get(move.holder());
            boolean leavesItBare = !BoardView.SPACE.equals(move.holder())
                    && holder != null
                    && BoardView.count(holder, seat, UnitType.Mech) <= move.count();
            addMove(spare, move.origin(), move.holder(), UnitType.Mech, move.count() - (leavesItBare ? 1 : 0));
        }
        return spare;
    }

    private static Map<String, Integer> oneEach(Player seat, List<Planet> planets) {
        Map<String, Integer> landings = new LinkedHashMap<>();
        for (Planet planet : planets) landings.put(planet.getName(), BoardView.groundForces(planet, seat) + 1);
        return landings;
    }

    private static Stream<Planet> ownedPlanets(Player seat, Tile tile) {
        return tile.getPlanetUnitHolders().stream()
                .filter(planet -> seat.getPlanets().contains(planet.getName()));
    }

    private static Optional<TacticalPlan> bestGarrison(
            Context context,
            Tile tile,
            Function<Tile, List<UnitMove>> cargoAt,
            Function<Integer, Map<String, Integer>> landingsFor) {
        TacticalPlan best = null;
        for (Tile origin : context.origins()) {
            if (origin == tile) continue;
            Optional<TacticalPlan> plan = garrisonFrom(context, tile, origin, cargoAt.apply(origin), landingsFor);
            if (plan.isPresent() && (best == null || plan.get().score() > best.score())) best = plan.get();
        }
        return Optional.ofNullable(best);
    }

    private static Optional<TacticalPlan> garrisonFrom(
            Context context,
            Tile tile,
            Tile origin,
            List<UnitMove> groundForces,
            Function<Integer, Map<String, Integer>> landingsFor) {
        Player seat = context.seat;
        int available = unitCount(groundForces);
        if (available == 0) return Optional.empty();
        List<UnitMove> transports = transports(context, origin, tile, available);
        if (transports.isEmpty()) return Optional.empty();
        List<UnitMove> cargo = take(groundForces, Math.min(available, capacityOf(seat, transports)));
        Optional<List<UnitMove>> legal = loadedMoves(context, origin, tile, transports, cargo);
        if (legal.isEmpty()) return Optional.empty();
        Map<String, Integer> landings = landingsFor.apply(unitCount(cargo));
        double gain = context.objectiveGain(tile, legal.get(), landings.keySet());
        if (gain <= 0) return Optional.empty();
        double score = gain
                - DISTANCE_COST
                        * context.distance(origin, tile, transports.getFirst().type())
                - GROUP_MOVE_COST * (movedNonFighterShips(transports) - 1)
                - context.defense().leaveCost(origin, legal.get());
        return Optional.of(new TacticalPlan(Kind.GARRISON, tile.getPosition(), legal.get(), landings, score));
    }

    private static Optional<TacticalPlan> expand(Context context, Tile tile) {
        Game game = context.game;
        Player seat = context.seat;
        if (BoardView.hasEnemyUnits(game, seat, tile)) return Optional.empty();
        List<Planet> free = tile.getPlanetUnitHolders().stream()
                .filter(planet -> BoardView.controller(game, planet.getName()) == null)
                .filter(planet -> !BoardView.hasCustodians(planet) || context.canPayCustodians())
                .sorted(IconStake.claimOrder(game, seat))
                .toList();
        if (free.isEmpty()) return Optional.empty();
        TacticalPlan best = landInPlace(context, tile, free)
                .flatMap(found -> afterCannon(context, tile, found))
                .orElse(null);
        for (Tile origin : context.origins()) {
            if (origin == tile) continue;
            Optional<TacticalPlan> plan = expandFrom(context, tile, origin, free)
                    .map(found -> withCommanderLandings(context, tile, found, free))
                    .flatMap(found -> afterCannon(context, tile, found));
            if (plan.isPresent() && (best == null || plan.get().score() > best.score())) best = plan.get();
        }
        return Optional.ofNullable(best);
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
        groundForces.addAll(context.commanderMoves(tile));
        int landed = Math.min(unitCount(groundForces), free.size());
        if (landed == 0) return Optional.empty();
        List<Planet> targets = free.subList(0, landed);
        List<UnitMove> landing = take(mechsFirstForHazardousPlanets(context.seat, groundForces, targets), landed);
        Map<Planet, int[]> forces =
                distribute(context.seat, targets, countOf(landing, UnitType.Infantry), countOf(landing, UnitType.Mech));
        Map<String, Integer> landings = new LinkedHashMap<>();
        double value = 0;
        for (Planet planet : targets) {
            int[] split = forces.get(planet);
            landings.put(planet.getName(), split[INFANTRY] + split[MECH]);
            value += BoardView.planetValue(planet)
                    + planetTempo(context.game)
                    + context.exploreValue(tile, planet, split[INFANTRY], split[MECH]);
            if (BoardView.hasCustodians(planet)) value += CUSTODIANS_VALUE;
            if (isMecatol(context.game, planet)) {
                value += MecatolStake.ownHoldValue(context.game, seat) + context.mecatolCash();
            }
            value += context.expansionZoneBonus(tile);
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
        boolean approaching = context.approaches(tile);
        if (approaching) loadable = Math.max(loadable, Math.min(landed + 1, Math.min(capacity, available - reserved)));
        List<Planet> targets = free.subList(0, landed);
        List<UnitMove> ordered = mechsFirstForHazardousPlanets(context.seat, groundForces, targets);
        List<UnitMove> cargo = take(ordered, loadable);
        Optional<List<UnitMove>> legal = loadedMoves(context, origin, tile, transports, cargo);
        if (legal.isEmpty()) {
            cargo = take(ordered, landed);
            legal = loadedMoves(context, origin, tile, transports, cargo);
        }
        if (legal.isEmpty()) return Optional.empty();
        Map<Planet, int[]> forces =
                distribute(context.seat, targets, countOf(cargo, UnitType.Infantry), countOf(cargo, UnitType.Mech));
        Map<String, Integer> landings = new LinkedHashMap<>();
        for (Planet planet : targets) {
            int[] landing = forces.get(planet);
            landings.put(planet.getName(), landing[INFANTRY] + landing[MECH]);
        }
        double value = landingValue(context, tile, targets, forces);
        int spareForces = unitCount(cargo) - landed;
        double approach = approaching && spareForces > 0 ? APPROACH_SHARE * context.approachGain(tile) : 0;
        if (approach > 0) spareForces--;
        value += approach;
        value += context.offense()
                .expansionImperial(tile, targets, defendersAfterExpanding(context, tile, transports, forces));
        value += FORWARD_GROUND_FORCE_VALUE * Math.min(spareForces, forward);
        value += FIGHTER_ESCORT_VALUE * countOf(legal.get(), UnitType.Fighter);
        value += context.objectiveGain(tile, legal.get(), landings.keySet());
        double score = value
                - DISTANCE_COST
                        * context.distance(origin, tile, transports.getFirst().type())
                - GROUP_MOVE_COST * (movedNonFighterShips(transports) - 1)
                - context.defense().leaveCost(origin, legal.get())
                - EXPAND_EXPOSURE_SHARE * context.exposure(tile, legal.get(), landedFrom(tile, forces))
                + forwardPositionGain(
                        context, origin, tile, transports.getFirst().type());
        TacticalPlan plan = new TacticalPlan(Kind.EXPAND, tile.getPosition(), legal.get(), landings, score);
        return Optional.of(withFollowers(context, tile, plan));
    }

    private static double landingValue(Context context, Tile tile, List<Planet> targets, Map<Planet, int[]> forces) {
        double value = 0;
        for (Planet planet : targets) {
            int[] landing = forces.get(planet);
            value += BoardView.planetValue(planet)
                    + planetTempo(context.game)
                    + context.exploreValue(tile, planet, landing[INFANTRY], landing[MECH]);
            if (BoardView.hasCustodians(planet)) value += CUSTODIANS_VALUE;
            if (isMecatol(context.game, planet)) value += MecatolStake.ownHoldValue(context.game, context.seat);
            value += context.expansionZoneBonus(tile);
        }
        return value;
    }

    private static List<Planet> landingTargets(Context context, Tile tile) {
        return freePlanetsIn(context, tile).stream()
                .filter(planet -> !BoardView.enemyGroundForcesOn(context.game, context.seat, planet))
                .filter(planet -> !BoardView.enemyStructuresOn(context.game, context.seat, planet))
                .sorted(IconStake.claimOrder(context.game, context.seat))
                .toList();
    }

    private static TacticalPlan withLandings(Context context, Tile tile, TacticalPlan plan) {
        return withFighters(context, tile, withGroundLandings(context, tile, plan));
    }

    private static TacticalPlan withFighters(Context context, Tile tile, TacticalPlan plan) {
        TacticalPlan best = plan;
        for (Tile origin : originsOf(context, plan.moves())) {
            best = withFightersFrom(context, tile, origin, best);
        }
        return best;
    }

    private static TacticalPlan withFightersFrom(Context context, Tile tile, Tile origin, TacticalPlan plan) {
        List<UnitMove> leaving = movesFrom(origin, plan.moves());
        int spare = capacityOf(context.seat, shipsIn(leaving)) - cargoIn(leaving);
        int fighters = Math.min(spare, escortFighters(context, origin, leaving) - countOf(leaving, UnitType.Fighter));
        if (fighters <= 0) return plan;
        UnitMove escort = new UnitMove(origin.getPosition(), BoardView.SPACE, UnitType.Fighter, fighters);
        Optional<List<UnitMove>> legal = legalMoves(context, origin, tile, concat(leaving, List.of(escort)));
        if (legal.isEmpty()) return plan;
        List<UnitMove> moves = new ArrayList<>(plan.moves());
        moves.removeIf(move -> move.origin().equals(origin.getPosition()));
        moves.addAll(legal.get());
        moves = merge(moves);
        double carried = countOf(moves, UnitType.Fighter) - countOf(plan.moves(), UnitType.Fighter);
        double objectiveDelta = context.objectiveGain(
                        tile, moves, plan.landings().keySet())
                - context.objectiveGain(tile, plan.moves(), plan.landings().keySet());
        double score = plan.score()
                + escortValue(FIGHTER_ESCORT_VALUE * carried, objectiveDelta)
                - leaveCosts(context, moves)
                + leaveCosts(context, plan.moves());
        if (score <= plan.score()) return plan;
        return new TacticalPlan(plan.kind(), plan.target(), moves, plan.landings(), score);
    }

    private static double escortValue(double escort, double objectiveDelta) {
        return objectiveDelta < 0 ? escort + objectiveDelta : Math.max(escort, objectiveDelta);
    }

    private static TacticalPlan withGroundLandings(Context context, Tile tile, TacticalPlan plan) {
        if (!plan.landings().isEmpty() || !movesAHauler(context, plan.moves())) return plan;
        List<Planet> free = landingTargets(context, tile);
        if (free.isEmpty() || BoardView.hasEnemyShips(context.game, context.seat, tile)) return plan;
        TacticalPlan best = plan;
        for (Tile origin : originsOf(context, plan.moves())) {
            TacticalPlan loaded = loadedFrom(context, tile, origin, free, plan);
            if (loaded.score() > best.score()) best = loaded;
        }
        return best;
    }

    private static boolean movesAHauler(Context context, List<UnitMove> moves) {
        return shipsIn(moves).stream().anyMatch(move -> BoardView.capacity(context.seat, move.type()) > 0);
    }

    private static TacticalPlan loadedFrom(
            Context context, Tile tile, Tile origin, List<Planet> free, TacticalPlan plan) {
        Player seat = context.seat;
        List<UnitMove> leaving = movesFrom(origin, plan.moves());
        int spare = capacityOf(seat, shipsIn(leaving)) - cargoIn(leaving);
        int aboard = unitCount(groundIn(leaving));
        List<UnitMove> onPlanets =
                availableGroundForces(context.game, seat, origin, context.defense(), shipsIn(leaving)).stream()
                        .filter(move -> !BoardView.SPACE.equals(move.holder()))
                        .toList();
        onPlanets = mechsFirstForHazardousPlanets(context.seat, onPlanets, free);
        int room = Math.min(Math.min(spare, unitCount(onPlanets)), free.size() - aboard);
        if (room <= 0 && aboard == 0) return plan;
        TacticalPlan best = plan;
        for (int added = Math.max(room, 0); added >= (aboard == 0 ? 1 : 0); added--) {
            Optional<TacticalPlan> loaded = loadedPlan(context, tile, origin, free, plan, take(onPlanets, added));
            if (loaded.isPresent() && loaded.get().score() > best.score()) best = loaded.get();
        }
        return best;
    }

    private static Optional<TacticalPlan> loadedPlan(
            Context context, Tile tile, Tile origin, List<Planet> free, TacticalPlan plan, List<UnitMove> cargo) {
        List<UnitMove> fromOrigin = concat(movesFrom(origin, plan.moves()), cargo);
        Optional<List<UnitMove>> legal = legalMoves(context, origin, tile, fromOrigin);
        if (legal.isEmpty()) return Optional.empty();
        List<UnitMove> moves = new ArrayList<>(plan.moves());
        moves.removeIf(move -> move.origin().equals(origin.getPosition()));
        moves.addAll(legal.get());
        moves = merge(moves);
        List<UnitMove> ground = groundIn(legal.get());
        int landed = Math.min(unitCount(ground), free.size());
        List<Planet> targets = free.subList(0, landed);
        List<UnitMove> ordered = take(mechsFirstForHazardousPlanets(context.seat, ground, targets), landed);
        Map<Planet, int[]> forces =
                distribute(context.seat, targets, countOf(ordered, UnitType.Infantry), countOf(ordered, UnitType.Mech));
        Map<String, Integer> landings = new LinkedHashMap<>();
        forces.forEach((planet, landing) -> landings.put(planet.getName(), landing[INFANTRY] + landing[MECH]));
        double score = plan.score()
                + landingValue(context, tile, targets, forces)
                + context.objectiveGain(tile, moves, landings.keySet())
                - context.objectiveGain(tile, plan.moves(), List.of())
                - leaveCosts(context, moves)
                + leaveCosts(context, plan.moves())
                + CapacityUse.emptyHaulerPenalty(context.seat, plan.moves())
                - CapacityUse.emptyHaulerPenalty(context.seat, moves)
                - EXPAND_EXPOSURE_SHARE
                        * Math.max(
                                0,
                                context.exposure(tile, moves, landedFrom(tile, forces))
                                        - context.exposure(tile, plan.moves(), List.of()));
        return Optional.of(new TacticalPlan(Kind.EXPAND, tile.getPosition(), moves, landings, score));
    }

    private static double leaveCosts(Context context, List<UnitMove> moves) {
        return originsOf(context, moves).stream()
                .mapToDouble(origin -> context.defense().leaveCost(origin, moves))
                .sum();
    }

    private static TacticalPlan withCommanderLandings(
            Context context, Tile tile, TacticalPlan plan, List<Planet> free) {
        List<Planet> uncovered = free.stream()
                .filter(planet -> !plan.landings().containsKey(planet.getName()))
                .limit(context.commanderPulls(tile).size())
                .toList();
        if (uncovered.isEmpty()) return plan;
        Map<String, Integer> landings = new LinkedHashMap<>(plan.landings());
        double value = plan.score()
                - context.objectiveGain(tile, plan.moves(), plan.landings().keySet());
        for (Planet planet : uncovered) {
            landings.put(planet.getName(), 1);
            value += BoardView.planetValue(planet)
                    + planetTempo(context.game)
                    + context.exploreValue(tile, planet, 1, 0)
                    + context.expansionZoneBonus(tile);
        }
        value += context.objectiveGain(tile, plan.moves(), landings.keySet());
        return new TacticalPlan(plan.kind(), plan.target(), plan.moves(), landings, value);
    }

    private static List<UnitMove> landedFrom(Tile tile, Map<Planet, int[]> forces) {
        int infantry =
                forces.values().stream().mapToInt(landing -> landing[INFANTRY]).sum();
        int mechs = forces.values().stream().mapToInt(landing -> landing[MECH]).sum();
        List<UnitMove> landed = new ArrayList<>();
        addMove(landed, tile.getPosition(), BoardView.SPACE, UnitType.Infantry, infantry);
        addMove(landed, tile.getPosition(), BoardView.SPACE, UnitType.Mech, mechs);
        return landed;
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

    private static List<UnitMove> mechsFirstForHazardousPlanets(
            Player seat, List<UnitMove> forces, List<Planet> targets) {
        long hazardous = mechanizes(seat)
                ? targets.size()
                : targets.stream().filter(TacticalPlanner::isHazardous).count();
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

    private static Map<Planet, int[]> distribute(Player seat, List<Planet> targets, int infantry, int mechs) {
        Map<Planet, int[]> forces = new LinkedHashMap<>();
        targets.forEach(planet -> forces.put(planet, new int[2]));
        int[] left = {infantry, mechs};
        boolean mechanize = mechanizes(seat);
        for (Planet planet : targets) {
            if (mechanize || isHazardous(planet)) give(forces.get(planet), left, MECH);
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

    private static Optional<TacticalPlan> cannonRaid(Context context, Tile tile) {
        Game game = context.game;
        Player seat = context.seat;
        if (BoardView.hasOwnShips(seat, tile)
                || !CannonWipeRules.coversAShip(game, seat, tile)
                || !context.offers(tile)) {
            return Optional.empty();
        }
        Player target = CannonWipeRules.shipOwner(game, seat, tile);
        double chance = CannonWipeRules.chanceToWipe(game, seat, tile, target, Map.of());
        double score = secretShare(game, seat, CannonWipeRules.TURN_THEIR_FLEETS_TO_DUST)
                        * ObjectiveValue.VICTORY_POINT_VALUE
                        * chance
                + chance * (target.isNeutral() ? 0 : BoardView.shipCost(BoardView.space(tile), target));
        if (score < Math.max(MIN_SCORE, CardValueScale.TOKEN_FLOOR)) return Optional.empty();
        return Optional.of(new TacticalPlan(Kind.RAID, tile.getPosition(), List.of(), Map.of(), score));
    }

    private static Optional<TacticalPlan> bestAttack(Context context, Tile tile) {
        Player opponent = singleOpponent(context.game, context.seat, tile);
        if (opponent == null) return SharedSystemAttack.best(context, tile);
        return finalPlan(context, tile, candidatePool(context, tile, opponent));
    }

    static List<Candidate> candidatePool(Context context, Tile tile, Player opponent) {
        List<Candidate> pool = new ArrayList<>();
        for (Tile origin : context.origins()) {
            if (origin != tile) pool.addAll(candidatesFrom(context, tile, origin, opponent));
        }
        pool.addAll(combinations(context, tile, opponent, pool));
        commanderOnly(context, tile, opponent).ifPresent(pool::add);
        return pool;
    }

    private static Optional<Candidate> commanderOnly(Context context, Tile tile, Player opponent) {
        if (context.commanderPulls(tile).isEmpty() || BoardView.hasEnemyShips(context.game, context.seat, tile)) {
            return Optional.empty();
        }
        return candidate(context, tile, opponent, List.of(), false, null);
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

    record Departure(List<UnitMove> ships, List<UnitMove> cargo, boolean heldBack) {}

    record Part(Tile origin, Departure departure) {}

    record Candidate(
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
        return raidsForHeldSecret(context.seat, candidate.estimate())
                || homeLandingJustified(context, opponent, candidate.quick());
    }

    private static boolean raidsForHeldSecret(Player seat, StrikeEstimate estimate) {
        Set<String> held = seat.getSecretsUnscored().keySet();
        return estimate.groundCombat() && (held.contains(DARKEN_THE_SKIES) || held.contains(BECOME_A_MARTYR));
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
        Strike strike = Strike.of(
                        seat, tile, opponent, origins, departing, rules(context, tile, opponent, homeLandings))
                .withPulled(context.commanderForces(tile));
        StrikeEstimate estimate = StrikeEvaluator.estimate(game, seat, strike);
        if (estimate.spaceWin() <= 0) return Optional.empty();
        Optional<List<UnitMove>> legal = executedMoves(context, parts, tile, estimate);
        if (legal.isEmpty()) return Optional.empty();
        Strike executed = strike.withMoves(legal.get());
        ContextTerms terms = terms(context, tile, executed, estimate);
        StrikeValue quick = context.offense().evaluate(executed, estimate, terms, false);
        return Optional.of(new Candidate(parts, executed, estimate, terms, quick, heldBack, blend));
    }

    static StrikeRules rules(Context context, Tile tile, Player opponent, boolean homeLandings) {
        Game game = context.game;
        double threat = context.defense().standings().winThreat(opponent);
        boolean conquering = tile.isHomeSystem(game) && wantsToConquer(game, context.seat);
        double minWin = threat >= DESPERATE_THREAT ? DESPERATE_MIN_WIN : MIN_WIN;
        return new StrikeRules(conquering, minWin, homeLandings, context.canPayCustodians());
    }

    static Optional<List<UnitMove>> executedMoves(
            Context context, List<Part> parts, Tile tile, StrikeEstimate estimate) {
        Player seat = context.seat;
        int landedTotal = estimate.groundSent().values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        int landed = Math.max(0, landedTotal - context.commanderPulls(tile).size());
        List<UnitMove> allCargo = new ArrayList<>();
        parts.forEach(part -> allCargo.addAll(part.departure().cargo()));
        List<UnitMove> carried = take(allCargo, landed);
        int moving = parts.stream()
                .mapToInt(part -> movedNonFighterShips(part.departure().ships()))
                .sum();
        boolean overflow = SeatStrike.landsOnSeat(context.game, seat, tile, estimate);
        if (!overflow && BoardView.nonFighterShips(BoardView.space(tile), seat) + moving > FleetLimit.ships(seat)) {
            return Optional.empty();
        }
        List<UnitMove> executed = new ArrayList<>();
        for (Part part : parts) {
            List<UnitMove> own = new ArrayList<>(part.departure().ships());
            carried.stream()
                    .filter(move -> move.origin().equals(part.origin().getPosition()))
                    .forEach(own::add);
            Optional<List<UnitMove>> legal = legalMoves(context, part.origin(), tile, own, overflow);
            if (legal.isEmpty()) return Optional.empty();
            executed.addAll(legal.get());
        }
        return Optional.of(merge(executed));
    }

    private static ContextTerms terms(Context context, Tile tile, Strike strike, StrikeEstimate estimate) {
        Game game = context.game;
        Player seat = context.seat;
        double objective =
                context.objectiveGain(tile, strike.moves(), estimate.landings().keySet())
                        + context.seatAdjustment(tile, strike.moves(), estimate);
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
                territoryValues(context, tile),
                TraitRaid.exposedObjective(context, tile, strike, estimate),
                context.seatStake(tile, estimate));
    }

    static Map<String, Double> territoryValues(Context context, Tile tile) {
        double bonus = ownAreaBonus(context.territory().zone(tile));
        if (bonus == 0) return Map.of();
        Map<String, Double> values = new HashMap<>();
        tile.getPlanetUnitHolders().forEach(planet -> values.put(planet.getName(), bonus));
        return values;
    }

    private static Optional<TacticalPlan> finalPlan(Context context, Tile tile, List<Candidate> pool) {
        return finalPlan(pool, finalist -> fullPlan(context, tile, finalist));
    }

    static Optional<TacticalPlan> finalPlan(List<Candidate> pool, Function<Candidate, Optional<TacticalPlan>> planner) {
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
            Optional<TacticalPlan> plan = planner.apply(finalist);
            if (plan.isPresent() && (best == null || plan.get().score() > best.score())) best = plan.get();
        }
        return Optional.ofNullable(best);
    }

    static double attackMinScore(Player opponent) {
        return opponent.isNeutral() ? NEUTRAL_ATTACK_MIN_SCORE : ATTACK_MIN_SCORE;
    }

    private static void addDistinct(List<Candidate> finalists, Candidate candidate) {
        if (finalists.stream().noneMatch(found -> found == candidate)) finalists.add(candidate);
    }

    static Optional<TacticalPlan> fullPlan(Context context, Tile tile, Candidate candidate) {
        if (candidate.estimate().spaceWin() < candidate.strike().rules().landMinWin()) return Optional.empty();
        double minimum = attackMinScore(candidate.strike().opponent());
        StrikeValue value =
                context.offense().evaluate(candidate.strike(), candidate.estimate(), candidate.terms(), true, minimum);
        double score = value.score() - CapacityUse.loneHaulerPenalty(context.seat, candidate.moves());
        if (score < minimum) return Optional.empty();
        return Optional.of(new TacticalPlan(
                Kind.ATTACK,
                tile.getPosition(),
                candidate.moves(),
                candidate.estimate().landings(),
                score));
    }

    private static List<TacticalPlan> stage(Context context, Predicate<Tile> activatable) {
        if (!STAGE_ENABLED) return List.of();
        Map<String, TacticalPlan> best = new LinkedHashMap<>();
        for (Tile target : stageTargets(context)) {
            Player opponent = singleOpponent(context.game, context.seat, target);
            for (TacticalPlan plan : stageAgainst(context, activatable, target, opponent)) {
                TacticalPlan known = best.get(plan.target());
                if (known == null || plan.score() > known.score()) best.put(plan.target(), plan);
            }
        }
        for (TacticalPlan plan : approachPlans(context, activatable)) {
            TacticalPlan known = best.get(plan.target());
            if (known == null || plan.score() > known.score()) best.put(plan.target(), plan);
        }
        for (TacticalPlan plan : marchPlans(context, activatable)) {
            TacticalPlan known = best.get(plan.target());
            if (known == null || plan.score() > known.score()) best.put(plan.target(), plan);
        }
        return new ArrayList<>(best.values());
    }

    private static List<TacticalPlan> approachPlans(Context context, Predicate<Tile> activatable) {
        Tile mecatol = context.game.getMecatolTile();
        if (mecatol == null
                || !context.race().isOpen()
                || context.expansionPlan(mecatol).isPresent()) {
            return List.of();
        }
        List<TacticalPlan> plans = new ArrayList<>();
        for (Tile origin : context.origins())
            approachFrom(context, activatable, origin).ifPresent(plans::add);
        return plans;
    }

    private static List<TacticalPlan> marchPlans(Context context, Predicate<Tile> activatable) {
        if (context.iconTiles().isEmpty()) return List.of();
        List<TacticalPlan> plans = new ArrayList<>();
        for (Tile origin : context.origins())
            marchFrom(context, activatable, origin).ifPresent(plans::add);
        return plans;
    }

    private static Optional<TacticalPlan> marchFrom(Context context, Predicate<Tile> activatable, Tile origin) {
        Game game = context.game;
        Player seat = context.seat;
        int distanceHere = context.iconDistance(origin);
        Optional<UnitMove> transport = cheapestTransport(context, origin);
        if (distanceHere == Integer.MAX_VALUE || transport.isEmpty()) return Optional.empty();
        List<UnitMove> ground = availableGroundForces(game, seat, origin, context.defense(), List.of(transport.get()));
        if (ground.isEmpty()) return Optional.empty();
        int capacity = BoardView.capacity(seat, transport.get().type());
        List<UnitMove> group = new ArrayList<>(List.of(transport.get()));
        group.addAll(take(ground, Math.min(APPROACH_GROUND_FORCES, capacity)));
        TacticalPlan best = null;
        for (Tile destination : game.getTileMap().values()) {
            if (destination == origin || !activatable.test(destination)) continue;
            int distanceThere = context.iconDistance(destination);
            if (distanceThere >= distanceHere || context.iconTiles().contains(destination)) continue;
            if (!canStageAt(context, origin, destination, group.getFirst().type())) continue;
            double closed = (double) (distanceHere - distanceThere) / distanceHere;
            double gain = ICON_MARCH_SHARE * ObjectiveValue.VICTORY_POINT_VALUE * closed;
            Optional<TacticalPlan> plan = stagingPlan(context, origin, destination, group, gain);
            if (plan.isPresent() && (best == null || plan.get().score() > best.score())) best = plan.get();
        }
        return Optional.ofNullable(best);
    }

    private static Optional<TacticalPlan> approachFrom(Context context, Predicate<Tile> activatable, Tile origin) {
        Game game = context.game;
        Player seat = context.seat;
        OptionalInt hopsHere = context.race().hopsToMecatol(seat, origin);
        Optional<UnitMove> transport = cheapestTransport(context, origin);
        if (hopsHere.isEmpty() || transport.isEmpty()) return Optional.empty();
        List<UnitMove> ground = availableGroundForces(game, seat, origin, context.defense(), List.of(transport.get()));
        if (ground.isEmpty()) return Optional.empty();
        int capacity = BoardView.capacity(seat, transport.get().type());
        List<UnitMove> group = new ArrayList<>(List.of(transport.get()));
        group.addAll(take(ground, Math.min(APPROACH_GROUND_FORCES, capacity)));
        TacticalPlan best = null;
        for (Tile destination : game.getTileMap().values()) {
            Optional<TacticalPlan> plan =
                    approachTo(context, activatable, origin, destination, group, hopsHere.getAsInt());
            if (plan.isPresent() && (best == null || plan.get().score() > best.score())) best = plan.get();
        }
        return Optional.ofNullable(best);
    }

    private static Optional<TacticalPlan> approachTo(
            Context context,
            Predicate<Tile> activatable,
            Tile origin,
            Tile destination,
            List<UnitMove> group,
            int hopsHere) {
        Game game = context.game;
        Player seat = context.seat;
        if (destination == origin || destination.isMecatol(game) || !activatable.test(destination)) {
            return Optional.empty();
        }
        if (!canStageAt(context, origin, destination, group.getFirst().type())) return Optional.empty();
        OptionalInt hopsThere = context.race().hopsToMecatol(seat, destination);
        if (hopsThere.isEmpty() || hopsThere.getAsInt() >= hopsHere) return Optional.empty();
        double gain = context.approachGain(destination);
        if (gain <= 0) return Optional.empty();
        return stagingPlan(context, origin, destination, group, APPROACH_SHARE * gain);
    }

    private static boolean canStageAt(Context context, Tile origin, Tile destination, UnitType transport) {
        return MovementGraph.canEnter(context.game, context.seat, destination)
                && !BoardView.hasEnemyShips(context.game, context.seat, destination)
                && context.reachesWithGravityDrive(origin, destination, transport);
    }

    private static Optional<TacticalPlan> stagingPlan(
            Context context, Tile origin, Tile destination, List<UnitMove> group, double gain) {
        Game game = context.game;
        Player seat = context.seat;
        UnitMove transport = group.getFirst();
        Optional<List<UnitMove>> legal = legalMoves(context, origin, destination, group);
        if (legal.isEmpty()) return Optional.empty();
        Defenders after = context.defense().defendersAt(destination).plusShips(transport.type(), transport.count(), 0);
        double stake = STAGE_SHIP_STAKE * fleetCost(seat, List.of(transport));
        double counter = context.defense().counterstrike(destination, after, List.of(), stake, 0, seat);
        double score = gain
                - counter
                - context.defense().leaveCost(origin, legal.get())
                + context.departureLoss(destination, legal.get())
                - DISTANCE_COST * context.distance(origin, destination, transport.type());
        TacticalPlan plan = new TacticalPlan(Kind.STAGE, destination.getPosition(), legal.get(), Map.of(), score);
        return afterCannon(
                context,
                destination,
                plan.withScore(PlayAreaNotes.adjustedScore(game, seat, destination, plan)
                        + SetupHint.bonusFor(context.hints, plan)));
    }

    private static Optional<UnitMove> cheapestTransport(Context context, Tile origin) {
        Player seat = context.seat;
        UnitType cheapest = null;
        for (UnitType type : TRANSPORT_PREFERENCE) {
            if (BoardView.capacity(seat, type) <= 0 || BoardView.undamaged(BoardView.space(origin), seat, type) == 0) {
                continue;
            }
            if (cheapest == null || cost(seat, type) < cost(seat, cheapest)) cheapest = type;
        }
        if (cheapest == null) return Optional.empty();
        return Optional.of(new UnitMove(origin.getPosition(), BoardView.SPACE, cheapest, 1));
    }

    private record RankedTarget(Tile tile, double worth) {}

    private static List<Tile> stageTargets(Context context) {
        Game game = context.game;
        Player seat = context.seat;
        List<RankedTarget> ranked = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            Player opponent = singleOpponent(game, seat, tile);
            if (opponent == null || !worthStagingFor(context, tile, opponent)) continue;
            double feasibility = stageFeasibility(context, tile, opponent);
            if (feasibility < STAGE_MIN_FEASIBILITY || context.attackPlan(tile).isPresent()) continue;
            ranked.add(new RankedTarget(tile, stagePrize(context, tile, opponent) * feasibility));
        }
        ranked.sort(Comparator.comparingDouble(RankedTarget::worth).reversed());
        return ranked.stream().limit(STAGE_TARGETS).map(RankedTarget::tile).toList();
    }

    static boolean worthStagingFor(Context context, Tile tile, Player opponent) {
        if (opponent.isNeutral()) return tile.getPlanetUnitHolders().stream().anyMatch(FracturePrize::isFracturePlanet);
        if (FracturePrize.holdsStyx(tile, opponent)) return true;
        if (context.defense().standings().denialWeight(opponent) >= STAGE_MIN_DENIAL) return true;
        if (tile == opponent.getHomeSystemTile() || holdsMecatol(context.game, tile)) return true;
        if (IconStake.marks(context.game, context.seat, tile)) return true;
        return tile.getPlanetUnitHolders().stream()
                .filter(planet -> BoardView.controller(context.game, planet.getName()) == opponent)
                .anyMatch(planet -> context.offense().deniesObjectives(opponent, planet));
    }

    static double stagePrize(Context context, Tile tile, Player opponent) {
        Game game = context.game;
        Player seat = context.seat;
        double prize = tile.getPlanetUnitHolders().stream()
                .filter(planet -> !seat.getPlanets().contains(planet.getName()))
                .mapToDouble(planet -> BoardView.planetValue(planet) + FracturePrize.gain(game, seat, planet))
                .sum();
        if (tile == opponent.getHomeSystemTile()) {
            prize += ObjectiveValue.VICTORY_POINT_VALUE
                    * context.defense().standings().denialWeight(opponent);
        }
        if (IconStake.marks(game, seat, tile)) prize += ObjectiveValue.VICTORY_POINT_VALUE;
        prize += SeatStrike.prize(game, seat, tile);
        boolean opponentHoldsMecatol = tile.getPlanetUnitHolders().stream()
                .anyMatch(planet -> game.mecatols().contains(planet.getName())
                        && BoardView.controller(game, planet.getName()) == opponent);
        if (opponentHoldsMecatol) {
            prize += MecatolStake.ownHoldValue(game, seat)
                    + context.defense().standings().denialWeight(opponent) * MecatolStake.holdValue(game, opponent);
        }
        return prize;
    }

    static double stageFeasibility(Context context, Tile target, Player opponent) {
        Player seat = context.seat;
        double ours = 0;
        for (Tile origin : context.origins()) {
            for (UnitMove ships : context.stageGroup(origin)) {
                int move = BoardView.moveValueWithBoosts(seat, ships.type());
                if (context.reach(origin, (stageLead(opponent) + 1) * move).containsKey(target.getPosition())) {
                    ours += ships.count() * cost(seat, ships.type());
                }
            }
        }
        double theirs = Math.max(
                        1,
                        context.defense()
                                .force(opponent, target, IncomingForces.Horizon.NEXT_ROUND)
                                .fleetCost())
                + pdsOn(target, opponent);
        return Math.min(1, ours / theirs);
    }

    private static int pdsOn(Tile target, Player opponent) {
        return target.getPlanetUnitHolders().stream()
                .mapToInt(planet -> BoardView.count(planet, opponent, UnitType.Pds))
                .sum();
    }

    private static boolean holdsMecatol(Game game, Tile tile) {
        return tile.getPlanetUnitHolders().stream()
                .anyMatch(planet -> game.mecatols().contains(planet.getName()));
    }

    record Contribution(Tile origin, List<UnitMove> ships, List<UnitMove> cargo, List<UnitMove> moves) {

        double shipCost(Player seat) {
            return fleetCost(seat, ships);
        }
    }

    record Gathering(Tile staging, List<Contribution> contributions, double cost, double counter) {

        List<UnitMove> ships() {
            return contributions.stream()
                    .flatMap(found -> found.ships().stream())
                    .toList();
        }

        List<UnitMove> cargo() {
            return contributions.stream()
                    .flatMap(found -> found.cargo().stream())
                    .toList();
        }

        Gathering withCounter(double newCounter) {
            return new Gathering(staging, contributions, cost, newCounter);
        }
    }

    // TODO: staging toward a player leaves fighters behind, and staged ships are assumed undamaged
    private static List<TacticalPlan> stageAgainst(
            Context context, Predicate<Tile> activatable, Tile target, Player opponent) {
        Game game = context.game;
        Player seat = context.seat;
        List<Gathering> gatherings = new ArrayList<>();
        for (Tile staging : game.getTileMap().values()) {
            if (staging == target || staging == seat.getHomeSystemTile() || !activatable.test(staging)) continue;
            if (!MovementGraph.canEnter(game, seat, staging)) continue;
            if (BoardView.hasEnemyShips(game, seat, staging)) continue;
            gather(context, target, staging, stageLead(opponent)).ifPresent(gatherings::add);
        }
        gatherings.sort(Comparator.comparingDouble(Gathering::cost).reversed());
        List<Gathering> candidates = new ArrayList<>();
        for (Gathering gathering : gatherings.subList(0, Math.min(STAGING_CANDIDATES, gatherings.size()))) {
            candidates.add(gathering.withCounter(counterAt(context, gathering, opponent)));
        }
        candidates.sort(Comparator.comparingDouble(Gathering::counter));
        List<TacticalPlan> plans = new ArrayList<>();
        for (Gathering gathering : candidates.subList(0, Math.min(STAGING_TILES_PER_TARGET, candidates.size()))) {
            stagePlan(context, target, opponent, gathering).ifPresent(plans::add);
        }
        return plans;
    }

    static int stageLead(Player opponent) {
        return opponent.isNeutral() ? FRACTURE_STAGE_LEAD : 1;
    }

    private static double stageShare(Context context, Tile target, Player opponent, Gathering gathering) {
        if (!opponent.isNeutral()) return STAGE_SHARE;
        int steps = gathering.ships().stream()
                .filter(move -> move.type() != UnitType.Fighter)
                .mapToInt(move -> stepsToTarget(context, gathering.staging(), target, move.type(), FRACTURE_STAGE_LEAD))
                .max()
                .orElse(FRACTURE_STAGE_LEAD);
        return routeShare(context) * FRACTURE_STAGE_SHARE * Math.pow(FRACTURE_STEP_SHARE, steps - 1);
    }

    private static double routeShare(Context context) {
        return FractureCampaign.routeShare(context.game, context.territory());
    }

    private static List<TacticalPlan> flankSpeedPlans(Context context, Predicate<Tile> activatable) {
        if (MoveBoost.flankSpeedCard(context.seat).isEmpty() || MoveBoost.active(context.seat)) return List.of();
        List<TacticalPlan> boosted = MoveBoost.withOneMore(context.seat, () -> {
            Context faster = new Context(context.game, context.seat, context.freed, context.hints);
            List<TacticalPlan> plans = new ArrayList<>();
            context.game.getTileMap().values().stream()
                    .filter(activatable)
                    .forEach(tile -> bestFor(faster, tile).ifPresent(plans::add));
            stage(faster, activatable).stream()
                    .filter(plan -> isFractureTile(context.game, plan.target()))
                    .forEach(plans::add);
            return plans;
        });
        return boosted.stream()
                .filter(plan -> needsFlankSpeed(context.game, context.seat, plan))
                .map(plan -> plan.withScore(plan.score() - FLANK_SPEED_COST))
                .toList();
    }

    private static boolean isFractureTile(Game game, String position) {
        Tile tile = game.getTileByPosition(position);
        return tile != null && tile.isFracture();
    }

    public static boolean needsFlankSpeed(Game game, Player seat, TacticalPlan plan) {
        return departingShips(plan.moves()).stream()
                .filter(move -> move.type() != UnitType.Fighter)
                .anyMatch(move -> !MovementGraph.reach(
                                game, seat, move.origin(), BoardView.moveValueWithGravityDrive(seat, move.type()))
                        .containsKey(plan.target()));
    }

    private static double marchForfeit(Context context, TacticalPlan plan) {
        try (Thinking.Span ignored = Thinking.enter("tactical.for.marchForfeit")) {
            return marchForfeitOf(context, plan);
        }
    }

    private static double marchForfeitOf(Context context, TacticalPlan plan) {
        Optional<Tile> styx = styxToTake(context);
        if (styx.isEmpty() || plan.target().equals(styx.get().getPosition())) return 0;
        Tile target = context.game.getTileByPosition(plan.target());
        double forfeit = 0;
        for (String position : departingShips(plan.moves()).stream()
                .map(UnitMove::origin)
                .distinct()
                .toList()) {
            Tile origin = context.game.getTileByPosition(position);
            if (origin == null || origin == styx.get()) continue;
            if (!origin.isFracture() && !context.marchesFrom(position)) continue;
            if (plan.kind() == Kind.STAGE && target != null && closerTo(context, styx.get(), target, origin)) continue;
            double stationed = fleetCost(context.seat, context.stageGroup(origin));
            if (stationed <= 0) continue;
            double leaving = fleetCost(
                    context.seat,
                    departingShips(plan.moves()).stream()
                            .filter(move -> move.origin().equals(position))
                            .toList());
            forfeit += marchValue(context, origin, styx.get()) * Math.min(1, leaving / stationed);
        }
        return forfeit;
    }

    public static boolean isMarchStep(Game game, Player seat, TacticalPlan plan) {
        if (plan.kind() != Kind.STAGE) return false;
        Context context = new Context(game, seat);
        Optional<Tile> styx = styxToTake(context);
        Tile target = game.getTileByPosition(plan.target());
        if (styx.isEmpty() || target == null) return false;
        if (!context.reach(target, FRACTURE_STAGE_LEAD).containsKey(styx.get().getPosition())) return false;
        List<String> origins = departingShips(plan.moves()).stream()
                .map(UnitMove::origin)
                .distinct()
                .toList();
        return !origins.isEmpty()
                && origins.stream()
                        .map(game::getTileByPosition)
                        .allMatch(origin -> origin != null && closerTo(context, styx.get(), target, origin));
    }

    private static Optional<Tile> styxToTake(Context context) {
        return FracturePrize.tiles(context.game).stream()
                .filter(tile -> tile.getPlanetUnitHolders().stream()
                        .anyMatch(planet -> FracturePrize.isStyx(planet)
                                && !context.seat.getPlanets().contains(planet.getName())))
                .findFirst();
    }

    private static boolean closerTo(Context context, Tile goal, Tile next, Tile origin) {
        int far = FRACTURE_STAGE_LEAD + 1;
        return context.reach(next, far).getOrDefault(goal.getPosition(), far + 1)
                < context.reach(origin, far).getOrDefault(goal.getPosition(), far + 1);
    }

    private static double marchValue(Context context, Tile origin, Tile styx) {
        return context.marchValues.computeIfAbsent(origin.getPosition(), position -> {
            Player opponent = singleOpponent(context.game, context.seat, styx);
            if (opponent == null) return 0.0;
            UnitHolder space = BoardView.space(origin);
            List<UnitMove> cargo = BoardView.GROUND_FORCES.stream()
                    .filter(type -> BoardView.count(space, context.seat, type) > 0)
                    .map(type ->
                            new UnitMove(position, BoardView.SPACE, type, BoardView.count(space, context.seat, type)))
                    .toList();
            double future = futureScore(context, styx, opponent, origin, List.of(), cargo);
            if (future < attackMinScore(opponent)) return 0.0;
            int steps = context.stageGroup(origin).stream()
                    .mapToInt(move -> stepsToTarget(context, origin, styx, move.type(), FRACTURE_STAGE_LEAD))
                    .max()
                    .orElse(FRACTURE_STAGE_LEAD);
            return routeShare(context) * FRACTURE_STAGE_SHARE * Math.pow(FRACTURE_STEP_SHARE, steps - 1) * future;
        });
    }

    private static int stepsToTarget(Context context, Tile from, Tile target, UnitType type, int maxSteps) {
        int move = BoardView.moveValue(context.seat, type);
        for (int steps = 1; steps <= maxSteps; steps++) {
            if (context.reach(from, steps * move).containsKey(target.getPosition())) return steps;
        }
        return maxSteps + 1;
    }

    static Optional<Gathering> gather(Context context, Tile target, Tile staging, int lead) {
        Player seat = context.seat;
        List<Contribution> contributions = new ArrayList<>();
        for (Tile origin : context.origins()) {
            if (origin == staging || origin == target) continue;
            contribution(context, target, staging, origin, lead).ifPresent(contributions::add);
        }
        contributions.sort(Comparator.comparingDouble((Contribution found) -> found.shipCost(seat))
                .reversed());
        List<Contribution> kept =
                new ArrayList<>(contributions.subList(0, Math.min(MAX_ORIGINS, contributions.size())));
        while (!kept.isEmpty() && exceedsFleetSupply(seat, staging, kept)) kept.removeLast();
        if (kept.isEmpty()) return Optional.empty();
        double cost = kept.stream().mapToDouble(found -> found.shipCost(seat)).sum();
        return Optional.of(new Gathering(staging, kept, cost, 0));
    }

    private static boolean exceedsFleetSupply(Player seat, Tile staging, List<Contribution> contributions) {
        int moving = contributions.stream()
                .mapToInt(found -> movedNonFighterShips(found.ships()))
                .sum();
        return BoardView.nonFighterShips(BoardView.space(staging), seat) + moving > FleetLimit.ships(seat);
    }

    private static Optional<Contribution> contribution(
            Context context, Tile target, Tile staging, Tile origin, int lead) {
        Game game = context.game;
        Player seat = context.seat;
        List<UnitMove> slow = context.stageGroup(origin).stream()
                .filter(move -> context.reaches(origin, staging, move.type()))
                .filter(move -> context.reach(staging, lead * BoardView.moveValue(seat, move.type()))
                        .containsKey(target.getPosition()))
                .filter(move -> !context.reaches(origin, target, move.type()))
                .filter(move -> lead == 1
                        || stepsToTarget(context, staging, target, move.type(), lead)
                                < stepsToTarget(context, origin, target, move.type(), lead + 1))
                .toList();
        if (slow.isEmpty()) return Optional.empty();
        List<UnitMove> ships = new ArrayList<>(slow);
        if (lead > 1) ships.addAll(escorts(context, origin, staging, target, slow, lead));
        boolean planetsToTake = !enemyPlanets(game, seat, target).isEmpty()
                || !freePlanetsIn(context, target).isEmpty()
                || entersTheFractureToStrike(context, target);
        int capacity = capacityOf(seat, ships);
        int groundRoom = lead > 1 ? Math.min(capacity, FractureCampaign.INFANTRY) : capacity;
        List<UnitMove> cargo = planetsToTake
                ? take(availableGroundForces(game, seat, origin, context.defense(), ships), groundRoom)
                : List.of();
        List<UnitMove> group = new ArrayList<>(ships);
        if (lead > 1) group.addAll(fightersAboard(context, origin, capacity - count(cargo)));
        List<UnitMove> departing = new ArrayList<>(group);
        departing.addAll(cargo);
        return legalMoves(context, origin, staging, departing)
                .map(moves -> new Contribution(origin, group, cargo, moves));
    }

    private static List<UnitMove> escorts(
            Context context, Tile origin, Tile staging, Tile target, List<UnitMove> slow, int lead) {
        return context.stageGroup(origin).stream()
                .filter(move -> !slow.contains(move))
                .filter(move -> move.type() != UnitType.Fighter)
                .filter(move -> context.reaches(origin, staging, move.type()))
                .filter(move -> context.reach(
                                staging, FRACTURE_STAGE_LEAD * BoardView.moveValue(context.seat, move.type()))
                        .containsKey(target.getPosition()))
                .toList();
    }

    private static boolean entersTheFractureToStrike(Context context, Tile target) {
        return target.isFracture()
                && target.getPlanetUnitHolders().isEmpty()
                && FracturePrize.interest(context.game, context.seat) > 0;
    }

    private static List<UnitMove> fightersAboard(Context context, Tile origin, int room) {
        int fighters = Math.min(room, BoardView.count(BoardView.space(origin), context.seat, UnitType.Fighter));
        if (fighters <= 0) return List.of();
        return List.of(new UnitMove(origin.getPosition(), BoardView.SPACE, UnitType.Fighter, fighters));
    }

    private static int count(List<UnitMove> moves) {
        return moves.stream().mapToInt(UnitMove::count).sum();
    }

    static double counterAt(Context context, Gathering gathering, Player opponent) {
        Defenders after = context.defense().defendersAt(gathering.staging());
        for (UnitMove move : gathering.ships()) after = after.plusShips(move.type(), move.count(), 0);
        return context.defense()
                .counterstrike(gathering.staging(), after, List.of(), STAGE_SHIP_STAKE * gathering.cost(), 0, opponent);
    }

    static Optional<TacticalPlan> stagePlan(Context context, Tile target, Player opponent, Gathering gathering) {
        Player seat = context.seat;
        Tile staging = gathering.staging();
        double future = futureScore(context, target, opponent, staging, gathering.ships(), gathering.cargo());
        if (future < attackMinScore(opponent)) return Optional.empty();
        boolean diplomacy = context.defense().standings().holdsUnplayedDiplomacy(opponent)
                && target.getPlanetUnitHolders().stream()
                        .anyMatch(planet -> BoardView.controller(context.game, planet.getName()) == opponent
                                && !context.game.mecatols().contains(planet.getName()));
        List<UnitMove> moves = merge(gathering.contributions().stream()
                .flatMap(found -> found.moves().stream())
                .toList());
        int farthest = gathering.contributions().stream()
                .flatMap(found -> found.ships().stream()
                        .filter(move -> move.type() != UnitType.Fighter)
                        .map(move -> context.distance(found.origin(), staging, move.type())))
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
        double leaving = gathering.contributions().stream()
                .mapToDouble(found -> context.defense().leaveCost(found.origin(), found.moves()))
                .sum();
        double score = stageShare(context, target, opponent, gathering) * future * (diplomacy ? DIPLOMACY_DISCOUNT : 1)
                - gathering.counter()
                - leaving
                + context.departureLoss(staging, moves)
                - DISTANCE_COST * farthest
                - GROUP_MOVE_COST * (movedNonFighterShips(moves) - 1)
                - emptyHaulerPenalty(seat, gathering);
        TacticalPlan plan = new TacticalPlan(Kind.STAGE, staging.getPosition(), moves, Map.of(), score);
        return afterCannon(
                context,
                staging,
                plan.withScore(PlayAreaNotes.adjustedScore(context.game, seat, staging, plan)
                        + SetupHint.bonusFor(context.hints, plan)));
    }

    private static double emptyHaulerPenalty(Player seat, Gathering gathering) {
        return gathering.contributions().stream()
                .mapToDouble(found -> CapacityUse.emptyHaulerPenalty(seat, found.moves()))
                .sum();
    }

    static double futureScore(
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
                    target,
                    opponent,
                    List.of(),
                    List.of(),
                    ships,
                    Map.of(),
                    ground,
                    extraShips,
                    extraGround,
                    rules,
                    Map.of());
            StrikeEstimate estimate = StrikeEvaluator.estimate(game, seat, strike);
            if (estimate.spaceWin() < rules.landMinWin()) return 0;
            StrikeValue value = context.offense()
                    .evaluate(strike, estimate, context.seatTerms(target, estimate), true, ATTACK_MIN_SCORE);
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

    private static boolean isMecatol(Game game, Planet planet) {
        return game.mecatols().contains(planet.getName());
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

    static double cost(Player seat, UnitType type) {
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

    static double[] secretBonuses(Game game, Player seat, Tile tile, Strike strike, StrikeEstimate estimate) {
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
        double wipe = space ? CannonWipeRules.chanceToWipe(game, seat, tile, opponent, ships) : 0;
        double[] fought = allocatedBonuses(game, seat, tile, opponent, ships, space, ground, true, 0);
        if (wipe <= 0) return fought;
        double[] wiped = allocatedBonuses(game, seat, tile, opponent, ships, space, ground, false, wipe);
        return wiped[0] + wiped[1] > fought[0] + fought[1] ? wiped : fought;
    }

    private static double[] allocatedBonuses(
            Game game,
            Player seat,
            Tile tile,
            Player opponent,
            Map<UnitType, Integer> ships,
            boolean space,
            boolean ground,
            boolean withDarkenTheSkies,
            double cannonWipe) {
        Set<String> held = seat.getSecretsUnscored().keySet();
        List<String> generic = new ArrayList<>();
        if (held.contains("sar")
                && !opponent.isNeutral()
                && opponent.getTotalVictoryPoints() == game.getHighestScore()) {
            generic.add("sar");
        }
        if (held.contains(BRAVE_THE_VOID) && tile.isAnomaly(game, seat)) generic.add(BRAVE_THE_VOID);
        if (withDarkenTheSkies
                && held.contains(DARKEN_THE_SKIES)
                && tile.isHomeSystem(game)
                && tile != seat.getHomeSystemTile()) {
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
            if (held.contains(DESTROY_THEIR_GREATEST_SHIP)
                    && !opponent.isNeutral()
                    && BoardView.count(holder, opponent, UnitType.Flagship)
                                    + BoardView.count(holder, opponent, UnitType.Warsun)
                            > 0) {
                spaceOnly.add(DESTROY_THEIR_GREATEST_SHIP);
            }
        }
        double bestSpaceOnly = Math.max(
                secretPoints(game, seat, opponent, spaceOnly).stream()
                        .findFirst()
                        .orElse(0.0),
                Math.max(space ? barragePoints(game, seat, tile, opponent, ships) : 0, cannonWipe));
        List<Double> genericPoints = secretPoints(game, seat, opponent, generic);
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

    private static List<Double> secretPoints(Game game, Player seat, Player opponent, List<String> secrets) {
        return secrets.stream()
                .map(secret -> secretShare(game, seat, opponent, secret))
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    private static double secretShare(Game game, Player seat, Player opponent, String secret) {
        if (opponent.isNeutral() && NEUTRAL_TARGET_SECRETS.contains(secret)) return 1.0;
        return secretShare(game, seat, secret);
    }

    private static double secretShare(Game game, Player seat, String secret) {
        if (WHOLE_POINT_SECRETS.contains(secret)) return 1.0;
        return ActionSecretTiming.pursueNow(game, seat, secret) ? 1.0 : PATIENT_SHARE;
    }

    private static double barragePoints(
            Game game, Player seat, Tile tile, Player opponent, Map<UnitType, Integer> arriving) {
        if (!PrecisionRules.held(seat)) return 0;
        Map<UnitType, Integer> present = new EnumMap<>(BoardView.ships(BoardView.space(tile), seat));
        arriving.forEach((type, count) -> present.merge(type, count, Integer::sum));
        double chance = PrecisionRules.chanceToClear(
                seat, present, BoardView.count(BoardView.space(tile), opponent, UnitType.Fighter));
        return secretShare(game, seat, opponent, PrecisionRules.FIGHT_WITH_PRECISION) * chance;
    }

    private static Optional<TacticalPlan> produce(Context context, Tile tile) {
        Player seat = context.seat;
        if (!hasDock(seat, tile)) return Optional.empty();
        ProductionPlanner.BuildPlan build = ProductionPlanner.freeBuild(context.game, seat, tile, context.defense())
                .orElseGet(() -> context.buildPlan(tile));
        if (build.isEmpty()) return Optional.empty();
        double objectiveGain = context.builtGain(tile, build);
        boolean forScoring = build.scoring() || objectiveGain > 0;
        Optional<GroundShift> shift = groundShift(context, tile);
        boolean worthBuilding = build.units() >= ProductionPlanner.MIN_UNITS
                || build.totalCost() >= ProductionPlanner.MIN_BUILD_RESOURCES
                || forScoring
                || build.defense();
        if (!worthBuilding && shift.isEmpty()) return Optional.empty();
        TacticalPlan plan =
                new TacticalPlan(Kind.PRODUCE, tile.getPosition(), List.of(), Map.of(), build.value() + objectiveGain);
        return Optional.of(shift.map(found -> found.before(plan)).orElse(plan));
    }

    private record GroundShift(List<UnitMove> moves, String planet, int wanted, double value) {
        TacticalPlan before(TacticalPlan produce) {
            return new TacticalPlan(
                    produce.kind(), produce.target(), moves, Map.of(planet, wanted), produce.score() + value);
        }
    }

    private static Optional<GroundShift> groundShift(Context context, Tile tile) {
        if (BoardView.hasEnemyUnits(context.game, context.seat, tile)) return Optional.empty();
        return Stream.of(mechShift(context, tile), fringeShift(context, tile))
                .flatMap(Optional::stream)
                .max(Comparator.comparingDouble(GroundShift::value));
    }

    private static Optional<GroundShift> mechShift(Context context, Tile tile) {
        Player seat = context.seat;
        if (!mechanizes(seat)) return Optional.empty();
        Optional<Planet> bare = ownedPlanets(seat, tile)
                .filter(planet -> context.campaign().bareTargets().contains(planet))
                .findFirst();
        Optional<Planet> spare = ownedPlanets(seat, tile)
                .filter(planet -> BoardView.count(planet, seat, UnitType.Mech) >= 2)
                .findFirst();
        if (bare.isEmpty() || spare.isEmpty()) return Optional.empty();
        List<UnitMove> mech =
                List.of(new UnitMove(tile.getPosition(), spare.get().getName(), UnitType.Mech, 1));
        return shiftOnto(context, tile, bare.get(), mech, 1);
    }

    private static Optional<GroundShift> fringeShift(Context context, Tile tile) {
        Player seat = context.seat;
        if (!context.campaign().holdsFringe()) return Optional.empty();
        Optional<Planet> fringe = fringeIn(context, tile);
        if (fringe.isEmpty()) return Optional.empty();
        int missing = context.campaign().fringeDemand();
        List<UnitMove> spare = spareFromDocks(context, tile);
        if (missing <= 0 || spare.isEmpty()) return Optional.empty();
        return shiftOnto(context, tile, fringe.get(), spare, missing);
    }

    private static List<UnitMove> spareFromDocks(Context context, Tile tile) {
        Player seat = context.seat;
        List<UnitMove> available = availableGroundForces(context.game, seat, tile, context.defense(), List.of());
        boolean guarded = context.defense().isGuarded(tile);
        List<UnitMove> spare = new ArrayList<>();
        for (Planet dock : ownedPlanets(seat, tile)
                .filter(planet -> BoardView.count(planet, seat, UnitType.Spacedock) > 0)
                .toList()) {
            List<UnitMove> here = available.stream()
                    .filter(move -> move.holder().equals(dock.getName()))
                    .toList();
            int keep = guarded && unitCount(here) >= BoardView.groundForces(dock, seat) ? 1 : 0;
            spare.addAll(take(here, unitCount(here) - keep));
        }
        return spare;
    }

    private static Optional<GroundShift> shiftOnto(
            Context context, Tile tile, Planet destination, List<UnitMove> cargo, int most) {
        Player seat = context.seat;
        int room = capacityOf(seat, shipsAt(seat, tile)) - BoardView.groundForces(BoardView.space(tile), seat);
        List<UnitMove> moves = new ArrayList<>();
        double cost = 0;
        if (room <= 0) {
            Optional<Map.Entry<List<UnitMove>, Double>> hauler = nearestHauler(context, tile);
            if (hauler.isEmpty()) return Optional.empty();
            moves.addAll(hauler.get().getKey());
            cost = hauler.get().getValue();
            room = capacityOf(seat, hauler.get().getKey());
        }
        List<UnitMove> lifted = take(cargo, Math.min(most, room));
        if (lifted.isEmpty()) return Optional.empty();
        moves.addAll(lifted);
        double gain = context.objectiveGain(tile, moves, List.of(destination.getName()));
        if (gain - cost <= 0) return Optional.empty();
        return Optional.of(new GroundShift(
                moves,
                destination.getName(),
                BoardView.groundForces(destination, seat) + unitCount(lifted),
                gain - cost));
    }

    private static Optional<Map.Entry<List<UnitMove>, Double>> nearestHauler(Context context, Tile tile) {
        Map.Entry<List<UnitMove>, Double> best = null;
        for (Tile origin : context.origins()) {
            if (origin == tile) continue;
            List<UnitMove> haulers = transports(context, origin, tile, 1).stream()
                    .filter(move -> CapacityUse.hauler(context.seat, move.type()))
                    .toList();
            if (haulers.isEmpty()) continue;
            Optional<List<UnitMove>> legal = legalMoves(context, origin, tile, haulers);
            if (legal.isEmpty()) continue;
            double cost = DISTANCE_COST
                            * context.distance(origin, tile, haulers.getFirst().type())
                    + context.defense().leaveCost(origin, legal.get());
            if (best == null || cost < best.getValue()) best = Map.entry(legal.get(), cost);
        }
        return Optional.ofNullable(best);
    }

    static double planetTempo(Game game) {
        return PLANET_TEMPO_VALUE * earlyGameWeight(game);
    }

    private static double zoneBonus(Game game, Territory.Zone zone) {
        if (zone == Territory.Zone.FOREIGN) return -FOREIGN_PLANET_PENALTY * earlyGameWeight(game);
        return ownAreaBonus(zone);
    }

    public static double claimedPlanetBonus(Game game, Territory territory, Tile tile) {
        return planetTempo(game) + zoneBonus(game, territory.zone(tile));
    }

    static double earlyGameWeight(Game game) {
        int roundsPastEarlyGame = Math.max(0, game.getRound() - LAST_EARLY_ROUND);
        return Math.max(0, 1 - EARLY_GAME_FADE_PER_ROUND * roundsPastEarlyGame);
    }

    private static Optional<TacticalPlan> advance(Context context, Tile tile) {
        Game game = context.game;
        Player seat = context.seat;
        if (tile.isHomeSystem(game) || BoardView.hasEnemyUnits(game, seat, tile)) return Optional.empty();
        TacticalPlan best = null;
        for (Tile origin : context.origins()) {
            if (origin == tile) continue;
            for (UnitType type : TRANSPORT_PREFERENCE) {
                if (BoardView.undamaged(BoardView.space(origin), seat, type) == 0
                        || BoardView.capacity(seat, type) <= 0
                        || !context.reaches(origin, tile, type)) {
                    continue;
                }
                double gain = context.lockShare()
                        * context.goals().forwardGain(origin, tile, BoardView.moveValue(seat, type), true);
                if (gain <= 0) continue;
                List<UnitMove> transport = List.of(new UnitMove(origin.getPosition(), BoardView.SPACE, type, 1));
                List<UnitMove> cargo = take(
                        availableGroundForces(game, seat, origin, context.defense(), transport),
                        Math.min(BoardView.capacity(seat, type), context.forwardNeed(tile, type)));
                if (cargo.isEmpty()) continue;
                Optional<List<UnitMove>> legal = legalMoves(context, origin, tile, concat(transport, cargo));
                if (legal.isEmpty()) continue;
                double base = ADVANCE_SHARE * gain - DISTANCE_COST * context.distance(origin, tile, type);
                List<UnitMove> moves = withEscorts(context, tile, base, legal.get());
                double score = advanceScore(context, tile, base, moves);
                Optional<TacticalPlan> priced = afterCannon(
                        context, tile, new TacticalPlan(Kind.POSITION, tile.getPosition(), moves, Map.of(), score));
                if (priced.isPresent() && (best == null || priced.get().score() > best.score())) best = priced.get();
            }
        }
        return Optional.ofNullable(best);
    }

    private static Optional<TacticalPlan> pickup(Context context, Tile tile) {
        if (BoardView.hasEnemyUnits(context.game, context.seat, tile)) return Optional.empty();
        int unserved = unservedCargo(context, tile);
        if (unserved <= 0) return Optional.empty();
        TacticalPlan best = null;
        for (Tile origin : context.origins()) {
            if (origin == tile) continue;
            Optional<TacticalPlan> plan = pickupFrom(context, tile, origin, unserved);
            if (plan.isPresent() && (best == null || plan.get().score() > best.score())) best = plan.get();
        }
        return Optional.ofNullable(best);
    }

    private static int unservedCargo(Context context, Tile tile) {
        Player seat = context.seat;
        UnitHolder space = BoardView.space(tile);
        boolean dock = hasDock(seat, tile);
        int ground = dock
                ? unitCount(availableGroundForces(context.game, seat, tile, context.defense(), List.of()))
                : BoardView.groundForces(space, seat);
        int fighters = Math.max(
                0, BoardView.count(space, seat, UnitType.Fighter) - (dock ? BoardView.DOCK_FIGHTER_ALLOWANCE : 0));
        return ground + fighters - capacityOf(seat, shipsAt(seat, tile));
    }

    private static List<UnitMove> shipsAt(Player seat, Tile tile) {
        UnitHolder space = BoardView.space(tile);
        List<UnitMove> ships = new ArrayList<>();
        for (UnitType type : BoardView.MOVING_SHIPS) {
            addMove(ships, tile.getPosition(), BoardView.SPACE, type, BoardView.count(space, seat, type));
        }
        return ships;
    }

    private static Optional<TacticalPlan> pickupFrom(Context context, Tile tile, Tile origin, int unserved) {
        Player seat = context.seat;
        if (movableGroundForces(context.game, seat, origin, context.defense()) > 0) return Optional.empty();
        List<UnitMove> haulers = transports(context, origin, tile, unserved).stream()
                .filter(move -> CapacityUse.hauler(seat, move.type()))
                .toList();
        if (haulers.isEmpty()) return Optional.empty();
        Optional<List<UnitMove>> legal = legalMoves(context, origin, tile, haulers);
        if (legal.isEmpty()) return Optional.empty();
        int pickedUp = Math.min(unserved, capacityOf(seat, haulers));
        double score = FORWARD_GROUND_FORCE_VALUE * pickedUp
                + secretCargoCredit(context, tile, pickedUp)
                - DISTANCE_COST
                        * context.distance(origin, tile, haulers.getFirst().type())
                - GROUP_MOVE_COST * (movedNonFighterShips(legal.get()) - 1)
                - context.defense().leaveCost(origin, legal.get())
                + context.departureLoss(tile, legal.get())
                - context.exposure(tile, legal.get(), List.of());
        return afterCannon(
                context, tile, new TacticalPlan(Kind.PICKUP, tile.getPosition(), legal.get(), Map.of(), score));
    }

    private static double secretCargoCredit(Context context, Tile tile, int pickedUp) {
        GroundCampaign campaign = context.campaign();
        if (!campaign.holdsEither()) return 0;
        int mechs = campaign.holdsMechanize() ? unitCount(spareMechs(context, tile)) : 0;
        int ground = campaign.holdsFringe()
                ? unitCount(availableGroundForces(context.game, context.seat, tile, context.defense(), List.of()))
                : 0;
        return campaign.cargoCredit(context.objectives(), mechs, ground, pickedUp);
    }

    private static double advanceScore(Context context, Tile tile, double base, List<UnitMove> moves) {
        double leaveCost = originsOf(context, moves).stream()
                .mapToDouble(origin -> context.defense().leaveCost(origin, moves))
                .sum();
        return base
                - leaveCost
                + context.departureLoss(tile, moves)
                - GROUP_MOVE_COST * (movedNonFighterShips(moves) - 1)
                - context.exposure(tile, moves, List.of());
    }

    private static List<UnitMove> withEscorts(Context context, Tile tile, double base, List<UnitMove> moves) {
        List<UnitMove> best = moves;
        double bestScore = advanceScore(context, tile, base, moves);
        for (int added = 0; added < MAX_ESCORTS && context.exposure(tile, best, List.of()) > 0; added++) {
            List<UnitMove> improved = null;
            for (UnitMove escort : escortCandidates(context, tile, best)) {
                Optional<List<UnitMove>> joined = withEscort(context, tile, best, escort);
                if (joined.isEmpty()) continue;
                double score = advanceScore(context, tile, base, joined.get());
                if (score > bestScore) {
                    improved = joined.get();
                    bestScore = score;
                }
            }
            if (improved == null) break;
            best = improved;
        }
        return best;
    }

    private static List<UnitMove> escortCandidates(Context context, Tile tile, List<UnitMove> moves) {
        List<UnitMove> candidates = new ArrayList<>();
        for (Tile origin : context.origins()) {
            if (origin == tile) continue;
            List<UnitMove> leaving = movesFrom(origin, moves);
            List<UnitMove> idle = new ArrayList<>();
            for (UnitType type : ESCORTS) {
                if (!context.reaches(origin, tile, type)) continue;
                int spare = BoardView.count(BoardView.space(origin), context.seat, type) - countOf(leaving, type);
                if (spare > 0) idle.add(new UnitMove(origin.getPosition(), BoardView.SPACE, type, 1));
            }
            int room = capacityOf(context.seat, shipsIn(leaving)) - cargoIn(leaving);
            int fighters = BoardView.count(BoardView.space(origin), context.seat, UnitType.Fighter)
                    - countOf(leaving, UnitType.Fighter);
            if (room > 0 && fighters > 0) {
                idle.add(new UnitMove(origin.getPosition(), BoardView.SPACE, UnitType.Fighter, 1));
            }
            candidates.addAll(context.defense().holdBack(origin, leaving, idle));
        }
        return candidates;
    }

    private static Optional<List<UnitMove>> withEscort(
            Context context, Tile tile, List<UnitMove> moves, UnitMove escort) {
        List<UnitMove> merged = merge(concat(moves, List.of(escort)));
        if (BoardView.nonFighterShips(BoardView.space(tile), context.seat) + movedNonFighterShips(merged)
                > FleetLimit.ships(context.seat)) {
            return Optional.empty();
        }
        Tile origin = context.game.getTileByPosition(escort.origin());
        Optional<List<UnitMove>> legal = legalMoves(context, origin, tile, movesFrom(origin, merged));
        if (legal.isEmpty()) return Optional.empty();
        List<UnitMove> joined = new ArrayList<>(merged);
        joined.removeIf(move -> move.origin().equals(escort.origin()));
        joined.addAll(legal.get());
        return Optional.of(merge(joined));
    }

    static Optional<TacticalPlan> position(Context context, Tile tile) {
        Game game = context.game;
        Player seat = context.seat;
        double frontier = frontierExplore(seat, tile);
        boolean barrage = PrecisionRules.held(seat);
        double scar = scarGain(context, tile);
        if (!context.objectives().caresAboutPresence() && frontier == 0 && !barrage && scar == 0) {
            return Optional.empty();
        }
        if (BoardView.hasEnemyShips(game, seat, tile)) return Optional.empty();
        boolean contested =
                !context.cannonChances(tile).isEmpty() || SeatCover.contests(game, seat, tile, context.objectives());
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
                double barrageGain = barrageGain(context, origin, tile, legal.get());
                double gain = context.objectiveGain(tile, legal.get(), List.of()) + frontier + barrageGain + scar;
                if (gain <= 0) continue;
                double score = gain
                        - barrageExposure(context, tile, legal.get(), barrageGain)
                        - DISTANCE_COST * context.distance(origin, tile, type)
                        - SHIP_COST_WEIGHT * fleetCost(seat, legal.get())
                        - context.defense().leaveCost(origin, legal.get())
                        - CapacityUse.emptyHaulerPenalty(seat, legal.get());
                TacticalPlan plan = withLandings(
                        context,
                        tile,
                        new TacticalPlan(Kind.POSITION, tile.getPosition(), legal.get(), Map.of(), score));
                Optional<TacticalPlan> allowed = contested ? underCannon(context, tile, plan) : Optional.of(plan);
                if (allowed.isPresent() && (best == null || allowed.get().score() > best.score())) {
                    best = allowed.get();
                }
            }
            Optional<TacticalPlan> group = groupPosition(context, tile, origin);
            if (contested) group = group.flatMap(plan -> underCannon(context, tile, plan));
            if (group.isPresent() && (best == null || group.get().score() > best.score())) best = group.get();
        }
        return Optional.ofNullable(best);
    }

    private static Optional<TacticalPlan> afterCannon(Context context, Tile tile, TacticalPlan plan) {
        List<Double> chances = context.cannonChances(tile);
        if (chances.isEmpty()) return Optional.of(plan);
        TacticalPlan current = plan;
        Optional<TacticalPlan> priced = priced(context, tile, current, chances);
        for (int added = 0; priced.isEmpty() && added < MAX_ESCORTS; added++) {
            Optional<TacticalPlan> escorted = withCheapestEscort(context, tile, current);
            if (escorted.isEmpty()) break;
            current = escorted.get();
            priced = priced(context, tile, current, chances);
        }
        return priced;
    }

    private static TacticalPlan chargedForCannon(Context context, Tile tile, TacticalPlan plan) {
        CannonExposure.Outcome outcome = CannonExposure.of(
                context.seat, tile, plan.moves(), cargoDelivered(context, tile, plan), context.cannonChances(tile));
        return plan.withScore(plan.score() - outcome.expectedLoss());
    }

    private static Optional<TacticalPlan> priced(Context context, Tile tile, TacticalPlan plan, List<Double> chances) {
        CannonExposure.Outcome outcome =
                CannonExposure.of(context.seat, tile, plan.moves(), cargoDelivered(context, tile, plan), chances);
        if (!outcome.viable()) return Optional.empty();
        double realised = plan.score() > 0 ? plan.score() * outcome.delivery() : plan.score();
        return Optional.of(plan.withScore(realised - outcome.expectedLoss()));
    }

    private static Optional<TacticalPlan> withCheapestEscort(Context context, Tile tile, TacticalPlan plan) {
        if (plan.moves().isEmpty()) return Optional.empty();
        return escortCandidates(context, tile, plan.moves()).stream()
                .sorted(Comparator.comparingDouble(escort -> cost(context.seat, escort.type())))
                .map(escort -> withEscort(context, tile, plan.moves(), escort))
                .flatMap(Optional::stream)
                .findFirst()
                .map(moves -> new TacticalPlan(plan.kind(), plan.target(), moves, plan.landings(), plan.score()));
    }

    private static int cargoDelivered(Context context, Tile tile, TacticalPlan plan) {
        int carried = plan.moves().stream()
                .filter(move -> BoardView.GROUND_FORCES.contains(move.type()))
                .mapToInt(UnitMove::count)
                .sum();
        int landed = 0;
        for (Map.Entry<String, Integer> landing : plan.landings().entrySet()) {
            if (tile.getUnitHolders().get(landing.getKey()) instanceof Planet planet) {
                landed += Math.max(0, landing.getValue() - BoardView.groundForces(planet, context.seat));
            }
        }
        return Math.max(carried, landed);
    }

    private static Optional<TacticalPlan> underCannon(Context context, Tile tile, TacticalPlan plan) {
        double fire = CannonExposure.expectedHits(context.game, context.seat, tile);
        Footprint after =
                context.footprintAfter(tile, plan.moves(), plan.landings().keySet());
        boolean chasing = SeatCover.chases(context.game, context.seat, tile, context.objectives());
        if (chasing
                && !SeatCover.completesThroughCannon(
                        context.game, context.seat, tile, context.objectives(), after, plan.moves(), fire)) {
            return Optional.empty();
        }
        double shortfall = SeatCover.shortfall(context.game, context.seat, tile, context.objectives(), plan.moves());
        TacticalPlan shortened = plan.withScore(plan.score() - shortfall);
        Optional<TacticalPlan> priced = chasing
                ? Optional.of(chargedForCannon(context, tile, shortened))
                : afterCannon(context, tile, shortened);
        if (shortfall > 0 && priced.filter(found -> found.score() > 0).isEmpty()) return Optional.empty();
        return priced;
    }

    private static Optional<TacticalPlan> screen(Context context, Tile tile) {
        Game game = context.game;
        Player seat = context.seat;
        if (!tile.isFracture() || BoardView.hasOwnShips(seat, tile)) return Optional.empty();
        double value = context.screen().valueOf(tile);
        if (value <= 0) return Optional.empty();
        TacticalPlan best = null;
        for (Tile origin : context.origins()) {
            if (origin == tile) continue;
            for (UnitType type : SCREEN_SHIPS) {
                if (BoardView.undamaged(BoardView.space(origin), seat, type) == 0
                        || !context.reachesWithGravityDrive(origin, tile, type)) {
                    continue;
                }
                List<UnitMove> moves = List.of(new UnitMove(origin.getPosition(), BoardView.SPACE, type, 1));
                Optional<List<UnitMove>> legal = legalMoves(context, origin, tile, moves);
                if (legal.isEmpty()) continue;
                double score = value
                        - DISTANCE_COST * context.distance(origin, tile, type)
                        - SHIP_COST_WEIGHT * fleetCost(seat, legal.get())
                        - context.defense().leaveCost(origin, legal.get())
                        + context.departureLoss(tile, legal.get())
                        - context.exposure(tile, legal.get(), List.of());
                Optional<TacticalPlan> priced = afterCannon(
                        context,
                        tile,
                        new TacticalPlan(Kind.POSITION, tile.getPosition(), legal.get(), Map.of(), score));
                if (priced.isPresent() && (best == null || priced.get().score() > best.score())) best = priced.get();
            }
        }
        return Optional.ofNullable(best);
    }

    private static double scarGain(Context context, Tile tile) {
        Game game = context.game;
        Player seat = context.seat;
        if (!tile.isScar(game) || !seat.getSecretsUnscored().containsKey(ADAPT_NEW_STRATEGIES)) return 0;
        boolean alreadyInAScar = game.getTileMap().values().stream()
                .anyMatch(other -> other.isScar(game) && BoardView.hasOwnShips(seat, other));
        if (alreadyInAScar) return 0;
        OptionalInt needed = TechPath.factionTechsNeeded(seat);
        if (needed.isEmpty() || needed.getAsInt() <= 0) return 0;
        return ObjectiveValue.VICTORY_POINT_VALUE * SCAR_SHARE / needed.getAsInt();
    }

    private static double barrageGain(Context context, Tile origin, Tile tile, List<UnitMove> moves) {
        Player seat = context.seat;
        if (!PrecisionRules.held(seat)) return 0;
        Map<UnitType, Integer> arrived = new EnumMap<>(BoardView.ships(BoardView.space(tile), seat));
        moves.stream()
                .filter(move -> BoardView.MOVING_SHIPS.contains(move.type()))
                .forEach(move -> arrived.merge(move.type(), move.count(), Integer::sum));
        double after = PrecisionRules.bestChance(context.game, seat, tile, arrived, context::reach);
        double before = PrecisionRules.bestChance(
                context.game, seat, origin, BoardView.ships(BoardView.space(origin), seat), context::reach);
        double share = secretShare(context.game, seat, PrecisionRules.FIGHT_WITH_PRECISION);
        return Math.max(0, ObjectiveValue.VICTORY_POINT_VALUE * share * (after - before));
    }

    private static double barrageExposure(Context context, Tile tile, List<UnitMove> moves, double barrageGain) {
        return barrageGain > 0 ? context.exposure(tile, moves, List.of()) : 0;
    }

    private static double frontierExplore(Player seat, Tile tile) {
        if (!seat.hasTech(DARK_ENERGY_TAP) || BoardView.hasOwnShips(seat, tile)) return 0;
        boolean frontier = BoardView.space(tile).getTokenList().contains(Mapper.getTokenID(Constants.FRONTIER));
        return frontier ? frontierCardValue(seat) + FRONTIER_POSITION_VALUE : 0;
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
        double barrageGain = barrageGain(context, origin, tile, legal.get());
        double gain = context.objectiveGain(tile, legal.get(), List.of())
                + frontierExplore(seat, tile)
                + barrageGain
                + scarGain(context, tile);
        if (gain <= 0) return Optional.empty();
        int farthest = legal.get().stream()
                .filter(move -> BoardView.MOVING_SHIPS.contains(move.type()))
                .mapToInt(move -> context.distance(origin, tile, move.type()))
                .max()
                .orElse(0);
        double score = gain
                - barrageExposure(context, tile, legal.get(), barrageGain)
                - DISTANCE_COST * farthest
                - GROUP_MOVE_COST * movedNonFighterShips(legal.get())
                - context.defense().leaveCost(origin, legal.get())
                - CapacityUse.emptyHaulerPenalty(seat, legal.get());
        return Optional.of(withLandings(
                context, tile, new TacticalPlan(Kind.POSITION, tile.getPosition(), legal.get(), Map.of(), score)));
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
        double buildBonus = hasDock(seat, tile) ? context.buildPlan(tile).nonDefenseValue() : 0;
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
        if (chosen.isEmpty()) {
            return ferriedDefence(context, tile, origin, candidates, now, costNow, buildBonus);
        }
        List<UnitMove> ships = merge(chosen);
        Optional<TacticalPlan> unloaded =
                defensePlan(context, tile, origin, ships, List.of(), after, costNow, buildBonus);
        if (defense.mostExposedPlanet(tile, after) == null) return unloaded;
        List<UnitMove> cargo =
                take(availableGroundForces(context.game, seat, origin, defense, ships), capacityOf(seat, ships));
        if (cargo.isEmpty()) return unloaded;
        return better(unloaded, defensePlan(context, tile, origin, ships, cargo, after, costNow, buildBonus));
    }

    private static Optional<TacticalPlan> ferriedDefence(
            Context context,
            Tile tile,
            Tile origin,
            List<UnitMove> candidates,
            Defenders now,
            double costNow,
            double buildBonus) {
        Player seat = context.seat;
        DefenseOutlook defense = context.defense();
        List<UnitMove> ferry = ferry(context, origin, candidates);
        if (ferry.isEmpty() || defense.mostExposedPlanet(tile, now) == null) return Optional.empty();
        Defenders reinforced = now;
        for (UnitMove ship : ferry) reinforced = reinforced.plusShips(ship.type(), ship.count(), 0);
        List<UnitMove> cargo =
                take(availableGroundForces(context.game, seat, origin, defense, ferry), capacityOf(seat, ferry));
        if (cargo.isEmpty()) return Optional.empty();
        return defensePlan(context, tile, origin, ferry, cargo, reinforced, costNow, buildBonus)
                .filter(plan -> plan.score() - buildBonus >= DefenseOutlook.HOLD_MIN_GAIN);
    }

    private static List<UnitMove> ferry(Context context, Tile origin, List<UnitMove> candidates) {
        Player seat = context.seat;
        int waiting = movableGroundForces(context.game, seat, origin, context.defense());
        List<UnitMove> ferry = new ArrayList<>();
        int capacity = 0;
        for (UnitMove candidate : candidates) {
            if (!CapacityUse.hauler(seat, candidate.type())) continue;
            int taken = 0;
            while (taken < candidate.count() && capacity < waiting) {
                taken++;
                capacity += BoardView.capacity(seat, candidate.type());
            }
            if (taken > 0) ferry.add(new UnitMove(origin.getPosition(), BoardView.SPACE, candidate.type(), taken));
        }
        return ferry;
    }

    private static Optional<TacticalPlan> defensePlan(
            Context context,
            Tile tile,
            Tile origin,
            List<UnitMove> ships,
            List<UnitMove> cargo,
            Defenders reinforced,
            double costNow,
            double buildBonus) {
        DefenseOutlook defense = context.defense();
        Defenders after = reinforced;
        Optional<List<UnitMove>> legal = legalMoves(context, origin, tile, concat(ships, cargo));
        if (legal.isEmpty()) return Optional.empty();
        Map<String, Integer> landed = new LinkedHashMap<>();
        for (UnitMove move : legal.get()) {
            if (!BoardView.GROUND_FORCES.contains(move.type())) continue;
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
                + context.departureLoss(tile, legal.get())
                - DISTANCE_COST * farthest
                - GROUP_MOVE_COST * (movedNonFighterShips(legal.get()) - 1)
                - CapacityUse.loneHaulerPenalty(context.seat, legal.get())
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
        return legalMoves(context, origin, target, moves, false);
    }

    private static Optional<List<UnitMove>> legalMoves(
            Context context, Tile origin, Tile target, List<UnitMove> moves, boolean overflow) {
        Player seat = context.seat;
        int fleetAfterMove = BoardView.nonFighterShips(BoardView.space(target), seat) + movedNonFighterShips(moves);
        if (!overflow && fleetAfterMove > FleetLimit.ships(seat)) return Optional.empty();
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
    static Player singleOpponent(Game game, Player seat, Tile tile) {
        List<Player> others = othersIn(game, seat, tile);
        if (others.size() != 1
                || !(others.getFirst().isRealPlayer() || others.getFirst().isNeutral())) return null;
        return others.getFirst();
    }

    static List<Player> othersIn(Game game, Player seat, Tile tile) {
        return game.getPlayers().values().stream()
                .filter(other -> other != seat)
                .filter(other -> other.getColor() != null)
                .filter(other -> tile.containsPlayersUnits(other)
                        || tile.getPlanetUnitHolders().stream()
                                .anyMatch(planet -> other.getPlanets().contains(planet.getName())))
                .toList();
    }

    static boolean heldOnlyBy(Game game, Player seat, Planet planet, Player opponent) {
        Player controller = BoardView.controller(game, planet.getName());
        if (controller != null && controller != opponent) return false;
        return game.getRealPlayersNNeutral().stream()
                .filter(other -> other != seat && other != opponent)
                .noneMatch(other -> BoardView.groundForces(planet, other) > 0);
    }

    static List<Planet> enemyPlanets(Game game, Player seat, Tile tile) {
        return tile.getPlanetUnitHolders().stream()
                .filter(planet -> !seat.getPlanets().contains(planet.getName()))
                .filter(planet -> BoardView.controller(game, planet.getName()) != null
                        || BoardView.enemyGroundForcesOn(game, seat, planet))
                .sorted(IconStake.claimOrder(game, seat))
                .toList();
    }

    public static int movableGroundForces(Game game, Player seat, Tile origin) {
        return movableGroundForces(game, seat, origin, new DefenseOutlook(game, seat));
    }

    static List<UnitMove> spareGround(Game game, Player seat, Tile origin, DefenseOutlook defense) {
        return availableGroundForces(game, seat, origin, defense, List.of());
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
            int planetInfantry = BoardView.count(planet, seat, UnitType.Infantry);
            int garrison = garrison(game, seat, origin, planet, defense, shipsLeaving, blend);
            int infantryFirst = mechanizes(seat) ? Math.min(planetInfantry, garrison) : 0;
            int mechsStaying = Math.min(planetMechs, garrison - infantryFirst);
            int infantryStaying = garrison - mechsStaying;
            addMove(infantry, position, planet.getName(), UnitType.Infantry, planetInfantry - infantryStaying);
            addMove(mechs, position, planet.getName(), UnitType.Mech, planetMechs - mechsStaying);
        }
        if (mechanizes(seat)) {
            mechs.addAll(infantry);
            return mechs;
        }
        infantry.addAll(mechs);
        return infantry;
    }

    private static boolean mechanizes(Player seat) {
        return seat.getSecretsUnscored().containsKey(GroundCampaign.MECHANIZE_THE_MILITARY);
    }

    private static void addMove(List<UnitMove> moves, String origin, String holder, UnitType type, int count) {
        if (count > 0) moves.add(new UnitMove(origin, holder, type, count));
    }

    private static int garrison(
            Game game,
            Player seat,
            Tile origin,
            Planet planet,
            DefenseOutlook defense,
            List<UnitMove> shipsLeaving,
            @Nullable Blend blend) {
        int fringe = defense.campaign().kept(planet);
        if (origin.isHomeSystem(game)) {
            return Math.max(fringe, defense.groundToKeep(origin, planet, shipsLeaving, blend));
        }
        return Math.max(fringe, game.mecatols().contains(planet.getName()) ? MECATOL_GARRISON : 0);
    }

    public static CommanderReach.Garrison garrison(Game game, Player seat) {
        return garrisonOf(game, new DefenseOutlook(game, seat));
    }

    private static CommanderReach.Garrison garrisonOf(Game game, DefenseOutlook defense) {
        return (tile, planet) -> {
            int mecatol = game.mecatols().contains(planet.getName()) ? MECATOL_GARRISON : 0;
            return Math.max(mecatol, defense.groundToKeep(tile, planet, List.of()));
        };
    }

    public static int groundKeptHomeWhileTransportsLeave(Game game, Player seat) {
        Tile home = seat.getHomeSystemTile();
        if (home == null) return 0;
        UnitHolder space = BoardView.space(home);
        List<UnitMove> transports = new ArrayList<>();
        for (UnitType type : BoardView.MOVING_SHIPS) {
            int count = BoardView.count(space, seat, type);
            if (count > 0 && BoardView.capacity(seat, type) > 0) {
                transports.add(new UnitMove(home.getPosition(), BoardView.SPACE, type, count));
            }
        }
        int present = home.getUnitHolders().values().stream()
                .mapToInt(holder -> BoardView.groundForces(holder, seat))
                .sum();
        DefenseOutlook defense = new DefenseOutlook(game, seat);
        return present - unitCount(availableGroundForces(game, seat, home, defense, transports));
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

    static double fleetCost(Player seat, List<UnitMove> moves) {
        return moves.stream()
                .mapToDouble(move -> BoardView.model(seat, move.type())
                                .map(UnitModel::getCost)
                                .orElse(0f)
                        * move.count())
                .sum();
    }

    static final class Context {
        final Game game;
        final Player seat;
        private final Set<String> freed;
        private final List<SetupHint> hints;
        private final Map<String, Map<String, Integer>> reachCache = new HashMap<>();
        private final Map<String, List<Double>> cannonChances = new HashMap<>();
        private final Map<String, Integer> forwardNeeds = new HashMap<>();
        private List<Tile> origins;
        private Set<String> offered;
        private ObjectiveValue objectives;
        private Boolean canPayCustodians;
        private Boolean expectsGravityDrive;
        private Double mecatolCash;
        private DefenseOutlook defense;
        private OffenseOutlook offense;
        private Exposure exposure;
        private final Map<String, List<CommanderReach.Pull>> commanderPulls = new HashMap<>();
        private final Map<String, Optional<TacticalPlan>> attacks = new HashMap<>();
        private final Map<String, Boolean> unreachable = new HashMap<>();
        private final Map<String, ProductionPlanner.BuildPlan> builds = new HashMap<>();
        private final Map<String, Double> futureScores = new HashMap<>();
        private final Map<String, Double> marchValues = new HashMap<>();
        private Optional<FractureMarch> march;
        private ExploreOutlook exploreOutlook;
        private final Map<String, Double> exploreValues = new HashMap<>();
        private Territory territory;
        private FractureScreen screen;
        private CustodiansRace.Snapshot race;
        private final Map<String, Optional<TacticalPlan>> expansions = new HashMap<>();
        private final Map<String, Double> approachGains = new HashMap<>();
        private final Map<String, List<UnitMove>> stageGroups = new HashMap<>();
        private boolean tokenFree;
        private RoundGoals goals;
        private List<Tile> iconTiles;
        private final Map<String, Integer> iconDistances = new HashMap<>();

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

        static Context tokenFree(Game game, Player seat) {
            Context context = new Context(game, seat);
            context.tokenFree = true;
            return context;
        }

        double lockShare() {
            return tokenFree ? 1 : LOCKED_FORWARD_SHARE;
        }

        RoundGoals goals() {
            if (goals == null) goals = RoundGoals.of(game, seat, territory());
            return goals;
        }

        double exploreValue(Tile tile, Planet planet, int infantry, int mechs) {
            if (exploreOutlook == null) exploreOutlook = new ExploreOutlook(game, seat);
            return exploreValues.computeIfAbsent(
                    planet.getName() + "#" + infantry + "#" + mechs,
                    key -> ExploreDeck.expectedValueOfPlanet(
                            ExploreSite.landing(game, seat, tile, planet, infantry, mechs, exploreOutlook)));
        }

        List<UnitMove> stageGroup(Tile origin) {
            return stageGroups.computeIfAbsent(origin.getPosition(), position -> {
                UnitHolder space = BoardView.space(origin);
                List<UnitMove> ships = new ArrayList<>();
                for (UnitType type : BoardView.MOVING_SHIPS) {
                    int count = BoardView.count(space, seat, type);
                    if (count > 0) ships.add(new UnitMove(position, BoardView.SPACE, type, count));
                }
                return defense().holdBack(origin, List.of(), ships);
            });
        }

        CustodiansRace.Snapshot race() {
            if (race == null) race = CustodiansRace.of(game, seat);
            return race;
        }

        boolean approaches(Tile tile) {
            return !tile.isMecatol(game) && race().isOpen();
        }

        double approachGain(Tile destination) {
            return approachGains.computeIfAbsent(destination.getPosition(), position -> {
                OptionalInt idle = race().idleArrival(seat);
                OptionalInt moved = race().arrivalFrom(seat, destination);
                OptionalInt best = idle.isEmpty() ? moved : earlier(idle, moved);
                return race().raceValue(seat, best, CustodiansRace.Order.UNKNOWN)
                        - race().raceValue(seat, idle, CustodiansRace.Order.UNKNOWN);
            });
        }

        private static OptionalInt earlier(OptionalInt first, OptionalInt second) {
            if (second.isEmpty()) return first;
            return OptionalInt.of(Math.min(first.getAsInt(), second.getAsInt()));
        }

        Optional<TacticalPlan> expansionPlan(Tile tile) {
            Optional<TacticalPlan> known = expansions.get(tile.getPosition());
            if (known != null) return known;
            Optional<TacticalPlan> computed = expand(this, tile);
            expansions.put(tile.getPosition(), computed);
            return computed;
        }

        boolean marchesFrom(String position) {
            if (march == null) march = FractureMarch.of(game, seat);
            return march.filter(found -> found.position().equals(position)).isPresent();
        }

        FractureScreen screen() {
            if (screen == null)
                screen = new FractureScreen(game, seat, defense().standings());
            return screen;
        }

        Territory territory() {
            if (territory == null) territory = Territory.of(game, seat);
            return territory;
        }

        double expansionZoneBonus(Tile tile) {
            return zoneBonus(game, territory().zone(tile));
        }

        GroundCampaign campaign() {
            return defense().campaign();
        }

        DefenseOutlook defense() {
            if (defense == null) defense = new DefenseOutlook(game, seat);
            return defense;
        }

        OffenseOutlook offense() {
            if (offense == null) offense = new OffenseOutlook(game, seat, defense());
            return offense;
        }

        double exposure(Tile tile, List<UnitMove> arriving, List<UnitMove> landed) {
            if (exposure == null) exposure = new Exposure(game, seat, defense());
            Defenders after = defense().defendersAt(tile).arriving(arriving).minus(landed);
            return exposure.added(tile, after, arriving);
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

        Footprint footprintAfter(Tile target, List<UnitMove> moves, Collection<String> gainedPlanets) {
            List<Footprint.Move> footprintMoves = moves.stream()
                    .map(move -> new Footprint.Move(move.origin(), move.holder(), move.type(), move.count()))
                    .toList();
            return objectives().before().after(target.getPosition(), footprintMoves, gainedPlanets);
        }

        double objectiveGain(Tile target, List<UnitMove> moves, Collection<String> gainedPlanets) {
            return objectives().gain(footprintAfter(target, moves, gainedPlanets));
        }

        double departureLoss(Tile target, List<UnitMove> base, List<UnitMove> extra, Collection<String> landings) {
            if (extra.isEmpty()) return 0;
            double with = objectiveGain(target, concat(base, extra), landings);
            return Math.min(0, with - objectiveGain(target, base, landings));
        }

        double departureLoss(Tile target, List<UnitMove> moves) {
            return departureLoss(target, List.of(), moves, List.of());
        }

        double seatAdjustment(Tile target, List<UnitMove> moves, StrikeEstimate estimate) {
            if (!SeatStrike.isSeat(game, seat, target)) return 0;
            Footprint departing =
                    footprintAfter(target, moves, estimate.landings().keySet());
            double departingGain = objectives().gainOf(SeatCover.OCCUPY_THE_SEAT, departing);
            return SeatStrike.survivorAdjustment(game, seat, objectives(), target, departingGain, estimate);
        }

        ContextTerms seatTerms(Tile target, StrikeEstimate estimate) {
            if (!SeatStrike.isSeat(game, seat, target)) return ContextTerms.none();
            boolean takes = SeatStrike.takesMecatol(game, seat, estimate);
            double gain = SeatStrike.gain(game, seat, objectives(), target, estimate.survivors(), takes);
            return new ContextTerms(gain, Map.of(), 0, 0, 0, 0, 0, Map.of(), 0, seatStake(target, estimate));
        }

        double seatStake(Tile target, StrikeEstimate estimate) {
            return SeatStrike.immediateStake(game, seat, objectives(), target, estimate);
        }

        double builtGain(Tile tile, ProductionPlanner.BuildPlan build) {
            Map<UnitType, Integer> built = new EnumMap<>(UnitType.class);
            for (ProductionPlanner.BuildOrder order : build.orders())
                built.merge(order.type(), order.units(), Integer::sum);
            ObjectiveValue value = objectives();
            return value.gain(value.before().withBuilt(tile.getPosition(), built));
        }

        boolean expectsGravityDrive() {
            if (expectsGravityDrive == null) expectsGravityDrive = OpeningHold.awaitsGravityDrive(game, seat);
            return expectsGravityDrive;
        }

        private boolean reachesForAnotherAction(Tile origin, Tile target, UnitType transport) {
            if (reachesWithGravityDrive(origin, target, transport)) return true;
            if (!expectsGravityDrive()) return false;
            int move = BoardView.moveValue(seat, transport) + 1;
            return reach(origin, move).containsKey(target.getPosition());
        }

        boolean canPayCustodians() {
            if (canPayCustodians == null) {
                SpendCost cost = SpendCost.influence(CUSTODIANS_COST);
                canPayCustodians = ScoringReserve.planForCustodians(game, seat).isPresent()
                        || IconStake.behindCustodians(game, seat)
                                && Wallet.of(game, seat).plan(cost).isPresent();
            }
            return canPayCustodians;
        }

        List<Tile> iconTiles() {
            if (iconTiles == null) iconTiles = IconStake.freeTiles(game, seat);
            return iconTiles;
        }

        int iconDistance(Tile tile) {
            return iconDistances.computeIfAbsent(tile.getPosition(), position -> {
                Map<String, Integer> around = reach(tile, ICON_MARCH_RANGE);
                return iconTiles().stream()
                        .map(prize -> around.get(prize.getPosition()))
                        .filter(Objects::nonNull)
                        .mapToInt(Integer::intValue)
                        .min()
                        .orElse(Integer.MAX_VALUE);
            });
        }

        double mecatolCash() {
            if (mecatolCash == null) {
                mecatolCash = MECATOL_CASH_SHARE * ScoringStakes.scoreValue(game, seat, MecatolCash.points(game, seat));
            }
            return mecatolCash;
        }

        boolean offers(Tile tile) {
            if (tokenFree || !freed.isEmpty()) return true;
            if (offered == null) {
                offered = CheckDistanceHelper.getTileDistancesRelativeToAllYourUnlockedTiles(game, seat)
                        .keySet();
            }
            return offered.contains(tile.getPosition());
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

        boolean unreachable(Tile tile) {
            return unreachable.computeIfAbsent(tile.getPosition(), position -> noShipCanReach(tile));
        }

        private boolean noShipCanReach(Tile tile) {
            if (BoardView.hasOwnShips(seat, tile) || !commanderPulls(tile).isEmpty()) return false;
            for (Tile origin : origins()) {
                if (origin == tile) continue;
                for (UnitType type : BoardView.MOVING_SHIPS) {
                    if (BoardView.count(BoardView.space(origin), seat, type) > 0
                            && reachesWithGravityDrive(origin, tile, type)) {
                        return false;
                    }
                }
            }
            return true;
        }

        String rankingKey() {
            return seat.getFaction() + " freed " + freed + " hints " + hints.size() + (tokenFree ? " token-free" : "")
                    + " ships " + FleetLimit.ships(seat);
        }

        ProductionPlanner.BuildPlan buildPlan(Tile tile) {
            return builds.computeIfAbsent(
                    tile.getPosition(), position -> ProductionPlanner.plan(game, seat, tile, 0, defense()));
        }

        boolean reaches(Tile origin, Tile target, UnitType type) {
            return distance(origin, target, type) <= BoardView.moveValue(seat, type);
        }

        boolean reachesWithGravityDrive(Tile origin, Tile target, UnitType type) {
            return distance(origin, target, type) <= BoardView.moveValueWithBoosts(seat, type);
        }

        Map<String, Integer> reach(Tile from, int move) {
            return reachCache.computeIfAbsent(
                    from.getPosition() + "#" + move, key -> MovementGraph.reach(game, seat, from.getPosition(), move));
        }

        int distance(Tile origin, Tile target, UnitType type) {
            int move = BoardView.moveValueWithBoosts(seat, type);
            Map<String, Integer> reach = reachCache.computeIfAbsent(
                    origin.getPosition() + "#" + move,
                    key -> MovementGraph.reach(game, seat, origin.getPosition(), move));
            return reach.getOrDefault(target.getPosition(), Integer.MAX_VALUE);
        }

        int forwardNeed(Tile tile, UnitType transport) {
            return forwardNeeds.computeIfAbsent(tile.getPosition() + "#" + transport, key -> {
                int move = BoardView.moveValueWithBoosts(seat, transport);
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
                    .filter(other -> reachesForAnotherAction(origin, other, transport))
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

        List<CommanderReach.Pull> commanderPulls(Tile tile) {
            return commanderPulls.computeIfAbsent(tile.getPosition(), position -> {
                boolean custodiansToPay = tile.getPlanetUnitHolders().stream().anyMatch(BoardView::hasCustodians);
                return custodiansToPay ? List.of() : CommanderReach.pulls(game, seat, tile, garrison());
            });
        }

        List<UnitMove> commanderMoves(Tile tile) {
            return commanderPulls(tile).stream()
                    .map(pull -> new UnitMove(pull.position(), pull.planet(), pull.type(), 1))
                    .toList();
        }

        Map<UnitType, Integer> commanderForces(Tile tile) {
            Map<UnitType, Integer> forces = new EnumMap<>(UnitType.class);
            commanderPulls(tile).forEach(pull -> forces.merge(pull.type(), 1, Integer::sum));
            return forces;
        }

        CommanderReach.Garrison garrison() {
            return garrisonOf(game, defense());
        }

        List<Double> cannonChances(Tile tile) {
            return cannonChances.computeIfAbsent(
                    tile.getPosition(), position -> CannonExposure.dieChances(game, seat, tile));
        }
    }
}

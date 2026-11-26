package ti4.ai.xxcha;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.DecisionMemo;
import ti4.ai.brain.SearchMemo;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.GameHorizon;
import ti4.ai.eval.ImaginedTurn;
import ti4.ai.eval.Threats;
import ti4.ai.explore.ExploreSite;
import ti4.ai.scoring.Footprint;
import ti4.ai.scoring.ObjectivePolicy;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.scoring.SpendUnlock;
import ti4.ai.strategy.CardValueScale;
import ti4.ai.strategy.ReadyingValue;
import ti4.ai.strategy.SpendOutlook;
import ti4.ai.tactical.GroundDrops;
import ti4.ai.tactical.GroundDrops.Drop;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;
import ti4.service.info.ListPlayerInfoService;

@UtilityClass
public class XxekirGromPlan {

    static final String MECH = "mech";
    static final String PDS = "pds";
    static final int PLACEMENTS = 4;
    static final int SPENT_PLANETS_TO_PLAY = 2;
    static final double PLAY_BAR = 4.0;
    static final double PDS_COVER = 1.0;
    static final double STRUCTURE_COMPLETION_SHARE = 0.8;
    static final double STRUCTURE_STEP_SHARE = 0.1;
    static final int EXPECTED_LAST_ROUND = 7;
    static final int FULL_VALUE_ROUNDS = 4;
    private static final String OFF_BOARD = "xxekirGrom";
    private static final Set<String> STRUCTURE_COUNT_OBJECTIVES = Set.of("build_defenses", "massive_cities", "eap");
    private static final Set<String> SPREAD_OBJECTIVES = Set.of("infrastructure", "protect_border");
    private static final int MAX_CACHED = 64;
    private static final ThreadLocal<Boolean> PLANNING = ThreadLocal.withInitial(() -> false);
    private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();

    record Placement(String unit, String planet) {}

    record Outlook(
            boolean unlocksSpend,
            boolean completesObjective,
            int spentPlanets,
            double readying,
            double scoring,
            double tactical,
            int roundsAhead,
            List<Placement> placements) {

        boolean paysForObjective() {
            return unlocksSpend || completesObjective;
        }

        double value() {
            return readying + scoring + unitScale(roundsAhead) * tactical;
        }

        boolean worthAnAction(boolean spendingAhead) {
            return paysForObjective()
                    || (spendingAhead && spentPlanets >= SPENT_PLANETS_TO_PLAY)
                    || value() >= PLAY_BAR;
        }

        int readiesCounted() {
            return Math.min(PLACEMENTS, spentPlanets);
        }
    }

    private record Cached(WeakReference<Game> game, List<Object> state, int readies) {

        boolean isFor(Game current, List<Object> currentState) {
            return game.get() == current && state.equals(currentState);
        }
    }

    private record StructureTarget(String id, int missing, int points, boolean spread) {

        int progress(int pds, int freshPds) {
            return spread ? freshPds : pds;
        }

        boolean completedBy(int pds, int freshPds) {
            return progress(pds, freshPds) >= missing;
        }

        double value(int pds, int freshPds) {
            double point = ObjectiveValue.VICTORY_POINT_VALUE * points;
            if (completedBy(pds, freshPds)) return STRUCTURE_COMPLETION_SHARE * point;
            return STRUCTURE_STEP_SHARE * point * progress(pds, freshPds);
        }
    }

    private record Option(
            String unit,
            Planet planet,
            Footprint footprint,
            double scoringGain,
            double objectiveGain,
            int pds,
            int freshPds,
            double tactical,
            double readying,
            double total) {}

    public static int readiesThisRound(Game game, Player seat) {
        if (!seat.hasLeaderUnlocked(XxekirGromRules.HERO) || seat.isPassed() || PLANNING.get()) return 0;
        String key = game.getName() + "|" + seat.getUserID() + ImaginedTurn.cacheMark(seat);
        List<Object> state = state(game, seat);
        Cached cached = CACHE.get(key);
        if (cached != null && cached.isFor(game, state)) return cached.readies();
        int readies = planning(() -> {
            Outlook outlook = compute(game, seat);
            return outlook.worthAnAction(SpendOutlook.spendingAhead(game, seat)) ? outlook.readiesCounted() : 0;
        });
        if (CACHE.size() > MAX_CACHED) CACHE.clear();
        CACHE.put(key, new Cached(new WeakReference<>(game), state, readies));
        return readies;
    }

    public static boolean isPlanning() {
        return PLANNING.get();
    }

    static Optional<String> actionReason(Game game, Player seat) {
        return planning(() -> {
            Outlook outlook = compute(game, seat);
            if (!outlook.worthAnAction(SpendOutlook.spendingAhead(game, seat))) return Optional.empty();
            return Optional.of(reason(outlook));
        });
    }

    static Optional<String> passingReason(Game game, Player seat) {
        return planning(() -> {
            Outlook outlook = compute(game, seat);
            if (!outlook.paysForObjective()) return Optional.empty();
            return Optional.of("before passing, " + reason(outlook));
        });
    }

    static Outlook outlook(Game game, Player seat) {
        return planning(() -> compute(game, seat));
    }

    static Optional<String> nextUnit(Game game, Player seat) {
        return planning(() -> new Planner(game, seat, sites(game, seat, seat.getPlanets()))
                .best(units(game, seat))
                .filter(option -> option.total() > 0)
                .map(Option::unit));
    }

    static Optional<String> nextPlanet(Game game, Player seat, String unit, Collection<String> candidates) {
        return planning(() -> new Planner(game, seat, sites(game, seat, candidates))
                .best(List.of(unit))
                .filter(option -> option.total() > 0)
                .map(option -> option.planet().getName()));
    }

    static int roundsAhead(Game game) {
        int byRound = EXPECTED_LAST_ROUND - game.getRound() + 1;
        return Math.max(1, Math.min(GameHorizon.roundsLeft(game), byRound));
    }

    static double unitScale(int roundsAhead) {
        return Math.min(Math.max(1, roundsAhead), FULL_VALUE_ROUNDS) / (double) FULL_VALUE_ROUNDS;
    }

    static boolean unlocksSpendObjective(Game game, Player seat) {
        return !spendUnlockShares(game, seat).isEmpty();
    }

    private static Map<String, Double> spendUnlockShares(Game game, Player seat) {
        List<String> readied = sites(game, seat, seat.getExhaustedPlanets()).stream()
                .map(Planet::getName)
                .sorted(Comparator.comparingInt((String planet) -> -higherValue(game, planet))
                        .thenComparing(Comparator.naturalOrder()))
                .limit(PLACEMENTS)
                .toList();
        if (readied.isEmpty()) return Map.of();
        int points = SpendUnlock.pointsUnlocked(game, seat, readied, 0);
        if (points <= 0) return Map.of();
        double share = points * CardValueScale.SPEND_UNLOCK_SHARE * CardValueScale.VICTORY_POINT / readied.size();
        return readied.stream().collect(Collectors.toMap(planet -> planet, planet -> share));
    }

    private static String reason(Outlook outlook) {
        if (outlook.unlocksSpend()) return "to ready the planets it spends on a public objective";
        if (outlook.completesObjective()) return "to place units that complete an objective";
        if (outlook.spentPlanets() >= SPENT_PLANETS_TO_PLAY) {
            return "to ready " + outlook.spentPlanets() + " planets it spends this round";
        }
        return "for units and readying worth " + Math.round(outlook.value());
    }

    private static Outlook compute(Game game, Player seat) {
        Planner planner = new Planner(game, seat, sites(game, seat, seat.getPlanets()));
        planner.plan(units(game, seat));
        return new Outlook(
                planner.unlocksSpend,
                planner.completesObjective(),
                planner.spentPlanets,
                planner.readyingTotal,
                planner.scoringTotal,
                planner.tacticalTotal,
                planner.roundsAhead,
                List.copyOf(planner.placements));
    }

    private static <T> T planning(Supplier<T> work) {
        boolean before = PLANNING.get();
        PLANNING.set(true);
        try (DecisionMemo.Open decision = DecisionMemo.open();
                SearchMemo.Open search = SearchMemo.open()) {
            return work.get();
        } finally {
            PLANNING.set(before);
        }
    }

    private static List<Object> state(Game game, Player seat) {
        return List.of(
                game.getLastModifiedDate(),
                game.getRound(),
                Set.copyOf(seat.getExhaustedPlanets()),
                Set.copyOf(seat.getPlanets()),
                seat.getTg());
    }

    private static List<String> units(Game game, Player seat) {
        List<String> units = new ArrayList<>();
        if (left(game, seat, UnitType.Mech) > 0) units.add(MECH);
        if (left(game, seat, UnitType.Pds) > 0) units.add(PDS);
        return units;
    }

    private static int left(Game game, Player seat, UnitType type) {
        UnitModel model = seat.getUnitByType(type);
        if (model == null) return 0;
        if (type == UnitType.Mech && game.isBaseGameMode()) return 0;
        int cap = seat.getUnitCap(model.getAsyncId());
        if (cap <= 0) return PLACEMENTS;
        return Math.max(0, cap - ButtonHelper.getNumberOfUnitsOnTheBoard(game, seat, model.getAsyncId()));
    }

    private static List<Planet> sites(Game game, Player seat, Collection<String> names) {
        return names.stream()
                .distinct()
                .filter(seat.getPlanets()::contains)
                .map(game::getUnitHolderFromPlanet)
                .filter(Objects::nonNull)
                .filter(planet -> !planet.isSpaceStation(game) && !ExploreSite.isDemilitarized(planet))
                .filter(planet -> game.getTileFromPlanet(planet.getName()) != null)
                .toList();
    }

    private static int higherValue(Game game, String planet) {
        return Math.max(BoardView.planetResources(game, planet), BoardView.planetInfluence(game, planet));
    }

    private static final class Planner {
        private final Game game;
        private final Player seat;
        private final List<Planet> sites;
        private final Map<String, Double> readying;
        private final Set<String> exhausted;
        private final boolean unlocksSpend;
        private final ObjectiveValue objectives;
        private final List<StructureTarget> structureTargets;
        private final Map<String, Double> mechDrops;
        private final Set<String> reachable;
        private final String home;
        private final int roundsAhead;
        private final double scale;
        private final Set<String> used = new HashSet<>();
        private final Set<String> coveredTiles = new HashSet<>();
        private final List<Placement> placements = new ArrayList<>();
        private Footprint footprint;
        private double objectiveGain;
        private int pds;
        private int freshPds;
        private int mechsLeft;
        private int pdsLeft;
        private int spentPlanets;
        private double readyingTotal;
        private double scoringTotal;
        private double tacticalTotal;

        Planner(Game game, Player seat, List<Planet> sites) {
            this.game = game;
            this.seat = seat;
            this.sites = sites;
            this.readying = ReadyingValue.eachAlone(game, seat, seat.getPlanets()).stream()
                    .collect(Collectors.toMap(
                            ReadyingValue.Pick::planet, ReadyingValue.Pick::value, Math::max, HashMap::new));
            Map<String, Double> unlockShares = spendUnlockShares(game, seat);
            unlockShares.forEach((planet, share) -> readying.merge(planet, share, Math::max));
            this.unlocksSpend = !unlockShares.isEmpty();
            this.exhausted = Set.copyOf(seat.getExhaustedPlanets());
            this.objectives = new ObjectiveValue(game, seat);
            this.structureTargets = structureTargets(game, seat);
            this.mechDrops = GroundDrops.drops(game, seat, Optional.empty()).stream()
                    .collect(Collectors.toMap(Drop::planet, Drop::mech, Math::max));
            this.reachable = Threats.reachableByEnemies(game, seat);
            Tile homeTile = seat.getHomeSystemTile();
            this.home = homeTile == null ? "" : homeTile.getPosition();
            this.roundsAhead = XxekirGromPlan.roundsAhead(game);
            this.scale = unitScale(roundsAhead);
            this.footprint = objectives.before();
            this.mechsLeft = left(game, seat, UnitType.Mech);
            this.pdsLeft = left(game, seat, UnitType.Pds);
        }

        void plan(List<String> units) {
            for (int step = 0; step < PLACEMENTS; step++) {
                Optional<Option> best = best(units);
                if (best.isEmpty()) return;
                apply(best.get());
            }
        }

        Optional<Option> best(List<String> units) {
            List<Option> options = new ArrayList<>();
            for (Planet planet : sites) {
                if (used.contains(planet.getName())) continue;
                for (String unit : units) {
                    if (available(unit)) options.add(evaluate(unit, planet));
                }
            }
            Comparator<Option> byName =
                    Comparator.comparing((Option option) -> option.planet().getName());
            return options.stream()
                    .max(Comparator.comparingDouble(Option::total)
                            .thenComparing((Option option) ->
                                    exhausted.contains(option.planet().getName()))
                            .thenComparingInt((Option option) ->
                                    higherValue(game, option.planet().getName()))
                            .thenComparing(byName.reversed()));
        }

        boolean completesObjective() {
            boolean structure = structureTargets.stream().anyMatch(target -> target.completedBy(pds, freshPds));
            return structure || objectives.completedPoints(footprint) > 0;
        }

        private boolean available(String unit) {
            return MECH.equals(unit) ? mechsLeft > 0 : pdsLeft > 0;
        }

        private Option evaluate(String unit, Planet planet) {
            Tile tile = game.getTileFromPlanet(planet.getName());
            String position = tile.getPosition();
            boolean isPds = PDS.equals(unit);
            Footprint after = isPds
                    ? footprint.withBuilt(position, Map.of(UnitType.Pds, 1))
                    : footprint.after(
                            position,
                            List.of(new Footprint.Move(OFF_BOARD, BoardView.SPACE, UnitType.Mech, 1)),
                            List.of(planet.getName()));
            int pdsAfter = pds + (isPds ? 1 : 0);
            int freshAfter = freshPds + (isPds && fresh(planet, position) ? 1 : 0);
            double gainAfter = objectives.gain(after);
            double scoringGain = gainAfter - objectiveGain + structureValue(pdsAfter, freshAfter) - structureValue();
            double raw = isPds ? cover(tile) : mechDrops.getOrDefault(planet.getName(), 0.0);
            double tactical = Math.max(0, raw - scoringGain);
            double ready = exhausted.contains(planet.getName()) ? readying.getOrDefault(planet.getName(), 0.0) : 0;
            double total = scoringGain + scale * tactical + ready;
            return new Option(
                    unit, planet, after, scoringGain, gainAfter, pdsAfter, freshAfter, tactical, ready, total);
        }

        private void apply(Option option) {
            String name = option.planet().getName();
            used.add(name);
            placements.add(new Placement(option.unit(), name));
            footprint = option.footprint();
            objectiveGain = option.objectiveGain();
            pds = option.pds();
            freshPds = option.freshPds();
            if (PDS.equals(option.unit())) {
                pdsLeft--;
                Tile tile = game.getTileFromPlanet(name);
                if (tile != null) coveredTiles.add(tile.getPosition());
            } else {
                mechsLeft--;
            }
            scoringTotal += option.scoringGain();
            tacticalTotal += option.tactical();
            readyingTotal += option.readying();
            if (option.readying() > 0) spentPlanets++;
        }

        private double cover(Tile tile) {
            String position = tile.getPosition();
            if (!reachable.contains(position) || coveredTiles.contains(position)) return 0;
            return BoardView.countInTile(tile, seat, UnitType.Pds) == 0 ? PDS_COVER : 0;
        }

        private boolean fresh(Planet planet, String position) {
            return !home.equals(position)
                    && BoardView.count(planet, seat, UnitType.Pds) == 0
                    && BoardView.count(planet, seat, UnitType.Spacedock) == 0;
        }

        private double structureValue() {
            return structureValue(pds, freshPds);
        }

        private double structureValue(int pdsCount, int freshCount) {
            return structureTargets.stream()
                    .mapToDouble(target -> target.value(pdsCount, freshCount))
                    .sum();
        }
    }

    private static List<StructureTarget> structureTargets(Game game, Player seat) {
        List<StructureTarget> targets = new ArrayList<>();
        for (String id : game.getRevealedPublicObjectives().keySet()) {
            if (isStructureObjective(id) && !ObjectivePolicy.hasScored(game, seat, id)) {
                structureTarget(game, seat, id, Math.max(1, ObjectivePolicy.victoryPoints(id)))
                        .ifPresent(targets::add);
            }
        }
        for (String id : ImaginedTurn.unscoredSecrets(seat).keySet()) {
            if (isStructureObjective(id)) structureTarget(game, seat, id, 1).ifPresent(targets::add);
        }
        return targets;
    }

    private static boolean isStructureObjective(String id) {
        return STRUCTURE_COUNT_OBJECTIVES.contains(id) || SPREAD_OBJECTIVES.contains(id);
    }

    private static Optional<StructureTarget> structureTarget(Game game, Player seat, String id, int points) {
        int threshold = ListPlayerInfoService.getObjectiveThreshold(id, game);
        if (threshold <= 0) return Optional.empty();
        int missing = threshold - ListPlayerInfoService.getPlayerProgressOnObjective(id, game, seat);
        if (missing <= 0) return Optional.empty();
        return Optional.of(new StructureTarget(id, missing, points, SPREAD_OBJECTIVES.contains(id)));
    }
}

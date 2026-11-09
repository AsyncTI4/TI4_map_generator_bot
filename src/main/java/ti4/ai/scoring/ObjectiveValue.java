package ti4.ai.scoring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import ti4.ai.AiExperiments;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.ImaginedTurn;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.service.info.ListPlayerInfoService;

public final class ObjectiveValue {

    public static final double VICTORY_POINT_VALUE = 8.0;
    private static final double PARTIAL_SHARE = 0.6;
    private static final double WITHIN_REACH_SHARE = 0.9;
    private static final int STEPS_WITHIN_REACH = 3;
    private static final int TRAIT_STEPS_WITHIN_REACH = 2;
    private static final int GROUND_STEPS_WITHIN_REACH = 3;
    private static final Set<String> GROUND_SECRETS = Set.of("mtm", "otf");
    public static final Map<String, String> TRAIT_SECRETS =
            Map.of("mp", "industrial", "faa", "cultural", "mrm", "hazardous");
    private static final int STAGE_TWO_POINTS = 2;
    public static final int NOT_MODELLED = -1;
    private static final Set<String> ATTACHMENT_OBJECTIVES = Set.of("lost_outposts", "ancient_monuments");
    private static final Set<String> SPECIALTY_OBJECTIVES = Set.of("research_outposts", "brain_trust");

    private record Target(String id, int threshold, int points, int before, boolean secret) {}

    private record PriceKey(int points, int returned) {}

    private final Game game;
    private final Player seat;
    private final Footprint before;
    private final List<Target> targets = new ArrayList<>();
    private final Map<String, Set<String>> systemSets = new HashMap<>();
    private final Map<PriceKey, Double> completionPrices = new HashMap<>();
    private final Map<Integer, Double> breakPrices = new HashMap<>();
    private Boolean winAware;
    private Integer readyPublicPoints;
    private Integer freePublicSlots;

    public ObjectiveValue(Game game, Player seat) {
        this(game, seat, true);
    }

    private ObjectiveValue(Game game, Player seat, boolean includeSecrets) {
        this.game = game;
        this.seat = seat;
        this.before = Footprint.of(game, seat);
        boolean canScorePublics = Helper.canPlayerScorePOs(game, seat);
        for (String id : game.getRevealedPublicObjectives().keySet()) {
            if (canScorePublics && !ObjectivePolicy.hasScored(game, seat, id)) {
                addTarget(id, Math.max(1, ObjectivePolicy.victoryPoints(id)), false);
            }
        }
        if (includeSecrets) {
            for (String id : ImaginedTurn.unscoredSecrets(seat).keySet()) addTarget(id, 1, true);
        }
    }

    public static ObjectiveValue publicOnly(Game game, Player player) {
        return new ObjectiveValue(game, player, false);
    }

    private void addTarget(String id, int points, boolean secret) {
        int threshold = ListPlayerInfoService.getObjectiveThreshold(id, game);
        if (threshold <= 0) return;
        int progress = progress(id, before);
        if (progress == NOT_MODELLED) return;
        targets.add(new Target(id, threshold, points, progress, secret));
    }

    public Footprint before() {
        return before;
    }

    public boolean caresAboutPresence() {
        return targets.stream().anyMatch(target -> target.before() < target.threshold() && isPresence(target.id()));
    }

    public double gain(Footprint after) {
        return gain(after, 0);
    }

    public double gain(Footprint after, int pointsReturned) {
        if (winAware()) return winAwareGain(targets, target -> progress(target.id(), after), pointsReturned);
        double total = 0;
        for (Target target : targets) total += gainOf(target, after);
        return total;
    }

    public double traitSecretGain(Footprint after) {
        if (winAware()) {
            List<Target> traits = targets.stream()
                    .filter(target -> TRAIT_SECRETS.containsKey(target.id()))
                    .toList();
            return winAwareGain(traits, target -> progress(target.id(), after), 0);
        }
        double total = 0;
        for (Target target : targets) {
            if (TRAIT_SECRETS.containsKey(target.id())) total += gainOf(target, after);
        }
        return total;
    }

    private double gainOf(Target target, Footprint after) {
        int then = progress(target.id(), after);
        return then == NOT_MODELLED ? 0 : targetGain(target, then);
    }

    public double gainOf(String id, Footprint after) {
        return gainAt(id, progress(id, after));
    }

    public double gainAt(String id, int progress) {
        if (progress == NOT_MODELLED) return 0;
        if (winAware()) {
            List<Target> matching =
                    targets.stream().filter(target -> target.id().equals(id)).toList();
            return winAwareGain(matching, target -> progress, 0);
        }
        double total = 0;
        for (Target target : targets) {
            if (target.id().equals(id)) total += targetGain(target, progress);
        }
        return total;
    }

    private boolean winAware() {
        if (winAware == null) winAware = AiExperiments.enabled(seat, AiExperiments.Experiment.WIN_AWARE_POINTS);
        return winAware;
    }

    private double winAwareGain(List<Target> chosen, ToIntFunction<Target> progressAfter, int pointsReturned) {
        double total = 0;
        int secretPoints = 0;
        int brokenPoints = 0;
        List<Integer> publicPoints = new ArrayList<>();
        for (Target target : chosen) {
            int now = target.before();
            int then = progressAfter.applyAsInt(target);
            if (then == NOT_MODELLED || then == now) continue;
            boolean wasComplete = now >= target.threshold();
            boolean isComplete = then >= target.threshold();
            if (wasComplete) {
                if (!isComplete) brokenPoints += target.points();
            } else if (!isComplete) {
                total += partialGain(target, now, then);
            } else if (target.secret()) {
                secretPoints += target.points();
            } else {
                publicPoints.add(target.points());
            }
        }
        publicPoints.sort(Comparator.reverseOrder());
        int slots = freePublicSlots();
        int scoredPoints = secretPoints;
        for (int index = 0; index < publicPoints.size(); index++) {
            int points = publicPoints.get(index);
            if (index < slots) scoredPoints += points;
            else total += VICTORY_POINT_VALUE * points * PARTIAL_SHARE;
        }
        return total + completionPrice(scoredPoints, pointsReturned) - breakPrice(brokenPoints);
    }

    private double completionPrice(int points, int pointsReturned) {
        if (points <= pointsReturned) return 0;
        return completionPrices.computeIfAbsent(new PriceKey(points, pointsReturned), key -> {
            int base = readyPoints() - key.returned();
            double stake = ScoringStakes.scoreValue(game, seat, base + key.points())
                    - ScoringStakes.scoreValue(game, seat, Math.max(0, base));
            return Math.max(VICTORY_POINT_VALUE * key.points(), stake);
        });
    }

    private double breakPrice(int points) {
        if (points <= 0) return 0;
        return breakPrices.computeIfAbsent(points, lost -> {
            int base = readyPoints();
            double stake =
                    ScoringStakes.scoreValue(game, seat, base) - ScoringStakes.scoreValue(game, seat, base - lost);
            return Math.max(VICTORY_POINT_VALUE * lost, stake);
        });
    }

    private int readyPoints() {
        if (readyPublicPoints == null) countReadyPublics();
        int readySecrets = (int) targets.stream()
                .filter(target -> target.secret() && target.before() >= target.threshold())
                .count();
        return readyPublicPoints + Math.min(1, readySecrets);
    }

    private int freePublicSlots() {
        if (freePublicSlots == null) countReadyPublics();
        return freePublicSlots;
    }

    private void countReadyPublics() {
        List<Integer> ready = ObjectivePolicy.scorablePublics(game, seat).stream()
                .map(ObjectivePolicy::victoryPoints)
                .sorted(Comparator.reverseOrder())
                .toList();
        int chances = ScoringReserve.scoringChances(game, seat);
        freePublicSlots = Math.max(0, chances - ready.size());
        readyPublicPoints =
                ready.stream().limit(chances).mapToInt(Integer::intValue).sum();
    }

    private static double targetGain(Target target, int then) {
        int now = target.before();
        if (then == now) return 0;
        double points = VICTORY_POINT_VALUE * target.points();
        if (now >= target.threshold()) return then < target.threshold() ? -points : 0;
        return then >= target.threshold() ? points : partialGain(target, now, then);
    }

    public int completedPoints(Footprint after) {
        int total = 0;
        for (Target target : targets) {
            int then = progress(target.id(), after);
            if (then != NOT_MODELLED && target.before() < target.threshold() && then >= target.threshold()) {
                total += target.points();
            }
        }
        return total;
    }

    private static double partialGain(Target target, int now, int then) {
        int stillNeeded = target.threshold() - Math.min(now, then);
        int fewestStillNeeded = target.threshold() - Math.max(now, then);
        return VICTORY_POINT_VALUE
                * target.points()
                * partialShare(target, fewestStillNeeded)
                * (then - now)
                / stillNeeded;
    }

    public double attachmentGain(String planetName) {
        Planet planet = game.getPlanetsInfo().get(planetName);
        if (planet == null || planet.hasAttachment() || !countsForScoring(planet)) return 0;
        return oneMoreGain(ATTACHMENT_OBJECTIVES);
    }

    public double specialtyGain(String planetName) {
        Planet planet = game.getPlanetsInfo().get(planetName);
        if (planet == null || !planet.getTechSpecialities().isEmpty()) return 0;
        return oneMoreGain(SPECIALTY_OBJECTIVES);
    }

    private double oneMoreGain(Set<String> objectives) {
        if (winAware()) {
            List<Target> open = targets.stream()
                    .filter(target -> objectives.contains(target.id()) && target.before() < target.threshold())
                    .toList();
            return winAwareGain(open, target -> target.before() + 1, 0);
        }
        double total = 0;
        for (Target target : targets) {
            int now = target.before();
            if (!objectives.contains(target.id()) || now >= target.threshold()) continue;
            total += now + 1 >= target.threshold()
                    ? VICTORY_POINT_VALUE * target.points()
                    : partialGain(target, now, now + 1);
        }
        return total;
    }

    public Set<String> brokenReady(Footprint after) {
        Set<String> broken = new HashSet<>();
        for (Target target : targets) {
            boolean complete = target.before() >= target.threshold();
            int then = progress(target.id(), after);
            if (complete && then != NOT_MODELLED && then < target.threshold()) broken.add(target.id());
        }
        return broken;
    }

    public double progressLoss(Footprint after) {
        double loss = 0;
        for (Target target : targets) {
            int now = target.before();
            int then = progress(target.id(), after);
            if (now >= target.threshold() || then == NOT_MODELLED || then >= now) continue;
            loss -= partialGain(target, now, then);
        }
        return loss;
    }

    public boolean oneStepShort() {
        return targets.stream().anyMatch(target -> target.before() == target.threshold() - 1);
    }

    private static double partialShare(Target target, int fewestStillNeeded) {
        boolean withinReach = target.points() >= STAGE_TWO_POINTS && fewestStillNeeded <= STEPS_WITHIN_REACH
                || TRAIT_SECRETS.containsKey(target.id()) && fewestStillNeeded <= TRAIT_STEPS_WITHIN_REACH
                || GROUND_SECRETS.contains(target.id()) && fewestStillNeeded <= GROUND_STEPS_WITHIN_REACH;
        return withinReach ? WITHIN_REACH_SHARE : PARTIAL_SHARE;
    }

    private static boolean isPresence(String id) {
        return switch (id) {
            case "intimidate",
                    "deep_space",
                    "vast_territories",
                    "outer_rim",
                    "control_borderlands",
                    "make_history",
                    "become_legend",
                    "raise_fleet",
                    "command_armada",
                    "ctr",
                    "lsc",
                    "te",
                    "btgk",
                    "ose",
                    "csl",
                    "fc",
                    "dfat",
                    "supremacy" -> true;
            default -> false;
        };
    }

    public int progress(String id, Footprint footprint) {
        return switch (id) {
            case "expand_borders", "subdue" -> countPlanets(footprint, this::outsideHomeSystems);
            case "corner", "unify_colonies" ->
                Math.max(
                        trait(footprint, "cultural"),
                        Math.max(trait(footprint, "hazardous"), trait(footprint, "industrial")));
            case "faa" -> trait(footprint, "cultural");
            case "mrm" -> trait(footprint, "hazardous");
            case "mp" -> trait(footprint, "industrial");
            case "research_outposts", "brain_trust" ->
                countAllPlanets(
                        footprint, planet -> !planet.getTechSpecialities().isEmpty());
            case "sai" -> countPlanets(footprint, Planet::isLegendary);
            case "lost_outposts", "ancient_monuments" -> countPlanets(footprint, Planet::hasAttachment);
            case "csl" -> overlap(footprint.shipSystems(), "otherDock");
            case "fc" -> neighbours(footprint);
            case "hrm" -> sumPlanets(footprint, true);
            case "eh" -> sumPlanets(footprint, false);
            case "intimidate" -> overlap(footprint.shipSystems(), "mecatolAdjacent");
            case "deep_space", "vast_territories" -> overlap(footprint.unitSystems(), "noPlanets");
            case "outer_rim", "control_borderlands" -> overlap(footprint.unitSystems(), "edge");
            case "make_history", "become_legend" -> overlap(footprint.unitSystems(), "history");
            case "raise_fleet", "command_armada" -> footprint.largestFleet();
            case "ctr" -> footprint.shipSystems().size();
            case "lsc" -> overlap(footprint.shipSystems(), "anomalyAdjacent");
            case "te" -> overlap(footprint.shipSystems(), "otherHomeAdjacent");
            case "syc" -> stakedClaims(footprint);
            case "distant_lands" -> distantLands(footprint);
            case "dfat" -> overlap(footprint.unitSystems(), "nexus");
            case "push_boundaries" -> pushBoundaries(footprint);
            case "btgk" ->
                Math.min(1, overlap(footprint.shipSystems(), "alpha"))
                        + Math.min(1, overlap(footprint.shipSystems(), "beta"));
            case "ose" -> occupiesMecatol(footprint);
            case "engineer_marvel" -> footprint.heavyShipCount();
            case "gamf" -> footprint.dreadnoughtCount();
            case "conquer" -> countPlanets(footprint, this::inAnotherHomeSystem);
            case "supremacy" -> overlap(footprint.heavyShipSystems(), "supremacy");
            case "mtm" -> countPlanets(footprint.mechPlanets(), planet -> true);
            case "otf" -> footprint.mostGroundForcesWithoutADock();
            default -> NOT_MODELLED;
        };
    }

    private int countPlanets(Footprint footprint, Predicate<Planet> wanted) {
        return countAllPlanets(footprint, planet -> countsForScoring(planet) && wanted.test(planet));
    }

    private int countPlanets(Set<String> names, Predicate<Planet> wanted) {
        int count = 0;
        for (String name : names) {
            Planet planet = game.getPlanetsInfo().get(name);
            if (planet != null && countsForScoring(planet) && wanted.test(planet)) count++;
        }
        return count;
    }

    private int countAllPlanets(Footprint footprint, Predicate<Planet> wanted) {
        int count = 0;
        for (String name : footprint.planets()) {
            Planet planet = game.getPlanetsInfo().get(name);
            if (planet != null && wanted.test(planet)) count++;
        }
        return count;
    }

    private boolean countsForScoring(Planet planet) {
        return countsForScoring(game, planet);
    }

    public static boolean countsForScoring(Game game, Planet planet) {
        return !planet.isSpaceStation(game) && !planet.isFake();
    }

    private int trait(Footprint footprint, String trait) {
        return countPlanets(footprint, planet -> planet.getPlanetTypes().contains(trait));
    }

    private int sumPlanets(Footprint footprint, boolean resources) {
        int total = 0;
        for (String name : footprint.planets()) {
            Planet planet = game.getPlanetsInfo().get(name);
            if (planet != null && countsForScoring(planet)) {
                total += resources ? planet.getResources() : planet.getInfluence();
            }
        }
        return total;
    }

    private Map<String, Set<String>> othersPlanetsBySystem;
    private Map<String, Set<String>> otherHomeSurroundings;
    private List<Integer> neighbourPlanetCounts;
    private Map<Player, Set<String>> otherPresence;
    private final Map<String, Set<String>> adjacency = new HashMap<>();

    private String tileOf(Planet planet) {
        Tile tile = game.getTileFromPlanet(planet.getName());
        return tile == null ? "" : tile.getPosition();
    }

    private Map<String, Set<String>> othersPlanetsBySystem() {
        if (othersPlanetsBySystem == null) {
            othersPlanetsBySystem = new HashMap<>();
            for (Player other : game.getRealPlayers()) {
                if (other == seat) continue;
                for (String planet : other.getPlanets()) {
                    Tile tile = game.getTileFromPlanet(planet);
                    if (tile != null) {
                        othersPlanetsBySystem
                                .computeIfAbsent(tile.getPosition(), position -> new HashSet<>())
                                .add(planet);
                    }
                }
            }
        }
        return othersPlanetsBySystem;
    }

    private int stakedClaims(Footprint footprint) {
        return countPlanets(footprint, planet -> {
            Set<String> theirs = othersPlanetsBySystem().getOrDefault(tileOf(planet), Set.of());
            return theirs.stream().anyMatch(other -> !footprint.planets().contains(other));
        });
    }

    private Map<String, Set<String>> otherHomeSurroundings() {
        if (otherHomeSurroundings == null) {
            otherHomeSurroundings = new HashMap<>();
            for (Player other : game.getRealAndEliminatedPlayers()) {
                Tile otherHome = other.getHomeSystemTile();
                if (other == seat || otherHome == null) continue;
                otherHomeSurroundings.put(
                        otherHome.getPosition(),
                        FoWHelper.getAdjacentTiles(game, otherHome.getPosition(), seat, false));
            }
        }
        return otherHomeSurroundings;
    }

    private int distantLands(Footprint footprint) {
        Set<String> homesTouched = new HashSet<>();
        Set<String> planets = new HashSet<>();
        for (Map.Entry<String, Set<String>> home : otherHomeSurroundings().entrySet()) {
            for (String name : footprint.planets()) {
                Planet planet = game.getPlanetsInfo().get(name);
                if (planet == null || !countsForScoring(planet)) continue;
                if (home.getValue().contains(tileOf(planet))) {
                    planets.add(name);
                    homesTouched.add(home.getKey());
                }
            }
        }
        return Math.min(planets.size(), homesTouched.size());
    }

    private int neighbours(Footprint footprint) {
        Set<String> reach = new HashSet<>();
        for (String position : occupied(footprint)) {
            reach.addAll(adjacency.computeIfAbsent(
                    position, from -> FoWHelper.getAdjacentTiles(game, from, seat, false, true)));
        }
        int count = 0;
        for (Set<String> theirs : otherPresence().values()) {
            if (!Collections.disjoint(reach, theirs)) count++;
        }
        return count;
    }

    private Set<String> occupied(Footprint footprint) {
        Set<String> positions = new HashSet<>(footprint.unitSystems());
        for (String name : footprint.planets()) {
            Tile tile = game.getTileFromPlanet(name);
            if (tile != null) positions.add(tile.getPosition());
        }
        return positions;
    }

    private Map<Player, Set<String>> otherPresence() {
        if (otherPresence == null) {
            otherPresence = new HashMap<>();
            for (Player other : game.getRealPlayers()) {
                if (other == seat) continue;
                Set<String> positions = new HashSet<>();
                for (Tile tile : game.getTileMap().values()) {
                    if (FoWHelper.playerIsInSystem(game, tile, other, true)) positions.add(tile.getPosition());
                }
                otherPresence.put(other, positions);
            }
        }
        return otherPresence;
    }

    private boolean hasAnotherPlayersDock(Tile tile) {
        for (Player other : game.getRealPlayers()) {
            if (other == seat) continue;
            for (UnitHolder holder : tile.getUnitHolders().values()) {
                if (BoardView.count(holder, other, UnitType.Spacedock) > 0) return true;
            }
        }
        return false;
    }

    private int pushBoundaries(Footprint footprint) {
        if (neighbourPlanetCounts == null) {
            neighbourPlanetCounts = new ArrayList<>();
            for (Player neighbour : seat.getNeighbouringPlayers(true)) {
                neighbourPlanetCounts.add(neighbour.getPlanets().size());
            }
        }
        int mine = countPlanets(footprint, planet -> true);
        return (int)
                neighbourPlanetCounts.stream().filter(count -> count < mine).count();
    }

    private boolean inAnotherHomeSystem(Planet planet) {
        Tile tile = game.getTileFromPlanet(planet.getName());
        return tile != null && tile.isHomeSystem(game) && tile != seat.getHomeSystemTile();
    }

    private boolean outsideHomeSystems(Planet planet) {
        Tile tile = game.getTileFromPlanet(planet.getName());
        return tile != null && !tile.isHomeSystem(game);
    }

    private int occupiesMecatol(Footprint footprint) {
        Tile mecatol = game.getMecatolTile();
        if (mecatol == null) return 0;
        boolean controls = footprint.planets().stream().anyMatch(game.mecatols()::contains);
        return controls ? footprint.shipsIn(mecatol.getPosition()) : 0;
    }

    private int overlap(Set<String> positions, String setName) {
        Set<String> wanted = systemSets.computeIfAbsent(setName, this::buildSet);
        int count = 0;
        for (String position : positions) {
            if (wanted.contains(position)) count++;
        }
        return count;
    }

    private Set<String> buildSet(String setName) {
        Set<String> positions = new HashSet<>();
        Tile home = seat.getHomeSystemTile();
        Tile mecatol = game.getMecatolTile();
        Set<String> mecatolAdjacent = mecatol == null
                ? Set.of()
                : FoWHelper.getAdjacentTilesAndNotThisTile(game, mecatol.getPosition(), seat, false);
        Set<String> otherHomeAdjacent = new HashSet<>();
        if ("otherHomeAdjacent".equals(setName)) {
            for (Player other : game.getRealPlayers()) {
                Tile otherHome = other.getHomeSystemTile();
                if (other == seat || otherHome == null) continue;
                Set<String> adjacent = FoWHelper.getAdjacentTiles(game, otherHome.getPosition(), seat, false, false);
                adjacent.remove(otherHome.getPosition());
                otherHomeAdjacent.addAll(adjacent);
            }
        }
        for (Tile tile : game.getTileMap().values()) {
            String position = tile.getPosition();
            boolean member =
                    switch (setName) {
                        case "mecatolAdjacent" -> mecatolAdjacent.contains(position);
                        case "noPlanets" -> tile.getPlanetUnitHolders().isEmpty();
                        case "edge" -> tile != home && tile.isEdgeOfBoard(game);
                        case "history" ->
                            tile.isMecatol(game) || tile.isAnomaly(game, seat) || ButtonHelper.isTileLegendary(tile);
                        case "anomalyAdjacent" ->
                            FoWHelper.getAdjacentTiles(game, position, seat, false, false).stream()
                                    .filter(next -> !next.equals(position))
                                    .map(game::getTileByPosition)
                                    .anyMatch(next -> next != null && next.isAnomaly(game, seat));
                        case "otherHomeAdjacent" -> otherHomeAdjacent.contains(position);
                        case "supremacy" -> tile.isMecatol(game) || (tile != home && tile.isHomeSystem(game));
                        case "nexus" ->
                            tile.getPlanetUnitHolders().stream()
                                    .anyMatch(planet -> planet.getName().contains("mallice"));
                        case "alpha" -> FoWHelper.doesTileHaveAlpha(game, position);
                        case "beta" -> FoWHelper.doesTileHaveBeta(game, position);
                        case "otherDock" -> hasAnotherPlayersDock(tile);
                        default -> false;
                    };
            if (member) positions.add(position);
        }
        return positions;
    }
}

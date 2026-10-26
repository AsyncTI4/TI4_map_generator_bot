package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.GameHorizon;
import ti4.ai.eval.MovementGraph;
import ti4.ai.scoring.ObjectiveValue;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.model.Mapper;
import ti4.model.UnitModel;

public final class GroundCampaign {

    public static final String OCCUPY_THE_FRINGE = FringeStack.OCCUPY_THE_FRINGE;
    public static final String MECHANIZE_THE_MILITARY = "mtm";
    public static final int MECHANIZED_PLANETS = 4;
    public static final int FRINGE_GROUND_FORCES = FringeStack.GROUND_FORCES;
    static final int ACTIVE_SLACK = 2;
    static final int PROTECTED_STACK = 5;
    private static final String CARRIER_II = "carrier2";
    private static final int HAULER_CAPACITY = 3;
    private static final int MAX_DISTANCE = 4;
    private static final int UNREACHABLE = 99;
    private static final int PIPELINE_TRIPS = 2;
    private static final int CLAIMS_PER_ROUND = 2;
    private static final int INFANTRY_PER_RESOURCE = 2;
    private static final int IDLE_MECHS = 2;
    private static final double SECRET_CARGO_SHARE = 0.3;
    private static final double COMPLETING_CARGO_SHARE = 0.5;
    private static final double FERRY_PULL = 0.4;
    private static final double COMPLETING_FERRY_PULL = 0.55;
    private static final double COMPLETION_SHARE = 0.8;
    private static final double MECH_PROGRESS_SHARE = 0.1;
    private static final double FRINGE_PROGRESS_SHARE = 0.6;
    private static final double FAR_STACK_SHARE = 0.8;
    private static final double MARGINAL_FEASIBILITY = 0.7;
    private static final double INFEASIBLE = 0.3;

    public record Eta(int rounds, int roundsLeft) {
        public int slack() {
            return roundsLeft - rounds;
        }

        public boolean done() {
            return rounds == 0;
        }

        public boolean feasible() {
            return rounds <= roundsLeft + 1;
        }

        public double feasibility() {
            if (rounds <= roundsLeft) return 1.0;
            return feasible() ? MARGINAL_FEASIBILITY : INFEASIBLE;
        }
    }

    private record Haul(int here, int near, int move) {}

    private final Game game;
    private final Player seat;
    private final boolean fringe;
    private final boolean mechanize;
    private Optional<Planet> anchor;
    private List<Planet> targets;
    private List<Tile> docks;
    private Integer roundsLeft;
    private Integer mechPlanets;
    private final Map<String, Eta> etas = new HashMap<>();
    private final Map<String, Map<String, Integer>> distances = new HashMap<>();

    private GroundCampaign(Game game, Player seat) {
        this.game = game;
        this.seat = seat;
        var unscored = seat.getSecretsUnscored();
        this.fringe = unscored.containsKey(OCCUPY_THE_FRINGE);
        this.mechanize = unscored.containsKey(MECHANIZE_THE_MILITARY);
    }

    public static GroundCampaign of(Game game, Player seat) {
        return new GroundCampaign(game, seat);
    }

    public boolean holdsFringe() {
        return fringe;
    }

    public boolean holdsMechanize() {
        return mechanize;
    }

    public boolean holdsEither() {
        return fringe || mechanize;
    }

    public Optional<Planet> anchor() {
        if (anchor == null) {
            anchor =
                    fringe ? FringeStack.best(game, seat).or(() -> FringeStack.fallback(game, seat)) : Optional.empty();
        }
        return anchor;
    }

    public List<Planet> mtmTargets() {
        if (targets == null) targets = mechanize ? chooseTargets() : List.of();
        return targets;
    }

    public List<Planet> bareTargets() {
        return mtmTargets().stream()
                .filter(planet -> BoardView.count(planet, seat, UnitType.Mech) == 0)
                .toList();
    }

    public int fringeDemand() {
        return anchor().map(planet -> Math.max(0, FRINGE_GROUND_FORCES - BoardView.groundForces(planet, seat)))
                .orElse(FRINGE_GROUND_FORCES);
    }

    public int mechDemand() {
        return Math.max(0, MECHANIZED_PLANETS - mechPlanets());
    }

    public Eta eta(String secret) {
        return eta(secret, false, false);
    }

    public Eta eta(String secret, boolean carrierTwo, boolean diodes) {
        return etas.computeIfAbsent(secret + "#" + carrierTwo + "#" + diodes, key -> {
            int rounds =
                    switch (secret) {
                        case OCCUPY_THE_FRINGE -> fringe ? fringeRounds(carrierTwo, diodes) : 0;
                        case MECHANIZE_THE_MILITARY -> mechanize ? mechanizeRounds(carrierTwo, diodes) : 0;
                        default -> 0;
                    };
            return new Eta(rounds, roundsLeft());
        });
    }

    public boolean fringeActive() {
        return fringe && active(eta(OCCUPY_THE_FRINGE));
    }

    public boolean mechActive() {
        return mechanize && active(eta(MECHANIZE_THE_MILITARY));
    }

    public boolean protects(Planet planet) {
        if (!fringe
                || anchor().filter(found -> found.getName().equals(planet.getName()))
                        .isEmpty()) return false;
        int stack = BoardView.groundForces(planet, seat);
        if (stack < FringeStack.PROTECTED_FROM) return false;
        return stack >= PROTECTED_STACK || fringeActive();
    }

    public int kept(Planet planet) {
        return protects(planet) ? FRINGE_GROUND_FORCES : 0;
    }

    public int infantryToBuild(Tile dock, int spare) {
        Optional<Planet> found = anchor();
        if (found.isEmpty()) return 0;
        int missing = fringeDemand() - spare;
        Eta eta = eta(OCCUPY_THE_FRINGE);
        if (missing <= 0 || eta.done() || !eta.feasible()) return 0;
        Tile anchorTile = game.getTileFromPlanet(found.get().getName());
        boolean sameSystem = anchorTile == dock;
        boolean diodes = diodesReady();
        Integer distance = distance(dock, anchorTile);
        if (!sameSystem && !diodes && (distance == null || distance > 2 * haulMove(dock))) return 0;
        Haul haul = haulNear(dock, false);
        int trip = Math.max(haul.near(), carrierCapacity(false));
        if (!active(eta)) return Math.min(missing, trip);
        if (sameSystem) return missing;
        return Math.min(missing, PIPELINE_TRIPS * (trip + (diodes ? TransitDiodesRules.MOVED_UNITS : 0)));
    }

    public boolean haulerWanted(Tile dock) {
        if (diodesReady()) return false;
        return fringeHaulerWanted(dock) || mechanizeHaulerWanted(dock);
    }

    public int mechsWanted(int idle) {
        return mechActive() ? MECHANIZED_PLANETS : idle;
    }

    public double cargoCredit(ObjectiveValue value, int mechCargo, int groundCargo, int pickedUp) {
        double total = 0;
        if (mechActive()) total += cargoShare(value, MECHANIZE_THE_MILITARY, MECHANIZED_PLANETS, mechCargo, pickedUp);
        if (fringeActive()) total += cargoShare(value, OCCUPY_THE_FRINGE, FRINGE_GROUND_FORCES, groundCargo, pickedUp);
        return total;
    }

    public double ferryPull(Planet planet, int arriving) {
        boolean toTheAnchor = anchor().filter(found -> found.getName().equals(planet.getName()))
                .isPresent();
        if (!toTheAnchor) return 0;
        if (arriving >= fringeDemand()) return COMPLETING_FERRY_PULL * arriving;
        return fringeActive() ? FERRY_PULL * arriving : 0;
    }

    public double fringeDropValue(Planet planet, int infantry) {
        if (!fringe || BoardView.count(planet, seat, UnitType.Spacedock) > 0) return 0;
        if (anchor().filter(found -> found.getName().equals(planet.getName())).isEmpty()) return 0;
        int stacked = BoardView.groundForces(planet, seat);
        if (stacked + infantry >= FRINGE_GROUND_FORCES) return COMPLETION_SHARE * ObjectiveValue.VICTORY_POINT_VALUE;
        double nearness = FringeStack.nearHome(game, seat, planet.getName()) ? 1.0 : FAR_STACK_SHARE;
        double perForce = FRINGE_PROGRESS_SHARE * ObjectiveValue.VICTORY_POINT_VALUE / (FRINGE_GROUND_FORCES - stacked);
        return nearness * perForce * infantry;
    }

    public double mechDropValue(Planet planet) {
        if (!mechanize || BoardView.count(planet, seat, UnitType.Mech) > 0) return 0;
        if (mtmTargets().stream().noneMatch(target -> target.getName().equals(planet.getName()))) return 0;
        double share = mechPlanets() + 1 >= MECHANIZED_PLANETS ? COMPLETION_SHARE : MECH_PROGRESS_SHARE;
        return share * ObjectiveValue.VICTORY_POINT_VALUE;
    }

    private boolean active(Eta eta) {
        return !eta.done() && eta.feasible() && eta.slack() <= ACTIVE_SLACK;
    }

    private boolean diodesReady() {
        return TransitDiodesRules.readyDiodes(seat).isPresent();
    }

    private int roundsLeft() {
        if (roundsLeft == null) roundsLeft = GameHorizon.roundsLeft(game);
        return roundsLeft;
    }

    private int mechPlanets() {
        if (mechPlanets == null) {
            mechPlanets = (int) ownedPlanets().stream()
                    .filter(planet -> BoardView.count(planet, seat, UnitType.Mech) > 0)
                    .count();
        }
        return mechPlanets;
    }

    private List<Planet> ownedPlanets() {
        return seat.getPlanets().stream()
                .map(game::getUnitHolderFromPlanet)
                .filter(Objects::nonNull)
                .filter(planet -> !planet.isSpaceStation(game))
                .toList();
    }

    private List<Tile> docks() {
        if (docks == null) {
            docks = ownedPlanets().stream()
                    .filter(planet -> BoardView.count(planet, seat, UnitType.Spacedock) > 0)
                    .map(planet -> game.getTileFromPlanet(planet.getName()))
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        }
        return docks;
    }

    private List<Planet> chooseTargets() {
        List<Planet> owned = ownedPlanets();
        List<Planet> chosen = new ArrayList<>(owned.stream()
                .filter(planet -> BoardView.count(planet, seat, UnitType.Mech) > 0)
                .limit(MECHANIZED_PLANETS)
                .toList());
        owned.stream()
                .filter(planet -> !chosen.contains(planet))
                .sorted(targetOrder())
                .limit(Math.max(0, MECHANIZED_PLANETS - chosen.size()))
                .forEach(chosen::add);
        return List.copyOf(chosen);
    }

    private Comparator<Planet> targetOrder() {
        Map<String, Long> ownedInSystem = new HashMap<>();
        for (Planet planet : ownedPlanets()) {
            Tile tile = game.getTileFromPlanet(planet.getName());
            if (tile != null) ownedInSystem.merge(tile.getPosition(), 1L, Long::sum);
        }
        return Comparator.comparing((Planet planet) -> exposed(game.getTileFromPlanet(planet.getName())))
                .thenComparingInt(this::legsFromDocks)
                .thenComparing(planet -> BoardView.count(planet, seat, UnitType.Spacedock) == 0)
                .thenComparing(planet -> -ownedInSystem.getOrDefault(positionOf(planet), 1L))
                .thenComparing(Planet::getName);
    }

    private String positionOf(Planet planet) {
        Tile tile = game.getTileFromPlanet(planet.getName());
        return tile == null ? "" : tile.getPosition();
    }

    private boolean exposed(Tile tile) {
        if (tile == null) return true;
        if (BoardView.hasEnemyShips(game, seat, tile)) return true;
        return FoWHelper.getAdjacentTiles(game, tile.getPosition(), seat, false).stream()
                .map(game::getTileByPosition)
                .filter(Objects::nonNull)
                .anyMatch(near -> BoardView.hasEnemyShips(game, seat, near));
    }

    private int legsFromDocks(Planet planet) {
        Tile tile = game.getTileFromPlanet(planet.getName());
        int best = UNREACHABLE;
        for (Tile dock : docks()) {
            Integer distance = distance(dock, tile);
            if (distance != null) best = Math.min(best, distance);
        }
        return best;
    }

    private Integer distance(Tile from, Tile to) {
        if (from == null || to == null) return null;
        return distances
                .computeIfAbsent(
                        from.getPosition(), position -> MovementGraph.reach(game, seat, position, MAX_DISTANCE))
                .get(to.getPosition());
    }

    private int fringeRounds(boolean carrierTwo, boolean diodes) {
        Optional<Planet> found = anchor();
        int stack = found.map(planet -> BoardView.groundForces(planet, seat)).orElse(0);
        int need = FRINGE_GROUND_FORCES - stack;
        if (need <= 0) return 0;
        Tile anchorTile =
                found.map(planet -> game.getTileFromPlanet(planet.getName())).orElse(null);
        int claim = found.isPresent() ? 0 : 1;
        int best = UNREACHABLE;
        for (Tile dock : docks()) {
            Integer distance = anchorTile == null ? Integer.valueOf(1) : distance(dock, anchorTile);
            best = Math.min(best, dockRounds(dock, anchorTile, distance, need, carrierTwo, diodes));
        }
        return best >= UNREACHABLE ? UNREACHABLE : best + claim;
    }

    private int dockRounds(Tile dock, Tile anchorTile, Integer distance, int need, boolean carrierTwo, boolean diodes) {
        int stock = stockAt(dock);
        int supplyNeed = Math.max(0, need - stock);
        int perRound = unitsPerRound(dock, INFANTRY_PER_RESOURCE, 1);
        int supply = supplyNeed == 0 ? 0 : perRound == 0 ? UNREACHABLE : ceilDiv(supplyNeed, perRound);
        int hauled = distance == null ? UNREACHABLE : haulRounds(dock, distance, need, carrierTwo);
        int jumped = diodes || diodesReady() ? ceilDiv(need, TransitDiodesRules.MOVED_UNITS) : UNREACHABLE;
        int transport = Math.min(hauled, jumped);
        if (supply >= UNREACHABLE || transport >= UNREACHABLE) return UNREACHABLE;
        return Math.max(supply, transport) + (supply > 0 ? 1 : 0);
    }

    private int haulRounds(Tile dock, int distance, int need, boolean carrierTwo) {
        Haul haul = haulNear(dock, carrierTwo);
        boolean mustBuild = (distance == 0 ? haul.here() : haul.near()) < HAULER_CAPACITY;
        int capacity = mustBuild ? carrierCapacity(carrierTwo) : distance == 0 ? haul.here() : haul.near();
        int move = mustBuild ? carrierMove(carrierTwo) : Math.max(1, haul.move());
        int trips = ceilDiv(need, Math.max(1, capacity));
        int build = mustBuild ? 1 : 0;
        if (distance == 0) return trips + build;
        int legs = ceilDiv(distance, move);
        return legs + 2 * legs * (trips - 1) + build;
    }

    private int stockAt(Tile dock) {
        String anchorName = anchor().map(Planet::getName).orElse("");
        int stock = BoardView.groundForces(BoardView.space(dock), seat);
        for (Planet planet : dock.getPlanetUnitHolders()) {
            if (!seat.getPlanets().contains(planet.getName())
                    || planet.getName().equals(anchorName)) continue;
            stock += Math.max(0, BoardView.groundForces(planet, seat) - 1);
        }
        return stock;
    }

    private int incomePerRound() {
        return ownedPlanets().stream()
                .mapToInt(planet -> BoardView.planetResources(game, planet.getName()))
                .sum();
    }

    private int unitsPerRound(Tile dock, int perResource, int unitCost) {
        int limit = Helper.getProductionValue(seat, game, dock, false);
        int affordable = incomePerRound() * perResource / Math.max(1, unitCost);
        return Math.min(limit, affordable);
    }

    private int mechanizeRounds(boolean carrierTwo, boolean diodes) {
        UnitModel mech = seat.getUnitByType(UnitType.Mech);
        if (mech == null || game.isBaseGameMode()) return UNREACHABLE;
        int missing = mechDemand();
        if (missing == 0) return 0;
        int owned = ownedPlanets().size();
        int claims = owned >= MECHANIZED_PLANETS ? 0 : ceilDiv(MECHANIZED_PLANETS - owned, CLAIMS_PER_ROUND);
        int cap = seat.getUnitCap(mech.getAsyncId());
        int limit = cap <= 0 ? MECHANIZED_PLANETS : Math.min(MECHANIZED_PLANETS, cap);
        int onBoard = ButtonHelper.getNumberOfUnitsOnTheBoard(game, seat, mech.getAsyncId());
        int toBuild = Math.max(0, limit - onBoard);
        if (docks().isEmpty()) return UNREACHABLE;
        int perRound = docks().stream()
                .mapToInt(dock -> unitsPerRound(dock, 1, Math.max(1, (int) mech.getCost())))
                .max()
                .orElse(0);
        int supply = toBuild == 0 ? 0 : perRound == 0 ? UNREACHABLE : ceilDiv(toBuild, perRound);
        int placements = placements(carrierTwo, diodes, bareTargets(), missing);
        if (supply >= UNREACHABLE || placements >= UNREACHABLE) return UNREACHABLE;
        return Math.max(supply, placements) + (supply > 0 ? 1 : 0) + claims;
    }

    private int placements(boolean carrierTwo, boolean diodes, List<Planet> bare, int missing) {
        List<Planet> offDock = bare.stream()
                .filter(planet -> BoardView.count(planet, seat, UnitType.Spacedock) == 0)
                .toList();
        int moves = Math.max(0, missing - (bare.size() - offDock.size()));
        if (moves == 0) return 0;
        long systems = offDock.stream()
                .map(planet -> game.getTileFromPlanet(planet.getName()))
                .distinct()
                .count();
        int jumped = diodes || diodesReady() ? ceilDiv(moves, TransitDiodesRules.MOVED_UNITS) : UNREACHABLE;
        boolean hauler =
                docks().stream().anyMatch(dock -> haulNear(dock, carrierTwo).near() >= HAULER_CAPACITY);
        int hauled = (int) Math.max(1, systems) + (hauler ? 0 : 1);
        return Math.min(hauled, jumped);
    }

    private boolean fringeHaulerWanted(Tile dock) {
        Optional<Planet> found = anchor();
        if (found.isEmpty()) return false;
        Eta eta = eta(OCCUPY_THE_FRINGE);
        if (eta.done() || !eta.feasible()) return false;
        Tile anchorTile = game.getTileFromPlanet(found.get().getName());
        Integer distance = distance(dock, anchorTile);
        boolean relevant = anchorTile == dock || distance != null && distance <= haulMove(dock);
        return relevant && haulNear(dock, false).near() < HAULER_CAPACITY;
    }

    private boolean mechanizeHaulerWanted(Tile dock) {
        if (!mechanize || !docks().contains(dock)) return false;
        Eta eta = eta(MECHANIZE_THE_MILITARY);
        if (eta.done() || !eta.feasible()) return false;
        int stuck = mechActive() ? 1 : 2;
        return spareMechsAt(dock) >= stuck && haulNear(dock, false).here() < HAULER_CAPACITY;
    }

    private int spareMechsAt(Tile dock) {
        int spare = BoardView.count(BoardView.space(dock), seat, UnitType.Mech);
        for (Planet planet : dock.getPlanetUnitHolders()) {
            spare += Math.max(0, BoardView.count(planet, seat, UnitType.Mech) - 1);
        }
        return spare;
    }

    private int haulMove(Tile dock) {
        Haul haul = haulNear(dock, false);
        return haul.near() >= HAULER_CAPACITY ? Math.max(1, haul.move()) : carrierMove(false);
    }

    private Haul haulNear(Tile dock, boolean carrierTwo) {
        Set<String> around = new HashSet<>(FoWHelper.getAdjacentTiles(game, dock.getPosition(), seat, false));
        around.add(dock.getPosition());
        int near = 0;
        int move = 0;
        for (String position : around) {
            Tile tile = game.getTileByPosition(position);
            if (tile == null) continue;
            near += capacityIn(tile, carrierTwo);
            move = Math.max(move, moveIn(tile, carrierTwo));
        }
        return new Haul(capacityIn(dock, carrierTwo), near, move);
    }

    private int capacityIn(Tile tile, boolean carrierTwo) {
        int capacity = 0;
        for (UnitType type : BoardView.MOVING_SHIPS) {
            int ships = BoardView.count(BoardView.space(tile), seat, type);
            if (ships > 0) capacity += ships * shipCapacity(type, carrierTwo);
        }
        return capacity;
    }

    private int moveIn(Tile tile, boolean carrierTwo) {
        int move = 0;
        for (UnitType type : BoardView.MOVING_SHIPS) {
            if (BoardView.count(BoardView.space(tile), seat, type) > 0 && shipCapacity(type, carrierTwo) > 0) {
                move = Math.max(move, shipMove(type, carrierTwo));
            }
        }
        return move;
    }

    private int shipCapacity(UnitType type, boolean carrierTwo) {
        int capacity = BoardView.capacity(seat, type);
        return carrierTwo && type == UnitType.Carrier ? Math.max(capacity, carrierCapacity(true)) : capacity;
    }

    private int shipMove(UnitType type, boolean carrierTwo) {
        int move = BoardView.moveValue(seat, type);
        return carrierTwo && type == UnitType.Carrier ? Math.max(move, carrierMove(true)) : move;
    }

    private int carrierCapacity(boolean carrierTwo) {
        UnitModel upgraded = Mapper.getUnit(CARRIER_II);
        int own = BoardView.capacity(seat, UnitType.Carrier);
        return carrierTwo && upgraded != null ? Math.max(own, upgraded.getCapacityValue()) : own;
    }

    private int carrierMove(boolean carrierTwo) {
        UnitModel upgraded = Mapper.getUnit(CARRIER_II);
        int own = Math.max(1, BoardView.moveValue(seat, UnitType.Carrier));
        return carrierTwo && upgraded != null ? Math.max(own, upgraded.getMoveValue()) : own;
    }

    private static int ceilDiv(int numerator, int denominator) {
        return (numerator + denominator - 1) / denominator;
    }

    private static double cargoShare(ObjectiveValue value, String secret, int threshold, int cargo, int pickedUp) {
        int progress = value.progress(secret, value.before());
        int toGo = threshold - progress;
        if (progress == ObjectiveValue.NOT_MODELLED || toGo <= 0) return 0;
        int carried = Math.min(toGo, Math.min(cargo, pickedUp));
        if (carried == toGo) return ObjectiveValue.VICTORY_POINT_VALUE * COMPLETING_CARGO_SHARE;
        return ObjectiveValue.VICTORY_POINT_VALUE * SECRET_CARGO_SHARE * carried / toGo;
    }
}

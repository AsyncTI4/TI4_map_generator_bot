package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.MovementGraph;
import ti4.ai.scoring.Footprint;
import ti4.ai.scoring.ObjectivePolicy;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.scoring.SpendCost;
import ti4.ai.scoring.Wallet;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
public class ProductionPlanner {

    private static final int WANTED_CARRIERS = 2;
    private static final int WANTED_DREADNOUGHTS = 3;
    private static final int INFANTRY_PER_BUILD = 4;
    private static final String MARVEL_OBJECTIVE = "engineer_marvel";
    private static final String RAISE_A_FLEET = "raise_fleet";
    private static final String COMMAND_AN_ARMADA = "command_armada";
    private static final String SUPREMACY = "supremacy";
    private static final String MIGHTY_FLEET = "gamf";
    private static final int MIGHTY_FLEET_DREADNOUGHTS = 5;
    private static final int ARMADA_SHIPS = 8;
    private static final int RAISE_A_FLEET_SHIPS = 5;
    private static final int WANTED_PAWNS = 4;
    private static final int PAWNS_PER_BUILD = 2;

    public record BuildOrder(String unitId, String location, UnitType type, int units, double cost) {
        public String handlerId() {
            return "place_" + unitId + "_" + location;
        }
    }

    public record BuildPlan(List<BuildOrder> orders) {
        public double totalCost() {
            return orders.stream().mapToDouble(BuildOrder::cost).sum();
        }

        public boolean isEmpty() {
            return orders.isEmpty();
        }

        public int units(UnitType type) {
            return orders.stream()
                    .filter(order -> order.type() == type)
                    .mapToInt(BuildOrder::units)
                    .sum();
        }

        public double cost(UnitType type) {
            return orders.stream()
                    .filter(order -> order.type() == type)
                    .mapToDouble(BuildOrder::cost)
                    .sum();
        }

        public String encode() {
            return String.join(
                    ";",
                    orders.stream()
                            .map(order -> String.join(
                                    "~",
                                    order.unitId(),
                                    order.location(),
                                    order.type().name(),
                                    String.valueOf(order.units()),
                                    String.valueOf(order.cost())))
                            .toList());
        }

        public static Optional<BuildPlan> decode(String encoded) {
            try {
                List<BuildOrder> orders = new ArrayList<>();
                for (String item : encoded.split(";")) {
                    if (item.isBlank()) continue;
                    String[] fields = item.split("~");
                    orders.add(new BuildOrder(
                            fields[0],
                            fields[1],
                            UnitType.valueOf(fields[2]),
                            Integer.parseInt(fields[3]),
                            Double.parseDouble(fields[4])));
                }
                return Optional.of(new BuildPlan(orders));
            } catch (RuntimeException e) {
                return Optional.empty();
            }
        }
    }

    public static BuildPlan plan(Game game, Player seat, Tile tile) {
        return plan(game, seat, tile, 0);
    }

    public static BuildPlan plan(Game game, Player seat, Tile tile, int extraResources) {
        if (BoardView.hasEnemyShips(game, seat, tile)) return new BuildPlan(List.of());
        Budget budget = new Budget(
                Helper.getProductionValue(seat, game, tile, false),
                spendableResources(game, seat) + extraResources + (seat.hasTech("st") ? 1 : 0),
                seat.getFleetCC() - BoardView.nonFighterShips(BoardView.space(tile), seat));
        List<BuildOrder> orders = new ArrayList<>();
        String position = tile.getPosition();
        if (wantsFlagship(game, seat, tile))
            addShip(game, seat, orders, budget, UnitType.Flagship, "flagship", position);
        int fleetShips = fleetShipsWanted(game, seat, tile, budget.units);
        for (int built = 0; built < fleetShips; built++) {
            if (!addShip(game, seat, orders, budget, UnitType.Destroyer, "destroyer", position)) break;
        }
        if (onBoard(game, seat, "cv") < WANTED_CARRIERS) {
            addShip(game, seat, orders, budget, UnitType.Carrier, "carrier", position);
        }
        Optional<String> infantryPlanet = infantryPlanet(game, seat, tile);
        if (infantryPlanet.isPresent()) {
            for (int built = 0; built < INFANTRY_PER_BUILD; built += 2) {
                if (!addInfantryPair(game, seat, orders, budget, infantryPlanet.get())) break;
            }
        }
        if (fleetShips == 0 && new ObjectiveValue(game, seat).caresAboutPresence()) {
            for (int built = 0; built < PAWNS_PER_BUILD && onBoard(game, seat, "dd") + built < WANTED_PAWNS; built++) {
                if (!addShip(game, seat, orders, budget, UnitType.Destroyer, "destroyer", position)) break;
            }
        }
        int wantedDreadnoughts =
                seat.getSecretsUnscored().containsKey(MIGHTY_FLEET) ? MIGHTY_FLEET_DREADNOUGHTS : WANTED_DREADNOUGHTS;
        for (int built = 0; onBoard(game, seat, "dn") + built < wantedDreadnoughts; built++) {
            if (!addShip(game, seat, orders, budget, UnitType.Dreadnought, "dreadnought", position)) break;
        }
        return new BuildPlan(orders);
    }

    private static int fleetShipsWanted(Game game, Player seat, Tile tile, int production) {
        int largest = Footprint.of(game, seat).largestFleet();
        int wanted = 0;
        if (wantsUnscored(game, seat, COMMAND_AN_ARMADA) && largest < ARMADA_SHIPS) wanted = ARMADA_SHIPS;
        if (wantsUnscored(game, seat, RAISE_A_FLEET) && largest < RAISE_A_FLEET_SHIPS) wanted = RAISE_A_FLEET_SHIPS;
        wanted = Math.min(wanted, seat.getFleetCC());
        if (wanted < RAISE_A_FLEET_SHIPS || wanted <= largest) return 0;
        int here = BoardView.nonFighterShips(BoardView.space(tile), seat);
        int missing = wanted - here;
        if (here < largest && missing > production) return 0;
        return Math.max(0, missing);
    }

    private static boolean wantsUnscored(Game game, Player seat, String objective) {
        return game.getRevealedPublicObjectives().containsKey(objective)
                && !ObjectivePolicy.hasScored(game, seat, objective);
    }

    private static boolean wantsFlagship(Game game, Player seat, Tile dock) {
        boolean scores = wantsUnscored(game, seat, MARVEL_OBJECTIVE)
                || (wantsUnscored(game, seat, SUPREMACY) && flagshipReachesSupremacy(game, seat, dock));
        return scores && onBoard(game, seat, "fs") == 0 && onBoard(game, seat, "ws") == 0;
    }

    private static boolean flagshipReachesSupremacy(Game game, Player seat, Tile dock) {
        int move = BoardView.moveValue(seat, UnitType.Flagship);
        for (String position :
                MovementGraph.reach(game, seat, dock.getPosition(), move).keySet()) {
            Tile tile = game.getTileByPosition(position);
            if (tile != null
                    && (tile.isMecatol(game) || (tile.isHomeSystem(game) && tile != seat.getHomeSystemTile()))) {
                return true;
            }
        }
        return false;
    }

    static int spendableResources(Game game, Player seat) {
        int available = BoardView.availableResources(game, seat);
        SpendCost reserve = ScoringReserve.of(game, seat);
        if (reserve.isNone()) return available;
        Wallet wallet = Wallet.of(game, seat);
        for (int spend = available; spend > 0; spend--) {
            if (ScoringReserve.planAfterReserve(wallet, reserve, SpendCost.resources(spend))
                    .isPresent()) return spend;
        }
        return 0;
    }

    private static final class Budget {
        private int units;
        private double resources;
        private int fleetRoom;

        Budget(int units, double resources, int fleetRoom) {
            this.units = units;
            this.resources = resources;
            this.fleetRoom = fleetRoom;
        }

        boolean allows(int unitCount, double cost, boolean usesFleet) {
            return units >= unitCount && resources >= cost && (!usesFleet || fleetRoom > 0);
        }

        void spend(int unitCount, double cost, boolean usesFleet) {
            units -= unitCount;
            resources -= cost;
            if (usesFleet) fleetRoom--;
        }
    }

    private static boolean addShip(
            Game game,
            Player seat,
            List<BuildOrder> orders,
            Budget budget,
            UnitType type,
            String unitId,
            String position) {
        UnitModel model = seat.getUnitByType(type);
        int planned = orders.stream()
                .filter(order -> order.type() == type)
                .mapToInt(BuildOrder::units)
                .sum();
        if (model == null || !underCap(game, seat, model.getAsyncId(), planned + 1)) return false;
        double cost = model.getCost();
        if (!budget.allows(1, cost, true)) return false;
        budget.spend(1, cost, true);
        orders.add(new BuildOrder(unitId, position, type, 1, cost));
        return true;
    }

    private static boolean addInfantryPair(
            Game game, Player seat, List<BuildOrder> orders, Budget budget, String planet) {
        UnitModel model = seat.getUnitByType(UnitType.Infantry);
        if (model == null || !underCap(game, seat, "gf", 2)) return false;
        double cost = model.getCost() * 2;
        if (!budget.allows(2, cost, false)) return false;
        budget.spend(2, cost, false);
        orders.add(new BuildOrder("2gf", planet, UnitType.Infantry, 2, cost));
        return true;
    }

    private static Optional<String> infantryPlanet(Game game, Player seat, Tile tile) {
        return tile.getPlanetUnitHolders().stream()
                .filter(planet -> seat.getPlanets().contains(planet.getName()))
                .filter(planet -> BoardView.count(planet, seat, UnitType.Spacedock) > 0)
                .map(Planet::getName)
                .findFirst();
    }

    private static boolean underCap(Game game, Player seat, String asyncId, int adding) {
        int cap = seat.getUnitCap(asyncId);
        return cap <= 0 || onBoard(game, seat, asyncId) + adding <= cap;
    }

    private static int onBoard(Game game, Player seat, String asyncId) {
        return ButtonHelper.getNumberOfUnitsOnTheBoard(game, seat, asyncId);
    }
}

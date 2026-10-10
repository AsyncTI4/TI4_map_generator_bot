package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.FlagshipRating;
import ti4.ai.eval.MovementGraph;
import ti4.ai.scoring.Footprint;
import ti4.ai.scoring.ObjectivePolicy;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.scoring.SpendCost;
import ti4.ai.scoring.Wallet;
import ti4.ai.strategy.StrategyCardRules;
import ti4.ai.strategy.TokenPurchase;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
public class ProductionPlanner {

    static final int MIN_UNITS = 4;
    private static final int WANTED_CARRIERS = 2;
    private static final int WANTED_DREADNOUGHTS = 3;
    private static final int WANTED_UPGRADED_LIGHT_SHIPS = 4;
    private static final int WANTED_CRUISERS = 2;
    private static final int WANTED_MECHS = 2;
    private static final int MECHANIZE_MECHS = 4;
    private static final int WANTED_SPARE_INFANTRY = 4;
    private static final int INFANTRY_ROOM_PER_CARRIER = 2;
    private static final int ENABLING_SHIPS = 2;
    private static final int MIGHTY_FLEET_DREADNOUGHTS = 5;
    private static final int ARMADA_SHIPS = 8;
    private static final int RAISE_A_FLEET_SHIPS = 5;
    private static final int WANTED_PAWNS = 4;
    private static final int PAWNS_PER_BUILD = 2;
    private static final double CHEAPEST_UNIT_COST = 0.5;
    private static final int INFANTRY_PER_RESOURCE = 2;
    private static final double CARRIER_VALUE = 2.5;
    private static final double NEEDED_INFANTRY_VALUE = 0.5;
    private static final double SCORING_VALUE_PER_RESOURCE = 0.5;
    private static final double ENABLER_VALUE = 0.3 * ObjectiveValue.VICTORY_POINT_VALUE;
    private static final double FILLER_VALUE_PER_RESOURCE = 0.5;
    private static final double EARLY_FILLER_DISCOUNT = 0.3;
    private static final double UNUPGRADED_LIGHT_SHIP_SHARE = 0.6;
    private static final String MARVEL_OBJECTIVE = "engineer_marvel";
    private static final String RAISE_A_FLEET = "raise_fleet";
    private static final String COMMAND_AN_ARMADA = "command_armada";
    private static final String SUPREMACY = "supremacy";
    private static final String MIGHTY_FLEET = "gamf";
    private static final String UNVEIL_FLAGSHIP = "uf";
    private static final String MAKE_AN_EXAMPLE = "mew";
    private static final String FIGHT_WITH_PRECISION = "fwp";
    private static final String MECHANIZE_THE_MILITARY = "mtm";
    private static final String AI_DEVELOPMENT = "aida";
    private static final int AIDA_MIN_DISCOUNT = 2;
    private static final List<UnitType> HEAVY_SHIPS = List.of(UnitType.Flagship, UnitType.Warsun);

    public record BuildOrder(
            String unitId, String location, UnitType type, int units, double cost, double value, boolean scoring) {
        public String handlerId() {
            return "place_" + unitId + "_" + location;
        }

        public String singleHandlerId() {
            return "place_" + type.name().toLowerCase() + "_" + location;
        }
    }

    public record BuildPlan(List<BuildOrder> orders) {
        public double totalCost() {
            return orders.stream().mapToDouble(BuildOrder::cost).sum();
        }

        public double value() {
            return orders.stream().mapToDouble(BuildOrder::value).sum();
        }

        public boolean scoring() {
            return orders.stream().anyMatch(BuildOrder::scoring);
        }

        public boolean isEmpty() {
            return orders.isEmpty();
        }

        public int units() {
            return orders.stream().mapToInt(BuildOrder::units).sum();
        }

        public int units(UnitType type) {
            return orders.stream()
                    .filter(order -> order.type() == type)
                    .mapToInt(BuildOrder::units)
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
                                    String.valueOf(order.cost()),
                                    String.valueOf(order.value()),
                                    String.valueOf(order.scoring())))
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
                            Double.parseDouble(fields[4]),
                            Double.parseDouble(fields[5]),
                            Boolean.parseBoolean(fields[6])));
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
        Builder builder = new Builder(
                game,
                seat,
                tile,
                new Budget(
                        Helper.getProductionValue(seat, game, tile, false),
                        spendableResources(game, seat)
                                + extraResources
                                + (seat.hasTech("st") ? 1 : 0)
                                + aidaDiscount(seat),
                        seat.getFleetCC() - BoardView.nonFighterShips(BoardView.space(tile), seat)));
        planScoringUnits(builder);
        planCoreUnits(builder);
        planSurplusShips(builder);
        fillProduction(builder);
        return new BuildPlan(builder.orders);
    }

    static int aidaDiscount(Player seat) {
        if (!seat.hasTechReady(AI_DEVELOPMENT)) return 0;
        int upgrades = ButtonHelper.getNumberOfUnitUpgrades(seat);
        boolean worthTheExhaust = upgrades >= AIDA_MIN_DISCOUNT || StrategyCardRules.cannotResearch(seat);
        return worthTheExhaust ? upgrades : 0;
    }

    public static BuildPlan planIntegrated(Game game, Player seat, Tile tile, String planet) {
        int resources = Math.min(spendableResources(game, seat), BoardView.planetResources(game, planet));
        if (resources <= 0) return new BuildPlan(List.of());
        Builder builder =
                new Builder(game, seat, tile, new Budget(resources * INFANTRY_PER_RESOURCE, resources, 0), planet);
        int wantedMechs =
                seat.getSecretsUnscored().containsKey(MECHANIZE_THE_MILITARY) ? MECHANIZE_MECHS : WANTED_MECHS;
        while (builder.count(UnitType.Mech) < wantedMechs) {
            if (!builder.ground(UnitType.Mech, 1, builder.fillerValue(UnitType.Mech))) break;
        }
        builder.infantry(builder.budget.units, builder.fillerValue(UnitType.Infantry));
        return new BuildPlan(builder.orders);
    }

    public static SpendCost reserve(Game game, Player seat) {
        SpendCost scoring = ScoringReserve.of(game, seat);
        return scoring.plus(TokenPurchase.influenceToKeep(game, seat, scoring));
    }

    static int spendableResources(Game game, Player seat) {
        int available = BoardView.availableResources(game, seat);
        SpendCost reserve = reserve(game, seat);
        if (reserve.isNone()) return available;
        Wallet wallet = Wallet.of(game, seat);
        for (int spend = available; spend > 0; spend--) {
            if (ScoringReserve.planAfterReserve(wallet, reserve, SpendCost.resources(spend))
                    .isPresent()) return spend;
        }
        return 0;
    }

    private static void planScoringUnits(Builder builder) {
        Player seat = builder.seat;
        for (UnitType type : heavyShipsWanted(builder)) {
            double enabler = type == UnitType.Flagship && wantsFlagshipForSecret(builder) ? ENABLER_VALUE : 0;
            if (builder.ship(type, builder.scoringValue(type) + enabler, true)) break;
        }
        int fleetShips = fleetShipsWanted(builder.game, seat, builder.tile, builder.budget.units);
        builder.shipsUpTo(
                UnitType.Destroyer,
                builder.count(UnitType.Destroyer) + fleetShips,
                builder.scoringValue(UnitType.Destroyer),
                true);
        if (seat.getSecretsUnscored().containsKey(MIGHTY_FLEET)) {
            builder.shipsUpTo(
                    UnitType.Dreadnought, MIGHTY_FLEET_DREADNOUGHTS, builder.scoringValue(UnitType.Dreadnought), true);
        }
        if (seat.getSecretsUnscored().containsKey(MAKE_AN_EXAMPLE)) {
            builder.enablingShips(model -> model.getBombardDieCount(seat) > 0);
        }
        if (seat.getSecretsUnscored().containsKey(FIGHT_WITH_PRECISION)) {
            builder.enablingShips(model -> model.getAfbDieCount(seat) > 0);
        }
        if (fleetShips == 0 && new ObjectiveValue(builder.game, seat).caresAboutPresence()) {
            int pawns = Math.min(WANTED_PAWNS, builder.count(UnitType.Destroyer) + PAWNS_PER_BUILD);
            builder.shipsUpTo(UnitType.Destroyer, pawns, builder.scoringValue(UnitType.Destroyer), true);
        }
    }

    private static void planCoreUnits(Builder builder) {
        builder.shipsUpTo(UnitType.Carrier, WANTED_CARRIERS, CARRIER_VALUE, false);
        int spare = TacticalPlanner.movableGroundForces(builder.game, builder.seat, builder.tile);
        builder.infantry(WANTED_SPARE_INFANTRY - spare, NEEDED_INFANTRY_VALUE);
        int wantedMechs =
                builder.seat.getSecretsUnscored().containsKey(MECHANIZE_THE_MILITARY) ? MECHANIZE_MECHS : WANTED_MECHS;
        while (builder.count(UnitType.Mech) < wantedMechs && builder.leavesRoomToFill(builder.cost(UnitType.Mech))) {
            if (!builder.ground(UnitType.Mech, 1, builder.fillerValue(UnitType.Mech))) return;
        }
    }

    private static void planSurplusShips(Builder builder) {
        Player seat = builder.seat;
        builder.surplusShipsUpTo(UnitType.Flagship, 1, 1, FlagshipRating.spareResourcesNeeded(seat));
        builder.surplusShipsUpTo(UnitType.Warsun, 1, 1);
        builder.surplusShipsUpTo(UnitType.Dreadnought, WANTED_DREADNOUGHTS, 1);
        for (UnitType light : List.of(UnitType.Cruiser, UnitType.Destroyer)) {
            if (isUpgraded(seat, light)) builder.surplusShipsUpTo(light, WANTED_UPGRADED_LIGHT_SHIPS, 1);
        }
        if (!isUpgraded(seat, UnitType.Cruiser)) {
            builder.surplusShipsUpTo(UnitType.Cruiser, WANTED_CRUISERS, UNUPGRADED_LIGHT_SHIP_SHARE);
        }
    }

    private static void fillProduction(Builder builder) {
        builder.fighters(builder.fillerValue(UnitType.Fighter));
        builder.infantry(builder.budget.units, builder.fillerValue(UnitType.Infantry));
    }

    private static boolean isUpgraded(Player seat, UnitType type) {
        return BoardView.model(seat, type).map(UnitModel::getIsUpgrade).orElse(false);
    }

    private static double fillerValuePerResource(Game game) {
        return FILLER_VALUE_PER_RESOURCE - EARLY_FILLER_DISCOUNT * TacticalPlanner.earlyGameWeight(game);
    }

    private static List<UnitType> heavyShipsWanted(Builder builder) {
        Game game = builder.game;
        Player seat = builder.seat;
        if (wantsFlagshipForSecret(builder)) return List.of(UnitType.Flagship);
        if (builder.onBoard(UnitType.Flagship) + builder.onBoard(UnitType.Warsun) > 0) return List.of();
        List<UnitType> byCost = HEAVY_SHIPS.stream()
                .filter(type -> seat.getUnitByType(type) != null)
                .sorted(Comparator.comparingDouble(builder::cost))
                .toList();
        if (wantsUnscored(game, seat, MARVEL_OBJECTIVE)) return byCost;
        if (!wantsUnscored(game, seat, SUPREMACY)) return List.of();
        return byCost.stream()
                .filter(type -> reachesSupremacy(game, seat, builder.tile, type))
                .toList();
    }

    private static boolean wantsFlagshipForSecret(Builder builder) {
        return builder.seat.getSecretsUnscored().containsKey(UNVEIL_FLAGSHIP)
                && builder.onBoard(UnitType.Flagship) == 0;
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

    private static boolean reachesSupremacy(Game game, Player seat, Tile dock, UnitType type) {
        int move = BoardView.moveValueWithGravityDrive(seat, type);
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

    private static final class Builder {
        private final Game game;
        private final Player seat;
        private final Tile tile;
        private final Budget budget;
        private final List<BuildOrder> orders = new ArrayList<>();
        private final String groundPlanet;

        Builder(Game game, Player seat, Tile tile, Budget budget) {
            this(game, seat, tile, budget, null);
        }

        Builder(Game game, Player seat, Tile tile, Budget budget, @Nullable String groundPlanet) {
            this.game = game;
            this.seat = seat;
            this.tile = tile;
            this.budget = budget;
            this.groundPlanet = groundPlanet;
        }

        boolean ship(UnitType type, double value, boolean scoring) {
            UnitModel model = seat.getUnitByType(type);
            if (model == null || !underCap(model.getAsyncId(), planned(type) + 1)) return false;
            double cost = model.getCost();
            if (!budget.allows(1, cost, true)) return false;
            budget.spend(1, cost, true);
            orders.add(new BuildOrder(type.name().toLowerCase(), tile.getPosition(), type, 1, cost, value, scoring));
            return true;
        }

        void shipsUpTo(UnitType type, int target, double value, boolean scoring) {
            while (count(type) < target) {
                if (!ship(type, value, scoring)) return;
            }
        }

        void surplusShipsUpTo(UnitType type, int target, double share) {
            surplusShipsUpTo(type, target, share, 0);
        }

        void surplusShipsUpTo(UnitType type, int target, double share, double spareNeeded) {
            double value = share * fillerValue(type);
            while (count(type) < target && leavesRoomToFill(cost(type) + spareNeeded)) {
                if (!ship(type, value, false)) return;
            }
        }

        void infantry(int wanted, double valueEach) {
            for (int left = wanted; left > 0; ) {
                int added = pairOrSingle(left, count -> ground(UnitType.Infantry, count, valueEach));
                if (added == 0) return;
                left -= added;
            }
        }

        void fighters(double valueEach) {
            for (int room = fighterRoom(); room > 0; room = fighterRoom()) {
                if (pairOrSingle(room, count -> addFighters(count, valueEach)) == 0) return;
            }
        }

        private static int pairOrSingle(int wanted, IntPredicate add) {
            for (int count = Math.min(2, wanted); count > 0; count--) {
                if (add.test(count)) return count;
            }
            return 0;
        }

        void enablingShips(Predicate<UnitModel> ability) {
            List<UnitType> types = BoardView.MOVING_SHIPS.stream()
                    .filter(type -> BoardView.model(seat, type).filter(ability).isPresent())
                    .sorted(Comparator.comparingDouble(this::cost))
                    .toList();
            int have = types.stream().mapToInt(this::count).sum();
            for (UnitType type : types) {
                double value = scoringValue(type) + ENABLER_VALUE / ENABLING_SHIPS;
                while (have < ENABLING_SHIPS && ship(type, value, true)) have++;
            }
        }

        boolean ground(UnitType type, int count, double valueEach) {
            Optional<String> planet = groundPlanet != null ? Optional.of(groundPlanet) : dockPlanet();
            UnitModel model = seat.getUnitByType(type);
            if (count <= 0 || planet.isEmpty() || model == null) return false;
            if (type == UnitType.Mech && game.isBaseGameMode()) return false;
            if (!underCap(model.getAsyncId(), planned(type) + count)) return false;
            double cost = model.getCost() * count;
            if (!budget.allows(count, cost, false)) return false;
            budget.spend(count, cost, false);
            String unitId = count == 2 ? "2" + type.getValue() : type.name().toLowerCase();
            orders.add(new BuildOrder(unitId, planet.get(), type, count, cost, valueEach * count, false));
            return true;
        }

        private boolean addFighters(int count, double valueEach) {
            UnitModel model = seat.getUnitByType(UnitType.Fighter);
            if (count <= 0 || model == null || !underCap(model.getAsyncId(), planned(UnitType.Fighter) + count)) {
                return false;
            }
            double cost = model.getCost() * count;
            if (!budget.allows(count, cost, false)) return false;
            budget.spend(count, cost, false);
            String unitId = count == 2 ? "2" + UnitType.Fighter.getValue() : "fighter";
            orders.add(new BuildOrder(
                    unitId, tile.getPosition(), UnitType.Fighter, count, cost, valueEach * count, false));
            return true;
        }

        int fighterRoom() {
            UnitHolder space = BoardView.space(tile);
            int shipCapacity = 0;
            int carriers = 0;
            for (UnitType type : BoardView.MOVING_SHIPS) {
                int ships = BoardView.count(space, seat, type) + planned(type);
                shipCapacity += ships * BoardView.capacity(seat, type);
                if (type == UnitType.Carrier) carriers = ships;
            }
            int keptForGroundForces =
                    Math.max(INFANTRY_ROOM_PER_CARRIER * carriers, BoardView.groundForces(space, seat));
            int allowance = dockPlanet().isPresent() ? BoardView.DOCK_FIGHTER_ALLOWANCE : 0;
            int fighters = BoardView.count(space, seat, UnitType.Fighter) + planned(UnitType.Fighter);
            return Math.max(0, allowance + Math.max(0, shipCapacity - keptForGroundForces) - fighters);
        }

        boolean leavesRoomToFill(double cost) {
            return budget.resources - cost >= CHEAPEST_UNIT_COST * (budget.units - 1);
        }

        double scoringValue(UnitType type) {
            return SCORING_VALUE_PER_RESOURCE * cost(type);
        }

        double fillerValue(UnitType type) {
            return fillerValuePerResource(game) * cost(type);
        }

        double cost(UnitType type) {
            return BoardView.model(seat, type).map(UnitModel::getCost).orElse(0f);
        }

        int count(UnitType type) {
            return onBoard(type) + planned(type);
        }

        int onBoard(UnitType type) {
            String asyncId =
                    BoardView.model(seat, type).map(UnitModel::getAsyncId).orElse(type.getValue());
            return ButtonHelper.getNumberOfUnitsOnTheBoard(game, seat, asyncId);
        }

        int planned(UnitType type) {
            return orders.stream()
                    .filter(order -> order.type() == type)
                    .mapToInt(BuildOrder::units)
                    .sum();
        }

        private Optional<String> dockPlanet() {
            return tile.getPlanetUnitHolders().stream()
                    .filter(planet -> seat.getPlanets().contains(planet.getName()))
                    .filter(planet -> BoardView.count(planet, seat, UnitType.Spacedock) > 0)
                    .map(Planet::getName)
                    .findFirst();
        }

        private boolean underCap(String asyncId, int adding) {
            int cap = seat.getUnitCap(asyncId);
            return cap <= 0 || ButtonHelper.getNumberOfUnitsOnTheBoard(game, seat, asyncId) + adding <= cap;
        }
    }
}

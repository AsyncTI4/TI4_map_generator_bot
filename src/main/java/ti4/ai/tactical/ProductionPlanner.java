package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.FlagshipRating;
import ti4.ai.eval.FleetLimit;
import ti4.ai.eval.FractureCampaign;
import ti4.ai.eval.FracturePrize;
import ti4.ai.eval.MovementGraph;
import ti4.ai.eval.Threats;
import ti4.ai.scoring.Footprint;
import ti4.ai.scoring.ObjectivePolicy;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.scoring.ScoringReserve;
import ti4.ai.scoring.SpendCost;
import ti4.ai.scoring.Wallet;
import ti4.ai.strategy.OpeningHold;
import ti4.ai.strategy.StrategyCardRules;
import ti4.ai.strategy.TokenPurchase;
import ti4.ai.trace.Thinking;
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
    private static final int OPENING_ROUND = 1;
    private static final int DEFENSE_FIELD = 7;
    private static final int BONUS_FIELD = 8;
    private static final String BELLUM_GLORIOSUM = "solbt";
    public static final String BELLUM_SOURCE = "solBtBuild";
    private static final int WANTED_CARRIERS = 2;
    private static final int WANTED_DREADNOUGHTS = 3;
    private static final double FRONTLINE_BONUS = 0.5;
    private static final int FRONTLINE_RANGE = 2;
    private static final int FRACTURE_RANGE = 2;
    private static final int FRACTURE_DREADNOUGHTS = 5;
    private static final double FRACTURE_FLEET_VALUE_PER_RESOURCE = 0.75;
    private static final double MAX_HURT_WEIGHT = 1.5;
    private static final double OUTLYING_SHARE = 0.5;
    private static final int WANTED_UPGRADED_LIGHT_SHIPS = 4;
    private static final int WANTED_CRUISERS = 2;
    private static final int WANTED_MECHS = 2;
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
    private static final int RESEARCH_SAVINGS = 4;
    private static final double CARRIER_VALUE = 2.5;
    private static final int HEAVY_LIFT_CAPACITY = 8;
    private static final double SUSTAINING_CARRIER_BONUS = 0.5;
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
    private static final int MAX_DEFENSE_BUNDLES = 6;
    private static final int MAX_DEFENSE_BUNDLE_SIZE = 3;
    private static final int MAX_DEFENSE_MECHS = 2;
    private static final double DEFENSE_MIN_VALUE_PER_RESOURCE = 0.5;
    private static final double DEFENSE_FOCUS_BIAS = 1.2;
    private static final List<UnitType> DEFENSE_SHIPS =
            List.of(UnitType.Destroyer, UnitType.Cruiser, UnitType.Dreadnought);
    private static final String AI_DEVELOPMENT = "aida";
    private static final int AIDA_MIN_DISCOUNT = 2;
    private static final List<UnitType> HEAVY_SHIPS = List.of(UnitType.Flagship, UnitType.Warsun);
    private static final String HACAN_HERO = "hacanhero";
    private static final int FREE_BUILD_RESOURCES = 1000;
    private static final double HERO_MIN_SAVING = 10;
    private static final String AUTO_FACTORIES = "hacanbt";
    private static final int AUTO_FACTORIES_SHIPS = 3;
    private static final double FLEET_TOKEN_VALUE = 1.0;
    private static final String FLEET_REGULATIONS = "regulations";
    private static final int REGULATED_FLEET = 4;

    public record BuildOrder(
            String unitId,
            String location,
            UnitType type,
            int units,
            double cost,
            double value,
            boolean scoring,
            boolean defense,
            boolean bonus) {
        public BuildOrder(
                String unitId,
                String location,
                UnitType type,
                int units,
                double cost,
                double value,
                boolean scoring,
                boolean defense) {
            this(unitId, location, type, units, cost, value, scoring, defense, false);
        }

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

        public boolean defense() {
            return orders.stream().anyMatch(BuildOrder::defense);
        }

        public double nonDefenseValue() {
            return orders.stream()
                    .filter(order -> !order.defense())
                    .mapToDouble(BuildOrder::value)
                    .sum();
        }

        public boolean isEmpty() {
            return orders.isEmpty();
        }

        public BuildPlan withoutBonus() {
            return new BuildPlan(orders.stream().filter(order -> !order.bonus()).toList());
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
                                    String.valueOf(order.scoring()),
                                    String.valueOf(order.defense()),
                                    String.valueOf(order.bonus())))
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
                            Boolean.parseBoolean(fields[6]),
                            fields.length > DEFENSE_FIELD && Boolean.parseBoolean(fields[DEFENSE_FIELD]),
                            fields.length > BONUS_FIELD && Boolean.parseBoolean(fields[BONUS_FIELD])));
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
        return plan(game, seat, tile, extraResources, new DefenseOutlook(game, seat));
    }

    public static BuildPlan plan(Game game, Player seat, Tile tile, int extraResources, DefenseOutlook defense) {
        return plan(game, seat, tile, extraResources, defense, 0);
    }

    public static BuildPlan plan(
            Game game, Player seat, Tile tile, int extraResources, DefenseOutlook defense, int overSupplyCarriers) {
        try (Thinking.Span ignored = Thinking.enter("production.plan")) {
            return buildPlan(game, seat, tile, extraResources, defense, overSupplyCarriers);
        }
    }

    private static BuildPlan buildPlan(
            Game game, Player seat, Tile tile, int extraResources, DefenseOutlook defense, int overSupplyCarriers) {
        if (BoardView.hasEnemyShips(game, seat, tile)) return new BuildPlan(List.of());
        Budget budget = new Budget(
                Helper.getProductionValue(seat, game, tile, false),
                spendableResources(game, seat) + extraResources + (seat.hasTech("st") ? 1 : 0) + aidaDiscount(seat),
                FleetLimit.ships(seat) - BoardView.nonFighterShips(BoardView.space(tile), seat),
                seat.hasUnlockedBreakthrough(BELLUM_GLORIOSUM));
        budget.overSupply = Math.max(0, overSupplyCarriers);
        Builder builder = new Builder(game, seat, tile, budget, defense);
        planScoringUnits(builder);
        planDefensiveUnits(builder);
        planCoreUnits(builder);
        planFractureFleet(builder);
        planSurplusShips(builder);
        planFractureFighters(builder);
        planAutoFactories(builder);
        fillProduction(builder);
        return new BuildPlan(builder.orders);
    }

    public static BuildPlan bellumGloriosum(
            Game game, Player seat, Tile tile, int limit, @Nullable String groundPlanet, DefenseOutlook defense) {
        if (BoardView.hasEnemyShips(game, seat, tile)) return new BuildPlan(List.of());
        Budget budget = Budget.bonusOnly(
                limit, spendableResources(game, seat) + (seat.hasTech("st") ? 1 : 0) + aidaDiscount(seat));
        Builder builder = new Builder(game, seat, tile, budget, groundPlanet, defense);
        planDefensiveUnits(builder);
        int spare = TacticalPlanner.movableGroundForces(game, seat, tile, defense);
        builder.infantry(WANTED_SPARE_INFANTRY - spare, NEEDED_INFANTRY_VALUE);
        builder.fighters(builder.fillerValue(UnitType.Fighter));
        return new BuildPlan(builder.orders);
    }

    private static int wantedCarriers(Player seat) {
        return BoardView.capacity(seat, UnitType.Carrier) >= HEAVY_LIFT_CAPACITY ? 1 : WANTED_CARRIERS;
    }

    private static double carrierValue(Player seat) {
        boolean sustains = BoardView.model(seat, UnitType.Carrier)
                .map(UnitModel::getSustainDamage)
                .orElse(false);
        return CARRIER_VALUE + (sustains ? SUSTAINING_CARRIER_BONUS : 0);
    }

    public static Optional<BuildPlan> freeBuild(Game game, Player seat, Tile tile, DefenseOutlook defense) {
        if (!seat.hasLeaderUnlocked(HACAN_HERO) || BoardView.hasEnemyShips(game, seat, tile)) return Optional.empty();
        BuildPlan free = plan(game, seat, tile, FREE_BUILD_RESOURCES, defense);
        int capacity = Helper.getProductionValue(seat, game, tile, false);
        if (free.totalCost() < HERO_MIN_SAVING || free.units() < capacity) return Optional.empty();
        return Optional.of(free);
    }

    private static void planAutoFactories(Builder builder) {
        Player seat = builder.seat;
        if (!seat.hasUnlockedBreakthrough(AUTO_FACTORIES)) return;
        if (ButtonHelper.isLawInPlay(builder.game, FLEET_REGULATIONS)
                && seat.getEffectiveFleetCC() >= REGULATED_FLEET) {
            return;
        }
        int ships = builder.plannedNonFighterShips();
        int missing = AUTO_FACTORIES_SHIPS - ships;
        if (ships == 0 || missing <= 0) return;
        double cost = builder.cost(UnitType.Destroyer);
        if (seat.getUnitByType(UnitType.Destroyer) == null || !builder.budget.allowsShips(missing, cost * missing)) {
            return;
        }
        double value = builder.fillerValue(UnitType.Destroyer) + FLEET_TOKEN_VALUE / missing;
        for (int added = 0; added < missing; added++) {
            if (!builder.ship(UnitType.Destroyer, value, false)) return;
        }
    }

    public static int aidaDiscount(Player seat) {
        if (!seat.hasTechReady(AI_DEVELOPMENT)) return 0;
        int upgrades = ButtonHelper.getNumberOfUnitUpgrades(seat);
        boolean worthTheExhaust = upgrades >= AIDA_MIN_DISCOUNT || StrategyCardRules.cannotResearch(seat);
        return worthTheExhaust ? upgrades : 0;
    }

    public static BuildPlan planIntegrated(Game game, Player seat, Tile tile, String planet) {
        int spendable = spendableResources(game, seat);
        int resources = Math.min(spendable, BoardView.planetResources(game, planet));
        if (resources <= 0) return new BuildPlan(List.of());
        int fleetRoom = FleetLimit.ships(seat) - BoardView.nonFighterShips(BoardView.space(tile), seat);
        Builder builder = new Builder(
                game, seat, tile, new Budget(resources * INFANTRY_PER_RESOURCE, resources, fleetRoom), planet);
        planScoringUnits(builder);
        if (builder.campaign().holdsMechanize()) {
            while (builder.count(UnitType.Mech) < builder.campaign().mechsWanted(WANTED_MECHS)) {
                if (!builder.ground(UnitType.Mech, 1, builder.scoringValue(UnitType.Mech))) break;
            }
        }
        if (Threats.anyEnemyCanReach(game, seat, tile) || spendable - otherUses(game, seat) >= resources) {
            builder.fighters(builder.fillerValue(UnitType.Fighter));
            builder.infantry(builder.budget.units, builder.fillerValue(UnitType.Infantry));
        }
        return new BuildPlan(builder.orders);
    }

    public static List<BuildOrder> oneShipOptions(Game game, Player seat, Tile tile, int resources) {
        if (resources <= 0 || BoardView.hasEnemyShips(game, seat, tile)) return List.of();
        Builder planned = oneShipBuilder(game, seat, tile, resources);
        planScoringUnits(planned);
        planned.shipsUpTo(UnitType.Carrier, wantedCarriers(seat), carrierValue(seat), false);
        planSurplusShips(planned);
        Builder cheapest = oneShipBuilder(game, seat, tile, resources);
        cheapest.ship(UnitType.Destroyer, cheapest.fillerValue(UnitType.Destroyer), false);
        List<BuildOrder> options = new ArrayList<>(planned.orders);
        cheapest.orders.stream()
                .filter(order -> options.stream().noneMatch(other -> other.type() == order.type()))
                .forEach(options::add);
        return options;
    }

    private static Builder oneShipBuilder(Game game, Player seat, Tile tile, int resources) {
        int fleetRoom = FleetLimit.ships(seat) - BoardView.nonFighterShips(BoardView.space(tile), seat);
        return new Builder(game, seat, tile, new Budget(1, resources, fleetRoom));
    }

    private static int otherUses(Game game, Player seat) {
        return ButtonHelper.checkHighestProductionSystem(seat, game) + RESEARCH_SAVINGS;
    }

    public static SpendCost reserve(Game game, Player seat) {
        SpendCost scoring = ScoringReserve.of(game, seat);
        SpendCost kept = scoring.plus(TokenPurchase.influenceToKeep(game, seat, scoring));
        SpendCost withDrive = kept.plus(OpeningHold.gravityDriveReserve(game, seat));
        return Wallet.of(game, seat).canPay(withDrive) ? withDrive : kept;
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
        if (builder.campaign().haulerWanted(builder.tile)) {
            builder.ship(UnitType.Carrier, builder.scoringValue(UnitType.Carrier) + ENABLER_VALUE, true);
        }
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
        if (PrecisionRules.held(seat)) builder.barrageShips();
        if (builder.campaign().holdsFringe()) planFringeInfantry(builder);
        planTraitRaidInfantry(builder);
        if (fleetShips == 0 && new ObjectiveValue(builder.game, seat).caresAboutPresence()) {
            int pawns = Math.min(WANTED_PAWNS, builder.count(UnitType.Destroyer) + PAWNS_PER_BUILD);
            builder.shipsUpTo(UnitType.Destroyer, pawns, builder.scoringValue(UnitType.Destroyer), true);
        }
    }

    private static void planTraitRaidInfantry(Builder builder) {
        int spare = TacticalPlanner.movableGroundForces(builder.game, builder.seat, builder.tile, builder.defense);
        int wanted = TraitRaid.infantryToBuild(builder.game, builder.seat, builder.tile, spare, builder.defense);
        builder.infantry(wanted, builder.scoringValue(UnitType.Infantry));
    }

    private static void planFringeInfantry(Builder builder) {
        int spare = TacticalPlanner.movableGroundForces(builder.game, builder.seat, builder.tile, builder.defense);
        builder.infantry(
                builder.campaign().infantryToBuild(builder.tile, spare), builder.scoringValue(UnitType.Infantry));
    }

    private record DefenseBundle(UnitType type, int count) {
        boolean ground() {
            return BoardView.GROUND_FORCES.contains(type);
        }
    }

    private static void planDefensiveUnits(Builder builder) {
        DefenseOutlook defense = builder.defense;
        Tile tile = builder.tile;
        if (!defense.isGuarded(tile)) return;
        for (int added = 0; added < MAX_DEFENSE_BUNDLES; added++) {
            Defenders planned = builder.plannedDefenders();
            double now = defense.cost(tile, planned);
            if (now <= 0) return;
            DefenseBundle best = null;
            double bestGain = 0;
            double bestPerResource = 0;
            for (DefenseBundle bundle : builder.defenseBundles()) {
                double gain = now - defense.cost(tile, builder.grown(planned, bundle));
                double bundleCost = Math.max(CHEAPEST_UNIT_COST, builder.cost(bundle.type()) * bundle.count());
                double perResource = gain / bundleCost * builder.defenseBias(bundle);
                if (perResource > bestPerResource) {
                    best = bundle;
                    bestGain = gain;
                    bestPerResource = perResource;
                }
            }
            if (best == null || bestPerResource < DEFENSE_MIN_VALUE_PER_RESOURCE) return;
            builder.addDefense(best, bestGain);
        }
    }

    public static OptionalDouble cheapestShipToScrap(Player seat, Tile tile) {
        double carrier =
                BoardView.model(seat, UnitType.Carrier).map(UnitModel::getCost).orElse(Float.MAX_VALUE);
        return BoardView.ships(BoardView.space(tile), seat).keySet().stream()
                .filter(type -> type != UnitType.Fighter)
                .map(type -> BoardView.model(seat, type))
                .flatMap(Optional::stream)
                .mapToDouble(UnitModel::getCost)
                .filter(cost -> cost < carrier)
                .min();
    }

    private static void planCoreUnits(Builder builder) {
        builder.shipsUpTo(UnitType.Carrier, wantedCarriers(builder.seat), carrierValue(builder.seat), false);
        builder.overSuppliedCarriersUpTo(wantedCarriers(builder.seat));
        int spare = TacticalPlanner.movableGroundForces(builder.game, builder.seat, builder.tile, builder.defense);
        builder.infantry(WANTED_SPARE_INFANTRY - spare, NEEDED_INFANTRY_VALUE);
        int wantedMechs = builder.campaign().mechsWanted(WANTED_MECHS);
        if (firesFromAdjacentSystems(builder.seat) && frontlineInterest(builder) > 0) wantedMechs++;
        while (builder.count(UnitType.Mech) < wantedMechs && builder.leavesRoomToFill(builder.cost(UnitType.Mech))) {
            if (!builder.ground(UnitType.Mech, 1, builder.fillerValue(UnitType.Mech))) return;
        }
    }

    private static boolean firesFromAdjacentSystems(Player seat) {
        UnitModel mech = seat.getUnitByType(UnitType.Mech);
        return mech != null && mech.getSpaceCannonDieCount(seat) > 0 && mech.getDeepSpaceCannon(seat);
    }

    private static void planFractureFleet(Builder builder) {
        if (!FractureCampaign.isDock(builder.game, builder.seat, builder.tile)) return;
        fractureShipsUpTo(builder, UnitType.Carrier, FractureCampaign.CARRIERS);
        fractureShipsUpTo(builder, UnitType.Dreadnought, FractureCampaign.DREADNOUGHTS);
        int spare = TacticalPlanner.movableGroundForces(builder.game, builder.seat, builder.tile, builder.defense)
                + builder.planned(UnitType.Infantry);
        builder.infantry(
                FractureCampaign.INFANTRY - spare, fractureValuePerResource(builder) * builder.cost(UnitType.Infantry));
    }

    private static void planFractureFighters(Builder builder) {
        if (!FractureCampaign.isDock(builder.game, builder.seat, builder.tile)) return;
        builder.fighters(fractureValuePerResource(builder) * builder.cost(UnitType.Fighter));
    }

    private static double fractureValuePerResource(Builder builder) {
        return FRACTURE_FLEET_VALUE_PER_RESOURCE * FractureCampaign.routeShare(builder.game, builder.seat);
    }

    private static void fractureShipsUpTo(Builder builder, UnitType type, int wanted) {
        double value = fractureValuePerResource(builder) * builder.cost(type);
        while (BoardView.count(BoardView.space(builder.tile), builder.seat, type) + builder.planned(type) < wanted) {
            if (!builder.ship(type, value, false)) return;
        }
    }

    private static void planSurplusShips(Builder builder) {
        Player seat = builder.seat;
        double fracture = fractureInterest(builder);
        double frontline = 1 + FRONTLINE_BONUS * Math.max(frontlineInterest(builder), fracture);
        int dreadnoughts = fracture > 0 ? FRACTURE_DREADNOUGHTS : WANTED_DREADNOUGHTS;
        builder.surplusShipsUpTo(UnitType.Flagship, 1, frontline, FlagshipRating.spareResourcesNeeded(seat));
        builder.surplusShipsUpTo(UnitType.Warsun, 1, frontline);
        builder.surplusShipsUpTo(UnitType.Dreadnought, dreadnoughts, frontline);
        for (UnitType light : List.of(UnitType.Cruiser, UnitType.Destroyer)) {
            if (isUpgraded(seat, light)) builder.surplusShipsUpTo(light, WANTED_UPGRADED_LIGHT_SHIPS, frontline);
        }
        if (!isUpgraded(seat, UnitType.Cruiser)) {
            builder.surplusShipsUpTo(UnitType.Cruiser, WANTED_CRUISERS, UNUPGRADED_LIGHT_SHIP_SHARE * frontline);
        }
    }

    private static double fractureInterest(Builder builder) {
        Game game = builder.game;
        double interest = FracturePrize.interest(game, builder.seat);
        if (interest == 0) return 0;
        boolean nearAnIngress =
                MovementGraph.reach(game, builder.seat, builder.tile.getPosition(), FRACTURE_RANGE).keySet().stream()
                        .map(game::getTileByPosition)
                        .anyMatch(tile -> tile != null && FracturePrize.hasIngress(tile));
        return nearAnIngress ? interest : 0;
    }

    private static double frontlineInterest(Builder builder) {
        Game game = builder.game;
        Standings standings = builder.defense.standings();
        double interest = 0;
        for (String position : MovementGraph.reach(game, builder.seat, builder.tile.getPosition(), FRONTLINE_RANGE)
                .keySet()) {
            Tile tile = game.getTileByPosition(position);
            if (tile == null) continue;
            for (Player rival : game.getRealPlayers()) {
                if (rival == builder.seat || !hasPresence(rival, tile)) continue;
                boolean decisive = tile == rival.getHomeSystemTile() || holdsMecatol(game, tile);
                double weight = standings.hurtWeight(rival) / MAX_HURT_WEIGHT * (decisive ? 1 : OUTLYING_SHARE)
                        + standings.winThreat(rival);
                interest = Math.max(interest, Math.clamp(weight, 0.0, 1.0));
            }
        }
        return interest;
    }

    private static boolean hasPresence(Player rival, Tile tile) {
        return tile.containsPlayersUnits(rival)
                || tile.getPlanetUnitHolders().stream()
                        .anyMatch(planet -> rival.getPlanets().contains(planet.getName()));
    }

    private static boolean holdsMecatol(Game game, Tile tile) {
        return tile.getPlanetUnitHolders().stream()
                .anyMatch(planet -> game.mecatols().contains(planet.getName()));
    }

    private static void fillProduction(Builder builder) {
        if (builder.game.getRound() <= OPENING_ROUND) {
            builder.infantry(builder.budget.slots(), builder.fillerValue(UnitType.Infantry));
            builder.fighters(builder.fillerValue(UnitType.Fighter));
            return;
        }
        builder.fighters(builder.fillerValue(UnitType.Fighter));
        builder.infantry(builder.budget.slots(), builder.fillerValue(UnitType.Infantry));
    }

    private static boolean isUpgraded(Player seat, UnitType type) {
        return BoardView.model(seat, type).map(UnitModel::getIsUpgrade).orElse(false);
    }

    public static double fillerValuePerResource(Game game) {
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
        wanted = Math.min(wanted, FleetLimit.ships(seat));
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
        private int overSupply;
        private int bonus;
        private final boolean bonusEnabled;

        Budget(int units, double resources, int fleetRoom) {
            this(units, resources, fleetRoom, false);
        }

        Budget(int units, double resources, int fleetRoom, boolean bonusEnabled) {
            this.units = units;
            this.resources = resources;
            this.fleetRoom = fleetRoom;
            this.bonusEnabled = bonusEnabled;
        }

        static Budget bonusOnly(int limit, double resources) {
            Budget budget = new Budget(0, resources, 0, true);
            budget.bonus = limit;
            return budget;
        }

        int slots() {
            return units + bonus;
        }

        void gainBonus(int capacity) {
            if (bonusEnabled) bonus += capacity;
        }

        boolean allowsGroundOrFighters(int unitCount, double cost) {
            return (units >= unitCount || bonus >= unitCount) && resources >= cost;
        }

        boolean spendGroundOrFighters(int unitCount, double cost) {
            resources -= cost;
            if (bonus >= unitCount) {
                bonus -= unitCount;
                return true;
            }
            units -= unitCount;
            return false;
        }

        boolean allows(int unitCount, double cost, boolean usesFleet) {
            return units >= unitCount && resources >= cost && (!usesFleet || fleetRoom > 0);
        }

        boolean allowsShips(int unitCount, double cost) {
            return units >= unitCount && resources >= cost && fleetRoom >= unitCount;
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
        private final DefenseOutlook defense;

        Builder(Game game, Player seat, Tile tile, Budget budget) {
            this(game, seat, tile, budget, null, new DefenseOutlook(game, seat));
        }

        Builder(Game game, Player seat, Tile tile, Budget budget, DefenseOutlook defense) {
            this(game, seat, tile, budget, null, defense);
        }

        Builder(Game game, Player seat, Tile tile, Budget budget, @Nullable String groundPlanet) {
            this(game, seat, tile, budget, groundPlanet, new DefenseOutlook(game, seat));
        }

        Builder(
                Game game,
                Player seat,
                Tile tile,
                Budget budget,
                @Nullable String groundPlanet,
                DefenseOutlook defense) {
            this.game = game;
            this.seat = seat;
            this.tile = tile;
            this.budget = budget;
            this.groundPlanet = groundPlanet;
            this.defense = defense;
        }

        GroundCampaign campaign() {
            return defense.campaign();
        }

        boolean ship(UnitType type, double value, boolean scoring) {
            return ship(type, value, scoring, false);
        }

        boolean ship(UnitType type, double value, boolean scoring, boolean forDefense) {
            UnitModel model = seat.getUnitByType(type);
            if (model == null || !underCap(model.getAsyncId(), planned(type) + 1)) return false;
            double cost = model.getCost();
            if (!budget.allows(1, cost, true)) return false;
            budget.spend(1, cost, true);
            budget.gainBonus(model.getCapacityValue());
            orders.add(new BuildOrder(
                    type.name().toLowerCase(), tile.getPosition(), type, 1, cost, value, scoring, forDefense));
            return true;
        }

        void shipsUpTo(UnitType type, int target, double value, boolean scoring) {
            while (count(type) < target) {
                if (!ship(type, value, scoring)) return;
            }
        }

        void overSuppliedCarriersUpTo(int target) {
            OptionalDouble scrap = cheapestShipToScrap(seat, tile);
            if (scrap.isEmpty()) return;
            while (budget.overSupply > 0 && budget.fleetRoom <= 0 && count(UnitType.Carrier) < target) {
                budget.overSupply--;
                budget.fleetRoom++;
                if (!ship(UnitType.Carrier, carrierValue(seat) - scrap.getAsDouble(), false)) {
                    budget.fleetRoom--;
                    return;
                }
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
            enablingShips(ability, ENABLING_SHIPS, this::count);
        }

        void barrageShips() {
            Predicate<UnitModel> barrage = model -> model.getAfbDieCount(seat) > 0;
            if (!PrecisionRules.facesFighters(game, seat, tile)) {
                enablingShips(barrage);
                return;
            }
            enablingShips(barrage, PrecisionRules.FORWARD_SHIPS, this::countHere);
        }

        private void enablingShips(Predicate<UnitModel> ability, int wanted, ToIntFunction<UnitType> counted) {
            List<UnitType> types = BoardView.MOVING_SHIPS.stream()
                    .filter(type -> BoardView.model(seat, type).filter(ability).isPresent())
                    .sorted(Comparator.comparingDouble(this::cost))
                    .toList();
            int have = types.stream().mapToInt(counted).sum();
            for (UnitType type : types) {
                double value = scoringValue(type) + ENABLER_VALUE / wanted;
                while (have < wanted && ship(type, value, true)) have++;
            }
        }

        private int countHere(UnitType type) {
            return BoardView.count(BoardView.space(tile), seat, type) + planned(type);
        }

        boolean ground(UnitType type, int count, double valueEach) {
            return ground(type, count, valueEach, false);
        }

        boolean ground(UnitType type, int count, double valueEach, boolean forDefense) {
            Optional<String> planet = groundTarget();
            UnitModel model = seat.getUnitByType(type);
            if (count <= 0 || planet.isEmpty() || model == null) return false;
            if (type == UnitType.Mech && game.isBaseGameMode()) return false;
            if (!underCap(model.getAsyncId(), planned(type) + count)) return false;
            double cost = model.getCost() * count;
            if (!budget.allowsGroundOrFighters(count, cost)) return false;
            boolean bonus = budget.spendGroundOrFighters(count, cost);
            String unitId = count == 2 ? "2" + type.getValue() : type.name().toLowerCase();
            orders.add(new BuildOrder(
                    unitId, planet.get(), type, count, cost, valueEach * count, false, forDefense, bonus));
            return true;
        }

        private boolean addFighters(int count, double valueEach) {
            return addFighters(count, valueEach, false);
        }

        private boolean addFighters(int count, double valueEach, boolean forDefense) {
            UnitModel model = seat.getUnitByType(UnitType.Fighter);
            if (count <= 0 || model == null || !underCap(model.getAsyncId(), planned(UnitType.Fighter) + count)) {
                return false;
            }
            double cost = model.getCost() * count;
            if (!budget.allowsGroundOrFighters(count, cost)) return false;
            boolean bonus = budget.spendGroundOrFighters(count, cost);
            String unitId = count == 2 ? "2" + UnitType.Fighter.getValue() : "fighter";
            orders.add(new BuildOrder(
                    unitId,
                    tile.getPosition(),
                    UnitType.Fighter,
                    count,
                    cost,
                    valueEach * count,
                    false,
                    forDefense,
                    bonus));
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

        Defenders plannedDefenders() {
            Defenders defenders = Defenders.of(seat, tile);
            for (BuildOrder order : orders) defenders = grown(defenders, order.type(), order.units(), order.location());
            return defenders;
        }

        Defenders grown(Defenders defenders, DefenseBundle bundle) {
            return grown(
                    defenders, bundle.type(), bundle.count(), groundTarget().orElse(""));
        }

        private static Defenders grown(Defenders defenders, UnitType type, int count, String planet) {
            return BoardView.GROUND_FORCES.contains(type)
                    ? defenders.plusGround(planet, type, count)
                    : defenders.plusShips(type, count, 0);
        }

        List<DefenseBundle> defenseBundles() {
            List<DefenseBundle> bundles = new ArrayList<>();
            for (int count = 1; count <= MAX_DEFENSE_BUNDLE_SIZE; count++) {
                addIfAffordable(bundles, UnitType.Infantry, count);
                if (count <= MAX_DEFENSE_MECHS) addIfAffordable(bundles, UnitType.Mech, count);
                addIfAffordable(bundles, UnitType.Fighter, count);
                for (UnitType ship : DEFENSE_SHIPS) addIfAffordable(bundles, ship, count);
            }
            return bundles;
        }

        private void addIfAffordable(List<DefenseBundle> bundles, UnitType type, int count) {
            if (canAdd(type, count)) bundles.add(new DefenseBundle(type, count));
        }

        private boolean canAdd(UnitType type, int count) {
            UnitModel model = seat.getUnitByType(type);
            if (model == null || !underCap(model.getAsyncId(), planned(type) + count)) return false;
            double cost = model.getCost() * count;
            if (BoardView.GROUND_FORCES.contains(type)) {
                boolean mechAllowed = type != UnitType.Mech || !game.isBaseGameMode();
                return mechAllowed && groundTarget().isPresent() && budget.allowsGroundOrFighters(count, cost);
            }
            if (type == UnitType.Fighter) return count <= fighterRoom() && budget.allowsGroundOrFighters(count, cost);
            return budget.allowsShips(count, cost);
        }

        double defenseBias(DefenseBundle bundle) {
            long planets = tile.getPlanetUnitHolders().stream()
                    .filter(planet -> seat.getPlanets().contains(planet.getName()))
                    .count();
            boolean focused = bundle.ground() ? planets == 1 : planets >= 2;
            return focused ? DEFENSE_FOCUS_BIAS : 1.0;
        }

        void addDefense(DefenseBundle bundle, double gain) {
            double each = gain / bundle.count();
            UnitType type = bundle.type();
            if (bundle.ground()) {
                addInPairs(bundle.count(), count -> ground(type, count, each, true));
            } else if (type == UnitType.Fighter) {
                addInPairs(bundle.count(), count -> addFighters(count, each, true));
            } else {
                for (int ship = 0; ship < bundle.count(); ship++) ship(type, each, false, true);
            }
        }

        private void addInPairs(int total, IntPredicate add) {
            for (int left = total; left > 0; ) {
                int added = pairOrSingle(left, add);
                if (added == 0) return;
                left -= added;
            }
        }

        private Optional<String> groundTarget() {
            return groundPlanet != null ? Optional.of(groundPlanet) : dockPlanet();
        }

        boolean leavesRoomToFill(double cost) {
            return budget.resources - cost >= CHEAPEST_UNIT_COST * (budget.slots() - 1);
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

        int plannedNonFighterShips() {
            return orders.stream()
                    .filter(order -> BoardView.MOVING_SHIPS.contains(order.type()))
                    .mapToInt(BuildOrder::units)
                    .sum();
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

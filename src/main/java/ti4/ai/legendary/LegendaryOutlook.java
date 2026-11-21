package ti4.ai.legendary;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.eval.BoardView;
import ti4.ai.eval.FleetLimit;
import ti4.ai.explore.ExploreValues;
import ti4.ai.perception.PromptButton;
import ti4.ai.scoring.Footprint;
import ti4.ai.scoring.ObjectiveValue;
import ti4.ai.scoring.SpendUnlock;
import ti4.ai.strategy.ReadyingValue;
import ti4.ai.tactical.GroundDrops;
import ti4.ai.tactical.GroundDrops.Drop;
import ti4.ai.tactical.ProductionPlanner;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlanner;
import ti4.ai.tactical.TacticalRules;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitType;
import ti4.service.planet.FaunusService;
import ti4.service.planet.IndustrexService;

@UtilityClass
class LegendaryOutlook {

    static final double NOT_USABLE = Double.NEGATIVE_INFINITY;
    static final String FAUNUS_PREFIX = "faunusTake_";
    static final String INDUSTREX_PREFIX = "industrexPickType_";
    private static final int FIGHTERS_PLACED = 2;
    private static final int COMMODITIES_FOR_MALLICE_TRADE_GOODS = 2;
    private static final double OWNER_LOSS_SHARE = 0.3;
    private static final double UNDEFENDED_SHARE = 0.7;

    record Mallice(boolean convert, double value) {}

    record HopesEnd(boolean mech, double value) {}

    record Seizure(String planet, double value) {}

    record Reinforcement(UnitType type, String position, double value) {}

    record Placement(String position, double value) {}

    static double value(AiTurnContext context, String planet) {
        Game game = context.game();
        Player seat = context.seat();
        if (LegendaryPlanets.FLIGHT_ACADEMIES.contains(planet)) {
            return fighters(game, seat).map(Placement::value).orElse(NOT_USABLE);
        }
        if (LegendaryPlanets.ORDINIANS.contains(planet)) return ordinian(game, seat);
        return switch (planet) {
            case LegendaryPlanets.MALLICE -> mallice(game, seat).value();
            case LegendaryPlanets.HOPES_END ->
                hopesEnd(game, seat, TacticalRules.rememberedPlan(context)).value();
            case LegendaryPlanets.PRIMOR ->
                primor(game, seat, TacticalRules.rememberedPlan(context))
                        .map(Drop::infantry)
                        .orElse(NOT_USABLE);
            case LegendaryPlanets.FAUNUS ->
                faunus(game, seat).map(Seizure::value).orElse(NOT_USABLE);
            case LegendaryPlanets.INDUSTREX ->
                industrex(game, seat).map(Reinforcement::value).orElse(NOT_USABLE);
            case LegendaryPlanets.EMELPAR ->
                emelpar(game, seat).map(ReadyingValue.Pick::value).orElse(NOT_USABLE);
            case LegendaryPlanets.THUNDERS_EDGE -> anotherAction(context);
            default -> NOT_USABLE;
        };
    }

    static Mallice mallice(Game game, Player seat) {
        double tradeGoods = COMMODITIES_FOR_MALLICE_TRADE_GOODS * ExploreValues.TRADE_GOOD
                + unlocked(game, seat, COMMODITIES_FOR_MALLICE_TRADE_GOODS);
        int commodities = seat.getCommodities();
        double converted =
                ExploreValues.convertedCommodities(game, seat, commodities) + unlocked(game, seat, commodities);
        return converted > tradeGoods ? new Mallice(true, converted) : new Mallice(false, tradeGoods);
    }

    private static double unlocked(Game game, Player seat, int tradeGoods) {
        return SpendUnlock.pointDelta(game, seat, tradeGoods) * ObjectiveValue.VICTORY_POINT_VALUE;
    }

    static HopesEnd hopesEnd(Game game, Player seat, Optional<TacticalPlan> plan) {
        double card = ExploreValues.drawnActionCards(game, seat, 1);
        Optional<Drop> drop = mechDrop(game, seat, plan);
        if (drop.isEmpty()) return new HopesEnd(false, card);
        double mech = ExploreValues.MECH + drop.get().mech();
        return mech >= card ? new HopesEnd(true, mech) : new HopesEnd(false, card);
    }

    static Optional<Drop> mechDrop(Game game, Player seat, Optional<TacticalPlan> plan) {
        if (!GroundDrops.mechAvailable(game, seat) || !ExploreValues.hasRoomForUnit(game, seat, UnitType.Mech)) {
            return Optional.empty();
        }
        return GroundDrops.drops(game, seat, plan).stream().max(Comparator.comparingDouble(Drop::mech));
    }

    static Optional<Drop> primor(Game game, Player seat, Optional<TacticalPlan> plan) {
        return GroundDrops.best(game, seat, plan);
    }

    static double ordinian(Game game, Player seat) {
        return ExploreValues.drawnActionCards(game, seat, 1) + ExploreValues.tokenValue(game, seat);
    }

    static Optional<Placement> fighters(Game game, Player seat) {
        if (!ExploreValues.hasRoomForUnit(game, seat, UnitType.Fighter)) return Optional.empty();
        double cost = BoardView.model(seat, UnitType.Fighter)
                        .map(model -> (double) model.getCost())
                        .orElse(0.0)
                * FIGHTERS_PLACED;
        double value = cost * ProductionPlanner.fillerValuePerResource(game);
        return game.getTileMap().values().stream()
                .filter(tile -> BoardView.hasOwnShips(seat, tile))
                .filter(tile -> capacityRoom(seat, tile) >= FIGHTERS_PLACED)
                .max(Comparator.comparingDouble(tile -> BoardView.shipCost(BoardView.space(tile), seat)))
                .map(tile -> new Placement(tile.getPosition(), value));
    }

    static int capacityRoom(Player seat, Tile tile) {
        UnitHolder space = BoardView.space(tile);
        int capacity = 0;
        for (UnitType type : BoardView.MOVING_SHIPS) {
            capacity += BoardView.count(space, seat, type) * BoardView.capacity(seat, type);
        }
        int carried = BoardView.count(space, seat, UnitType.Fighter) + BoardView.groundForces(space, seat);
        return capacity - carried;
    }

    static Optional<Seizure> faunus(Game game, Player seat) {
        ObjectiveValue objectives = new ObjectiveValue(game, seat);
        return FaunusService.getFaunusButtons(game, seat).stream()
                .map(button -> PromptButton.of(0, button).handlerId())
                .filter(id -> id.startsWith(FAUNUS_PREFIX))
                .map(id -> StringUtils.removeStart(id, FAUNUS_PREFIX))
                .map(name -> seizure(game, seat, objectives, name))
                .flatMap(Optional::stream)
                .max(Comparator.comparingDouble(Seizure::value));
    }

    static Optional<Seizure> seizure(Game game, Player seat, ObjectiveValue objectives, String name) {
        Planet planet = game.getPlanetsInfo().get(name);
        if (planet == null || BoardView.hasCustodians(planet)) return Optional.empty();
        double worth = BoardView.planetValue(planet);
        Player owner = BoardView.controller(game, name);
        double denial = owner == null || owner == seat ? 0 : OWNER_LOSS_SHARE * worth;
        double objective = objectives.gain(objectives.before().withPlanet(name));
        return Optional.of(new Seizure(name, UNDEFENDED_SHARE * worth + denial + objective));
    }

    static Optional<Reinforcement> industrex(Game game, Player seat) {
        ObjectiveValue objectives = new ObjectiveValue(game, seat);
        Optional<Reinforcement> best = Optional.empty();
        for (UnitType type : industrexTypes(game, seat)) {
            Optional<Reinforcement> placed = reinforcement(game, seat, objectives, type);
            if (placed.isPresent()
                    && (best.isEmpty() || placed.get().value() > best.get().value())) {
                best = placed;
            }
        }
        return best;
    }

    static Optional<Reinforcement> reinforcement(Game game, Player seat, ObjectiveValue objectives, UnitType type) {
        if (!ExploreValues.hasRoomForUnit(game, seat, type)) return Optional.empty();
        double filler = ProductionPlanner.fillerValuePerResource(game)
                * BoardView.model(seat, type)
                        .map(model -> (double) model.getCost())
                        .orElse(0.0);
        boolean fighter = type == UnitType.Fighter;
        Footprint before = objectives.before();
        Optional<Reinforcement> best = Optional.empty();
        List<Tile> fleets = game.getTileMap().values().stream()
                .sorted(Comparator.comparingDouble((Tile tile) -> BoardView.shipCost(BoardView.space(tile), seat))
                        .reversed())
                .toList();
        for (Tile tile : fleets) {
            if (!BoardView.hasOwnShips(seat, tile)) continue;
            if (!fighter && BoardView.nonFighterShips(BoardView.space(tile), seat) + 1 > FleetLimit.ships(seat)) {
                continue;
            }
            double gain = objectives.gain(before.withBuilt(tile.getPosition(), Map.of(type, 1)));
            double value = filler + gain;
            if (best.isEmpty() || value > best.get().value()) {
                best = Optional.of(new Reinforcement(type, tile.getPosition(), value));
            }
        }
        return best;
    }

    static List<UnitType> industrexTypes(Game game, Player seat) {
        return IndustrexService.getIndustrexButtonsPart1(game, seat).stream()
                .map(button -> PromptButton.of(0, button).handlerId())
                .filter(id -> id.startsWith(INDUSTREX_PREFIX))
                .map(id -> StringUtils.removeStart(id, INDUSTREX_PREFIX))
                .map(LegendaryOutlook::unitTypeOf)
                .flatMap(Optional::stream)
                .toList();
    }

    static Optional<UnitType> unitTypeOf(String value) {
        return Arrays.stream(UnitType.values())
                .filter(type -> type.value.equals(value))
                .findFirst();
    }

    static Optional<ReadyingValue.Pick> emelpar(Game game, Player seat) {
        return ReadyingValue.bestSingle(game, seat, seat.getExhaustedPlanets());
    }

    static double anotherAction(AiTurnContext context) {
        if (!TacticalRules.actionTaken(context)
                || TacticalRules.secondActionStarted(context)
                || !TacticalRules.secondTacticalActionIsWorthIt(context)) {
            return NOT_USABLE;
        }
        return TacticalPlanner.best(context.game(), context.seat())
                .map(TacticalPlan::score)
                .orElse(NOT_USABLE);
    }
}

package ti4.ai.tactical;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import ti4.ai.eval.BoardView;
import ti4.ai.strategy.CardValueScale;
import ti4.ai.tactical.ProductionPlanner.BuildOrder;
import ti4.ai.tactical.ProductionPlanner.BuildPlan;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitType;

public record ProductionTrigger(Set<UnitType> counted, int needed, double reward, int fleetRoomGained) {

    private static final String AUTO_FACTORIES = "hacanbt";
    private static final int AUTO_FACTORIES_SHIPS = 3;
    private static final String FLEET_REGULATIONS = "regulations";
    private static final int REGULATED_FLEET = 4;

    public static List<ProductionTrigger> held(Game game, Player seat) {
        List<ProductionTrigger> triggers = new ArrayList<>();
        if (grantsFleetToken(game, seat)) triggers.add(autoFactories());
        return triggers;
    }

    private static boolean grantsFleetToken(Game game, Player seat) {
        if (!seat.hasUnlockedBreakthrough(AUTO_FACTORIES)) return false;
        return !ButtonHelper.isLawInPlay(game, FLEET_REGULATIONS) || seat.getEffectiveFleetCC() < REGULATED_FLEET;
    }

    static ProductionTrigger autoFactories() {
        return new ProductionTrigger(BoardView.MOVING_SHIPS, AUTO_FACTORIES_SHIPS, CardValueScale.HELD_TOKEN, 1);
    }

    public static BuildPlan credited(BuildPlan plan, List<ProductionTrigger> triggers) {
        BuildPlan credited = plan;
        for (ProductionTrigger trigger : triggers) credited = trigger.credited(credited);
        return credited;
    }

    public boolean met(BuildPlan plan) {
        return countedUnits(plan) >= needed;
    }

    public int missing(BuildPlan plan) {
        return Math.max(0, needed - countedUnits(plan));
    }

    BuildPlan credited(BuildPlan plan) {
        int units = countedUnits(plan);
        if (units < needed) return plan;
        double share = reward / units;
        List<BuildOrder> orders = plan.orders().stream()
                .map(order -> counted.contains(order.type()) ? withExtraValue(order, share * order.units()) : order)
                .toList();
        return new BuildPlan(orders);
    }

    private int countedUnits(BuildPlan plan) {
        return plan.orders().stream()
                .filter(order -> counted.contains(order.type()))
                .mapToInt(BuildOrder::units)
                .sum();
    }

    private static BuildOrder withExtraValue(BuildOrder order, double extra) {
        return new BuildOrder(
                order.unitId(),
                order.location(),
                order.type(),
                order.units(),
                order.cost(),
                order.value() + extra,
                order.scoring(),
                order.defense(),
                order.bonus());
    }
}

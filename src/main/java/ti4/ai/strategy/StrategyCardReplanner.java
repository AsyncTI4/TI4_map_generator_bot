package ti4.ai.strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.StrategyCard;
import ti4.ai.strategy.CardPlan.Benefit;
import ti4.ai.tactical.SetupHint;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalPlanner;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;

@UtilityClass
class StrategyCardReplanner {

    private static final Set<CardUse> POK_SETUP_USES =
            Set.of(CardUse.WARFARE_DOUBLE_BUILD, CardUse.WARFARE_BUILD_THEN_MOVE);
    private static final Set<CardUse> THUNDERS_EDGE_SETUP_USES = Set.of(CardUse.WARFARE_DOUBLE_BUILD);

    static List<CardPlan> refresh(AiTurnContext context) {
        Game game = context.game();
        Player seat = context.seat();
        PlanInputs in = PlanInputs.of(game, seat, PlanMoment.ACTION);
        List<CardPlan> plans = new ArrayList<>();
        for (int initiative : heldUnplayed(game, seat)) {
            CardPlan now = StrategyCardPlanner.plan(in, initiative);
            Optional<CardUse> before = CardPlanMemory.read(context, initiative)
                    .flatMap(plan -> plan.main().map(Benefit::use));
            Optional<CardUse> after = now.main().map(Benefit::use);
            if (before.isPresent() && !before.equals(after)) {
                CardPlanMemory.markReplanned(context, initiative, before.get());
            }
            CardPlanMemory.write(context, now);
            plans.add(now);
        }
        writeSetupHints(context, in, plans);
        return plans;
    }

    static List<Integer> heldUnplayed(Game game, Player seat) {
        return seat.getSCs().stream()
                .filter(initiative -> initiative > 0 && !game.getPlayedSCs().contains(initiative))
                .sorted()
                .toList();
    }

    private static void writeSetupHints(AiTurnContext context, PlanInputs in, List<CardPlan> plans) {
        List<SetupHint> hints = new ArrayList<>();
        for (CardPlan plan : plans) {
            setupHint(in, plan).ifPresent(hints::add);
        }
        hints.addAll(holdHomeForGravityDrive(in));
        String key = SetupHint.key(context.game());
        context.memory().remove(key);
        context.memory().put(key, SetupHint.encodeAll(hints));
    }

    private static List<SetupHint> holdHomeForGravityDrive(PlanInputs in) {
        Game game = in.game();
        Player seat = in.seat();
        Tile home = seat.getHomeSystemTile();
        if (home == null || home.hasPlayerCC(seat) || seat.getStrategicCC() <= 0) return List.of();
        if (!OpeningNeeds.wantsTechnologySoon(game, seat)) return List.of();
        boolean somethingElseToDo = in.ranked().stream()
                .anyMatch(
                        plan -> !plan.target().equals(home.getPosition()) && plan.score() >= TacticalPlanner.MIN_SCORE);
        if (!somethingElseToDo) return List.of();
        double reach = OpeningOutlook.gain(game, seat, OpeningOutlook.Boost.GRAVITY_DRIVE);
        return List.of(
                new SetupHint(TacticalPlan.Kind.PRODUCE, home.getPosition(), -reach),
                new SetupHint(TacticalPlan.Kind.DEFEND, home.getPosition(), -reach));
    }

    private static Optional<SetupHint> setupHint(PlanInputs in, CardPlan plan) {
        if (plan.card() != StrategyCard.WARFARE) return Optional.empty();
        Optional<Benefit> main = plan.main();
        Tile home = in.seat().getHomeSystemTile();
        if (main.isEmpty() || home == null || home.hasPlayerCC(in.seat())) return Optional.empty();
        Set<CardUse> needsSetup = CardVariant.of(in.game(), plan.initiative()) == CardVariant.THUNDERS_EDGE
                ? THUNDERS_EDGE_SETUP_USES
                : POK_SETUP_USES;
        if (!needsSetup.contains(main.get().use())) return Optional.empty();
        return Optional.of(new SetupHint(
                TacticalPlan.Kind.PRODUCE,
                home.getPosition(),
                CardValueScale.SETUP_SHARE * main.get().value()));
    }
}

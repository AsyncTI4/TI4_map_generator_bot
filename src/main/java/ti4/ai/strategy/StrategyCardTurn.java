package ti4.ai.strategy;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.StrategyCard;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.game.Game;

@UtilityClass
public class StrategyCardTurn {

    private static final String STRATEGIC_ACTION = "strategicAction_";
    private static final String ACTION_PHASE = "action";

    public static Optional<AiDecision> playIfDue(AiTurnContext context, List<AiPrompt> thisTurn) {
        if (!ACTION_PHASE.equalsIgnoreCase(context.game().getPhaseOfGame())) return Optional.empty();
        Optional<AiDecision> requested = playRequestedTechnology(context, thisTurn);
        if (requested.isPresent()) return requested;
        List<CardPlan> plans = StrategyCardReplanner.refresh(context);
        PlanInputs in = PlanInputs.of(context.game(), context.seat(), PlanMoment.ACTION);
        for (CardPlan plan : plans) {
            if (timing(context, in, plan) != CardTiming.Timing.NOW) continue;
            Optional<AiDecision> play = play(context, thisTurn, plan.initiative());
            if (play.isPresent()) return play;
        }
        return Optional.empty();
    }

    private static CardTiming.Timing timing(AiTurnContext context, PlanInputs in, CardPlan plan) {
        if (plan.card() == StrategyCard.TECHNOLOGY && heldBack(context)) return CardTiming.Timing.FALLBACK;
        return CardTiming.of(in, plan);
    }

    private static boolean heldBack(AiTurnContext context) {
        return TechnologyTiming.heldBack(context.game(), context.seat(), context.memory());
    }

    private static Optional<AiDecision> playRequestedTechnology(AiTurnContext context, List<AiPrompt> thisTurn) {
        Game game = context.game();
        if (!context.memory().has(OpeningNeeds.technologyRequestedKey(game)) || heldBack(context)) {
            return Optional.empty();
        }
        return context.seat().getSCs().stream()
                .filter(initiative -> !game.getPlayedSCs().contains(initiative))
                .filter(initiative -> StrategyCard.of(game, initiative) == StrategyCard.TECHNOLOGY)
                .findFirst()
                .flatMap(initiative -> play(context, thisTurn, initiative));
    }

    public static boolean dueNow(AiTurnContext context, int initiative) {
        Game game = context.game();
        if (!context.seat().getSCs().contains(initiative) || game.getPlayedSCs().contains(initiative)) return false;
        PlanInputs in = PlanInputs.of(game, context.seat(), PlanMoment.ACTION);
        return timing(context, in, StrategyCardPlanner.plan(in, initiative)) == CardTiming.Timing.NOW;
    }

    public static String playReason(AiTurnContext context, int initiative) {
        Optional<CardPlan> plan = CardPlanMemory.read(context, initiative);
        if (plan.isEmpty()) return "play a strategy card";
        String replanned = CardPlanMemory.replannedFrom(context, initiative)
                .map(use -> "replanned from " + use.label() + ": ")
                .orElse("");
        return replanned + "play " + plan.get().describe();
    }

    private static Optional<AiDecision> play(AiTurnContext context, List<AiPrompt> thisTurn, int initiative) {
        for (AiPrompt prompt : thisTurn) {
            Optional<PromptButton> button = prompt.firstEnabled(candidate -> candidate.isOwnedBy(context.faction())
                    && candidate.handlerId().startsWith(STRATEGIC_ACTION)
                    && StrategyCardRanking.initiative(candidate) == initiative
                    && !context.alreadyPressed(prompt, candidate));
            if (button.isPresent()) {
                return Optional.of(StrategyCardRules.play(
                        context, prompt, button.get(), initiative, playReason(context, initiative)));
            }
        }
        return Optional.empty();
    }
}

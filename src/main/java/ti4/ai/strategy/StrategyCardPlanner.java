package ti4.ai.strategy;

import java.util.Collection;
import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.StrategyCard;
import ti4.ai.strategy.CardPlan.Benefit;
import ti4.ai.trace.Thinking;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class StrategyCardPlanner {

    static CardPlan plan(PlanInputs in, int initiative) {
        return in.plans().computeIfAbsent(initiative, card -> evaluate(in, card));
    }

    private static CardPlan evaluate(PlanInputs in, int initiative) {
        StrategyCard card = StrategyCard.of(in.game(), initiative);
        List<Benefit> benefits =
                switch (card) {
                    case LEADERSHIP -> LeadershipUses.benefits(in, initiative);
                    case DIPLOMACY -> DiplomacyUses.benefits(in, initiative);
                    case POLITICS -> PoliticsUses.benefits(in, initiative);
                    case CONSTRUCTION -> ConstructionUses.benefits(in, initiative);
                    case TRADE -> TradeUses.benefits(in, initiative);
                    case WARFARE -> WarfareUses.benefits(in, initiative);
                    case TECHNOLOGY -> TechnologyUses.benefits(in, initiative);
                    case IMPERIAL -> ImperialUses.benefits(in, initiative);
                    case OTHER -> List.of(new Benefit(CardUse.OTHER_CARD, CardValueScale.OTHER_CARD));
                };
        return new CardPlan(
                card,
                initiative,
                benefits.stream().filter(benefit -> benefit.value() != 0).toList());
    }

    static List<CardPlan> planAll(Game game, Player seat, Collection<Integer> initiatives, PlanMoment moment) {
        try (Thinking.Span ignored = Thinking.enter("strategy.planAll")) {
            PlanInputs in = PlanInputs.of(game, seat, moment);
            return initiatives.stream().map(initiative -> plan(in, initiative)).toList();
        }
    }
}

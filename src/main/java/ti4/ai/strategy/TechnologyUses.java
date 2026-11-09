package ti4.ai.strategy;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.ValueScale;
import ti4.ai.scoring.Wallet;
import ti4.ai.strategy.CardPlan.Benefit;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class TechnologyUses {

    private static final int PROPAGATION_TOKENS = 3;

    static List<Benefit> benefits(PlanInputs in, int initiative) {
        Game game = in.game();
        Player seat = in.seat();
        if (StrategyCardRules.cannotResearch(seat)) {
            return List.of(
                    new Benefit(CardUse.TECHNOLOGY_PROPAGATION, TokenValue.carriedTokens(in, PROPAGATION_TOKENS)));
        }
        double research = in.moment() == PlanMoment.PICK
                ? ResearchPolicy.bestValueAsTechnologyHolder(game, seat)
                : ResearchPolicy.bestResearchable(game, seat)
                        .map(alias -> ResearchPolicy.value(game, seat, alias))
                        .orElse(0.0);
        boolean paysForSecond = StrategyCardRules.worthPayingForResearch(
                game, seat, StrategyCardRules.SECOND_TECH_COST, ResearchPolicy.WORTH_PAYING_FOR, Wallet.of(game, seat));
        double value = in.techInTradeGoods()
                ? tradeGoodValue(in, research, paysForSecond)
                : researchUnitValue(research, paysForSecond);
        return List.of(new Benefit(CardUse.TECHNOLOGY_RESEARCH, value));
    }

    private static double researchUnitValue(double research, boolean paysForSecond) {
        return paysForSecond ? research * (1 + CardValueScale.SECOND_RESEARCH_SHARE) : research;
    }

    private static double tradeGoodValue(PlanInputs in, double research, boolean paysForSecond) {
        double first = ResearchPolicy.TECH_TEMPO * research;
        if (!paysForSecond) return first;
        return first + secondResearchValue(in);
    }

    static double secondResearchValue(PlanInputs in) {
        Game game = in.game();
        Player seat = in.seat();
        Optional<String> first = in.moment() == PlanMoment.PICK
                ? ResearchPolicy.firstResearchAsTechnologyHolder(game, seat)
                : ResearchPolicy.bestResearchable(game, seat);
        double second = first.flatMap(alias -> ResearchPolicy.bestResearchableExcept(game, seat, alias))
                .map(alias -> ResearchPolicy.value(game, seat, alias))
                .orElse(0.0);
        double cost = StrategyCardRules.SECOND_TECH_COST * ValueScale.RESOURCE;
        return Math.max(0, ResearchPolicy.TECH_TEMPO * second - cost);
    }
}

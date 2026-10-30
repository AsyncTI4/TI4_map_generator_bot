package ti4.ai.strategy;

import java.util.List;
import lombok.experimental.UtilityClass;
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
        double value = paysForSecond ? research * (1 + CardValueScale.SECOND_RESEARCH_SHARE) : research;
        return List.of(new Benefit(CardUse.TECHNOLOGY_RESEARCH, value));
    }
}

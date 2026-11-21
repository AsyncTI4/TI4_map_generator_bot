package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.tactical.ProductionPlanner;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.testUtils.BaseTi4Test;

// Live game aiplay1, round 1: Letnev and Sol followed Leadership and spent their trade goods on command tokens, then
// ended the round with tokens left in tactics and no home build, and Letnev skipped the extra technology of its own
// Technology primary. Producing and researching run on a fixed tempo, so the Leadership purchase now keeps what the
// home build and the extra technology need and buys tokens only with what is left.
class TokensAfterBuildAndResearchTest extends BaseTi4Test {

    private static final int TECHNOLOGY = 7;
    private static final int TRADE_GOODS = 6;

    private AiTestGame test;
    private Player seat;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(1);
        test.aiIsActive("action");
        seat = test.nekro;
        seat.setFaction(test.game, "letnev");
        seat.setTg(TRADE_GOODS);
        seat.setTacticalCC(2);
        seat.setFleetCC(3);
        seat.setStrategicCC(2);
    }

    private int tokensBought() {
        return TokenPurchase.best(test.game, seat, StrategyCardRules.reinforcements(test.game, seat))
                .map(TokenPurchase.Purchase::tokens)
                .orElse(0);
    }

    @Test
    void withNothingElseToSpendOnTheTradeGoodsBuyTokens() {
        assertThat(tokensBought()).isEqualTo(2);
    }

    @Test
    void keepsWhatTheHomeBuildNeeds() {
        Tile home = test.nekroHome();
        double buildCost = ProductionPlanner.plan(test.game, seat, home).totalCost();
        assertThat(buildCost).isGreaterThanOrEqualTo(4);

        assertThat(tokensBought()).isLessThan(2);
    }

    @Test
    void keepsWhatTheExtraTechnologyNeeds() {
        seat.addSC(TECHNOLOGY);
        assertThat(StrategyCardRules.worthResearching(test.game, seat, ResearchPolicy.WORTH_PAYING_FOR))
                .isTrue();

        assertThat(tokensBought()).isZero();
    }
}

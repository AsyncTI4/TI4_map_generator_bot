package ti4.ai.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.testUtils.BaseTi4Test;

class ExpectedTradeGoodsTest extends BaseTi4Test {

    private AiTestGame test;

    // Nekro has 1 of 6 commodities (as Hacan would) and no strategy token left.
    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.aiIsActive("action");
        test.nekro.setCommoditiesBase(6);
        test.nekro.setCommodities(1);
        test.nekro.setStrategicCC(0);
    }

    // Without a strategy token it can't follow Trade, so only the commodity it holds can be washed.
    @Test
    void followingTradeWithoutATokenOnlyWashesWhatItHolds() {
        assertThat(ExpectedTradeGoods.ifFollowingTrade(test.game, test.nekro)).isEqualTo(1);
    }

    // Masters of Trade makes the secondary free, so following still refills all 6 to wash.
    @Test
    void mastersOfTradeFollowsWithoutAToken() {
        test.nekro.addAbility("master_of_trade");

        assertThat(ExpectedTradeGoods.ifFollowingTrade(test.game, test.nekro)).isEqualTo(6);
    }
}

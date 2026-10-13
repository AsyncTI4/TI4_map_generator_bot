package ti4.ai.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.testUtils.BaseTi4Test;

// Round-one self-play showed seats spending their home resources on units early in the round, before the Trade
// primary and washed commodities brought the trade goods that would have paid for a spend objective at status. The
// reserve now also keeps back what a seat must still hold for a spend objective it can expect to afford by then.
class ScoringReserveProjectionTest extends BaseTi4Test {

    private static final int TRADE = 5;

    private AiTestGame test;

    // Nekro controls Mordai II (4 resources) and has 3 commodities to replenish; Erect a Monument takes 8 resources.
    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(1);
        test.aiIsActive("action");
        test.nekroHome();
        test.game.getRevealedPublicObjectives().put("monument", 1);
        test.nekro.setTg(1);
    }

    // Holding the unplayed Trade card: 3 trade goods from the primary and 3 washed commodities make 4 + 1 + 6 = 11, so
    // the seat must keep 2 resources of Mordai II and cannot spend the planet on units.
    @Test
    void keepsResourcesForAMonumentTheTradeCardWillFund() {
        test.nekro.addSC(TRADE);

        assertThat(ExpectedTradeGoods.beforeStatus(test.game, test.nekro)).isEqualTo(6);
        assertThat(ScoringReserve.reserved(test.game, test.nekro)).hasValueSatisfying(reserved -> {
            assertThat(reserved.objectiveId()).isEqualTo("monument");
            assertThat(reserved.cost()).isEqualTo(SpendCost.resources(2));
        });
        assertThat(ScoringReserve.planAfterReserve(test.game, test.nekro, SpendCost.resources(4)))
                .isEmpty();
    }

    // Once Trade has been played and the commodities washed away, nothing more is expected: 4 + 1 cannot reach 8 and
    // the planet may be spent.
    @Test
    void reservesNothingWhenNoMoreIncomeIsExpected() {
        test.nekro.addSC(TRADE);
        test.game.setSCPlayed(TRADE, true);

        assertThat(ExpectedTradeGoods.beforeStatus(test.game, test.nekro)).isZero();
        assertThat(ScoringReserve.reserved(test.game, test.nekro)).isEmpty();
        assertThat(ScoringReserve.planAfterReserve(test.game, test.nekro, SpendCost.resources(4)))
                .isPresent();
    }

    // Commodities already in hand still count after Trade was played: with 3 of them the seat reaches 8 and keeps
    // Mordai II.
    @Test
    void countsCommoditiesStillToWash() {
        test.sol.addSC(TRADE);
        test.game.setSCPlayed(TRADE, true);
        test.nekro.setCommodities(3);

        assertThat(ExpectedTradeGoods.beforeStatus(test.game, test.nekro)).isEqualTo(3);
        assertThat(ScoringReserve.reserved(test.game, test.nekro))
                .hasValueSatisfying(reserved -> assertThat(reserved.cost()).isEqualTo(SpendCost.resources(5)));
    }

    // Outside the action phase there is nothing to protect.
    @Test
    void noProjectionOutsideTheActionPhase() {
        test.nekro.addSC(TRADE);
        test.game.setPhaseOfGame("strategy");

        assertThat(ScoringReserve.reserved(test.game, test.nekro)).isEmpty();
    }

    // Expected trade goods pay the trade good part first, then resources, then influence.
    @Test
    void netCostSubtractsTradeGoodsInOrder() {
        assertThat(ExpectedTradeGoods.netOf(SpendCost.each(3), 4, 1)).isEqualTo(new SpendCost(2, 3, 0, 0));
        assertThat(ExpectedTradeGoods.netOf(SpendCost.resources(8), 3, 2)).isEqualTo(SpendCost.resources(2));
        assertThat(ExpectedTradeGoods.netOf(SpendCost.tradeGoods(5), 3, 1)).isEqualTo(SpendCost.tradeGoods(2));
        assertThat(ExpectedTradeGoods.netOf(SpendCost.tokens(3), 3, 1)).isEqualTo(SpendCost.tokens(3));
    }
}

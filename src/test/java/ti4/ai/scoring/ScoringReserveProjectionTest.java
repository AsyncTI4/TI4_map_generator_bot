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
        assertThat(ExpectedTradeGoods.tradeGoodsUsed(SpendCost.resources(8), 3, 2))
                .isEqualTo(3);
        assertThat(ExpectedTradeGoods.tradeGoodsUsed(SpendCost.tradeGoods(5), 9, 1))
                .isEqualTo(5);
    }

    private static final int DIPLOMACY = 2;

    // Abyz (3 resources) and Fria (2), both captured exhausted.
    private void exhaustedCaptures() {
        test.place("38", "201");
        for (String planet : java.util.List.of("abyz", "fria")) {
            test.nekro.addPlanet(planet);
            test.nekro.exhaustPlanet(planet);
        }
    }

    // Holding the unplayed Diplomacy card readies Abyz and Fria: 4 + 1 + 5 = 10 reaches 8, so 3 resources of Mordai II
    // must stay unspent and the planet cannot pay for units.
    @Test
    void countsPlanetsItsDiplomacyWillReady() {
        exhaustedCaptures();
        test.nekro.addSC(DIPLOMACY);

        assertThat(ExpectedReadying.beforeStatus(test.game, test.nekro))
                .extracting(Wallet.PlanetValue::name)
                .containsExactly("abyz", "fria");
        assertThat(ScoringReserve.reserved(test.game, test.nekro))
                .hasValueSatisfying(reserved -> assertThat(reserved.cost()).isEqualTo(SpendCost.resources(3)));
        assertThat(ScoringReserve.planAfterReserve(test.game, test.nekro, SpendCost.resources(4)))
                .isEmpty();
    }

    // Another seat's Diplomacy readies them too when this seat has a strategy token to follow it, and not otherwise.
    @Test
    void countsFollowingAnotherSeatsDiplomacyOnlyWithAStrategyToken() {
        exhaustedCaptures();
        test.sol.addSC(DIPLOMACY);
        test.nekro.setStrategicCC(1);
        assertThat(ScoringReserve.reserved(test.game, test.nekro)).isPresent();

        test.nekro.setStrategicCC(0);
        assertThat(ExpectedReadying.beforeStatus(test.game, test.nekro)).isEmpty();
        assertThat(ScoringReserve.reserved(test.game, test.nekro)).isEmpty();
    }

    // Readied planets cover resources first; only the 3 trade goods still needed are counted as spoken for, so the
    // rest of the expected income stays free for other reserves such as the Gravity Drive follow.
    @Test
    void readiedPlanetsLeaveExpectedTradeGoodsForOtherReserves() {
        exhaustedCaptures();
        test.nekro.addSC(DIPLOMACY);
        test.nekro.addSC(TRADE);

        assertThat(ScoringReserve.reserved(test.game, test.nekro)).hasValueSatisfying(reserved -> {
            assertThat(reserved.cost()).isEqualTo(SpendCost.NONE);
            assertThat(reserved.expectedTradeGoods()).isEqualTo(3);
        });
        assertThat(ScoringReserve.expectedTradeGoodsCounted(test.game, test.nekro))
                .isEqualTo(3);
    }
}

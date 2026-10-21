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
        test.nekro.setCommoditiesBase(3);
    }

    // Holding the unplayed Trade card: the 3 trade goods of the primary and the 3 commodities it washes make 4 + 1 + 6
    // =
    // 11. Planets pay first and trade goods last, so the seat keeps Mordai II and the 1 trade good in hand: 5
    // resources, of which the 3 trade goods it still expects cover the rest. It cannot spend the planet on units.
    @Test
    void keepsResourcesForAMonumentTheTradeCardWillFund() {
        test.nekro.addSC(TRADE);

        assertThat(ExpectedTradeGoods.beforeStatus(test.game, test.nekro)).isEqualTo(6);
        assertThat(ScoringReserve.reserved(test.game, test.nekro)).hasValueSatisfying(reserved -> {
            assertThat(reserved.objectiveId()).isEqualTo("monument");
            assertThat(reserved.cost()).isEqualTo(SpendCost.resources(5));
            assertThat(reserved.expectedTradeGoods()).isEqualTo(3);
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

    // A seat that does not hold the unplayed Trade card sells its commodities to the holder at X-1: with a commodity
    // value of 3 it forecasts 2 trade goods. The holder counts the primary and its own value. A Masters of Trade seat
    // (Hacan)
    // replenishes automatically and other seats turn its commodities into trade goods, so it forecasts its full value
    // whether or not Trade has been played.
    @Test
    void aNonHolderForecastsOneLessThanItsCommodityValue() {
        test.sol.addSC(TRADE);
        test.sol.setCommoditiesBase(4);

        assertThat(ExpectedTradeGoods.beforeStatus(test.game, test.nekro)).isEqualTo(2);
        assertThat(ExpectedTradeGoods.beforeStatus(test.game, test.sol)).isEqualTo(7);

        test.nekro.addAbility("master_of_trade");
        test.nekro.setCommoditiesBase(6);

        assertThat(ExpectedTradeGoods.beforeStatus(test.game, test.nekro)).isEqualTo(6);

        test.game.setSCPlayed(TRADE, true);

        assertThat(ExpectedTradeGoods.beforeStatus(test.game, test.nekro)).isEqualTo(6);
    }

    // After Trade was played, commodities in hand are counted at X-1 while a wash partner holds some, and not at all
    // otherwise.
    @Test
    void commoditiesInHandAfterTradeNeedAWashPartner() {
        test.sol.addSC(TRADE);
        test.game.setSCPlayed(TRADE, true);
        test.nekro.setCommodities(3);
        test.sol.setCommodities(2);

        assertThat(ExpectedTradeGoods.beforeStatus(test.game, test.nekro)).isEqualTo(2);

        test.sol.setCommodities(0);

        assertThat(ExpectedTradeGoods.beforeStatus(test.game, test.nekro)).isZero();
    }

    // Amass Wealth for a seat whose forecast exceeds the 3 trade goods the objective asks for: planets pay first, and
    // the spare trade good is only counted where the planets fall short. Mordai II, Wren Terra and Archon Tau cover
    // 3 resources and 2 influence, so the spare trade good pays the third influence.
    @Test
    void sparesExpectedTradeGoodsOnlyForWhatThePlanetsCannotPay() {
        test.game.getRevealedPublicObjectives().remove("monument");
        test.game.getRevealedPublicObjectives().put("amass_wealth", 1);
        test.nekro.setTg(0);
        test.place("10", "201");
        test.place("14", "202");
        test.nekro.addPlanet("wrenterra");
        test.nekro.addPlanet("archontau");
        test.sol.addSC(TRADE);
        test.nekro.setCommoditiesBase(5);

        assertThat(ExpectedTradeGoods.beforeStatus(test.game, test.nekro)).isEqualTo(4);
        assertThat(ScoringReserve.reserved(test.game, test.nekro)).hasValueSatisfying(reserved -> {
            assertThat(reserved.objectiveId()).isEqualTo("amass_wealth");
            assertThat(reserved.cost()).isEqualTo(new SpendCost(3, 2, 0, 0));
            assertThat(reserved.expectedTradeGoods()).isEqualTo(4);
        });
    }

    // A Letnev-like holder of the unplayed Trade card (6 resources, 1 influence) with 2 trade goods in hand: the
    // primary pays the objective's 3, the trade goods in hand cover the influence its planets lack, and the home
    // stays unspent. With no trade goods in hand the forecast covers 2 of the 3 influence, and the home still stays.
    @Test
    void keepsTheHomeForAmassWealthWhenTheTradePrimaryAndHeldGoodsCoverIt() {
        test.game.getRevealedPublicObjectives().remove("monument");
        test.game.getRevealedPublicObjectives().put("amass_wealth", 1);
        test.place("10", "201");
        test.nekro.addPlanet("wrenterra");
        test.nekro.addSC(TRADE);
        test.nekro.setTg(2);

        assertThat(ScoringReserve.reserved(test.game, test.nekro)).hasValueSatisfying(reserved -> {
            assertThat(reserved.objectiveId()).isEqualTo("amass_wealth");
            assertThat(reserved.cost()).isEqualTo(new SpendCost(3, 3, 0, 0));
        });
        assertThat(ScoringReserve.planAfterReserve(test.game, test.nekro, SpendCost.resources(4)))
                .isEmpty();

        test.nekro.setTg(0);

        assertThat(ScoringReserve.reserved(test.game, test.nekro)).hasValueSatisfying(reserved -> {
            assertThat(reserved.cost()).isEqualTo(new SpendCost(3, 1, 0, 0));
            assertThat(reserved.expectedTradeGoods()).isEqualTo(5);
        });
        assertThat(ScoringReserve.planAfterReserve(test.game, test.nekro, SpendCost.resources(4)))
                .isEmpty();
    }

    // A Sol-like holder (Jord 4 resources and 2 influence, plus a 3 influence planet): Jord pays the resources, the
    // other planet the influence, and the primary the trade goods, so units may not be built from either.
    @Test
    void keepsASolLikeHomeForAmassWealth() {
        test.game.getRevealedPublicObjectives().remove("monument");
        test.game.getRevealedPublicObjectives().put("amass_wealth", 1);
        test.place("01", "201");
        test.place("34", "202");
        test.nekro.removePlanet("mordaiii");
        test.nekro.addPlanet("jord");
        test.nekro.addPlanet("centauri");
        test.nekro.addSC(TRADE);
        test.nekro.setTg(0);

        assertThat(ScoringReserve.reserved(test.game, test.nekro)).hasValueSatisfying(reserved -> {
            assertThat(reserved.objectiveId()).isEqualTo("amass_wealth");
            assertThat(reserved.cost()).isEqualTo(new SpendCost(3, 3, 0, 0));
        });
        assertThat(ScoringReserve.planAfterReserve(test.game, test.nekro, SpendCost.resources(4)))
                .isEmpty();
    }

    // Outside the action phase there is nothing to protect.
    @Test
    void noProjectionOutsideTheActionPhase() {
        test.nekro.addSC(TRADE);
        test.game.setPhaseOfGame("strategy");

        assertThat(ScoringReserve.reserved(test.game, test.nekro)).isEmpty();
    }

    // Expected trade goods first cover the objective's own trade good part. Planets cover resources and influence, and
    // a spare expected trade good only fills what the planets cannot.
    @Test
    void netCostLeavesResourcesAndInfluenceToThePlanetsFirst() {
        assertThat(ExpectedTradeGoods.netOf(SpendCost.each(3), 4, 1, cost -> true))
                .contains(new SpendCost(3, 3, 0, 0));
        assertThat(ExpectedTradeGoods.netOf(SpendCost.each(3), 4, 1, cost -> cost.influence() <= 2))
                .contains(new SpendCost(3, 2, 0, 0));
        assertThat(ExpectedTradeGoods.netOf(SpendCost.each(3), 4, 1, cost -> cost.resources() <= 2))
                .contains(new SpendCost(2, 3, 0, 0));
        assertThat(ExpectedTradeGoods.netOf(SpendCost.each(3), 4, 1, cost -> cost.influence() <= 1))
                .isEmpty();
        assertThat(ExpectedTradeGoods.netOf(SpendCost.resources(8), 3, 2, cost -> cost.resources() <= 2))
                .contains(SpendCost.resources(2));
        assertThat(ExpectedTradeGoods.netOf(SpendCost.tradeGoods(5), 3, 1, cost -> true))
                .contains(SpendCost.tradeGoods(2));
        assertThat(ExpectedTradeGoods.netOf(SpendCost.tokens(3), 3, 1, cost -> true))
                .contains(SpendCost.tokens(3));
        assertThat(ExpectedTradeGoods.tradeGoodsUsed(SpendCost.resources(8), SpendCost.resources(2), 3, 2))
                .isEqualTo(3);
        assertThat(ExpectedTradeGoods.tradeGoodsUsed(SpendCost.tradeGoods(5), SpendCost.NONE, 9, 1))
                .isEqualTo(5);
        assertThat(ExpectedTradeGoods.netOf(SpendCost.resources(5), 0, 1, cost -> false))
                .isEmpty();
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

    // Readied planets and Mordai II cover the resources, and planets pay before trade goods, so none of the expected
    // income is spoken for and all of it stays free for other reserves such as the Gravity Drive follow.
    @Test
    void readiedPlanetsLeaveExpectedTradeGoodsForOtherReserves() {
        exhaustedCaptures();
        test.nekro.addSC(DIPLOMACY);
        test.nekro.addSC(TRADE);

        assertThat(ScoringReserve.reserved(test.game, test.nekro)).hasValueSatisfying(reserved -> {
            assertThat(reserved.cost()).isEqualTo(SpendCost.resources(3));
            assertThat(reserved.expectedTradeGoods()).isZero();
        });
        assertThat(ScoringReserve.expectedTradeGoodsCounted(test.game, test.nekro))
                .isZero();
    }
}

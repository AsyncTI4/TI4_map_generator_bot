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

    // Holding the unplayed Trade card: the 3 trade goods of the primary make 4 + 1 + 3 = 8, so the seat must keep 5
    // resources (Mordai II and its trade good) and cannot spend the planet on units. Commodities it might wash are not
    // counted.
    @Test
    void keepsResourcesForAMonumentTheTradeCardWillFund() {
        test.nekro.addSC(TRADE);

        assertThat(ExpectedTradeGoods.beforeStatus(test.game, test.nekro)).isEqualTo(3);
        assertThat(ScoringReserve.reserved(test.game, test.nekro)).hasValueSatisfying(reserved -> {
            assertThat(reserved.objectiveId()).isEqualTo("monument");
            assertThat(reserved.cost()).isEqualTo(SpendCost.resources(5));
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

    // Commodities in hand are only counted for a Masters of Trade seat, whose even wash with the Trade holder is
    // certain;
    // for any other seat a wash depends on a partner and a fee.
    @Test
    void countsOnlyAMastersOfTradeWash() {
        test.sol.addSC(TRADE);
        test.game.setSCPlayed(TRADE, true);
        test.nekro.setCommodities(3);

        assertThat(ExpectedTradeGoods.beforeStatus(test.game, test.nekro)).isZero();
        assertThat(ScoringReserve.reserved(test.game, test.nekro)).isEmpty();

        test.nekro.addAbility("master_of_trade");

        assertThat(ExpectedTradeGoods.beforeStatus(test.game, test.nekro)).isEqualTo(3);
        assertThat(ScoringReserve.reserved(test.game, test.nekro))
                .hasValueSatisfying(reserved -> assertThat(reserved.cost()).isEqualTo(SpendCost.resources(5)));
    }

    // Before Trade is played a Masters of Trade seat washes no more than the Trade holder has commodities to give.
    @Test
    void theEvenWashIsLimitedByTheTradeHoldersCommodities() {
        test.sol.addSC(TRADE);
        test.sol.setCommoditiesBase(4);
        test.nekro.setCommoditiesBase(6);
        test.nekro.addAbility("master_of_trade");

        assertThat(ExpectedTradeGoods.beforeStatus(test.game, test.nekro)).isEqualTo(4);

        test.nekro.addSC(TRADE);
        test.sol.removeSC(TRADE);

        assertThat(ExpectedTradeGoods.beforeStatus(test.game, test.nekro)).isEqualTo(3);
    }

    // Amass Wealth for a seat whose washed trade goods exceed the 3 the objective asks for: the spare one has to pay
    // influence (Mordai II has none), not resources, or the seat's planets could not pay and nothing would be kept.
    @Test
    void sparesExpectedTradeGoodsForInfluenceWhenResourcesAreAbundant() {
        test.game.getRevealedPublicObjectives().remove("monument");
        test.game.getRevealedPublicObjectives().put("amass_wealth", 1);
        test.nekro.setTg(0);
        test.place("10", "201");
        test.place("14", "202");
        test.nekro.addPlanet("wrenterra");
        test.nekro.addPlanet("archontau");
        test.sol.addSC(TRADE);
        test.sol.setCommoditiesBase(4);
        test.nekro.setCommoditiesBase(6);
        test.nekro.addAbility("master_of_trade");

        assertThat(ExpectedTradeGoods.netOf(SpendCost.each(3), 4, 1)).isEqualTo(new SpendCost(2, 3, 0, 0));
        assertThat(ExpectedTradeGoods.netOfInfluenceFirst(SpendCost.each(3), 4, 1))
                .isEqualTo(new SpendCost(3, 2, 0, 0));
        assertThat(ScoringReserve.reserved(test.game, test.nekro)).hasValueSatisfying(reserved -> {
            assertThat(reserved.objectiveId()).isEqualTo("amass_wealth");
            assertThat(reserved.cost()).isEqualTo(new SpendCost(3, 2, 0, 0));
            assertThat(reserved.expectedTradeGoods()).isEqualTo(4);
        });
    }

    // A Letnev-like holder of the unplayed Trade card (6 resources, 1 influence) with 2 trade goods in hand: the
    // primary pays the objective's 3, the trade goods cover the influence its planets lack, and the home stays unspent.
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

        assertThat(ScoringReserve.reserved(test.game, test.nekro)).isEmpty();
        assertThat(ScoringReserve.planAfterReserve(test.game, test.nekro, SpendCost.resources(4)))
                .isPresent();
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

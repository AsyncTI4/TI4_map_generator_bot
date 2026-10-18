package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.expedition.ExpeditionKind;
import ti4.ai.expedition.ExpeditionOutlook;
import ti4.ai.expedition.ExpeditionUnlock;
import ti4.ai.expedition.Holdings;
import ti4.ai.strategy.CardPlan.Benefit;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

// Strategy cards that hand the seat what an expedition slice costs (trade goods, action cards, spent planets) or let it
// claim a contested slice first are worth the expedition they unlock, net of what the seat could already afford.
// The seat is Mahact, whose breakthrough lifts its win rate by about 5 points and is worth 4.6 while locked.
class ExpeditionCardUsesTest extends BaseTi4Test {

    private static final int LEADERSHIP = 1;
    private static final int DIPLOMACY = 2;
    private static final int POLITICS = 3;
    private static final int TRADE = 5;

    private AiTestGame test;
    private Player seat;
    private long modified = 1;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        test.game.setVp(10);
        seat = test.nekro;
        seat.setFaction(test.game, "mahact");
        seat.addBreakthrough("mahactbt");
        seat.exhaustPlanet("mordaiii");
        test.sol.addBreakthrough("solbt");
        changed();
    }

    private void changed() {
        test.game.setLastModifiedDate(modified++);
    }

    private CardPlan plan(int initiative, PlanMoment moment) {
        return StrategyCardPlanner.plan(PlanInputs.of(test.game, seat, moment), initiative);
    }

    private double value(int initiative, CardUse use, PlanMoment moment) {
        return plan(initiative, moment).benefit(use).map(Benefit::value).orElse(0.0);
    }

    private void readySevenResources() {
        test.place("38", "201");
        seat.addPlanet("abyz");
        seat.refreshPlanet("mordaiii");
        changed();
    }

    private void exhaustSevenResources() {
        test.place("38", "201");
        seat.addPlanet("abyz");
        seat.exhaustPlanet("abyz");
        changed();
    }

    private void unlockTheBreakthrough() {
        seat.setBreakthroughUnlocked("mahactbt", true);
        changed();
    }

    private void everySliceClaimedBy(Player claimer) {
        for (ExpeditionKind kind : ExpeditionKind.values()) {
            test.game.getExpeditions().getExpeditionFactions().put(kind.id(), claimer.getFaction());
        }
        changed();
    }

    private double netWith(Holdings holdings) {
        return ExpeditionOutlook.best(test.game, seat, holdings)
                .map(ExpeditionOutlook.Choice::net)
                .orElse(0.0);
    }

    @Test
    void tradeUnlocksTheTradeGoodsSliceThatNothingElsePays() {
        Holdings now = Holdings.of(test.game, seat);
        assertThat(ExpeditionOutlook.best(test.game, seat, now)).isEmpty();

        double value = value(TRADE, CardUse.TRADE_EXPEDITION, PlanMoment.PICK);

        assertThat(value).isPositive();
        assertThat(value).isEqualTo(netWith(now.withWallet(now.wallet().withTradeGoods(3))));
    }

    // Seven ready resources pay for five nearly for free as the seat passes, and no rival can take that slice: the
    // three trade goods would only be a dearer way to the same slice.
    @Test
    void tradeAddsNothingWhenWaitingForThePassPaysForASliceAnyway() {
        readySevenResources();

        assertThat(ExpeditionOutlook.best(
                        test.game, seat, Holdings.of(test.game, seat).atPassing()))
                .isPresent();
        assertThat(value(TRADE, CardUse.TRADE_EXPEDITION, PlanMoment.PICK)).isZero();
    }

    // Two rivals could also pay for five resources, so waiting for the pass may lose the slice and the trade goods'
    // slice is worth having now.
    @Test
    void tradeIsWorthTheTradeGoodsSliceWhenRivalsCouldTakeTheCheaperOne() {
        readySevenResources();
        twoRivalsWithSevenResources();

        assertThat(value(TRADE, CardUse.TRADE_EXPEDITION, PlanMoment.PICK)).isPositive();
    }

    private void twoRivalsWithSevenResources() {
        Player mentak = test.addSeat("200000000000000010", "mentak", "red");
        for (Player rival : java.util.List.of(test.sol, mentak)) {
            rival.addBreakthrough(rival.getFaction() + "bt");
            rival.addPlanet("abyz");
            rival.addPlanet("mordaiii");
        }
        changed();
    }

    @Test
    void tradeLeavesTheTradeGoodsOneForOneAlone() {
        assertThat(plan(TRADE, PlanMoment.PICK).benefit(CardUse.TRADE_GOODS))
                .map(Benefit::value)
                .contains(3.0);
    }

    @Test
    void politicsMakesTheActionCardSlicePayableWithTheCardsItDraws() {
        Holdings now = Holdings.of(test.game, seat);
        assertThat(ExpeditionOutlook.best(test.game, seat, now)).isEmpty();

        double value = value(POLITICS, CardUse.POLITICS_EXPEDITION, PlanMoment.PICK);

        assertThat(value).isPositive();
        assertThat(value).isEqualTo(netWith(now.withActionCards(2)));
    }

    @Test
    void politicsAddsNothingWhenPlanetsAlreadyPayForASlice() {
        readySevenResources();

        assertThat(value(POLITICS, CardUse.POLITICS_EXPEDITION, PlanMoment.PICK))
                .isZero();
    }

    // Seven exhausted resources cannot pay for anything; readying them makes five resources payable.
    @Test
    void diplomacyRefundsThePlanetsThatMakeASlicePayable() {
        exhaustSevenResources();
        seat.setTacticalCC(0);
        changed();

        assertThat(ExpeditionOutlook.best(test.game, seat)).isEmpty();
        assertThat(value(DIPLOMACY, CardUse.DIPLOMACY_EXPEDITION, PlanMoment.ACTION))
                .isPositive();
    }

    @Test
    void diplomacyCountsOnlyWhatReadyingDoesNotAlreadyEarn() {
        exhaustSevenResources();
        seat.setTacticalCC(0);
        changed();

        CardPlan plan = plan(DIPLOMACY, PlanMoment.ACTION);
        double unlocked = ExpeditionUnlock.afterReadying(test.game, seat, seat.getExhaustedPlanets())
                .orElseThrow()
                .gain();
        double ready = plan.benefit(CardUse.DIPLOMACY_READY).map(Benefit::value).orElse(0.0);

        assertThat(plan.benefit(CardUse.DIPLOMACY_EXPEDITION))
                .map(Benefit::value)
                .contains(Math.max(0, unlocked - ready));
    }

    // At pick time the seat expects to exhaust the cheapest planets on its first turn and have Diplomacy return them.
    // With no tactic token left the planets are worth a middle rate, and two rivals make waiting for the pass risky.
    @Test
    void diplomacyExpectsToRefundThePlanetsTheFirstClaimExhausts() {
        readySevenResources();
        twoRivalsWithSevenResources();
        seat.setTacticalCC(0);
        changed();

        ExpeditionOutlook.Choice first =
                ExpeditionOutlook.claimNow(test.game, seat).orElseThrow();

        assertThat(first.kind()).isEqualTo(ExpeditionKind.FIVE_RES);
        assertThat(ExpeditionUnlock.refundOfFirstClaim(test.game, seat)).isPositive();
        assertThat(ExpeditionUnlock.refundOfFirstClaim(test.game, seat)).isLessThanOrEqualTo(first.cost());
    }

    @Test
    void diplomacyAddsNothingWithoutAnExhaustedPlanetToReady() {
        readySevenResources();

        assertThat(value(DIPLOMACY, CardUse.DIPLOMACY_EXPEDITION, PlanMoment.ACTION))
                .isZero();
    }

    @Test
    void leadershipTakesTheSecretSliceFirstWhenTheSecretIsBadAndARivalCouldTakeIt() {
        seat.setSecret("sb");
        seat.setSecret("dts");
        seat.setSecret("btv");
        seat.setSecret("mtm");
        test.sol.setSecret("sb");
        changed();

        assertThat(ExpeditionOutlook.best(test.game, seat))
                .hasValueSatisfying(choice -> assertThat(choice.kind()).isEqualTo(ExpeditionKind.SECRET));
        assertThat(value(LEADERSHIP, CardUse.LEADERSHIP_EXPEDITION, PlanMoment.PICK))
                .isPositive();
    }

    @Test
    void leadershipLeavesTheSecretSliceAloneWhenTheSecretIsWorthKeeping() {
        test.sol.addOwnedPromissoryNoteByID("blue_cf");
        seat.setPromissoryNote("blue_cf");
        seat.addPromissoryNoteToPlayArea("blue_cf");
        seat.setSecret("sb");
        test.sol.setSecret("sb");
        changed();

        assertThat(ExpeditionOutlook.options(test.game, seat, Holdings.of(test.game, seat)))
                .filteredOn(choice -> choice.kind() == ExpeditionKind.SECRET)
                .singleElement()
                .satisfies(choice -> assertThat(choice.cost()).isGreaterThan(1.0));
        assertThat(value(LEADERSHIP, CardUse.LEADERSHIP_EXPEDITION, PlanMoment.PICK))
                .isZero();
    }

    @Test
    void leadershipAddsNothingWhenNoRivalCouldClaimTheSlice() {
        seat.setSecret("sb");
        seat.setSecret("dts");
        seat.setSecret("btv");
        seat.setSecret("mtm");
        changed();

        assertThat(test.sol.getSecretsUnscored()).isEmpty();
        assertThat(value(LEADERSHIP, CardUse.LEADERSHIP_EXPEDITION, PlanMoment.PICK))
                .isZero();
    }

    @Test
    void noCardIsWorthAnExpeditionOnceTheBreakthroughIsUnlocked() {
        readySevenResources();
        seat.setSecret("sb");
        test.sol.setSecret("sb");
        unlockTheBreakthrough();

        assertThat(expeditionValueOfEveryCard()).isZero();
    }

    @Test
    void noCardIsWorthAnExpeditionOnceEverySliceIsClaimed() {
        seat.setSecret("sb");
        test.sol.setSecret("sb");
        everySliceClaimedBy(test.sol);

        assertThat(expeditionValueOfEveryCard()).isZero();
    }

    @Test
    void aTradeCardThatPaysForASliceIsPlayedNow() {
        CardPlan plan = plan(TRADE, PlanMoment.ACTION);

        assertThat(plan.benefit(CardUse.TRADE_EXPEDITION)).isPresent();
        assertThat(CardTiming.of(PlanInputs.of(test.game, seat, PlanMoment.ACTION), plan))
                .isEqualTo(CardTiming.Timing.NOW);
    }

    @Test
    void aTradeCardWithNoExpeditionToPayForWaitsAsBefore() {
        unlockTheBreakthrough();
        CardPlan plan = plan(TRADE, PlanMoment.ACTION);

        assertThat(plan.benefit(CardUse.TRADE_EXPEDITION)).isEmpty();
        assertThat(CardTiming.of(PlanInputs.of(test.game, seat, PlanMoment.ACTION), plan))
                .isEqualTo(CardTiming.Timing.FALLBACK);
    }

    private double expeditionValueOfEveryCard() {
        double total = 0;
        for (PlanMoment moment : PlanMoment.values()) {
            total += value(TRADE, CardUse.TRADE_EXPEDITION, moment)
                    + value(POLITICS, CardUse.POLITICS_EXPEDITION, moment)
                    + value(DIPLOMACY, CardUse.DIPLOMACY_EXPEDITION, moment)
                    + value(LEADERSHIP, CardUse.LEADERSHIP_EXPEDITION, moment);
        }
        return total;
    }
}

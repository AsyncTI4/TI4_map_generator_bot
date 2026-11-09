package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiTestGame;
import ti4.ai.eval.GameHorizon;
import ti4.ai.scoring.WinRace;
import ti4.ai.strategy.CardValueScale;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.ai.tactical.TokenPrice.Prices;
import ti4.testUtils.BaseTi4Test;

// A tactical action spends a command token that would otherwise carry into next round, worth 3 influence
// (CardValueScale.HELD_TOKEN, 2.4 TG). A plan is taken only when its score plus what passing now would forgo (the
// stall while a rival still acts) covers that price. For a non-build plan, a token spent while the seat holds more than
// the standard 3 tactic tokens is priced at the TOKEN_FLOOR, and tokens are free in the last round or when a rival is
// set to win first.
class TokenPriceTest extends BaseTi4Test {

    private static final double STALL = Stall.ACTION_VALUE;
    private static final int STANDARD = TokenPrice.STANDARD_TACTIC;
    private static final int IMPERIAL = 8;
    private static final Prices WHILE_RIVALS_ACT =
            new Prices(CardValueScale.HELD_TOKEN, CardValueScale.TOKEN_FLOOR, STALL);
    private static final Prices AFTER_RIVALS_PASSED =
            new Prices(CardValueScale.HELD_TOKEN, CardValueScale.TOKEN_FLOOR, 0);

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.aiIsActive("action");
        test.nekroHome();
    }

    private static TacticalPlan plan(Kind kind, String target, double score) {
        return new TacticalPlan(kind, target, List.of(), Map.of(), score);
    }

    @Test
    void aOnePointMoveIsTakenWhileARivalStillActs() {
        TacticalPlan move = plan(Kind.EXPAND, "203", 1.0);

        assertThat(TokenPrice.worthTheirToken(List.of(move), 1, WHILE_RIVALS_ACT))
                .containsExactly(move);
    }

    // The audit case: every rival has passed, so acting now forgoes nothing, and a 1.0 move is not worth a token
    // that carries over.
    @Test
    void aOnePointMoveIsNotWorthATokenOnceEveryRivalHasPassed() {
        TacticalPlan move = plan(Kind.EXPAND, "203", 1.0);

        assertThat(TokenPrice.worthTheirToken(List.of(move), 1, AFTER_RIVALS_PASSED))
                .isEmpty();
    }

    @Test
    void aPlanWorthTheHeldTokenIsTakenAfterEveryRivalHasPassed() {
        TacticalPlan move = plan(Kind.EXPAND, "203", CardValueScale.HELD_TOKEN);

        assertThat(TokenPrice.worthTheirToken(List.of(move), 1, AFTER_RIVALS_PASSED))
                .containsExactly(move);
    }

    // Old behaviour let any non-build plan through once tokens outnumbered worthwhile moves. Now a surplus token
    // still costs the TOKEN_FLOOR, so a 0.5 position move after every rival has passed is declined.
    @Test
    void spareTokensNoLongerBuyAMinorMoveAfterEveryRivalHasPassed() {
        TacticalPlan advance = plan(Kind.POSITION, "203", 0.5);

        assertThat(TacticalPlanner.withinTokenBudget(List.of(advance), 3)).containsExactly(advance);
        assertThat(TokenPrice.worthTheirToken(List.of(advance), 3, AFTER_RIVALS_PASSED))
                .isEmpty();
    }

    @Test
    void anAbundantTokenStillBuysAUsefulSetupMoveAfterEveryRivalHasPassed() {
        TacticalPlan advance = plan(Kind.POSITION, "203", CardValueScale.TOKEN_FLOOR);

        assertThat(TokenPrice.worthTheirToken(List.of(advance), 3, AFTER_RIVALS_PASSED))
                .isEmpty();
        assertThat(TokenPrice.worthTheirToken(List.of(advance), 4, AFTER_RIVALS_PASSED))
                .containsExactly(advance);
    }

    // The audit case: 4 tokens and 2 worthwhile targets. Those two rank first and are taken, which leaves 2 tokens,
    // within the standard count, so a rival still acting no longer makes a 0.3 move worth one of them: 0.3 plus the
    // 1.5 stall is short of the 2.4 a carried token is worth.
    @Test
    void aCheapMoveIsNotWorthAStandardTokenWhileARivalStillActs() {
        TacticalPlan advance = plan(Kind.POSITION, "203", 0.3);

        assertThat(TacticalPlanner.withinTokenBudget(List.of(advance), 2)).containsExactly(advance);
        assertThat(TokenPrice.worthTheirToken(List.of(advance), 2, WHILE_RIVALS_ACT))
                .isEmpty();
        assertThat(TokenPrice.worthTheirToken(List.of(advance), STANDARD, WHILE_RIVALS_ACT))
                .isEmpty();
    }

    // Conservative case: with more tactic tokens than the standard 3, spending one still leaves the standard count
    // to carry over (plus the status-phase gain), so the spent token is priced at the TOKEN_FLOOR. While a rival
    // still acts, passing would forgo the stall, so a cheap positional move beats passing early.
    @Test
    void anAbundantTokenStillBuysACheapMoveWhileARivalStillActs() {
        TacticalPlan advance = plan(Kind.POSITION, "203", 0.3);

        assertThat(TokenPrice.worthTheirToken(List.of(advance), STANDARD + 1, WHILE_RIVALS_ACT))
                .containsExactly(advance);
    }

    @Test
    void aMinorBuildIsNeverWorthASpareToken() {
        TacticalPlan build = plan(Kind.PRODUCE, "304", 0.5);

        assertThat(TokenPrice.worthTheirToken(List.of(build), STANDARD + 3, WHILE_RIVALS_ACT))
                .isEmpty();
    }

    // The spare-token test no longer depends on how many targets the round offers.
    @Test
    void abundanceIsAboutTokensHeldNotTargetsOnOffer() {
        TacticalPlan advance = plan(Kind.POSITION, "203", 0.3);
        TacticalPlan worthwhile = plan(Kind.EXPAND, "204", CardValueScale.HELD_TOKEN);

        assertThat(TokenPrice.worthTheirToken(List.of(worthwhile, advance), 2, WHILE_RIVALS_ACT))
                .containsExactly(worthwhile);
    }

    @Test
    void tokensAreFreeWhenTheyLoseTheirWorth() {
        TacticalPlan build = plan(Kind.PRODUCE, "304", 0.3);

        assertThat(TokenPrice.worthTheirToken(List.of(build), 1, Prices.FREE)).containsExactly(build);
    }

    @Test
    void theStallCountsOnlyWhileARivalStillActs() {
        assertThat(TokenPrice.of(test.game, test.nekro).stall()).isEqualTo(STALL);

        test.sol.setPassed(true);

        assertThat(TokenPrice.of(test.game, test.nekro).stall()).isZero();
    }

    @Test
    void aTokenCostsTheHeldPriceEarlyInTheGame() {
        assertThat(TokenPrice.of(test.game, test.nekro).held()).isEqualTo(CardValueScale.HELD_TOKEN);
    }

    @Test
    void aTokenIsFreeInTheLastRound() {
        test.game.setVp(10);
        test.points(test.sol, 8);

        assertThat(TokenPrice.of(test.game, test.nekro).held()).isZero();
    }

    // Sol on 7 of 10 holds an unplayed Imperial and Mecatol Rex, with the 6 tokens for Galvanize the People ready: the
    // Imperial primary scores it (2) plus the Mecatol point in this action phase. The leader is 3 points short, so the
    // round horizon alone (roundsLeft 2) would keep the token price; the projected winner makes tokens free.
    @Test
    void aTokenIsFreeWhenARivalIsProjectedToWinFirst() {
        test.game.setVp(10);
        test.game.setRound(5);
        test.solHomeBeside(AiTestGame.neighbourOf(AiTestGame.HOME));
        for (int card = 1; card <= IMPERIAL; card++) test.game.setScTradeGood(card, 0);
        test.game.getRevealedPublicObjectives().put("galvanize", 1);
        test.points(test.sol, 7);

        assertThat(TokenPrice.of(test.game, test.nekro).held()).isEqualTo(CardValueScale.HELD_TOKEN);

        test.place("18", "000");
        test.sol.addPlanet("mr");
        test.sol.addSC(IMPERIAL);
        test.sol.setTacticalCC(3);
        test.sol.setStrategicCC(3);

        assertThat(GameHorizon.roundsLeft(test.game)).isEqualTo(2);
        assertThat(WinRace.winnersBefore(test.game, test.nekro)).containsExactly(test.sol);
        assertThat(TokenPrice.of(test.game, test.nekro).held()).isZero();
        assertThat(TokenPrice.of(test.game, test.nekro).surplus()).isZero();
    }

    // A seat racing for Prove Endurance values the stall above the plain 1.5 (Endurance.stallBonus), so passing
    // forgoes more and a cheaper plan clears the token price.
    @Test
    void aBiggerStallLowersTheBar() {
        TacticalPlan build = plan(Kind.PRODUCE, "304", 0.5);
        Prices racingForEndurance = new Prices(CardValueScale.HELD_TOKEN, CardValueScale.TOKEN_FLOOR, STALL + 0.5);

        assertThat(TokenPrice.worthTheirToken(List.of(build), 1, WHILE_RIVALS_ACT))
                .isEmpty();
        assertThat(TokenPrice.worthTheirToken(List.of(build), 1, racingForEndurance))
                .containsExactly(build);
    }

    @Test
    void thePlannerKeepsTheOldBudgetWhenTheExperimentIsOff() {
        test.sol.setPassed(true);
        TacticalPlan move = plan(Kind.EXPAND, "203", 1.0);
        List<TacticalPlan> plans = List.of(move);

        assertThat(TacticalPlanner.affordable(test.game, test.nekro, plans, 1)).isEmpty();

        AiExperiments.disable(test.nekro, EnumSet.of(Experiment.TOKEN_PRICE));

        assertThat(TacticalPlanner.affordable(test.game, test.nekro, plans, 1))
                .isEqualTo(TacticalPlanner.withinTokenBudget(plans, 1))
                .containsExactly(move);
    }
}

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
import ti4.ai.strategy.CardValueScale;
import ti4.ai.tactical.TacticalPlan.Kind;
import ti4.ai.tactical.TokenPrice.Prices;
import ti4.testUtils.BaseTi4Test;

// A tactical action spends a command token that would otherwise carry into next round, worth 3 influence
// (CardValueScale.HELD_TOKEN, 2.4 TG). A plan is taken only when its score plus what passing now would forgo (the
// stall while a rival still acts) covers that price. Tokens beyond the round's worthwhile plans are priced at the
// TOKEN_FLOOR, and tokens are free in the last round or when a rival is set to win first.
class TokenPriceTest extends BaseTi4Test {

    private static final double STALL = Stall.ACTION_VALUE;
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
    void spareTokensStillBuyAUsefulSetupMoveAfterEveryRivalHasPassed() {
        TacticalPlan advance = plan(Kind.POSITION, "203", CardValueScale.TOKEN_FLOOR);

        assertThat(TokenPrice.worthTheirToken(List.of(advance), 3, AFTER_RIVALS_PASSED))
                .containsExactly(advance);
    }

    // Conservative case: tokens to spare and a rival still acting, so a cheap positional move beats passing early.
    @Test
    void spareTokensStillBuyACheapMoveWhileARivalStillActs() {
        TacticalPlan advance = plan(Kind.POSITION, "203", 0.3);

        assertThat(TokenPrice.worthTheirToken(List.of(advance), 3, WHILE_RIVALS_ACT))
                .containsExactly(advance);
    }

    @Test
    void aMinorBuildIsNeverWorthASpareToken() {
        TacticalPlan build = plan(Kind.PRODUCE, "304", 0.5);

        assertThat(TokenPrice.worthTheirToken(List.of(build), 3, WHILE_RIVALS_ACT))
                .isEmpty();
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

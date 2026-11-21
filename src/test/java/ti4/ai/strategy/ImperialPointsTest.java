package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiTestGame;
import ti4.ai.eval.DecidingSeat;
import ti4.ai.scoring.ObjectiveOdds;
import ti4.ai.scoring.ScoringStakes;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Imperial is priced by the points it adds and by whether they decide the game, for the seat and for any rival who
// picks after it (experiment IMPERIAL_POINTS). Both publics used here are 1 VP: Raise a Fleet (five non-fighter ships
// in one system, met on the board: 0.9 by status) and Erect a Monument (8 resources, affordable now: 0.72).
// Card text assumed (LRR 45.2): the primary scores one public objective the player meets, then gives 1 VP to the
// Mecatol Rex holder. A status phase scores at most one public and one secret (LRR 61.6).
class ImperialPointsTest extends BaseTi4Test {

    private static final int LEADERSHIP = 1;
    private static final int IMPERIAL = 8;
    private static final String MECATOL = "000";

    private AiTestGame test;
    private Tile nekroHome;
    private long modified = 1;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        nekroHome = test.nekroHome();
        test.game.setVp(10);
        test.game.setSpeaker(test.nekro);
        changed();
    }

    private void changed() {
        test.game.setLastModifiedDate(modified++);
    }

    private void reveal(String objective) {
        test.game
                .getRevealedPublicObjectives()
                .put(objective, test.game.getRevealedPublicObjectives().size() + 1);
    }

    private void disableExperiment(Player seat) {
        AiExperiments.disable(seat, Set.of(AiExperiments.Experiment.IMPERIAL_POINTS));
        changed();
    }

    private CardPlan plan(int initiative) {
        return StrategyCardPlanner.plan(PlanInputs.of(test.game, test.nekro, PlanMoment.PICK), initiative);
    }

    private double value(int initiative) {
        return StrategyCardRanking.value(test.game, test.nekro, initiative);
    }

    private double denial() {
        return StrategyCardRanking.imperialDenial(test.game, test.nekro);
    }

    private void revealBothPublics() {
        reveal("raise_fleet");
        reveal("monument");
        changed();
    }

    private void meetsBothPublics(Player player, Tile home, int tradeGoods) {
        test.units(home, "space", player, UnitType.Dreadnought, 5);
        player.setTg(tradeGoods);
        changed();
    }

    private Tile solHome() {
        return test.solHomeBeside(AiTestGame.neighbourOf(AiTestGame.HOME));
    }

    private void solHoldsMecatol() {
        test.place("18", MECATOL);
        test.sol.addPlanet("mr");
        changed();
    }

    // Sol (a human) sits at 8 VP with both publics met and picks after the AI: with Imperial Sol scores one now and
    // the other at status and wins (0.9: Raise a Fleet holds once Monument is scored with the primary); without it Sol
    // stops at 9. The AI only cares while Imperial gives it point tempo of its own: with no fleet and no trade goods
    // it meets neither public and holds no Mecatol Rex, so blocking Sol alone is not worth the pick.
    @Test
    void deniesImperialOnlyWhenTheAiGainsPointsFromItToo() {
        revealBothPublics();
        test.points(test.sol, 8);
        meetsBothPublics(test.sol, solHome(), 4);
        assertThat(denial()).isZero();

        meetsBothPublics(test.nekro, nekroHome, 4);
        // A status-phase win can still be stopped by the table before it is scored, so it takes the denial share.
        assertThat(denial()).isCloseTo(CardValueScale.DENIAL_SHARE * ScoringStakes.WIN_VALUE * 0.9, within(1e-9));
        assertThat(value(IMPERIAL)).isGreaterThan(value(LEADERSHIP));

        test.sol.addSC(LEADERSHIP);
        assertThat(denial()).isZero();

        test.sol.getSCs().clear();
        disableExperiment(test.nekro);
        assertThat(denial()).isZero();
    }

    // Sol at 9 VP holds Mecatol Rex and no one can reach it: Imperial's point ends the game in the action phase before
    // anyone can react, so denying it is worth the whole win. Once a Nekro dreadnought can reach Mecatol Rex the
    // point can be taken away first, and only the denial share of Sol's win swing counts. Sol's publics: Monument
    // three resources short (Jord 4 + Mecatol Rex 1, 0.45) and Raise a Fleet five ships short (0.12), so without
    // Imperial Sol reaches 10 with chance 1 - 0.55 x 0.88.
    @Test
    void deniesTheWholeWinOnlyWhenNoOneCanStopIt() {
        revealBothPublics();
        meetsBothPublics(test.nekro, nekroHome, 4);
        solHome();
        solHoldsMecatol();
        test.points(test.sol, 9);
        changed();
        assertThat(denial()).isEqualTo(ScoringStakes.WIN_VALUE);

        Tile next = test.place("26", AiTestGame.neighbourOf(MECATOL));
        test.units(next, "space", test.nekro, UnitType.Dreadnought, 1);
        changed();
        assertThat(denial())
                .isCloseTo(CardValueScale.DENIAL_SHARE * ScoringStakes.WIN_VALUE * 0.55 * 0.88, within(1e-9));
    }

    // A rival far from the goal who would merely gain the Mecatol Rex point (and a small chance at an extra public)
    // gets the normal point-denial share: half a point, halved again while it is far from winning, as the old
    // Mecatol-holder denial priced it. The previous experiment charged 0.75 of a point for every later picker.
    @Test
    void pricesARivalsNormalPointAtThePointDenialShare() {
        revealBothPublics();
        meetsBothPublics(test.nekro, nekroHome, 4);
        solHome();
        solHoldsMecatol();

        double extra = ObjectiveOdds.imperial(test.game, test.sol, 1, 0).extraPoints();
        assertThat(extra).isCloseTo(0.45 * 0.12, within(1e-9));
        assertThat(denial())
                .isCloseTo(
                        CardValueScale.DENIAL_SHARE
                                * CardValueScale.VICTORY_POINT
                                * CardValueScale.DISTANT_DENIAL_SHARE
                                * (1 + extra),
                        within(1e-9));
    }

    // The seat at 8 VP with both publics met wins this round only with Imperial (0.9 that Raise a Fleet holds once
    // Monument is scored with the primary), so the extra public is worth most of the win rather than 0.9 of a point.
    @Test
    void valuesTheSeatsWinningImperialAsTheWin() {
        test.points(test.nekro, 8);
        revealBothPublics();
        meetsBothPublics(test.nekro, nekroHome, 4);

        assertThat(plan(IMPERIAL).benefit(CardUse.IMPERIAL_SCORE))
                .map(CardPlan.Benefit::value)
                .hasValueSatisfying(value -> assertThat(value).isCloseTo(0.9 * ScoringStakes.WIN_VALUE, within(1e-9)));

        disableExperiment(test.nekro);
        assertThat(plan(IMPERIAL).benefit(CardUse.IMPERIAL_SCORE))
                .map(CardPlan.Benefit::value)
                .hasValueSatisfying(
                        value -> assertThat(value).isCloseTo(0.9 * CardValueScale.VICTORY_POINT, within(1e-9)));
    }

    // The flag belongs to the deciding seat. A control Sol modelling the Nekro's Imperial keeps the old numbers even
    // though the Nekro runs the experiment, and an experiment Sol modelling a control Nekro uses the new ones.
    @Test
    void gatesOnTheDecidingSeatNotTheModelledSeat() {
        test.points(test.nekro, 8);
        revealBothPublics();
        meetsBothPublics(test.nekro, nekroHome, 4);

        disableExperiment(test.sol);
        assertThat(DecidingSeat.of(test.sol, () -> imperialScore()))
                .isCloseTo(0.9 * CardValueScale.VICTORY_POINT, within(1e-9));
        assertThat(DecidingSeat.of(test.nekro, () -> imperialScore()))
                .isCloseTo(0.9 * ScoringStakes.WIN_VALUE, within(1e-9));

        AiExperiments.disable(test.sol, Set.of());
        disableExperiment(test.nekro);
        assertThat(DecidingSeat.of(test.sol, () -> imperialScore()))
                .isCloseTo(0.9 * ScoringStakes.WIN_VALUE, within(1e-9));
        assertThat(DecidingSeat.of(test.nekro, () -> imperialScore()))
                .isCloseTo(0.9 * CardValueScale.VICTORY_POINT, within(1e-9));
    }

    private double imperialScore() {
        return plan(IMPERIAL)
                .benefit(CardUse.IMPERIAL_SCORE)
                .map(CardPlan.Benefit::value)
                .orElse(0.0);
    }

    // At 8 VP holding a threatened Mecatol Rex with Monument met now and Raise a Fleet one ship short: Imperial played
    // now wins at once (8 + Mecatol + Monument). The win premium must stay on the Mecatol point, whose play-now rule
    // covers a threatened Mecatol Rex; on the extra public it would wait for a second scorable public and risk the
    // planet. The old values kept the Mecatol point as the main benefit too.
    @Test
    void playsAWinningImperialAtOnceWhileMecatolIsThreatened() {
        test.place("18", MECATOL);
        test.nekro.addPlanet("mr");
        Tile next = test.place("26", AiTestGame.neighbourOf(MECATOL));
        test.units(next, "space", test.sol, UnitType.Dreadnought, 1);
        test.points(test.nekro, 8);
        reveal("raise_fleet");
        reveal("monument");
        test.units(nekroHome, "space", test.nekro, UnitType.Dreadnought, 4);
        test.nekro.setTg(8);
        changed();

        PlanInputs in = PlanInputs.of(test.game, test.nekro, PlanMoment.ACTION);
        assertThat(ImperialUses.holdsThreatenedMecatol(in)).isTrue();
        CardPlan imperial = StrategyCardPlanner.plan(in, IMPERIAL);
        assertThat(imperial.main()).map(CardPlan.Benefit::use).contains(CardUse.IMPERIAL_MECATOL);
        assertThat(imperial.benefit(CardUse.IMPERIAL_MECATOL))
                .map(CardPlan.Benefit::value)
                .contains(ScoringStakes.WIN_VALUE);
        assertThat(CardTiming.of(in, imperial)).isEqualTo(CardTiming.Timing.NOW);

        disableExperiment(test.nekro);
        PlanInputs old = PlanInputs.of(test.game, test.nekro, PlanMoment.ACTION);
        CardPlan oldImperial = StrategyCardPlanner.plan(old, IMPERIAL);
        assertThat(oldImperial.main()).map(CardPlan.Benefit::use).contains(CardUse.IMPERIAL_MECATOL);
        assertThat(CardTiming.of(old, oldImperial)).isEqualTo(CardTiming.Timing.NOW);
    }

    // Holding a safe Mecatol Rex at 9 VP, the Imperial point is the win: play it now rather than wait. The old timing
    // only played the Mecatol point at once when the planet was threatened.
    @Test
    void playsAWinningImperialAtOnceOnASafeMecatol() {
        test.place("18", MECATOL);
        test.nekro.addPlanet("mr");
        test.points(test.nekro, 9);
        changed();

        PlanInputs in = PlanInputs.of(test.game, test.nekro, PlanMoment.ACTION);
        assertThat(ImperialUses.holdsThreatenedMecatol(in)).isFalse();
        assertThat(CardTiming.of(in, StrategyCardPlanner.plan(in, IMPERIAL))).isEqualTo(CardTiming.Timing.NOW);

        disableExperiment(test.nekro);
        PlanInputs old = PlanInputs.of(test.game, test.nekro, PlanMoment.ACTION);
        assertThat(CardTiming.of(old, StrategyCardPlanner.plan(old, IMPERIAL))).isEqualTo(CardTiming.Timing.FALLBACK);
    }

    // Holding Mecatol Rex at 9 VP: the Imperial point is the win, with nothing to score.
    @Test
    void valuesAWinningMecatolPointAsTheWin() {
        test.place("18", MECATOL);
        test.nekro.addPlanet("mr");
        test.points(test.nekro, 9);
        changed();

        assertThat(plan(IMPERIAL).benefit(CardUse.IMPERIAL_MECATOL))
                .map(CardPlan.Benefit::value)
                .contains(ScoringStakes.WIN_VALUE);

        disableExperiment(test.nekro);
        assertThat(plan(IMPERIAL).benefit(CardUse.IMPERIAL_MECATOL))
                .map(CardPlan.Benefit::value)
                .contains(CardValueScale.VICTORY_POINT);
    }
}

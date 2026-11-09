package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiTestGame;
import ti4.ai.scoring.ScoringStakes;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Imperial is priced by the points it adds and by whether they decide the game, for the seat and for any rival who
// picks after it (experiment IMPERIAL_POINTS). Both publics used here are 1 VP: Raise a Fleet (five non-fighter ships
// in one system, met on the board: 0.9 by status) and Erect a Monument (8 resources, affordable now: 0.72).
class ImperialPointsTest extends BaseTi4Test {

    private static final int LEADERSHIP = 1;
    private static final int IMPERIAL = 8;

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

    private void disableExperiment() {
        AiExperiments.disable(test.nekro, Set.of(AiExperiments.Experiment.IMPERIAL_POINTS));
        changed();
    }

    private CardPlan plan(int initiative) {
        return StrategyCardPlanner.plan(PlanInputs.of(test.game, test.nekro, PlanMoment.PICK), initiative);
    }

    private double value(int initiative) {
        return StrategyCardRanking.value(test.game, test.nekro, initiative);
    }

    private void meetsBothPublics(Player player, Tile home, int tradeGoods) {
        reveal("raise_fleet");
        reveal("monument");
        test.units(home, "space", player, UnitType.Dreadnought, 5);
        player.setTg(tradeGoods);
        changed();
    }

    // The audit's losing case: Sol (a human) sits at 8 VP off Mecatol Rex with two publics met and picks after the AI.
    // With Imperial Sol scores one now and the other in the status phase and wins; without it Sol stops at 9. The old
    // denial only looked at Mecatol Rex holders, so it was zero here and the AI took another card.
    @Test
    void deniesImperialToARivalItWouldCarryToTheGoal() {
        Tile solHome = test.solHomeBeside(AiTestGame.neighbourOf(AiTestGame.HOME));
        test.points(test.sol, 8);
        meetsBothPublics(test.sol, solHome, 4);

        double denial = StrategyCardRanking.imperialDenial(test.game, test.nekro);
        assertThat(denial).isCloseTo(0.9 * ScoringStakes.WIN_VALUE, within(1e-9));
        assertThat(value(IMPERIAL)).isGreaterThan(value(LEADERSHIP));

        disableExperiment();
        assertThat(StrategyCardRanking.imperialDenial(test.game, test.nekro)).isZero();
    }

    // A rival far from the goal still gets a point price for the extra public, but no win premium.
    @Test
    void pricesARivalsExtraPublicAsAPointWhenItDoesNotWin() {
        Tile solHome = test.solHomeBeside(AiTestGame.neighbourOf(AiTestGame.HOME));
        meetsBothPublics(test.sol, solHome, 4);

        double denial = StrategyCardRanking.imperialDenial(test.game, test.nekro);
        assertThat(denial).isPositive().isLessThan(CardValueScale.VICTORY_POINT);

        test.sol.addSC(LEADERSHIP);
        assertThat(StrategyCardRanking.imperialDenial(test.game, test.nekro)).isZero();
    }

    // The seat at 8 VP with both publics met wins this round only with Imperial (0.9 that Raise a Fleet holds once
    // Monument is scored with the primary), so the extra public is worth most of the win rather than 0.9 of a point.
    @Test
    void valuesTheSeatsWinningImperialAsTheWin() {
        test.points(test.nekro, 8);
        meetsBothPublics(test.nekro, nekroHome, 4);

        assertThat(plan(IMPERIAL).benefit(CardUse.IMPERIAL_SCORE))
                .map(CardPlan.Benefit::value)
                .hasValueSatisfying(value -> assertThat(value).isCloseTo(0.9 * ScoringStakes.WIN_VALUE, within(1e-9)));

        disableExperiment();
        assertThat(plan(IMPERIAL).benefit(CardUse.IMPERIAL_SCORE))
                .map(CardPlan.Benefit::value)
                .hasValueSatisfying(
                        value -> assertThat(value).isCloseTo(0.9 * CardValueScale.VICTORY_POINT, within(1e-9)));
    }

    // Holding Mecatol Rex at 9 VP: the Imperial point is the win, with nothing to score.
    @Test
    void valuesAWinningMecatolPointAsTheWin() {
        test.place("18", "000");
        test.nekro.addPlanet("mr");
        test.points(test.nekro, 9);
        changed();

        assertThat(plan(IMPERIAL).benefit(CardUse.IMPERIAL_MECATOL))
                .map(CardPlan.Benefit::value)
                .contains(ScoringStakes.WIN_VALUE);

        disableExperiment();
        assertThat(plan(IMPERIAL).benefit(CardUse.IMPERIAL_MECATOL))
                .map(CardPlan.Benefit::value)
                .contains(CardValueScale.VICTORY_POINT);
    }
}

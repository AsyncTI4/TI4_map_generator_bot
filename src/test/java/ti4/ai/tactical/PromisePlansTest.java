package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiTestGame;
import ti4.ai.promise.Promise;
import ti4.ai.promise.PromiseBook;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The AI keeps its own promises: a plan that would activate a system the promise protects is never a candidate, and
// a seat that was promised peace no longer fears the promiser there.
class PromisePlansTest extends BaseTi4Test {

    private static final double EXACT = 1e-9;

    private AiTestGame test;
    private Tile lodor;
    private Tile empty;

    @BeforeEach
    void setUp() {
        test = AiTestGame.withSolAi();
        test.nekroHome();
        lodor = test.place("26", AiTestGame.neighbourOf(AiTestGame.HOME));
        empty = test.place("19", "310");
        test.units(lodor, "space", test.sol, UnitType.Carrier, 1);
        test.game.setPhaseOfGame("action");
    }

    private void promise(Player promiser, Player promisee, Promise.Term term, String target) {
        Promise offer = Promise.offer(test.game, term, promiser, promisee, promisee, "", target, 0, 1, 1, 60_000);
        PromiseBook.save(test.game, offer.accepted(test.game));
    }

    private static TacticalPlan attackOn(Tile tile) {
        return new TacticalPlan(TacticalPlan.Kind.ATTACK, tile.getPosition(), List.of(), Map.of(), 5);
    }

    @Test
    void aNoAttackPromiseRulesOutEverySystemThePromiseeHolds() {
        promise(test.nekro, test.sol, Promise.Term.NO_ATTACK, "");

        assertThat(TacticalPlanner.offLimits(test.game, test.nekro, attackOn(lodor)))
                .isTrue();
        assertThat(TacticalPlanner.offLimits(test.game, test.nekro, attackOn(empty)))
                .isFalse();
        assertThat(TacticalPlanner.offLimits(test.game, test.sol, attackOn(lodor)))
                .isFalse();
    }

    @Test
    void aStayOutPromiseRulesOutOnlyThatSystem() {
        promise(test.nekro, test.sol, Promise.Term.STAY_OUT, empty.getPosition());

        assertThat(TacticalPlanner.offLimits(test.game, test.nekro, attackOn(empty)))
                .isTrue();
        assertThat(TacticalPlanner.offLimits(test.game, test.nekro, attackOn(lodor)))
                .isFalse();
    }

    @Test
    void anAiPromiserIsNotFearedWhereItPromisedPeace() {
        Tile home = test.game.getTileByPosition(AiTestGame.HOME);
        assertThat(ExtortionPlans.fearOf(test.game, test.sol, test.nekro, home)).isCloseTo(1.0, within(EXACT));

        promise(test.sol, test.nekro, Promise.Term.NO_ATTACK, "");

        assertThat(ExtortionPlans.fearOf(test.game, test.sol, test.nekro, home)).isCloseTo(0.0, within(EXACT));
    }

    // A human's word counts like an extortion payer's deal elsewhere: half the usual fear.
    @Test
    void aHumanPromiserIsFearedHalfAsMuch() {
        AiTestGame humanTable = new AiTestGame();
        Tile home = humanTable.nekroHome();
        humanTable.game.setPhaseOfGame("action");
        Promise offer = Promise.offer(
                humanTable.game,
                Promise.Term.NO_ATTACK,
                humanTable.sol,
                humanTable.nekro,
                humanTable.nekro,
                "",
                "",
                0,
                1,
                1,
                60_000);
        PromiseBook.save(humanTable.game, offer.accepted(humanTable.game));

        assertThat(ExtortionPlans.fearOf(humanTable.game, humanTable.sol, humanTable.nekro, home))
                .isCloseTo(ExtortionPlans.PAID_FEAR, within(EXACT));
    }

    @Test
    void controlSeatIgnoresPromises() {
        promise(test.nekro, test.sol, Promise.Term.NO_ATTACK, "");
        promise(test.sol, test.nekro, Promise.Term.NO_ATTACK, "");
        AiExperiments.disable(test.nekro, Set.of(Experiment.PROMISE_DEALS));
        Tile home = test.game.getTileByPosition(AiTestGame.HOME);

        assertThat(TacticalPlanner.offLimits(test.game, test.nekro, attackOn(lodor)))
                .isFalse();
        assertThat(ExtortionPlans.fearOf(test.game, test.sol, test.nekro, home)).isCloseTo(1.0, within(EXACT));
    }
}

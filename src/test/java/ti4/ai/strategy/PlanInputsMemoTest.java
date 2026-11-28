package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.DecisionMemo;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// The inputs cache is shared by every game in the JVM and keeps 32 entries. In a ten-game self-play batch the other
// games pushed a seat's inputs out while one long decision (or the harness's agenda probes) still needed them, so each
// ask ranked the seat's tactical plans again. Clearing the shared cache stands in for those other games here.
class PlanInputsMemoTest extends BaseTi4Test {

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        PlanInputs.clearShared();
        test = new AiTestGame();
        Tile home = test.nekroHome();
        test.aiIsActive("action");
        test.units(home, "space", test.nekro, UnitType.Carrier, 1);
        test.units(home, "space", test.nekro, UnitType.Infantry, 2);
        test.place("28", AiTestGame.neighbourOf(AiTestGame.HOME));
    }

    @AfterEach
    void clear() {
        PlanInputs.clearShared();
    }

    @Test
    void aDecisionKeepsItsInputsWhenOtherGamesPushThemOutOfTheSharedCache() {
        try (DecisionMemo.Open decision = DecisionMemo.open()) {
            PlanInputs first = PlanInputs.of(test.game, test.nekro, PlanMoment.ACTION);
            PlanInputs.clearShared();

            assertThat(PlanInputs.of(test.game, test.nekro, PlanMoment.ACTION)).isSameAs(first);
        }
    }

    // Without the memo, an evicted entry is worked out again: this is what the decision memo saves.
    @Test
    void outsideADecisionAnEvictedEntryIsWorkedOutAgain() {
        PlanInputs first = PlanInputs.of(test.game, test.nekro, PlanMoment.ACTION);
        PlanInputs.clearShared();

        assertThat(PlanInputs.of(test.game, test.nekro, PlanMoment.ACTION)).isNotSameAs(first);
    }

    @Test
    void aSaveChangeInsideTheDecisionWorksThemOutAgain() {
        try (DecisionMemo.Open decision = DecisionMemo.open()) {
            PlanInputs first = PlanInputs.of(test.game, test.nekro, PlanMoment.ACTION);
            test.game.setLastModifiedDate(test.game.getLastModifiedDate() + 1);

            assertThat(PlanInputs.of(test.game, test.nekro, PlanMoment.ACTION)).isNotSameAs(first);
        }
    }

    @Test
    void anOrdinaryDecisionSharesItsInputsWithTheNextOne() {
        PlanInputs first;
        try (DecisionMemo.Open decision = DecisionMemo.open()) {
            first = PlanInputs.of(test.game, test.nekro, PlanMoment.ACTION);
        }
        try (DecisionMemo.Open next = DecisionMemo.open()) {
            assertThat(PlanInputs.of(test.game, test.nekro, PlanMoment.ACTION)).isSameAs(first);
        }
    }

    // The harness's probes run on its own reloaded copy of the game; their inputs stay in the probe's scope, so they
    // never replace the entry the next tick would use.
    @Test
    void anIsolatedScopeKeepsItsInputsOutOfTheSharedCache() {
        PlanInputs shared;
        try (DecisionMemo.Open decision = DecisionMemo.open()) {
            shared = PlanInputs.of(test.game, test.nekro, PlanMoment.ACTION);
        }
        test.game.setLastModifiedDate(test.game.getLastModifiedDate() + 1);
        PlanInputs probed;
        try (DecisionMemo.Open probes = DecisionMemo.openIsolated()) {
            probed = PlanInputs.of(test.game, test.nekro, PlanMoment.ACTION);
            assertThat(PlanInputs.of(test.game, test.nekro, PlanMoment.ACTION)).isSameAs(probed);
        }
        assertThat(probed).isNotSameAs(shared);

        try (DecisionMemo.Open next = DecisionMemo.open()) {
            assertThat(PlanInputs.of(test.game, test.nekro, PlanMoment.ACTION))
                    .isNotSameAs(probed)
                    .isNotSameAs(shared);
        }
    }
}

package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import ti4.ai.AiTestGame;
import ti4.testUtils.BaseTi4Test;

// A stall only pays off at the end of the action phase, so a component action taken instead of a tactical action
// earns no stall credit unless it is urgent or funds this turn's plan. The beforePassing slots keep the credit.
class StallTimingTest extends BaseTi4Test {

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.aiIsActive("action");
    }

    @Test
    void dropsTheStallCreditBeforeATacticalAction() {
        assertThat(Stall.insteadOfTacticalAction(test.game, test.nekro, Stall.ACTION_VALUE))
                .isZero();
    }

    @Test
    void keepsTheStallCreditForAnUrgentComponent() {
        assertThat(Stall.insteadOfTacticalAction(test.game, test.nekro, 99, () -> true))
                .isEqualTo(Stall.actionValue(test.game, test.nekro))
                .isPositive();
    }

    // Once everyone else has passed, a stall is worth nothing, even for an urgent component.
    @Test
    void anUrgentComponentEarnsNoStallOnceEveryoneElsePassed() {
        test.sol.setPassed(true);

        assertThat(Stall.insteadOfTacticalAction(test.game, test.nekro, Stall.ACTION_VALUE, () -> true))
                .isZero();
    }

    // The disabled path does no extra work: the urgency check is never run.
    @Test
    void theDisabledExperimentNeverAsksWhetherTheComponentIsUrgent() {
        AiExperiments.disable(test.nekro, EnumSet.of(Experiment.STALL_TIMING));

        assertThat(Stall.insteadOfTacticalAction(test.game, test.nekro, 2.0, () -> {
                    throw new AssertionError("urgency evaluated with the experiment off");
                }))
                .isEqualTo(2.0);
    }

    @Test
    void withoutStallCreditSubtractsTheCredit() {
        assertThat(Stall.withoutStallCredit(test.nekro, 4.0, 1.5)).isEqualTo(2.5);
    }

    @Test
    void theDisabledExperimentKeepsTheLegacyCredit() {
        AiExperiments.disable(test.nekro, EnumSet.of(Experiment.STALL_TIMING));

        assertThat(Stall.insteadOfTacticalAction(test.game, test.nekro, Stall.ACTION_VALUE))
                .isEqualTo(Stall.ACTION_VALUE);
        assertThat(Stall.insteadOfTacticalAction(test.game, test.nekro, 7.0, () -> true))
                .isEqualTo(7.0);
        assertThat(Stall.withoutStallCredit(test.nekro, 4.0, 1.5)).isEqualTo(4.0);
    }
}

package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.DecisionMemo;
import ti4.ai.eval.ImaginedTurn;
import ti4.testUtils.BaseTi4Test;

// Nekro's expected tech copy values the same technology for every fight the planner estimates, so the value is kept for
// the decision. Fight with Precision makes Destroyer II worth more to Sol (a barrage reach step), which lets these
// tests tell a remembered value from a fresh one: the secret is changed without a save, so only a memo hit keeps the
// old value.
class CopiedTechMemoTest extends BaseTi4Test {

    private static final String DESTROYER_II = "dd2";

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        ResearchPlan.forgetAll();
        test = new AiTestGame();
        test.sol.addTech("ps");
        test.sol.addTech("da");
    }

    private double value() {
        return CopiedTechPolicy.value(test.game, test.sol, DESTROYER_II);
    }

    @Test
    void theSecretChangesTheValue() {
        double without = value();
        test.sol.setSecret("fwp");

        assertThat(value()).isGreaterThan(without);
    }

    @Test
    void aDecisionKeepsTheValueItWorkedOut() {
        try (DecisionMemo.Open ignored = DecisionMemo.open()) {
            double first = value();
            test.sol.setSecret("fwp");

            assertThat(value()).isEqualTo(first);
        }
    }

    @Test
    void nothingIsKeptOutsideADecision() {
        double first = value();
        test.sol.setSecret("fwp");

        assertThat(value()).isGreaterThan(first);
    }

    // A rival imagining Sol's turn cannot see Fight with Precision. Worked out first, inside the same decision, that
    // value must not be handed to Sol's own valuation, which does see the secret.
    @Test
    void anImaginedValueIsNotHandedToTheSeatsOwnValuation() {
        double withoutTheSecret = value();
        test.sol.setSecret("fwp");

        try (DecisionMemo.Open ignored = DecisionMemo.open()) {
            double imagined = ImaginedTurn.of(test.sol, this::value);
            double own = value();

            assertThat(imagined).isEqualTo(withoutTheSecret);
            assertThat(own).isGreaterThan(imagined);
        }
    }
}

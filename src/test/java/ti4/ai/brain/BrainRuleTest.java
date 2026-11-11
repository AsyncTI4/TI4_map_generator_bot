package ti4.ai.brain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BrainRuleTest {

    private static Optional<AiDecision> passes(AiTurnContext context) {
        return Optional.empty();
    }

    // The timing report names each rule after the method it points at, so a rule is found in the code by its name.
    @Test
    void aRuleIsNamedAfterTheMethodItRefersTo() {
        List<BrainRule.Named> named = BrainRule.named(List.of(BrainRuleTest::passes));

        assertThat(named).singleElement().extracting(BrainRule.Named::name).isEqualTo("BrainRuleTest.passes");
    }
}

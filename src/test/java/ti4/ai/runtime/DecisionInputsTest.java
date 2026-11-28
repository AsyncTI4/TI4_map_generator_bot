package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DecisionInputsTest {

    @Test
    void aSeatsFirstDecisionHasNothingToCompareWith() {
        assertThat(new DecisionInputs(10, 1).changeSince(null)).isEqualTo(DecisionInputs.FIRST);
    }

    @Test
    void theSameSaveAndInputsAreUnchanged() {
        assertThat(new DecisionInputs(10, 1).changeSince(new DecisionInputs(10, 1)))
                .isEqualTo(DecisionInputs.UNCHANGED);
    }

    // Prompts, presses or memory moved on while the save stayed the same, as when a rule wrote memory or a message
    // arrived without a save.
    @Test
    void otherInputsChangingUnderTheSameSaveAreToldApart() {
        assertThat(new DecisionInputs(10, 2).changeSince(new DecisionInputs(10, 1)))
                .isEqualTo(DecisionInputs.SAME_SAVE);
    }

    @Test
    void aNewSaveCountsAsSavedWhateverElseMatches() {
        assertThat(new DecisionInputs(11, 1).changeSince(new DecisionInputs(10, 1)))
                .isEqualTo(DecisionInputs.SAVED);
    }
}

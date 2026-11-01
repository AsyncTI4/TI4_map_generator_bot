package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StatusHelperTest {

    @Test
    void pendingScarChoicePreventsAutomaticNoScore() {
        assertThat(StatusHelper.shouldAutoMarkNoPublicObjective(false, true, true))
                .isFalse();
    }

    @Test
    void noScorableObjectiveAndNoScarChoiceStillAutoMarksNoScore() {
        assertThat(StatusHelper.shouldAutoMarkNoPublicObjective(false, true, false))
                .isTrue();
    }

    @Test
    void homeSystemRestrictionStillAutoMarksNoScore() {
        assertThat(StatusHelper.shouldAutoMarkNoPublicObjective(true, false, true))
                .isTrue();
    }

    @Test
    void immediatelyScorableObjectiveDoesNotAutoMarkNoScore() {
        assertThat(StatusHelper.shouldAutoMarkNoPublicObjective(true, true, false))
                .isFalse();
    }
}

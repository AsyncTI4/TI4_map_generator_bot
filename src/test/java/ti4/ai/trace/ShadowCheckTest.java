package ti4.ai.trace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ShadowCheckTest {

    @BeforeEach
    void clear() {
        ShadowCheck.reset();
    }

    @AfterEach
    void restore() {
        ShadowCheck.enable(Boolean.getBoolean(ShadowCheck.PROPERTY));
        ShadowCheck.reset();
    }

    @Test
    void withoutTheShadowTheCandidateIsPlayed() {
        ShadowCheck.enable(false);

        assertThat(ShadowCheck.choose("check", false, () -> true)).isTrue();
        assertThat(ShadowCheck.lines()).singleElement().asString().contains("no checks recorded");
    }

    // In the shadow the current answer is still played, so a shadow batch plays exactly the baseline games.
    @Test
    void inTheShadowTheCurrentAnswerIsPlayedAndADifferenceCounted() {
        ShadowCheck.enable(true);

        ShadowCheck.startDecision();
        assertThat(ShadowCheck.choose("check", false, () -> true)).isFalse();
        assertThat(ShadowCheck.choose("check", true, () -> true)).isTrue();
        ShadowCheck.endDecision();
        ShadowCheck.startDecision();
        ShadowCheck.choose("check", true, () -> true);
        ShadowCheck.endDecision();

        assertThat(ShadowCheck.lines())
                .contains("SHADOW   check: 2 decisions, 1 changed (50.0%); 3 calls, 1 changed")
                .contains("SHADOW   (any check): 2 decisions, 1 changed (50.0%); 0 calls, 0 changed");
    }

    @Test
    void aDecisionThatNeverReachesACheckIsNotCounted() {
        ShadowCheck.enable(true);

        ShadowCheck.startDecision();
        ShadowCheck.endDecision();

        assertThat(ShadowCheck.lines()).singleElement().asString().contains("no checks recorded");
    }
}

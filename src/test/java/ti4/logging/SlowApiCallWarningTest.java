package ti4.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class SlowApiCallWarningTest {

    @Test
    void callsUnderTheThresholdAreNotSlow() {
        assertThat(SlowApiCallWarning.isSlow(4999, Duration.ofSeconds(5))).isFalse();
    }

    @Test
    void callsAtOrOverTheThresholdAreSlow() {
        assertThat(SlowApiCallWarning.isSlow(5000, Duration.ofSeconds(5))).isTrue();
        assertThat(SlowApiCallWarning.isSlow(12_000, Duration.ofSeconds(5))).isTrue();
    }

    @Test
    void thresholdsAreMoreLenientThanTheTwoSecondCommandWarning() {
        assertThat(SlowApiCallWarning.INCOMING_THRESHOLD).isGreaterThan(Duration.ofSeconds(2));
        assertThat(SlowApiCallWarning.OUTGOING_THRESHOLD).isGreaterThan(Duration.ofSeconds(2));
    }

    @Test
    void messageNamesTheDirectionCallOutcomeAndThreshold() {
        String message = SlowApiCallWarning.message(
                "Incoming", "GET /api/public/games/events", "status 200", 7250, Duration.ofSeconds(5));

        assertThat(message)
                .contains("Slow Incoming API Call Warning")
                .contains("Took over 5000ms")
                .contains("`GET /api/public/games/events`")
                .contains("`status 200`");
    }
}

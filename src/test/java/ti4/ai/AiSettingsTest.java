package ti4.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import ti4.testUtils.BaseTi4Test;

class AiSettingsTest extends BaseTi4Test {

    // /ai watch can ask for a fast pace, which shortens the AI's pauses. It only applies while every seat is an AI:
    // a human taking a seat gets the normal timings back. The referee never goes below 10 seconds, because the bot
    // refuses to flip agendas within 6 seconds of each other.
    @Test
    void fastPaceOnlyAppliesWhenNoHumanPlays() {
        AiTestGame allAi = AiTestGame.withSolAi();
        assertThat(AiSettings.refereeDelay(allAi.game)).isEqualTo(AiSettings.REFEREE_DELAY);

        allAi.game.setStoredValue(AiSettings.PACE_KEY, AiSettings.FAST_PACE);
        assertThat(AiSettings.refereeDelay(allAi.game))
                .isLessThan(AiSettings.REFEREE_DELAY)
                .isGreaterThanOrEqualTo(Duration.ofSeconds(10));
        assertThat(AiSettings.maxActionsPerHour(allAi.game)).isGreaterThan(AiSettings.MAX_ACTIONS_PER_HOUR);

        AiTestGame withHuman = new AiTestGame();
        withHuman.game.setStoredValue(AiSettings.PACE_KEY, AiSettings.FAST_PACE);
        assertThat(AiSettings.refereeDelay(withHuman.game)).isEqualTo(AiSettings.REFEREE_DELAY);
        assertThat(AiSettings.stallThreshold(withHuman.game)).isEqualTo(AiSettings.STALL_THRESHOLD);
    }

    // Trading is on unless the JVM is started with -Dai.trading=false (used for A/B self-play runs).
    @Test
    void tradingCanBeSwitchedOff() {
        String before = System.getProperty(AiSettings.TRADING_PROPERTY);
        try {
            System.clearProperty(AiSettings.TRADING_PROPERTY);
            assertThat(AiSettings.isTradingEnabled()).isTrue();

            System.setProperty(AiSettings.TRADING_PROPERTY, "FALSE");
            assertThat(AiSettings.isTradingEnabled()).isFalse();

            System.setProperty(AiSettings.TRADING_PROPERTY, "true");
            assertThat(AiSettings.isTradingEnabled()).isTrue();
        } finally {
            if (before == null) System.clearProperty(AiSettings.TRADING_PROPERTY);
            else System.setProperty(AiSettings.TRADING_PROPERTY, before);
        }
    }
}

package ti4.ai.actioncards;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.testUtils.BaseTi4Test;

// The engine stamps an agenda's reveal in wall-clock time; the AI reads it against its own clock. A seeded self-play
// batch runs a clock a month behind real time, so the stamp looked a month in the future, a Veto never counted as
// settled and was never resolved, and the agenda phase stalled. A stamp from the future is treated as unknown.
class AgendaRevealClockTest extends BaseTi4Test {

    private static final String REVEALED_AT = "lastAgendaReactTime";
    private static final long DAY = 24 * 60 * 60 * 1000L;

    @Test
    void aRevealStampFromTheFutureCountsAsSettled() {
        AiTestGame test = new AiTestGame();

        test.game.setStoredValue(REVEALED_AT, String.valueOf(AiTestGame.NOW + 30 * DAY));
        assertThat(AgendaCards.revealSettled(test.contextFor(test.nekro, Set.of(), AiTestGame.NOW)))
                .isTrue();

        test.game.setStoredValue(REVEALED_AT, String.valueOf(AiTestGame.NOW - 1_000L));
        assertThat(AgendaCards.revealSettled(test.contextFor(test.nekro, Set.of(), AiTestGame.NOW)))
                .isFalse();

        test.game.setStoredValue(REVEALED_AT, String.valueOf(AiTestGame.NOW - 60_000L));
        assertThat(AgendaCards.revealSettled(test.contextFor(test.nekro, Set.of(), AiTestGame.NOW)))
                .isTrue();
    }
}

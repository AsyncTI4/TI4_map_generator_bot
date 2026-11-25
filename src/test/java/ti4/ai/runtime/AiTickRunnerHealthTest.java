package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// The breaker that stops every AI game after 10 press failures in 10 minutes is JVM-wide. Self-play batches run many
// games in one JVM, each on its own virtual clock, and those clocks drift hours apart. Stamping the shared counter with
// each game's virtual time meant a game far behind could never prune a game ahead of it, so one game's failures paused
// the others until their clocks caught up. The shared counter now runs on the wall clock; each game's own count of 3
// still runs on its own clock.
class AiTickRunnerHealthTest {

    private static final long TEN_MINUTES = 10 * 60 * 1000L;
    private static final long BEHIND = 1_790_000_000_000L;
    private static final long AHEAD = BEHIND + 3 * 60 * 60 * 1000L;

    private final AtomicLong wall = new AtomicLong(1_800_000_000_000L);
    private LongSupplier savedClock;

    @BeforeEach
    void setUp() {
        savedClock = AiTickRunner.wallClock;
        AiTickRunner.wallClock = wall::get;
        AiTickRunner.clearRecentFailures();
    }

    @AfterEach
    void tearDown() {
        AiTickRunner.clearRecentFailures();
        AiTickRunner.wallClock = savedClock;
    }

    @Test
    void failuresInAGameAheadOnItsClockExpireForAGameBehindIt() {
        AiLane[] ahead = new AiLane[10];
        for (int game = 0; game < ahead.length; game++) {
            ahead[game] = new AiLane("ahead-" + game);
            AiTickRunner.recordFailure(ahead[game], AHEAD);
        }
        AiLane behind = new AiLane("behind");

        assertThat(AiTickRunner.isCoolingDown(behind, BEHIND)).isTrue();

        wall.addAndGet(TEN_MINUTES + 1);
        assertThat(AiTickRunner.isCoolingDown(behind, BEHIND)).isFalse();
    }

    @Test
    void aGameStillPausesAfterThreeFailuresOnItsOwnClock() {
        AiLane lane = new AiLane("own");
        for (int failure = 0; failure < 3; failure++) AiTickRunner.recordFailure(lane, AHEAD + failure);
        AiLane other = new AiLane("other");

        assertThat(AiTickRunner.isCoolingDown(lane, AHEAD + 3)).isTrue();
        assertThat(AiTickRunner.isCoolingDown(other, BEHIND)).isFalse();
    }
}

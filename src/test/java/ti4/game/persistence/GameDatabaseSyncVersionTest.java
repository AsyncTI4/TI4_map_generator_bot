package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GameDatabaseSyncVersionTest {

    @Test
    void versionsAlwaysIncrease() {
        long previous = GameDatabaseSyncVersion.next();
        for (int i = 0; i < 10_000; i++) {
            long next = GameDatabaseSyncVersion.next();
            assertThat(next).isGreaterThan(previous);
            previous = next;
        }
    }

    @Test
    void versionsStayAheadOfWallClockSoTheySurviveRestarts() {
        // Versions are seeded from the clock, so a restarted process does not issue versions below ones already
        // stored by the previous process.
        long nowInMicroseconds = System.currentTimeMillis() * 1000;
        assertThat(GameDatabaseSyncVersion.next()).isGreaterThanOrEqualTo(nowInMicroseconds);
    }
}

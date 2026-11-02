package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GameFileStampTest {

    @Test
    void matchesOnlyTheSameModificationTimeAndSize() {
        GameFileStamp stored = new GameFileStamp(1000, 500);

        assertThat(stored.matches(new GameFileStamp(1000, 500))).isTrue();
        assertThat(stored.matches(new GameFileStamp(1001, 500))).isFalse();
        // Two saves inside one filesystem clock tick share a modification time, so the size has to differ too.
        assertThat(stored.matches(new GameFileStamp(1000, 501))).isFalse();
    }

    @Test
    void stampWithoutAModificationTimeNeverMatches() {
        // Rows written before the stamp columns existed default to 0, which is also what a missing file reports.
        GameFileStamp stored = new GameFileStamp(0, 0);

        assertThat(stored.matches(new GameFileStamp(0, 0))).isFalse();
    }
}

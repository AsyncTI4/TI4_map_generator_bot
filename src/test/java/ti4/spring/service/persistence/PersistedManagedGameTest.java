package ti4.spring.service.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.game.persistence.ManagedGameState;

class PersistedManagedGameTest {

    @Test
    void matchesOnlyTheExactGameFileModificationTime() {
        PersistedManagedGame persisted = new PersistedManagedGame(state(), 1000);

        assertThat(persisted.matchesGameFile(1000)).isTrue();
        assertThat(persisted.matchesGameFile(1001)).isFalse();
        assertThat(persisted.matchesGameFile(999)).isFalse();
    }

    @Test
    void rowWrittenBeforeModificationTimesWereStoredNeverMatches() {
        // Rows that predate the column default to 0, which is also what a missing game file reports.
        PersistedManagedGame persisted = new PersistedManagedGame(state(), 0);

        assertThat(persisted.matchesGameFile(0)).isFalse();
    }

    private static ManagedGameState state() {
        return new ManagedGameState(
                "pbd1", false, false, false, false, false, false, false, false, false, false, false, 0, 0, null, 0, 0,
                1, null, null, null, null, List.of());
    }
}

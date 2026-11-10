package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ManagedGameStateRevisionTest {

    @Test
    void rowIsTrustedOnlyWhenTheGameFileHasTheSameRevisionKey() {
        ManagedGameState row = stateWithRevision("revision-1");

        assertThat(row.matchesRevision("revision-1")).isTrue();
        assertThat(row.matchesRevision("revision-2")).isFalse();
        // A save file written before revision keys existed has none, so its row is never trusted.
        assertThat(row.matchesRevision(null)).isFalse();
        assertThat(row.matchesRevision(" ")).isFalse();
        assertThat(stateWithRevision(null).matchesRevision(null)).isFalse();
    }

    private static ManagedGameState stateWithRevision(String revisionKey) {
        return new ManagedGameState(
                "pbd1",
                revisionKey,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                false,
                0,
                0,
                null,
                0,
                0,
                1,
                null,
                null,
                null,
                null,
                List.of(),
                Map.of());
    }
}

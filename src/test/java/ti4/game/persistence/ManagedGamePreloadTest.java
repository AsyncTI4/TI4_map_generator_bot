package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ManagedGamePreloadTest {

    private static final ManagedGamePreload.Snapshot PRELOAD = new ManagedGamePreload.Snapshot(
            Map.of(
                    "unchanged", state("unchanged", "rev-1"),
                    "saved-since", state("saved-since", "rev-1"),
                    "deleted-since", state("deleted-since", "rev-1"),
                    "row-behind-file", state("row-behind-file", "rev-1")),
            // row-behind-file's row didn't match its game file during the preload, so it was never verified.
            Set.of("unchanged", "saved-since", "deleted-since"),
            0L);

    @Test
    void reusesOnlyVerifiedGamesWhoseDatabaseRevisionIsUnchangedSinceThePreload() {
        Map<String, String> currentRevisionKeys = Map.of(
                "unchanged", "rev-1",
                "saved-since", "rev-2",
                "row-behind-file", "rev-1",
                "created-since", "rev-1");

        assertThat(PRELOAD.stillCurrent(currentRevisionKeys)).containsExactly("unchanged");
    }

    @Test
    void reusesNothingWhenTheCurrentRevisionKeysAreUnavailable() {
        assertThat(PRELOAD.stillCurrent(Map.of())).isEmpty();
    }

    @Test
    void takeWithoutAPreloadReturnsNothingSoWarmupBuildsFromScratch() {
        assertThat(ManagedGamePreload.take()).isNull();
    }

    private static ManagedGameState state(String gameName, String revisionKey) {
        return new ManagedGameState(
                gameName,
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

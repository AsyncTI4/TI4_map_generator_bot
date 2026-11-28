package ti4.ai.trace;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AiLogStoreTest {

    @TempDir
    Path directory;

    @BeforeEach
    void useTheTempDirectory() {
        AiLogStore.useDirectory(directory);
    }

    @AfterEach
    void goBackToMemory() {
        AiLogStore.keepInMemory();
    }

    @Test
    void valuesComeBackAfterTheInMemoryCopyIsDropped() {
        AiLogStore.put("pbd1", "key|1", "a, b: c; d");

        AiLogStore.forget("pbd1");

        assertThat(AiLogStore.get("pbd1", "key|1")).isEqualTo("a, b: c; d");
        assertThat(AiLogStore.get("pbd1", "missing")).isEmpty();
    }

    // Games are deleted outside the AI code, so the AI sweeps the logs of games that no longer exist.
    @Test
    void logsOfGamesThatNoLongerExistAreDeletedAndTheRestKept() {
        AiLogStore.put("pbd1", "k", "v");
        AiLogStore.put("pbd2", "k", "v");

        AiLogStore.deleteLogsOfMissingGames(Set.of("pbd2")::contains);

        assertThat(directory.resolve("pbd1.properties")).doesNotExist();
        assertThat(directory.resolve("pbd2.properties")).exists();
        AiLogStore.forget("pbd1");
        assertThat(AiLogStore.get("pbd1", "k")).isEmpty();
    }

    @Test
    void sweepingWithoutALogDirectoryDoesNothing() throws Exception {
        Path missing = directory.resolve("nothing-here");
        AiLogStore.useDirectory(missing);

        AiLogStore.deleteLogsOfMissingGames(name -> false);

        assertThat(Files.exists(missing)).isFalse();
    }
}

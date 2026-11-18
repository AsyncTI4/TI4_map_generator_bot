package ti4.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;

class PersistenceManagerTest {

    private static final String FILE_NAME = "data.json";
    private static final TypeReference<Map<String, Integer>> MAP_TYPE = new TypeReference<>() {};

    @TempDir
    Path directory;

    @Test
    void writesAndReadsBackObject() throws IOException {
        PersistenceManager.writeObjectToJsonFile(directory.toString(), FILE_NAME, Map.of("a", 1));

        assertThat(PersistenceManager.readObjectFromJsonFile(directory.toString(), FILE_NAME, MAP_TYPE))
                .containsExactlyEntriesOf(Map.of("a", 1));
    }

    @Test
    void overwritesExistingFile() throws IOException {
        PersistenceManager.writeObjectToJsonFile(directory.toString(), FILE_NAME, Map.of("a", 1, "b", 2, "c", 3));
        PersistenceManager.writeObjectToJsonFile(directory.toString(), FILE_NAME, Map.of("z", 9));

        assertThat(PersistenceManager.readObjectFromJsonFile(directory.toString(), FILE_NAME, MAP_TYPE))
                .containsExactlyEntriesOf(Map.of("z", 9));
    }

    @Test
    void leavesNoTemporaryFileBehind() throws IOException {
        PersistenceManager.writeObjectToJsonFile(directory.toString(), FILE_NAME, Map.of("a", 1));
        PersistenceManager.writeObjectToJsonFile(directory.toString(), FILE_NAME, Map.of("b", 2));

        assertThat(filesIn(directory)).containsExactly(FILE_NAME);
    }

    @Test
    void createsMissingParentDirectory() throws IOException {
        Path nested = directory.resolve("nested");

        PersistenceManager.writeObjectToJsonFile(nested.toString(), FILE_NAME, List.of("x"));

        assertThat(filesIn(nested)).containsExactly(FILE_NAME);
    }

    @Test
    void failedSerializationKeepsPreviousFileAndCleansUp() throws IOException {
        PersistenceManager.writeObjectToJsonFile(directory.toString(), FILE_NAME, Map.of("a", 1));

        // The getter throws after Jackson has started writing the temp file.
        assertThatThrownBy(() ->
                        PersistenceManager.writeObjectToJsonFile(directory.toString(), FILE_NAME, new ExplodingBean()))
                .isInstanceOf(JacksonException.class);

        assertThat(PersistenceManager.readObjectFromJsonFile(directory.toString(), FILE_NAME, MAP_TYPE))
                .containsExactlyEntriesOf(Map.of("a", 1));
        assertThat(filesIn(directory)).containsExactly(FILE_NAME);
    }

    @Test
    void truncatedFileThrowsUncheckedJacksonException() throws IOException {
        // Readers must catch JacksonException, not only IOException, to survive a corrupt file.
        Files.writeString(directory.resolve(FILE_NAME), "{\"a\": 1, \"b\"");

        assertThatThrownBy(() -> PersistenceManager.readObjectFromJsonFile(directory.toString(), FILE_NAME, MAP_TYPE))
                .isInstanceOf(JacksonException.class);
    }

    @Test
    void missingFileReadsAsNull() {
        assertThat(PersistenceManager.readObjectFromJsonFile(directory.toString(), FILE_NAME, MAP_TYPE))
                .isNull();
    }

    static class ExplodingBean {
        public String getFirst() {
            return "written";
        }

        public String getSecond() {
            throw new IllegalStateException("boom");
        }
    }

    private static List<String> filesIn(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString()).toList();
        }
    }
}

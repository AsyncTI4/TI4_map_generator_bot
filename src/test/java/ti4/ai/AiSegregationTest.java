package ti4.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The AI player must stay segregated from the rest of the bot. Code outside {@code ti4.ai} may only
 * reference it at the seams listed here; anything else should be achieved from inside {@code ti4.ai}
 * (polling, state diffs, the AI's own commands and handlers). Adding a seam is a deliberate decision:
 * update this list in the same change and keep the seam a one-liner or a small generic improvement.
 */
class AiSegregationTest {

    private static final Path MAIN_SOURCES = Path.of("src", "main", "java");
    private static final Path AI_PACKAGE = MAIN_SOURCES.resolve(Path.of("ti4", "ai"));
    private static final Pattern AI_REFERENCE = Pattern.compile("\\bti4\\.ai\\.");

    private static final Set<String> SEAMS = Set.of(
            // Registers the /ai slash command.
            "ti4/discord/interactions/commands/SlashCommandManager.java",
            // Lets test-bed games that contain an AI seat still be reset and scripted.
            "ti4/service/testbed/TestBedService.java");

    @Test
    void onlyListedSeamsReferenceTheAiPackage() throws IOException {
        List<String> offenders;
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            offenders = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.startsWith(AI_PACKAGE))
                    .filter(AiSegregationTest::referencesAi)
                    .map(path -> MAIN_SOURCES.relativize(path).toString().replace('\\', '/'))
                    .filter(path -> !SEAMS.contains(path))
                    .sorted()
                    .toList();
        }
        assertThat(offenders)
                .as("Files outside ti4.ai that reference it. Add a seam to AiSegregationTest only deliberately.")
                .isEmpty();
    }

    @Test
    void everyListedSeamStillExists() {
        for (String seam : SEAMS) {
            assertThat(MAIN_SOURCES.resolve(seam)).as("seam " + seam).exists();
        }
    }

    private static boolean referencesAi(Path path) {
        try {
            return AI_REFERENCE
                    .matcher(Files.readString(path, StandardCharsets.UTF_8))
                    .find();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + path, e);
        }
    }
}

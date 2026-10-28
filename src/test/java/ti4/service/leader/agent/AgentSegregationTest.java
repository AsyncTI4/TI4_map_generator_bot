package ti4.service.leader.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The agent core and the official agent modules know nothing about Twilight's Fall, genomes or homebrew: those are
 * plug-ins that the composition root (LeaderAgents) wires in. A source scan keeps that true.
 */
class AgentSegregationTest {

    private static final Path AGENT_SOURCES = Path.of("src", "main", "java", "ti4", "service", "leader", "agent");
    private static final Path GENOME_SOURCES = Path.of("src", "main", "java", "ti4", "service", "leader", "genome");
    private static final String HOMEBREW_DIRECTORY = "homebrew";

    private static final List<String> FORBIDDEN_IN_THE_AGENT_CORE =
            List.of("isTwilightsFallMode", "TFName", "enome", "twilightsfall", "homebrew", "tknova", "_br\"");

    @Test
    void theAgentCoreAndOfficialAgentsMentionNeitherTwilightsFallNorHomebrew() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFilesOutsideHomebrew(AGENT_SOURCES)) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            for (String forbidden : FORBIDDEN_IN_THE_AGENT_CORE) {
                if (source.toLowerCase(Locale.ROOT).contains(forbidden.toLowerCase(Locale.ROOT))) {
                    offenders.add(file.getFileName() + " mentions " + forbidden);
                }
            }
        }

        assertThat(offenders).isEmpty();
    }

    @Test
    void officialAgentModulesDoNotImportGenomesOrHomebrew() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFilesOutsideHomebrew(AGENT_SOURCES)) {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.startsWith("import ti4.service.leader.genome") || line.contains(".homebrew.")) {
                    offenders.add(file.getFileName() + ": " + line);
                }
            }
        }

        assertThat(offenders).isEmpty();
    }

    @Test
    void genomesAreNamedGenomeAndNeverGenomeAgent() throws IOException {
        List<String> misnamed = new ArrayList<>();
        try (Stream<Path> files = Files.walk(GENOME_SOURCES)) {
            files.filter(file -> file.toString().endsWith(".java"))
                    .map(file -> file.getFileName().toString().replace(".java", ""))
                    .filter(name -> name.contains("Agent"))
                    .forEach(misnamed::add);
        }

        assertThat(misnamed).isEmpty();
    }

    private static List<Path> javaFilesOutsideHomebrew(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(file -> file.toString().endsWith(".java"))
                    .filter(file -> !file.toString().contains(HOMEBREW_DIRECTORY))
                    .toList();
        }
    }
}

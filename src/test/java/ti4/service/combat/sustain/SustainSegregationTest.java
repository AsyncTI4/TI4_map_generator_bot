package ti4.service.combat.sustain;

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
 * The sustain core and the official catalog know nothing about Twilight's Fall or homebrew: those catalogs plug in
 * through the composition root (SustainCatalogs). A source scan keeps that true.
 */
class SustainSegregationTest {

    private static final Path SUSTAIN_SOURCES = Path.of("src", "main", "java", "ti4", "service", "combat", "sustain");
    private static final List<String> PLUG_IN_DIRECTORIES = List.of("homebrew", "twilightsfall");

    // Ids and helpers that only exist in homebrew or Twilight's Fall content.
    private static final List<String> FORBIDDEN_IN_THE_CORE = List.of(
            "isTwilightsFallMode",
            "twilightsfall",
            "homebrew",
            "\"tf-",
            "purpletf",
            "dsxinystar",
            "XinTechHandler",
            "glatison",
            "belkosea",
            "sigma_",
            "khrask",
            "mortheus",
            "nivyn",
            "ponthous",
            "vanguard",
            "thurviali",
            "radiant_grafting",
            "kryxos",
            "isScar");

    @Test
    void theSustainCoreAndOfficialCatalogMentionNeitherTwilightsFallNorHomebrew() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : coreJavaFiles()) {
            String source = Files.readString(file, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            for (String forbidden : FORBIDDEN_IN_THE_CORE) {
                if (source.contains(forbidden.toLowerCase(Locale.ROOT))) {
                    offenders.add(file.getFileName() + " mentions " + forbidden);
                }
            }
        }

        assertThat(offenders).isEmpty();
    }

    @Test
    void theSustainCoreDoesNotImportPlugInCatalogs() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : coreJavaFiles()) {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.startsWith("import") && (line.contains(".homebrew.") || line.contains(".twilightsfall."))) {
                    offenders.add(file.getFileName() + ": " + line);
                }
            }
        }

        assertThat(offenders).isEmpty();
    }

    @Test
    void theScanReachesTheCoreAndOfficialCatalog() throws IOException {
        assertThat(coreJavaFiles().stream().map(file -> file.getFileName().toString()))
                .contains("SustainEligibility.java", "OfficialSustainRules.java");
    }

    private static List<Path> coreJavaFiles() throws IOException {
        try (Stream<Path> files = Files.walk(SUSTAIN_SOURCES)) {
            return files.filter(file -> file.toString().endsWith(".java"))
                    .filter(file -> PLUG_IN_DIRECTORIES.stream()
                            .noneMatch(directory ->
                                    SUSTAIN_SOURCES.relativize(file).startsWith(directory)))
                    .toList();
        }
    }
}

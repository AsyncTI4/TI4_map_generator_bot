package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// The run catalog: a benchmark batch (-Dai.selfplay.record=true) leaves a folder with the code state, per-seat rows,
// a README and the console output, and a row in INDEX.md.
class AbRecordTest {

    @TempDir
    Path catalog;

    private static SelfPlayGame.Outcome outcome(int slot, Map<String, Integer> points) {
        return new SelfPlayGame.Outcome(
                SelfPlayGame.Ending.VICTORY_POINTS,
                5,
                "action",
                400,
                0L,
                points,
                List.of(),
                Map.of(),
                List.of(),
                points.size(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "",
                120L,
                slot,
                Set.of(),
                null);
    }

    @Test
    void aBenchmarkBatchLeavesARunFolderAndAnIndexRow() throws IOException {
        System.setProperty(AbRecord.PROPERTY, "true");
        System.setProperty(AbRecord.PROPERTY + ".dir", catalog.toString());
        System.setProperty(AbRecord.PROPERTY + ".label", "Unit Test Run");
        try {
            AbRecord run = AbRecord.start("AbRecordTest", 2, 1).orElseThrow();
            System.out.println("a batch line");
            run.finish(
                    List.of(outcome(0, Map.of("sol", 10, "hacan", 7)), outcome(1, Map.of("sol", 6, "hacan", 10))),
                    SelfPlayGame.Ending::finished,
                    240);

            Path folder = run.folder();
            assertThat(folder.getFileName().toString()).endsWith("-unit-test-run");
            assertThat(Files.readString(folder.resolve("run.json")))
                    .contains("\"kind\" : \"benchmark\"", "\"commit\"", "\"averageLeaderPoints\" : 10.0");
            List<String> rows = Files.readAllLines(folder.resolve("games.csv"));
            assertThat(rows).hasSize(5);
            assertThat(rows).anyMatch(row -> row.contains(",sol,none,10,true"));
            assertThat(Files.readString(folder.resolve("README.md"))).contains("**Question:**", "**Verdict:**");
            try (InputStream log = new GZIPInputStream(Files.newInputStream(folder.resolve("console.log.gz")))) {
                assertThat(new String(log.readAllBytes(), StandardCharsets.UTF_8))
                        .contains("a batch line");
            }
            String index = Files.readString(catalog.resolve("INDEX.md"));
            assertThat(index).contains("| Run | Date |", "runs/" + folder.getFileName() + "/README.md", "benchmark");
        } finally {
            System.clearProperty(AbRecord.PROPERTY);
            System.clearProperty(AbRecord.PROPERTY + ".dir");
            System.clearProperty(AbRecord.PROPERTY + ".label");
        }
    }

    // A snapshot continuation run is recorded too, marked as such, with one row per continuation pair.
    @Test
    void aSnapshotContinuationIsRecordedAsOne() throws IOException {
        System.setProperty(AbRecord.PROPERTY + ".dir", catalog.toString());
        try {
            AbRecord run = AbRecord.startContinuation(
                            "AbRecordTest",
                            java.util.EnumSet.of(ti4.ai.AiExperiments.Experiment.VOTE_ANGER),
                            7000,
                            1,
                            1,
                            Map.of("horizonRounds", 1))
                    .orElseThrow();
            ContinuationAb.Arm a = new ContinuationAb.Arm(Set.of("sol"), Map.of("sol", 2, "hacan", 1));
            ContinuationAb.Arm b = new ContinuationAb.Arm(Set.of("hacan"), Map.of("sol", 1, "hacan", 1));
            run.finishContinuation(
                    List.of(new ContinuationAb.Pair(7000, 2, 0, a, b)), 0, Map.of("STOP_CONDITION", 2), 3, 4, 9, 60);

            Path folder = run.folder();
            assertThat(folder.getFileName().toString()).endsWith("-continuation-vote-anger");
            assertThat(Files.readString(folder.resolve("run.json")))
                    .contains("\"kind\" : \"snapshot-continuation\"", "\"gainDifferenceOverGames\"");
            assertThat(Files.readAllLines(folder.resolve("pairs.csv"))).hasSize(2);
            assertThat(Files.readString(catalog.resolve("INDEX.md"))).contains("snapshot continuation: [VOTE_ANGER]");
        } finally {
            System.clearProperty(AbRecord.PROPERTY + ".dir");
        }
    }

    @Test
    void labelsBecomeFolderSafeSlugs() {
        assertThat(AbRecord.slug("RELATIONS_ANGER+VOTE_ANGER")).isEqualTo("relations-anger+vote-anger");
        assertThat(AbRecord.slug("  Odd / label!  ")).isEqualTo("odd-label");
    }
}

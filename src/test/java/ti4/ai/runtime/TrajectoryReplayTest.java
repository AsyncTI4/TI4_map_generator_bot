package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import ti4.ai.brain.AiDecision;
import ti4.game.persistence.GameManager;
import ti4.testUtils.BaseTi4Test;
import tools.jackson.databind.JsonNode;

// Plays the first round of a six-seat self-play game with the trajectory log on, then asks each seat's brain again for
// every recorded decision from its snapshot. A decision that comes out differently points at state the snapshot does
// not hold (seat state kept by the runtime, randomness, the clock), which is what reproducible games have to close.
// mvn -o test -Dtest=TrajectoryReplayTest -Dai.selfplay.replay=true
@EnabledIfSystemProperty(named = "ai.selfplay.replay", matches = "true")
class TrajectoryReplayTest extends BaseTi4Test {

    private static final List<String> FACTIONS = List.of("nekro", "sardakk", "hacan", "letnev", "sol", "xxcha");
    private static final long VIRTUAL_DAY = 24 * 60 * 60 * 1000L;
    private static final int MAX_TICKS = 3000;

    @Test
    void recordedDecisionsReplayFromTheirSnapshots() throws Exception {
        Path folder = Files.createTempDirectory("ai-trajectories");
        String previous = System.getProperty(TrajectoryLog.PROPERTY);
        System.setProperty(TrajectoryLog.PROPERTY, folder.toString());
        System.setProperty(TrajectoryLog.SNAPSHOTS_PROPERTY, "true");
        try (SelfPlayEnvironment env = new SelfPlayEnvironment(System.currentTimeMillis())) {
            SelfPlayGame selfPlay =
                    new SelfPlayGame(env, "aitraj" + ThreadLocalRandom.current().nextInt(100_000, 1_000_000));
            try {
                selfPlay.setUp(FACTIONS);
                SelfPlayGame.Outcome outcome = selfPlay.run(game -> game.getRound() >= 2, VIRTUAL_DAY, MAX_TICKS);
                // Replays go through the same recording brains, so the finished log is detached first.
                env.trajectory.close();
                env.trajectory = null;

                List<JsonNode> lines = new ArrayList<>();
                Path log = folder.resolve(selfPlay.name + TrajectoryLog.LINES_SUFFIX);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        new GZIPInputStream(Files.newInputStream(log)), StandardCharsets.UTF_8))) {
                    reader.lines().forEach(line -> lines.add(TrajectoryLog.JSON.readTree(line)));
                }
                assertThat(lines).as(outcome.report()).isNotEmpty();
                assertThat(lines.getLast().get("type").asString()).isEqualTo("outcome");
                long recorded = lines.stream()
                        .filter(line -> "decision".equals(line.get("type").asString()))
                        .count();

                Path snapshots = folder.resolve(selfPlay.name + ".snapshots.zip");
                List<Integer> decisions = TrajectoryReplay.decisions(snapshots);
                assertThat((long) decisions.size()).isEqualTo(recorded);

                int same = 0;
                int presses = 0;
                List<String> differing = new ArrayList<>();
                for (int index : decisions) {
                    JsonNode line = decisionLine(lines, index);
                    if (!"press".equals(line.get("kind").asString())) continue;
                    presses++;
                    String expected = line.get("chosen").get("customId").asString();
                    AiDecision again = TrajectoryReplay.decide(env, TrajectoryReplay.load(snapshots, index));
                    Optional<String> replayed = TrajectoryReplay.choice(again);
                    if (replayed.filter(expected::equals).isPresent()) {
                        same++;
                    } else if (differing.size() < 20) {
                        differing.add(index + " " + line.get("seat").asString() + ": " + expected + " ("
                                + line.get("reason").asString() + ") became " + replayed.orElse(again.toString()));
                    }
                }
                System.out.printf(
                        "TRAJECTORY REPLAY %d decision lines, %d presses replayed, %d the same (%.0f%%)%n",
                        recorded, presses, same, presses == 0 ? 0.0 : 100.0 * same / presses);
                differing.forEach(line -> System.out.println("  differs: " + line));
                assertThat(presses).isPositive();
                assertThat(same).as("presses that replay to the same choice").isEqualTo(presses);
            } finally {
                AiRuntime.forget(selfPlay.name);
                GameManager.delete(selfPlay.name);
            }
        } finally {
            System.clearProperty(TrajectoryLog.SNAPSHOTS_PROPERTY);
            if (previous == null) System.clearProperty(TrajectoryLog.PROPERTY);
            else System.setProperty(TrajectoryLog.PROPERTY, previous);
        }
    }

    private static JsonNode decisionLine(List<JsonNode> lines, int index) {
        return lines.stream()
                .filter(line -> "decision".equals(line.get("type").asString()))
                .filter(line -> line.get("index").asInt() == index)
                .findFirst()
                .orElseThrow();
    }
}

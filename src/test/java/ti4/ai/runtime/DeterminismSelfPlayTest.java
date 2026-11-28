package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import ti4.ai.strategy.SecondaryQueue;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedPlayers;
import ti4.testUtils.BaseTi4Test;

// Plays one seeded six-seat game twice, one after the other, with the same name, virtual clock start and seed, and
// compares the two trajectory logs line by line. Any difference is randomness or state the seed does not reach.
// mvn -o test -Dtest=DeterminismSelfPlayTest -Dai.selfplay.determinism=true [-Dai.selfplay.determinism.rounds=2]
@EnabledIfSystemProperty(named = "ai.selfplay.determinism", matches = "true")
class DeterminismSelfPlayTest extends BaseTi4Test {

    private static final List<String> FACTIONS = List.of("nekro", "sardakk", "hacan", "letnev", "sol", "xxcha");
    private static final long VIRTUAL_DAY = 24 * 60 * 60 * 1000L;
    private static final long CLOCK_START = 1_790_000_000_000L;
    private static final long SEED = Long.getLong("ai.selfplay.determinism.seed", 20261025L);
    private static final int ROUNDS = Integer.getInteger("ai.selfplay.determinism.rounds", 2);
    private static final String NAME = "aidet" + SEED % 1_000_000;

    @Test
    void theSameSeedPlaysTheSameGame() throws Exception {
        List<String> first = play(Files.createTempDirectory("ai-determinism-a"));
        List<String> second = play(Files.createTempDirectory("ai-determinism-b"));

        int differs = -1;
        for (int i = 0; i < Math.min(first.size(), second.size()); i++) {
            if (!first.get(i).equals(second.get(i))) {
                differs = i;
                break;
            }
        }
        if (differs < 0 && first.size() != second.size()) differs = Math.min(first.size(), second.size());
        System.out.printf(
                "DETERMINISM %d and %d lines, first difference at %s%n", first.size(), second.size(), differs);
        if (differs >= 0) {
            for (int i = Math.max(0, differs - 2); i <= differs; i++) {
                System.out.println("  A " + i + ": " + shorten(i < first.size() ? first.get(i) : "(none)"));
                System.out.println("  B " + i + ": " + shorten(i < second.size() ? second.get(i) : "(none)"));
            }
        }
        assertThat(differs).as("first differing trajectory line").isEqualTo(-1);
    }

    private static List<String> play(Path folder) throws Exception {
        String previous = System.getProperty(TrajectoryLog.PROPERTY);
        System.setProperty(TrajectoryLog.PROPERTY, folder.toString());
        try (SelfPlayEnvironment env = new SelfPlayEnvironment(CLOCK_START)) {
            SelfPlayGame selfPlay = new SelfPlayGame(env, NAME, 0, SEED);
            List<String> seats = new ArrayList<>();
            try {
                selfPlay.setUp(FACTIONS).getRealPlayers().forEach(player -> seats.add(player.getUserID()));
                selfPlay.run(game -> game.getRound() > ROUNDS, 14 * VIRTUAL_DAY, 100_000);
            } finally {
                AiRuntime.forget(selfPlay.name);
                SecondaryQueue.forget(selfPlay.name);
                GameManager.delete(selfPlay.name);
                ManagedPlayers.forgetWithoutGames(seats);
            }
        } finally {
            if (previous == null) System.clearProperty(TrajectoryLog.PROPERTY);
            else System.setProperty(TrajectoryLog.PROPERTY, previous);
        }
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(Files.newInputStream(folder.resolve(NAME + TrajectoryLog.LINES_SUFFIX))),
                StandardCharsets.UTF_8))) {
            reader.lines().forEach(lines::add);
        }
        return lines;
    }

    private static String shorten(String line) {
        return line.length() > 900 ? line.substring(0, 900) + "..." : line;
    }
}

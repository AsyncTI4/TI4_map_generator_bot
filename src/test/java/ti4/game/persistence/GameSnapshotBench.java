package ti4.game.persistence;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import ti4.game.Game;
import ti4.testUtils.BaseTi4Test;

// Times GameSnapshots.copy on real saves; the render pipeline pays this on the queueing thread.
// mvn -o test -Dtest=GameSnapshotBench -Dgolden.saves=DIR
@EnabledIfSystemProperty(named = "golden.saves", matches = ".+")
class GameSnapshotBench extends BaseTi4Test {

    private static final int RUNS = 15;

    @Test
    void timeSnapshots() throws Exception {
        List<Path> saves;
        try (Stream<Path> files = Files.list(Path.of(System.getProperty("golden.saves")))) {
            saves = files.filter(path -> path.toString().endsWith(".txt"))
                    .sorted()
                    .toList();
        }
        List<Double> medians = new ArrayList<>();
        for (Path save : saves) {
            Game game = GameLoadService.readGame(Files.readAllLines(new File(save.toString()).toPath()));
            for (int i = 0; i < 3; i++) GameSnapshots.copy(game);
            List<Double> times = new ArrayList<>();
            long allocatedBefore = allocatedBytes();
            for (int i = 0; i < RUNS; i++) {
                long start = System.nanoTime();
                GameSnapshots.copy(game);
                times.add((System.nanoTime() - start) / 1e6);
            }
            long allocatedPerCopy = (allocatedBytes() - allocatedBefore) / RUNS;
            Collections.sort(times);
            double median = times.get(RUNS / 2);
            medians.add(median);
            System.out.printf(
                    "[snapshot] %s players=%d median=%.1fms max=%.1fms allocated=%dKB%n",
                    game.getName(), game.getRealPlayers().size(), median, times.getLast(), allocatedPerCopy / 1024);
        }
        Collections.sort(medians);
        System.out.printf(
                "[snapshot] TOTAL games=%d median=%.1fms p95=%.1fms max=%.1fms%n",
                medians.size(),
                medians.get(medians.size() / 2),
                medians.get((int) Math.ceil(medians.size() * 0.95) - 1),
                medians.getLast());
    }

    private static long allocatedBytes() {
        return ((com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean())
                .getCurrentThreadAllocatedBytes();
    }
}

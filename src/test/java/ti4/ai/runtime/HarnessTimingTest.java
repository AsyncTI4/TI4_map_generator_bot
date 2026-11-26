package ti4.ai.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HarnessTimingTest {

    private static final long SECOND = 1_000_000_000L;

    @Test
    void closesARoundWhenTheNextOneStartsAndTotalsAtTheEnd() {
        HarnessTiming timing = new HarnessTiming("aiseed1");
        timing.start();
        timing.observeRound(1, () -> 2048);
        timing.iteration();
        timing.add(HarnessTiming.TICK, 3 * SECOND, 2 * SECOND);
        timing.add(HarnessTiming.RELOAD, SECOND, SECOND);
        timing.add(HarnessTiming.RELOAD, SECOND, SECOND);
        timing.add("track.spending", SECOND / 10, SECOND / 10);
        // Still round 1: nothing is printed yet.
        timing.observeRound(1, () -> 2048);
        assertThat(timing.lines()).isEmpty();

        // The save size printed for a round is the one when it closes.
        timing.observeRound(2, () -> 4096);
        timing.iteration();
        timing.add("track.factionComponents", SECOND / 2, SECOND / 4);
        timing.finish(8192);

        assertThat(timing.lines()).hasSize(3);
        assertThat(timing.lines().get(0))
                .startsWith("HARNESS aiseed1 r1: ")
                .contains("ticks 3.0 s [2.0 s] x1")
                .contains("reloads 2.0 s [2.0 s] x2 (1000.0 ms each)")
                .contains("track.spending 0.1 s [0.1 s]")
                .contains("iterations 1, save 4 KB");
        assertThat(timing.lines().get(1))
                .startsWith("HARNESS aiseed1 r2: ")
                .contains("reloads 0.0 s [0.0 s] x0,")
                .contains("track.factionComponents 0.5 s [0.3 s]");
        assertThat(timing.lines().get(2))
                .startsWith("HARNESS aiseed1 total: ")
                .contains("ticks 3.0 s [2.0 s] x1")
                .contains("reloads 2.0 s [2.0 s] x2")
                .contains("track.factionComponents 0.5 s [0.3 s]")
                .contains("iterations 2, save 8 KB");
    }

    @Test
    void listsTheCostliestPartsFirst() {
        HarnessTiming timing = new HarnessTiming("aiseed3");
        timing.start();
        timing.observeRound(1, () -> 0);
        timing.add("track.small", SECOND, SECOND);
        timing.add("track.large", 5 * SECOND, 5 * SECOND);
        timing.finish(0);

        String line = timing.lines().getFirst();
        assertThat(line.indexOf("track.large")).isLessThan(line.indexOf("track.small"));
    }

    @Test
    void finishesOnlyOnce() {
        HarnessTiming timing = new HarnessTiming("aiseed2");
        timing.start();
        timing.observeRound(1, () -> 0);
        timing.finish(0);
        timing.finish(0);

        assertThat(timing.lines()).hasSize(2);
    }
}

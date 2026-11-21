package ti4.ai.brain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.game.Game;

class TickMemoTest {

    private final Game game = new Game();
    private final Game other = new Game();

    @Test
    void aTickHandsTheSameMapToEveryCallerForItsGame() {
        try (TickMemo.Open tick = TickMemo.open(game)) {
            Map<String, Integer> first = TickMemo.mapFor(game, "scans");
            first.put("a", 1);

            assertThat(TickMemo.<String, Integer>mapFor(game, "scans")).containsEntry("a", 1);
            assertThat(TickMemo.<String, Integer>mapFor(game, "reaches")).isEmpty();
        }
    }

    @Test
    void anotherGameAndNoTickGetAFreshMapEveryTime() {
        try (TickMemo.Open tick = TickMemo.open(game)) {
            TickMemo.<String, Integer>mapFor(other, "scans").put("a", 1);

            assertThat(TickMemo.<String, Integer>mapFor(other, "scans")).isEmpty();
        }
        TickMemo.<String, Integer>mapFor(game, "scans").put("a", 1);

        assertThat(TickMemo.<String, Integer>mapFor(game, "scans")).isEmpty();
    }

    // With verification on, a shared answer is recomputed with sharing switched off, and a stale one is counted.
    @Test
    void verificationRecomputesWithoutSharingAndCountsADifference() {
        TickMemo.resetVerification();
        TickMemo.verify(true);
        try (TickMemo.Open tick = TickMemo.open(game)) {
            TickMemo.<String, Integer>mapFor(game, "scans").put("a", 1);

            Integer kept = TickMemo.checked(
                    "scans",
                    "a",
                    1,
                    () -> TickMemo.<String, Integer>mapFor(game, "scans").getOrDefault("a", 2));

            assertThat(kept).isEqualTo(1);
            assertThat(TickMemo.verificationLines())
                    .containsExactly("TICKMEMO scans: 1 shared answers checked, 1 differed", "TICKMEMO   scans a");
            assertThat(TickMemo.<String, Integer>mapFor(game, "scans")).containsEntry("a", 1);
        } finally {
            TickMemo.verify(Boolean.getBoolean(TickMemo.VERIFY_PROPERTY));
            TickMemo.resetVerification();
        }
    }

    // Closing a tick drops everything it shared, and an inner tick restores the outer one when it closes.
    @Test
    void closingATickForgetsItAndRestoresTheOuterOne() {
        try (TickMemo.Open outer = TickMemo.open(game)) {
            TickMemo.<String, Integer>mapFor(game, "scans").put("outer", 1);
            try (TickMemo.Open inner = TickMemo.open(game)) {
                assertThat(TickMemo.<String, Integer>mapFor(game, "scans")).isEmpty();
            }
            assertThat(TickMemo.<String, Integer>mapFor(game, "scans")).containsKey("outer");
        }
        try (TickMemo.Open next = TickMemo.open(game)) {
            assertThat(TickMemo.<String, Integer>mapFor(game, "scans")).isEmpty();
        }
    }
}

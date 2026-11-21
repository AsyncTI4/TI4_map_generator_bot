package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.helpers.GameClock;
import ti4.testUtils.BaseTi4Test;

// A save stamps the game with its last modified time, and the AI and its caches treat a new stamp as "the game
// changed". The stamp comes from the thread's game clock, so self-play on a virtual clock stamps virtual time and a
// seed replays the same game however fast the machine runs. Two saves at the same instant still get different stamps;
// on the wall clock, two saves in one millisecond used to look like no change at all.
class SaveStampClockTest extends BaseTi4Test {

    private final AtomicLong clock = new AtomicLong(1_790_000_000_000L);
    private Game game;

    @BeforeEach
    void setUp() {
        GameClock.useForThisThread(clock::get);
        game = new Game();
        game.newGameSetup();
        game.setName("save-stamp-clock-test");
    }

    @AfterEach
    void tearDown() {
        GameClock.useWallClockForThisThread();
    }

    @Test
    void aNewGameAndALaterSaveAreStampedWithTheThreadsGameClock() throws Exception {
        assertThat(game.getLastModifiedDate()).isEqualTo(clock.get());
        assertThat(game.getCreationDateTime()).isEqualTo(clock.get());

        clock.addAndGet(10_000);
        GameSaveService.writeGame(game, new StringWriter());

        assertThat(game.getLastModifiedDate()).isEqualTo(clock.get());
    }

    @Test
    void twoSavesAtTheSameInstantGetDifferentStamps() throws Exception {
        GameSaveService.writeGame(game, new StringWriter());
        long first = game.getLastModifiedDate();
        GameSaveService.writeGame(game, new StringWriter());

        assertThat(game.getLastModifiedDate()).isGreaterThan(first);
    }

    @Test
    void withoutAGameClockASaveUsesTheWallClock() throws Exception {
        GameClock.useWallClockForThisThread();
        long before = System.currentTimeMillis();
        GameSaveService.writeGame(game, new StringWriter());

        assertThat(game.getLastModifiedDate()).isBetween(before, System.currentTimeMillis() + 1);
    }
}

package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import ti4.testUtils.BaseTi4Test;

@SuppressWarnings("removal")
class GameDatabaseSyncPipelineResyncCountTest extends BaseTi4Test {

    @Test
    void resyncThatFindsNoGameFileStillLeavesTheBacklog() {
        int queuedBefore = GameDatabaseSyncPipeline.queuedResyncs();

        // The resync runs on the pipeline's own thread, so static mocks would not reach it; a game with no file
        // takes the same early exit a game deleted after warmup does.
        GameDatabaseSyncPipeline.queueSyncFromGameFile("pbd-resync-count-missing-game");

        assertThat(GameDatabaseSyncPipeline.queuedResyncs()).isEqualTo(queuedBefore + 1);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (GameDatabaseSyncPipeline.pendingResyncs() > 0 && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertThat(GameDatabaseSyncPipeline.pendingResyncs()).isZero();
    }
}

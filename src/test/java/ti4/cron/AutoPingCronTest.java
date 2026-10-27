package ti4.cron;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.persistence.ManagedGame;

class AutoPingCronTest {

    @Test
    void onlyActiveGamesWithPingingEnabledAndPingMetadataAreLoaded() {
        Game enabled = newGame();
        enabled.setAutoPing(true);
        Game paused = newGame();
        paused.setAutoPing(true);
        paused.setTemporaryPingDisable(true);
        Game disabled = newGame();
        Game ended = newGame();
        ended.setAutoPing(true);
        ended.setHasEnded(true);
        Set<String> withMetadata = Set.of(enabled.getName(), paused.getName(), disabled.getName(), ended.getName());

        assertThat(AutoPingCron.isDueForAutoPingCheck(new ManagedGame(enabled), withMetadata))
                .isTrue();
        // No AutoPing.json entry means handleAutoPing would return without pinging.
        assertThat(AutoPingCron.isDueForAutoPingCheck(new ManagedGame(enabled), Set.of()))
                .isFalse();
        assertThat(AutoPingCron.isDueForAutoPingCheck(new ManagedGame(paused), withMetadata))
                .isFalse();
        assertThat(AutoPingCron.isDueForAutoPingCheck(new ManagedGame(disabled), withMetadata))
                .isFalse();
        assertThat(AutoPingCron.isDueForAutoPingCheck(new ManagedGame(ended), withMetadata))
                .isFalse();
    }

    private static Game newGame() {
        Game game = new Game();
        game.setName("auto-ping-" + UUID.randomUUID());
        return game;
    }
}

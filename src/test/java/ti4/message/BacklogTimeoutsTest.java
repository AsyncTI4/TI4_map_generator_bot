package ti4.message;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.service.megagame.MegagameGroupService;

class BacklogTimeoutsTest {

    private static final String GAME_NAME = "backlogtimeoutstest";

    @Test
    void onlyNonFogGamesWithTheMegagameTagAreTracked() {
        Game game = new Game();
        game.setName(GAME_NAME);
        try {
            BacklogTimeouts.track(game);
            assertThat(BacklogTimeouts.isTracked(GAME_NAME)).isFalse();

            game.addTag(MegagameGroupService.SC_GROUPS_TAG);
            BacklogTimeouts.track(game);
            assertThat(BacklogTimeouts.isTracked(GAME_NAME)).isTrue();

            // The tag can reach a fog game through free-text tag editing; fog must keep the old timeouts.
            game.setFowMode(true);
            BacklogTimeouts.track(game);
            assertThat(BacklogTimeouts.isTracked(GAME_NAME)).isFalse();
        } finally {
            game.removeTag(MegagameGroupService.SC_GROUPS_TAG);
            BacklogTimeouts.track(game);
        }
    }

    @Test
    void eachPendingSendExtendsTheTimeoutUpToTheCap() {
        assertThat(BacklogTimeouts.timeoutSeconds(0)).isEqualTo(60);
        assertThat(BacklogTimeouts.timeoutSeconds(80)).isEqualTo(220);
        assertThat(BacklogTimeouts.timeoutSeconds(10_000)).isEqualTo(BacklogTimeouts.MAX_TIMEOUT_SECONDS);
    }

    @Test
    void releasedSlotsFreeTheChannel() {
        String channel = "backlog-timeouts-test-channel";
        assertThat(BacklogTimeouts.reserve(channel)).isZero();
        assertThat(BacklogTimeouts.reserve(channel)).isEqualTo(1);
        BacklogTimeouts.release(channel);
        BacklogTimeouts.release(channel);
        // Both sends finished, so the next one is first in line again.
        assertThat(BacklogTimeouts.reserve(channel)).isZero();
        BacklogTimeouts.release(channel);
    }
}

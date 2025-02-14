package ti4.map;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GameStatisticsDashboardPayloadTest {

    @Test
    void getSetupTime() {
        var game = createGame();
        game.setCreationDate("2024.10.30");

        var setupTimestamp = new GameStatsDashboardPayload(game).getSetupTimestamp();

        assertThat(setupTimestamp).isEqualTo(1730248570L);
    }

    @Test
    void getSetupTimeHandlesException() {
        var game = createGame();
        game.setCreationDate("2024-10-30");

        var setupTimestamp = new GameStatsDashboardPayload(game).getSetupTimestamp();

        assertThat(String.valueOf(setupTimestamp)).endsWith("70");
    }

    private Game createGame() {
        var game = new Game();
        game.setName("pbd123");
        game.setCustomName("pbd123-a-test-for-you-and-me");
        return game;
    }
}

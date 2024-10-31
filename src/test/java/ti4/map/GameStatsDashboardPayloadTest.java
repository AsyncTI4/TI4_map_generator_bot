package ti4.map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameStatsDashboardPayloadTest {

    @Test
    void getSetupTime() {
        var game = new Game();
        game.setCreationDate("2024.10.30");

        var setupTimestamp = new GameStatsDashboardPayload(game).getSetupTimestamp();

        assertThat(setupTimestamp).isEqualTo("1730260800");
    }

    @Test
    void getSetupTimeHandlesException() {
        var game = new Game();
        game.setCreationDate("2024-10-30");

        var setupTimestamp = new GameStatsDashboardPayload(game).getSetupTimestamp();

        assertThat(setupTimestamp).matches(str -> str.length() >= 10);
    }

}
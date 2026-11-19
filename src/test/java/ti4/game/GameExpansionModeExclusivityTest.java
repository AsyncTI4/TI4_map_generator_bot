package ti4.game;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.testUtils.BaseTi4Test;

class GameExpansionModeExclusivityTest extends BaseTi4Test {

    @Test
    void turningOnTwilightsFallTurnsOffThundersEdge() {
        Game game = new Game();
        game.setThundersEdge(true);

        game.setTwilightsFallMode(true);

        assertThat(game.isTwilightsFallMode()).isTrue();
        assertThat(game.isThundersEdge()).isFalse();
    }

    @Test
    void turningOnThundersEdgeTurnsOffTwilightsFall() {
        Game game = new Game();
        game.setTwilightsFallMode(true);

        game.setThundersEdge(true);

        assertThat(game.isThundersEdge()).isTrue();
        assertThat(game.isTwilightsFallMode()).isFalse();
    }

    @Test
    void turningOneModeOffLeavesTheOtherAlone() {
        Game game = new Game();
        game.setThundersEdge(true);
        game.setTwilightsFallMode(false);
        assertThat(game.isThundersEdge()).isTrue();

        game.setTwilightsFallMode(true);
        game.setThundersEdge(false);
        assertThat(game.isTwilightsFallMode()).isTrue();
    }

    @Test
    void aSaveWithBothModesOnLoadsAsTwilightsFall() {
        // Saves write thunders_edge_mode before twilights_fall_mode, so the loader applies them in that order.
        Game game = new Game();
        game.setThundersEdge(true);
        game.setTwilightsFallMode(true);

        assertThat(game.isTwilightsFallMode()).isTrue();
        assertThat(game.isThundersEdge()).isFalse();
    }
}

package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;

class ButtonHelperStatusACsTest {

    // The megagame summary path must keep the guard against dealing status action cards twice.
    @Test
    void playerWhoAlreadyDrewGetsNoSummaryLine() {
        Game game = new Game();
        Player player = new Player("player1", "", game);
        player.setFaction("sol");
        game.setCurrentACDrawStatusInfo("_sol");

        assertThat(ButtonHelper.drawStatusACsForSummary(game, player)).isNull();
        assertThat(player.getActionCards()).isEmpty();
    }
}

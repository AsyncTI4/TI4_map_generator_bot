package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class OplusplusConfirmedRoundTripTest extends BaseTi4Test {

    @Test
    void oplusplusCouncilConfirmedSurvivesSaveAndLoad() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            Player player = game.getRealPlayers().getFirst();
            player.setOplusplusCouncilConfirmed(true);
            GameSaveService.save(game, "test");

            Game reloaded = harness.load();
            Player reloadedPlayer = reloaded.getPlayer(player.getUserID());

            assertThat(reloadedPlayer.isOplusplusCouncilConfirmed()).isTrue();
        }
    }
}

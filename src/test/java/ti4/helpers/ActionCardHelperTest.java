package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.GameStats;
import ti4.game.Player;

class ActionCardHelperTest {

    @Test
    void shouldRecordGenericActionCardPlayByName() {
        var game = new Game();
        var player = new Player("player1", "", game);

        ActionCardHelper.recordTrackedActionCardPlay(game, player, "Flank Speed");

        assertThat(game.getGameStats().getTotalPlays("Flank Speed")).isEqualTo(1);
        assertThat(game.getGameStats().getActionCardPlays())
                .singleElement()
                .extracting(GameStats.ActionCardPlay::getPlayerId)
                .isEqualTo("player1");
    }

    @Test
    void shouldNotDuplicateSabotageAndOverruleTargetTracking() {
        var game = new Game();
        var player = new Player("player1", "", game);

        ActionCardHelper.recordTrackedActionCardPlay(game, player, GameStats.SABOTAGE);
        ActionCardHelper.recordTrackedActionCardPlay(game, player, GameStats.OVERRULE);

        assertThat(game.getGameStats().getTotalPlays(GameStats.SABOTAGE)).isZero();
        assertThat(game.getGameStats().getTotalPlays(GameStats.OVERRULE)).isZero();
        assertThat(game.getGameStats().getActionCardPlays()).isEmpty();
    }
}
